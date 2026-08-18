[CmdletBinding()]
param(
    [string] $JarPath = (Join-Path $PSScriptRoot "..\build\libs\log-monitoring-system-0.0.1-SNAPSHOT.jar"),
    [int] $Port = 18080,
    [int] $StartupTimeoutSeconds = 30,
    [int] $ShutdownTimeoutSeconds = 15
)

$ErrorActionPreference = "Stop"
$resolvedJar = (Resolve-Path -LiteralPath $JarPath).Path
$runId = [Guid]::NewGuid().ToString("N")
$logDirectory = Join-Path ([System.IO.Path]::GetTempPath()) ("log-monitoring-shutdown-" + $runId)
$stdoutPath = Join-Path $logDirectory "stdout.log"
$stderrPath = Join-Path $logDirectory "stderr.log"
$jcmd = Get-Command jcmd -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
$javaPath = if ($null -ne $jcmd) {
    Join-Path (Split-Path -Parent $jcmd.Source) "java.exe"
} else {
    (Get-Command java -CommandType Application | Select-Object -Last 1).Source
}
$process = $null
$httpClient = [System.Net.Http.HttpClient]::new()
$httpClient.Timeout = [TimeSpan]::FromSeconds(2)
$lastReadinessStatus = "no response"
$keepLogs = $false
$environmentNames = @(
    "SPRING_PROFILES_ACTIVE",
    "MONGODB_URI",
    "SERVER_PORT",
    "SHUTDOWN_TIMEOUT",
    "SHUTDOWN_TIMEOUT_MS"
)
$previousEnvironment = @{}

New-Item -ItemType Directory -Path $logDirectory | Out-Null

try {
    foreach ($name in $environmentNames) {
        $previousEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, "Process")
    }

    $env:SPRING_PROFILES_ACTIVE = "test"
    $env:MONGODB_URI = "mongodb://root:example_password@localhost:27017/log_monitor_test?authSource=admin"
    $env:SERVER_PORT = [string] $Port
    $env:SHUTDOWN_TIMEOUT = "10s"
    $env:SHUTDOWN_TIMEOUT_MS = "5000"

    $process = Start-Process -FilePath $javaPath `
        -ArgumentList @("-jar", ('"{0}"' -f $resolvedJar)) `
        -RedirectStandardOutput $stdoutPath `
        -RedirectStandardError $stderrPath `
        -WindowStyle Hidden `
        -PassThru

    $startupDeadline = [DateTime]::UtcNow.AddSeconds($StartupTimeoutSeconds)
    do {
        Start-Sleep -Milliseconds 250
        try {
            $response = $httpClient.GetAsync(
                ("http://localhost:{0}/actuator/health/readiness" -f $Port),
                [System.Net.Http.HttpCompletionOption]::ResponseHeadersRead
            ).GetAwaiter().GetResult()
            $lastReadinessStatus = [int] $response.StatusCode
            if ([int] $response.StatusCode -eq 200) {
                $response.Dispose()
                break
            }
            $response.Dispose()
        } catch {
            # The application is still starting or the connector is not ready.
        }
        if ($process.HasExited) {
            throw "Application exited before readiness became healthy. See $stdoutPath and $stderrPath"
        }
    } while ([DateTime]::UtcNow -lt $startupDeadline)

    if ($process.HasExited) {
        throw "Application exited before the shutdown signal was sent"
    }

    if ([DateTime]::UtcNow -ge $startupDeadline) {
        throw "Application did not become ready within $StartupTimeoutSeconds seconds (last status: $lastReadinessStatus)"
    }

    if ($env:OS -eq "Windows_NT") {
        # Windows has no portable kill(2) SIGTERM equivalent. The shutdown
        # endpoint is enabled only by application-test.yml and enters the same
        # Spring ContextClosedEvent path used by a JVM termination signal.
        $shutdownRequest = [System.Net.Http.HttpRequestMessage]::new(
            [System.Net.Http.HttpMethod]::Post,
            ("http://localhost:{0}/actuator/shutdown" -f $Port)
        )
        $shutdownResponse = $httpClient.SendAsync(
            $shutdownRequest,
            [System.Net.Http.HttpCompletionOption]::ResponseHeadersRead
        ).GetAwaiter().GetResult()
        if ([int] $shutdownResponse.StatusCode -ge 400) {
            throw "Test-profile shutdown endpoint returned status $([int] $shutdownResponse.StatusCode)"
        }
        $shutdownResponse.Dispose()
        $shutdownRequest.Dispose()
    } elseif ($env:OS -ne "Windows_NT") {
        & kill -TERM $process.Id
    } else {
        $process.CloseMainWindow() | Out-Null
        if (-not $process.WaitForExit(3000)) {
            Stop-Process -Id $process.Id -Force
        }
    }

    $shutdownDeadline = [DateTime]::UtcNow.AddSeconds($ShutdownTimeoutSeconds)
    while (-not $process.HasExited -and [DateTime]::UtcNow -lt $shutdownDeadline) {
        Start-Sleep -Milliseconds 250
    }

    if (-not $process.HasExited) {
        throw "Application did not exit within $ShutdownTimeoutSeconds seconds"
    }

    $combinedLog = ((Get-Content -LiteralPath $stdoutPath -ErrorAction SilentlyContinue) +
        (Get-Content -LiteralPath $stderrPath -ErrorAction SilentlyContinue)) -join [Environment]::NewLine
    if ($combinedLog -notmatch "Graceful shutdown started") {
        throw "Shutdown coordinator marker was not found in application logs"
    }
    if ($combinedLog -notmatch "Completed graceful drain of ingestion queue") {
        throw "Persistence worker drain marker was not found in application logs"
    }

    Write-Output ("Graceful shutdown smoke test passed (pid={0}, exitCode={1})" -f $process.Id, $process.ExitCode)
} catch {
    $keepLogs = $true
    Write-Error ("Graceful shutdown smoke test failed: " + $_.Exception.Message)
    if (Test-Path -LiteralPath $stdoutPath) {
        Write-Error "--- application stdout ---"
        Get-Content -LiteralPath $stdoutPath | Select-Object -Last 80 | Write-Error
    }
    if (Test-Path -LiteralPath $stderrPath) {
        Write-Error "--- application stderr ---"
        Get-Content -LiteralPath $stderrPath | Select-Object -Last 80 | Write-Error
    }
    throw
} finally {
    if ($null -ne $process -and -not $process.HasExited) {
        Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
    }
    $httpClient.Dispose()
    foreach ($name in $environmentNames) {
        [Environment]::SetEnvironmentVariable($name, $previousEnvironment[$name], "Process")
    }
    if (-not $keepLogs -and (Test-Path -LiteralPath $logDirectory)) {
        Remove-Item -LiteralPath $logDirectory -Recurse -Force
    }
}
