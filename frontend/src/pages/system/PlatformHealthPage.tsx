import { useEffect, useRef, useState } from 'react'
import { fetchPlatformHealth } from '../../services/systemApi'
import type { PlatformHealthSnapshot } from '../../types/system'

interface RateSnapshot {
  acceptedPerSecond: number | null
  rejectedPerSecond: number | null
}

interface PreviousSnapshot {
  snapshot: PlatformHealthSnapshot
  at: number
}

const EMPTY_RATE: RateSnapshot = { acceptedPerSecond: null, rejectedPerSecond: null }

function formatCount(value: number) {
  return Math.round(value).toLocaleString()
}

function formatRate(value: number | null) {
  return value === null ? '—' : `${value.toFixed(1)}/s`
}

function formatBytes(value: number) {
  if (!Number.isFinite(value) || value <= 0) return '—'
  const units = ['B', 'MB', 'GB']
  let normalized = value
  let unit = 0
  while (normalized >= 1024 && unit < units.length - 1) {
    normalized /= 1024
    unit += 1
  }
  return `${normalized.toFixed(unit === 0 ? 0 : 1)} ${units[unit]}`
}

function formatCpu(value: number) {
  return Number.isFinite(value) && value > 0 ? `${(value * 100).toFixed(1)}%` : '—'
}

function readinessTone(status: string) {
  return status === 'UP'
    ? 'bg-emerald-50 text-emerald-700 border-emerald-200'
    : 'bg-rose-50 text-rose-700 border-rose-200'
}

function MetricCard({ label, value, detail, tone = 'text-slate-900' }: {
  label: string
  value: string
  detail: string
  tone?: string
}) {
  return (
    <article className="rounded-2xl border border-slate-200 bg-white p-5 shadow-xs">
      <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">{label}</p>
      <p className={`mt-2 text-2xl font-extrabold ${tone}`}>{value}</p>
      <p className="mt-1 text-xs text-slate-500">{detail}</p>
    </article>
  )
}

export function PlatformHealthPage() {
  const [snapshot, setSnapshot] = useState<PlatformHealthSnapshot | null>(null)
  const [rates, setRates] = useState<RateSnapshot>(EMPTY_RATE)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const previous = useRef<PreviousSnapshot | null>(null)

  useEffect(() => {
    let active = true

    const load = async () => {
      try {
        const next = await fetchPlatformHealth()
        if (!active) return
        const at = Date.parse(next.generatedAt) || Date.now()
        const prior = previous.current
        if (prior) {
          const elapsedSeconds = Math.max((at - prior.at) / 1_000, 0.001)
          const previousRejected = prior.snapshot.ingestion.validationRejectedTotal
            + prior.snapshot.ingestion.backpressureRejectedTotal
            + prior.snapshot.ingestion.shutdownRejectedTotal
          const nextRejected = next.ingestion.validationRejectedTotal
            + next.ingestion.backpressureRejectedTotal
            + next.ingestion.shutdownRejectedTotal
          setRates({
            acceptedPerSecond: Math.max(0, next.ingestion.acceptedTotal - prior.snapshot.ingestion.acceptedTotal) / elapsedSeconds,
            rejectedPerSecond: Math.max(0, nextRejected - previousRejected) / elapsedSeconds,
          })
        }
        previous.current = { snapshot: next, at }
        setSnapshot(next)
        setError(null)
      } catch (cause) {
        if (active) setError(cause instanceof Error ? cause.message : 'Unable to load platform health')
      } finally {
        if (active) setLoading(false)
      }
    }

    void load()
    const timer = window.setInterval(() => void load(), 5_000)
    return () => {
      active = false
      window.clearInterval(timer)
    }
  }, [])

  const ingestion = snapshot?.ingestion
  const persistence = snapshot?.persistence
  const liveTail = snapshot?.liveTail
  const alerts = snapshot?.alerts
  const runtime = snapshot?.runtime

  return (
    <main className="mx-auto max-w-7xl space-y-8 px-6 py-8">
      <header className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <p className="text-xs font-bold uppercase tracking-[0.2em] text-sky-600">Operator console</p>
          <h1 className="mt-2 text-3xl font-extrabold tracking-tight text-slate-900">Platform Health</h1>
          <p className="mt-2 max-w-2xl text-sm text-slate-500">
            Diagnose ingestion, persistence, live-tail, alert delivery, and runtime health without opening raw Prometheus output.
          </p>
        </div>
        <div className="text-right text-xs text-slate-500">
          <span className="inline-flex items-center gap-2 rounded-full border border-sky-200 bg-sky-50 px-3 py-1.5 font-semibold text-sky-700">
            <span className="h-2 w-2 animate-pulse rounded-full bg-sky-500" />
            Auto-refresh 5s
          </span>
          {snapshot && <p className="mt-2">Updated {new Date(snapshot.generatedAt).toLocaleTimeString()}</p>}
        </div>
      </header>

      {loading && <p className="text-sm font-semibold text-sky-600">Loading platform telemetry...</p>}
      {error && <div className="rounded-2xl border border-rose-200 bg-rose-50 p-4 text-sm text-rose-700">{error}</div>}

      {snapshot && ingestion && persistence && liveTail && alerts && runtime && (
        <>
          <section aria-labelledby="readiness-heading" className="rounded-2xl border border-slate-200 bg-white p-6 shadow-xs">
            <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <h2 id="readiness-heading" className="text-base font-bold text-slate-900">Readiness and dependencies</h2>
                <p className="mt-1 text-xs text-slate-500">Traffic safety state reported by the backend health group.</p>
              </div>
              <span className={`inline-flex w-fit rounded-full border px-3 py-1 text-xs font-bold ${readinessTone(snapshot.readiness.status)}`}>
                {snapshot.readiness.status}
              </span>
            </div>
            <div className="mt-4 flex flex-wrap gap-2">
              {Object.entries(snapshot.readiness.dependencies).map(([name, status]) => (
                <span key={name} className={`rounded-lg border px-3 py-1.5 text-xs font-semibold ${readinessTone(status)}`}>
                  {name}: {status}
                </span>
              ))}
            </div>
          </section>

          <section aria-labelledby="ingestion-heading">
            <div className="mb-3 flex items-end justify-between">
              <div>
                <h2 id="ingestion-heading" className="text-lg font-bold text-slate-900">Ingestion pipeline</h2>
                <p className="text-xs text-slate-500">Rates are calculated from consecutive cumulative snapshots.</p>
              </div>
              <span className="text-xs font-semibold text-slate-500">{ingestion.queueUtilizationPercent.toFixed(1)}% queue used</span>
            </div>
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
              <MetricCard label="Accepted rate" value={formatRate(rates.acceptedPerSecond)} detail={`${formatCount(ingestion.acceptedTotal)} total events`} tone="text-emerald-600" />
              <MetricCard label="Rejected rate" value={formatRate(rates.rejectedPerSecond)} detail={`${formatCount(ingestion.backpressureRejectedTotal + ingestion.validationRejectedTotal + ingestion.shutdownRejectedTotal)} total rejected`} tone="text-rose-600" />
              <MetricCard label="Queue depth" value={`${formatCount(ingestion.queueDepth)} / ${formatCount(ingestion.queueCapacity)}`} detail={`${ingestion.queueUtilizationPercent.toFixed(1)}% capacity`} />
              <MetricCard label="Worker activity" value={`${formatCount(ingestion.activeWorkers)} / ${formatCount(ingestion.workerCapacity)}`} detail={`batch avg ${ingestion.averageBatchSize.toFixed(1)} events`} />
            </div>
          </section>

          <section aria-labelledby="operations-heading">
            <h2 id="operations-heading" className="mb-3 text-lg font-bold text-slate-900">Storage and delivery</h2>
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
              <MetricCard label="Mongo persistence" value={`${persistence.averageDurationMs.toFixed(1)} ms`} detail={`max ${persistence.maxDurationMs.toFixed(1)} ms`} />
              <MetricCard label="Persistence failures" value={formatCount(persistence.failuresTotal)} detail={`${formatCount(persistence.eventsFailedTotal)} failed events`} tone={persistence.failuresTotal > 0 ? 'text-rose-600' : 'text-emerald-600'} />
              <MetricCard label="Live Tail" value={formatCount(liveTail.activeSessions)} detail={`${formatCount(liveTail.activeSubscriptions)} active subscriptions`} />
              <MetricCard label="Alert delivery failures" value={formatCount(alerts.deliveryFailureTotal)} detail={`${formatCount(alerts.triggeredTotal)} alerts triggered`} tone={alerts.deliveryFailureTotal > 0 ? 'text-rose-600' : 'text-emerald-600'} />
            </div>
          </section>

          <section aria-labelledby="runtime-heading">
            <h2 id="runtime-heading" className="mb-3 text-lg font-bold text-slate-900">Runtime signals</h2>
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
              <MetricCard label="Heap" value={`${formatBytes(runtime.heapUsedBytes)} / ${formatBytes(runtime.heapMaxBytes)}`} detail={`${runtime.gcPauseCount} GC pauses observed`} />
              <MetricCard label="CPU" value={formatCpu(runtime.processCpuUsage)} detail={`system ${formatCpu(runtime.systemCpuUsage)}`} />
              <MetricCard label="Live threads" value={formatCount(runtime.liveThreads)} detail="JVM live thread count" />
              <MetricCard label="Live-tail drops" value={formatCount(liveTail.eventsDroppedTotal)} detail={`${formatCount(liveTail.authorizationFailuresTotal)} authorization failures`} tone={liveTail.eventsDroppedTotal > 0 ? 'text-amber-600' : 'text-emerald-600'} />
            </div>
          </section>
        </>
      )}
    </main>
  )
}
