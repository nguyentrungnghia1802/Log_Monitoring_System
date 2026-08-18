import { useEffect, useMemo, useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { fetchAlertById, fetchAlerts, acknowledgeAlert, retryAlertNotification } from '../../services/api'
import { fetchProjects } from '../../services/projectApi'
import type { AlertOccurrence } from '../../types/alert'

export function AlertsPage() {
    const queryClient = useQueryClient()
    const [projectId, setProjectId] = useState('')
    const [selectedAlertId, setSelectedAlertId] = useState<string | null>(null)

    const projectsQuery = useQuery({ queryKey: ['projects'], queryFn: fetchProjects })
    const projects = useMemo(() => projectsQuery.data ?? [], [projectsQuery.data])

    useEffect(() => {
        if (!projectId && projects.length > 0) setProjectId(projects[0].id)
    }, [projectId, projects])

    const { data: alerts = [], isLoading, error, refetch } = useQuery<AlertOccurrence[]>({
        queryKey: ['alerts', projectId],
        queryFn: () => fetchAlerts(projectId),
        enabled: Boolean(projectId),
    })

    const detailQuery = useQuery({
        queryKey: ['alert', projectId, selectedAlertId],
        queryFn: () => fetchAlertById(projectId, selectedAlertId as string),
        enabled: Boolean(projectId && selectedAlertId),
    })

    const ackMutation = useMutation({
        mutationFn: (id: string) => acknowledgeAlert(projectId, id),
        onSuccess: (updated) => {
            void queryClient.invalidateQueries({ queryKey: ['alerts', projectId] })
            queryClient.setQueryData(['alert', projectId, updated.id], updated)
        },
    })

    const retryMutation = useMutation({
        mutationFn: (id: string) => retryAlertNotification(projectId, id),
        onSuccess: (updated) => {
            void queryClient.invalidateQueries({ queryKey: ['alerts', projectId] })
            queryClient.setQueryData(['alert', projectId, updated.id], updated)
        },
    })

    return (
        <div className="p-6 max-w-7xl mx-auto space-y-6">
            <div>
                <h1 className="text-2xl font-bold text-slate-100">Alert History</h1>
                <p className="text-sm text-slate-400">Triggered occurrences and notification delivery status</p>
            </div>

            <label className="block max-w-xl text-sm text-slate-300">
                Project
                <select aria-label="Project" value={projectId} onChange={(event) => { setProjectId(event.target.value); setSelectedAlertId(null) }} className="mt-1 w-full rounded-lg border border-slate-700 bg-slate-900 px-3 py-2">
                    {projects.length === 0 && <option value="">No projects available</option>}
                    {projects.map((project) => <option key={project.id} value={project.id}>{project.name} ({project.key})</option>)}
                </select>
            </label>

            {projectsQuery.isLoading || isLoading ? (
                <div className="text-center py-12 text-slate-400">Loading alerts...</div>
            ) : error ? (
                <div className="rounded-xl border border-red-800 bg-red-950/40 p-5 text-red-200">
                    <p>{error.message}</p>
                    <button onClick={() => void refetch()} className="mt-3 rounded bg-red-800 px-3 py-1 text-sm">Retry</button>
                </div>
            ) : alerts.length === 0 ? (
                <div className="bg-slate-900 border border-slate-800 rounded-xl p-12 text-center text-slate-400">
                    No alert occurrences triggered yet.
                </div>
            ) : (
                <div className="bg-slate-900 border border-slate-800 rounded-xl overflow-hidden">
                    <table className="w-full text-left text-sm text-slate-300">
                        <thead className="bg-slate-950 text-slate-400 text-xs uppercase border-b border-slate-800">
                            <tr>
                                <th className="px-4 py-3">Triggered At</th>
                                <th className="px-4 py-3">Rule Name</th>
                                <th className="px-4 py-3">Observed / Threshold</th>
                                <th className="px-4 py-3">Status</th>
                                <th className="px-4 py-3">Delivery Status</th>
                                <th className="px-4 py-3 text-right">Actions</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-slate-800/50 font-mono text-xs">
                            {alerts.map((alert) => (
                                <tr key={alert.id} onClick={() => setSelectedAlertId(alert.id)} className="cursor-pointer hover:bg-slate-800/30 transition">
                                    <td className="px-4 py-3 text-slate-400">
                                        {new Date(alert.triggeredAt).toLocaleString()}
                                    </td>
                                    <td className="px-4 py-3 font-sans font-medium text-slate-200">
                                        {alert.ruleName}
                                    </td>
                                    <td className="px-4 py-3 text-amber-400 font-bold">
                                        {alert.observedValue} / {alert.threshold}
                                    </td>
                                    <td className="px-4 py-3">
                                        <span
                                            className={`px-2 py-0.5 rounded text-[10px] font-semibold uppercase ${alert.status === 'ACKNOWLEDGED'
                                                    ? 'bg-slate-800 text-slate-400 border border-slate-700'
                                                    : 'bg-red-500/10 text-red-400 border border-red-500/20'
                                                }`}
                                        >
                                            {alert.status}
                                        </span>
                                    </td>
                                    <td className="px-4 py-3">
                                        <span
                                            className={`px-2 py-0.5 rounded text-[10px] font-semibold uppercase ${alert.deliveryStatus === 'DELIVERED'
                                                    ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20'
                                                    : alert.deliveryStatus === 'FAILED'
                                                        ? 'bg-amber-500/10 text-amber-400 border border-amber-500/20'
                                                        : 'bg-slate-800 text-slate-400 border border-slate-700'
                                                }`}
                                        >
                                            {alert.deliveryStatus}
                                        </span>
                                    </td>
                                    <td className="px-4 py-3 text-right space-x-2 font-sans">
                                        {alert.status === 'TRIGGERED' && (
                                            <button
                                                onClick={(event) => { event.stopPropagation(); ackMutation.mutate(alert.id) }}
                                                className="px-2 py-1 bg-slate-800 hover:bg-slate-700 text-slate-300 text-xs rounded transition"
                                            >
                                                Acknowledge
                                            </button>
                                        )}
                                        {alert.deliveryStatus === 'FAILED' && (
                                            <button
                                                onClick={(event) => { event.stopPropagation(); retryMutation.mutate(alert.id) }}
                                                className="px-2 py-1 bg-blue-600/20 hover:bg-blue-600/40 text-blue-400 text-xs rounded border border-blue-500/30 transition"
                                            >
                                                Retry Notification
                                            </button>
                                        )}
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            )}

            {(ackMutation.error || retryMutation.error) && (
                <p role="alert" className="rounded-lg border border-red-800 bg-red-950/40 p-3 text-sm text-red-200">
                    {(ackMutation.error ?? retryMutation.error)?.message}
                </p>
            )}

            {selectedAlertId && (
                <section aria-label="Alert occurrence detail" className="rounded-xl border border-slate-800 bg-slate-900 p-6 text-sm text-slate-300">
                    <div className="flex items-start justify-between gap-4">
                        <div>
                            <h2 className="text-lg font-semibold text-slate-100">Occurrence detail</h2>
                            <p className="font-mono text-xs text-slate-500">{selectedAlertId}</p>
                        </div>
                        <button onClick={() => setSelectedAlertId(null)} className="text-slate-400 hover:text-white">Close</button>
                    </div>
                    {detailQuery.isLoading ? <p className="mt-4">Loading detail...</p> : detailQuery.error ? <p className="mt-4 text-red-300">{detailQuery.error.message}</p> : detailQuery.data && (
                        <div className="mt-5 space-y-5">
                            <dl className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
                                <div><dt className="text-slate-500">Rule</dt><dd>{detailQuery.data.ruleName}</dd></div>
                                <div><dt className="text-slate-500">Why triggered</dt><dd>{detailQuery.data.observedValue} events reached threshold {detailQuery.data.threshold}</dd></div>
                                <div><dt className="text-slate-500">Window</dt><dd>{new Date(detailQuery.data.windowStart).toLocaleString()} – {new Date(detailQuery.data.windowEnd).toLocaleString()}</dd></div>
                                <div><dt className="text-slate-500">Acknowledgement</dt><dd>{detailQuery.data.acknowledgedAt ? `${detailQuery.data.acknowledgedBy} at ${new Date(detailQuery.data.acknowledgedAt).toLocaleString()}` : 'Not acknowledged'}</dd></div>
                            </dl>
                            <div>
                                <h3 className="font-semibold text-slate-100">Delivery attempts</h3>
                                {(detailQuery.data.deliveryAttempts ?? []).length === 0 ? <p className="mt-2 text-slate-500">No delivery attempt recorded.</p> : (
                                    <ol className="mt-2 space-y-2">
                                        {detailQuery.data.deliveryAttempts.map((attempt) => (
                                            <li key={`${attempt.attemptNumber}-${attempt.attemptedAt}`} className="rounded-lg bg-slate-950 p-3">
                                                Attempt {attempt.attemptNumber} via {attempt.provider}: <strong>{attempt.status}</strong> at {new Date(attempt.attemptedAt).toLocaleString()}
                                                {attempt.errorSummary && <p className="mt-1 text-amber-300">{attempt.errorSummary}</p>}
                                            </li>
                                        ))}
                                    </ol>
                                )}
                            </div>
                        </div>
                    )}
                </section>
            )}
        </div>
    )
}
