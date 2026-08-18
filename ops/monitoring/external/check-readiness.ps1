[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $Uri,
    [ValidateRange(1, 60)]
    [int] $TimeoutSeconds = 10
)

$ErrorActionPreference = "Stop"

try {
    $response = Invoke-WebRequest -Uri $Uri -Method Get -TimeoutSec $TimeoutSeconds -UseBasicParsing
    if ([int] $response.StatusCode -ne 200) {
        throw "Readiness returned HTTP status $([int] $response.StatusCode)"
    }

    $body = $response.Content | ConvertFrom-Json
    if ($null -eq $body.status -or $body.status -ne "UP") {
        throw "Readiness status was '$($body.status)' rather than UP"
    }

    Write-Output ("External readiness check passed: {0}" -f $Uri)
    exit 0
} catch {
    Write-Error ("External readiness check failed for {0}: {1}" -f $Uri, $_.Exception.Message)
    exit 1
}
