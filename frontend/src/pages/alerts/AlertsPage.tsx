import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { fetchAlerts, acknowledgeAlert, retryAlertNotification } from '../../services/api'
import type { AlertOccurrence } from '../../types/alert'

export function AlertsPage() {
    const queryClient = useQueryClient()
    const projectId = 'demo-project'

    const { data: alerts = [], isLoading } = useQuery<AlertOccurrence[]>({
        queryKey: ['alerts', projectId],
        queryFn: () => fetchAlerts(projectId),
    })

    const ackMutation = useMutation({
        mutationFn: (id: string) => acknowledgeAlert(projectId, id),
        onSuccess: () => queryClient.invalidateQueries({ queryKey: ['alerts', projectId] }),
    })

    const retryMutation = useMutation({
        mutationFn: (id: string) => retryAlertNotification(projectId, id),
        onSuccess: () => queryClient.invalidateQueries({ queryKey: ['alerts', projectId] }),
    })

    return (
        <div className="p-6 max-w-7xl mx-auto space-y-6">
            <div>
                <h1 className="text-2xl font-bold text-slate-100">Alert History</h1>
                <p className="text-sm text-slate-400">Triggered occurrences and notification delivery status</p>
            </div>

            {isLoading ? (
                <div className="text-center py-12 text-slate-400">Loading alerts...</div>
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
                                <tr key={alert.id} className="hover:bg-slate-800/30 transition">
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
                                                onClick={() => ackMutation.mutate(alert.id)}
                                                className="px-2 py-1 bg-slate-800 hover:bg-slate-700 text-slate-300 text-xs rounded transition"
                                            >
                                                Acknowledge
                                            </button>
                                        )}
                                        {alert.deliveryStatus === 'FAILED' && (
                                            <button
                                                onClick={() => retryMutation.mutate(alert.id)}
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
        </div>
    )
}
