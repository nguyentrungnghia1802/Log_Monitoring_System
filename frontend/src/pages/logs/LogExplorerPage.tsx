import { useMemo, useState, type FormEvent } from 'react'
import { useQuery } from '@tanstack/react-query'
import { fetchLogById, fetchLogs } from '../../services/api'
import type { LogSearchParams } from '../../types/log'

const levelStyles: Record<string, string> = {
  ERROR: 'bg-rose-100 text-rose-800',
  WARN: 'bg-amber-100 text-amber-800',
  INFO: 'bg-sky-100 text-sky-800',
  DEBUG: 'bg-slate-100 text-slate-700',
}

export function LogExplorerPage() {
  const [projectId, setProjectId] = useState('demo-project')
  const [filters, setFilters] = useState({ level: '', service: '', search: '' })
  const [queryFilters, setQueryFilters] = useState(filters)
  const [selectedId, setSelectedId] = useState<string | null>(null)

  const params = useMemo<LogSearchParams>(() => ({
    projectId,
    level: queryFilters.level || undefined,
    service: queryFilters.service || undefined,
    search: queryFilters.search || undefined,
    limit: 100,
  }), [projectId, queryFilters])
  const logs = useQuery({ queryKey: ['logs', params], queryFn: () => fetchLogs(params), enabled: Boolean(projectId), retry: false })
  const detail = useQuery({
    queryKey: ['log-detail', projectId, selectedId],
    queryFn: () => fetchLogById(projectId, selectedId as string),
    enabled: Boolean(projectId && selectedId),
    retry: false,
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    setQueryFilters(filters)
  }

  return (
    <main className="mx-auto max-w-7xl px-6 py-8">
      <div className="mb-6"><h1 className="text-2xl font-bold text-slate-950">Log Explorer</h1><p className="text-sm text-slate-500">Search normalized events in your project.</p></div>
      <form onSubmit={submit} className="mb-5 grid gap-3 rounded-xl border border-slate-200 bg-white p-4 shadow-sm md:grid-cols-5">
        <input aria-label="Project ID" value={projectId} onChange={(event) => setProjectId(event.target.value)} placeholder="Project ID" className="rounded-lg border border-slate-300 px-3 py-2" />
        <select aria-label="Level" value={filters.level} onChange={(event) => setFilters({ ...filters, level: event.target.value })} className="rounded-lg border border-slate-300 px-3 py-2"><option value="">All levels</option><option>ERROR</option><option>WARN</option><option>INFO</option><option>DEBUG</option></select>
        <input aria-label="Service" value={filters.service} onChange={(event) => setFilters({ ...filters, service: event.target.value })} placeholder="Service" className="rounded-lg border border-slate-300 px-3 py-2" />
        <input aria-label="Search" value={filters.search} onChange={(event) => setFilters({ ...filters, search: event.target.value })} placeholder="Message, trace, request..." className="rounded-lg border border-slate-300 px-3 py-2" />
        <button className="rounded-lg bg-sky-600 px-4 py-2 font-semibold text-white hover:bg-sky-700">Search</button>
      </form>
      {logs.isLoading && <p className="rounded-xl bg-white p-6 text-slate-600">Loading events...</p>}
      {logs.isError && <p role="alert" className="rounded-xl bg-rose-50 p-6 text-rose-700">{logs.error.message}</p>}
      {logs.data && (
        <div className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
          <div className="overflow-x-auto"><table className="w-full text-left text-sm"><thead className="bg-slate-50 text-xs uppercase text-slate-500"><tr><th className="px-4 py-3">Time</th><th className="px-4 py-3">Level</th><th className="px-4 py-3">Service</th><th className="px-4 py-3">Message</th></tr></thead><tbody className="divide-y divide-slate-100">
            {logs.data.events.map((event) => <tr key={event.id} onClick={() => setSelectedId(event.id)} className="cursor-pointer hover:bg-sky-50"><td className="whitespace-nowrap px-4 py-3 text-slate-500">{new Date(event.timestamp).toLocaleString()}</td><td className="px-4 py-3"><span className={`rounded px-2 py-1 text-xs font-bold ${levelStyles[event.level] ?? levelStyles.DEBUG}`}>{event.level}</span></td><td className="px-4 py-3 font-medium text-slate-700">{event.service}</td><td className="max-w-xl truncate px-4 py-3 text-slate-700">{event.message}</td></tr>)}
          </tbody></table></div>
          {logs.data.events.length === 0 && <p className="p-8 text-center text-slate-500">No events match these filters.</p>}
        </div>
      )}
      {selectedId && <aside aria-label="Event details" className="fixed inset-y-0 right-0 z-50 w-full max-w-xl overflow-auto border-l border-slate-200 bg-white p-6 shadow-2xl"><button onClick={() => setSelectedId(null)} className="float-right rounded-lg border px-3 py-1.5 text-sm">Close</button><h2 className="mb-4 text-xl font-bold">Event details</h2>{detail.isLoading && <p className="text-slate-500">Loading event details...</p>}{detail.isError && <p role="alert" className="text-rose-700">Unable to load event details.</p>}{detail.data && <pre className="overflow-auto rounded-xl bg-slate-950 p-4 text-xs text-slate-100">{JSON.stringify(detail.data, null, 2)}</pre>}</aside>}
    </main>
  )
}
