import { useEffect, useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  createProject,
  deactivateProject,
  fetchProjects,
  updateProject,
  updateProjectRetention,
} from '../../services/projectApi'

function environmentList(value: string): string[] {
  return value
    .split(',')
    .map((environment) => environment.trim().toLowerCase())
    .filter(Boolean)
}

function formatTimestamp(value: string | null) {
  return value ? new Date(value).toLocaleString() : 'No events yet'
}

const RETENTION_LEVELS = ['DEBUG', 'INFO', 'WARN', 'ERROR', 'FATAL'] as const

function validRetentionDays(value: string, optional = false) {
  if (optional && value === '') return true
  const days = Number(value)
  return Number.isInteger(days) && days >= 1 && days <= 3650
}

export function ProjectsPage() {
  const queryClient = useQueryClient()
  const [selectedProjectId, setSelectedProjectId] = useState<string | null>(null)
  const [createForm, setCreateForm] = useState({
    key: '',
    name: '',
    environments: 'development',
    defaultDays: '7',
  })
  const [editForm, setEditForm] = useState({ name: '', environments: '' })
  const [retentionDays, setRetentionDays] = useState('7')
  const [retentionOverrides, setRetentionOverrides] = useState<Record<string, string>>({})

  const projectsQuery = useQuery({
    queryKey: ['projects'],
    queryFn: fetchProjects,
  })

  const selectedProject = projectsQuery.data?.find((project) => project.id === selectedProjectId) ?? null

  useEffect(() => {
    if (!selectedProjectId && projectsQuery.data?.length) {
      setSelectedProjectId(projectsQuery.data[0].id)
    }
  }, [projectsQuery.data, selectedProjectId])

  useEffect(() => {
    if (selectedProject) {
      setEditForm({ name: selectedProject.name, environments: selectedProject.environments.join(', ') })
      setRetentionDays(String(selectedProject.retention.defaultDays))
      setRetentionOverrides(Object.fromEntries(
        RETENTION_LEVELS.map((level) => [level, selectedProject.retention.levelOverrides[level]?.toString() ?? '']),
      ))
    }
  }, [selectedProject])

  const createMutation = useMutation({
    mutationFn: () => createProject({
      key: createForm.key,
      name: createForm.name,
      environments: environmentList(createForm.environments),
      retention: { defaultDays: Number(createForm.defaultDays), levelOverrides: {} },
    }),
    onSuccess: (created) => {
      queryClient.invalidateQueries({ queryKey: ['projects'] })
      setSelectedProjectId(created.id)
      setCreateForm({ key: '', name: '', environments: 'development', defaultDays: '7' })
    },
  })

  const updateMutation = useMutation({
    mutationFn: () => updateProject(selectedProject!.id, {
      name: editForm.name,
      environments: environmentList(editForm.environments),
    }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['projects'] }),
  })

  const retentionMutation = useMutation({
    mutationFn: () => updateProjectRetention(selectedProject!.id, {
      defaultDays: Number(retentionDays),
      levelOverrides: Object.fromEntries(
        RETENTION_LEVELS
          .filter((level) => retentionOverrides[level] !== '')
          .map((level) => [level, Number(retentionOverrides[level])]),
      ),
    }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['projects'] }),
  })

  const deactivateMutation = useMutation({
    mutationFn: () => deactivateProject(selectedProject!.id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['projects'] }),
  })

  const submitCreate = (event: FormEvent) => {
    event.preventDefault()
    if (createForm.key.trim() && createForm.name.trim() && environmentList(createForm.environments).length > 0) {
      createMutation.mutate()
    }
  }

  const submitUpdate = (event: FormEvent) => {
    event.preventDefault()
    if (selectedProject && editForm.name.trim() && environmentList(editForm.environments).length > 0) {
      updateMutation.mutate()
    }
  }

  if (projectsQuery.isLoading) {
    return <main className="mx-auto max-w-6xl p-6 text-slate-600">Loading projects…</main>
  }

  if (projectsQuery.error) {
    return (
      <main className="mx-auto max-w-6xl space-y-4 p-6">
        <h1 className="text-2xl font-bold text-slate-900">Projects</h1>
        <div className="rounded-xl border border-rose-200 bg-rose-50 p-5 text-rose-800">
          <p>{projectsQuery.error instanceof Error ? projectsQuery.error.message : 'Projects could not be loaded.'}</p>
          <button onClick={() => void projectsQuery.refetch()} className="mt-3 rounded-lg bg-rose-700 px-3 py-2 text-sm font-semibold text-white">Retry</button>
        </div>
      </main>
    )
  }

  const projects = projectsQuery.data ?? []
  const retentionValid = validRetentionDays(retentionDays)
    && RETENTION_LEVELS.every((level) => validRetentionDays(retentionOverrides[level] ?? '', true))
  const storageWindowRatio = validRetentionDays(retentionDays) ? Number(retentionDays) / 7 : null
  return (
    <main className="mx-auto max-w-7xl space-y-6 p-6">
      <div>
        <p className="text-sm font-semibold uppercase tracking-wide text-sky-700">Administration</p>
        <h1 className="mt-1 text-3xl font-bold text-slate-900">Projects</h1>
        <p className="mt-1 text-sm text-slate-600">Onboard monitored applications, configure environments and inspect recent activity.</p>
      </div>

      <section className="grid gap-6 lg:grid-cols-[0.8fr_1.2fr]">
        <form onSubmit={submitCreate} className="space-y-4 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
          <div>
            <h2 className="text-lg font-semibold text-slate-900">Create project</h2>
            <p className="mt-1 text-xs text-slate-500">The project key is a stable lowercase slug and cannot be changed after creation.</p>
          </div>
          <label className="block text-sm font-medium text-slate-700">Project key
            <input required maxLength={80} value={createForm.key} onChange={(event) => setCreateForm({ ...createForm, key: event.target.value })} placeholder="line-smart-queue" className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm" />
          </label>
          <label className="block text-sm font-medium text-slate-700">Display name
            <input required maxLength={200} value={createForm.name} onChange={(event) => setCreateForm({ ...createForm, name: event.target.value })} placeholder="LINE Smart Queue Assistant" className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm" />
          </label>
          <label className="block text-sm font-medium text-slate-700">Environments
            <input required value={createForm.environments} onChange={(event) => setCreateForm({ ...createForm, environments: event.target.value })} placeholder="development, staging, production" className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm" />
          </label>
          <label className="block text-sm font-medium text-slate-700">Default retention (days)
            <input required min={1} max={3650} type="number" value={createForm.defaultDays} onChange={(event) => setCreateForm({ ...createForm, defaultDays: event.target.value })} className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm" />
          </label>
          <button disabled={createMutation.isPending} className="rounded-lg bg-sky-700 px-4 py-2 text-sm font-semibold text-white disabled:opacity-50">{createMutation.isPending ? 'Creating…' : 'Create project'}</button>
          {createMutation.error && <p className="text-sm text-rose-700">{createMutation.error.message}</p>}
        </form>

        <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
          <div className="flex items-center justify-between gap-4">
            <div>
              <h2 className="text-lg font-semibold text-slate-900">Authorized projects</h2>
              <p className="mt-1 text-sm text-slate-500">{projects.length} project(s) visible in your organization scope.</p>
            </div>
          </div>
          {projects.length === 0 ? (
            <p className="mt-5 rounded-lg bg-slate-50 p-5 text-sm text-slate-600">No projects found. Create the first monitored application above.</p>
          ) : (
            <div className="mt-5 grid gap-3 sm:grid-cols-2">
              {projects.map((project) => (
                <button key={project.id} onClick={() => setSelectedProjectId(project.id)} className={`rounded-xl border p-4 text-left transition ${selectedProjectId === project.id ? 'border-sky-400 bg-sky-50' : 'border-slate-200 hover:border-sky-300'}`}>
                  <div className="flex items-start justify-between gap-3"><span className="font-semibold text-slate-900">{project.name}</span><span className={project.active ? 'text-xs font-semibold text-emerald-700' : 'text-xs font-semibold text-slate-500'}>{project.active ? 'Active' : 'Inactive'}</span></div>
                  <p className="mt-1 font-mono text-xs text-slate-500">{project.key}</p>
                  <p className="mt-3 text-xs text-slate-600">{project.services.length} service(s) · {project.recentIngestion.eventsLast24Hours.toLocaleString()} events / 24h</p>
                </button>
              ))}
            </div>
          )}
        </section>
      </section>

      {selectedProject && (
        <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
          <div className="flex flex-wrap items-start justify-between gap-4">
            <div><p className="font-mono text-xs text-slate-500">{selectedProject.key}</p><h2 className="mt-1 text-xl font-semibold text-slate-900">Project details</h2></div>
            {selectedProject.active && <button onClick={() => { if (window.confirm(`Deactivate ${selectedProject.name}? Ingestion will be rejected.`)) deactivateMutation.mutate() }} disabled={deactivateMutation.isPending} className="rounded-lg border border-rose-300 px-3 py-2 text-sm font-semibold text-rose-700 disabled:opacity-50">{deactivateMutation.isPending ? 'Deactivating…' : 'Deactivate project'}</button>}
          </div>
          <div className="mt-5 grid gap-6 lg:grid-cols-2">
            <form onSubmit={submitUpdate} className="space-y-4">
              <h3 className="font-semibold text-slate-900">Settings</h3>
              <label className="block text-sm font-medium text-slate-700">Display name
                <input required maxLength={200} value={editForm.name} onChange={(event) => setEditForm({ ...editForm, name: event.target.value })} className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm" />
              </label>
              <label className="block text-sm font-medium text-slate-700">Environments
                <input required value={editForm.environments} onChange={(event) => setEditForm({ ...editForm, environments: event.target.value })} className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm" />
              </label>
              <button disabled={updateMutation.isPending} className="rounded-lg bg-slate-900 px-4 py-2 text-sm font-semibold text-white disabled:opacity-50">{updateMutation.isPending ? 'Saving…' : 'Save project'}</button>
              {updateMutation.error && <p className="text-sm text-rose-700">{updateMutation.error.message}</p>}
            </form>
            <form onSubmit={(event) => { event.preventDefault(); retentionMutation.mutate() }} className="space-y-4">
              <h3 className="font-semibold text-slate-900">Retention</h3>
              <div className="rounded-lg border border-sky-200 bg-sky-50 p-3 text-xs text-sky-900">
                MongoDB TTL cleanup is asynchronous, so expiry is not exact to the second. Changes apply only to future ingested events; existing <code>expireAt</code> values are not backfilled.
              </div>
              <label className="block text-sm font-medium text-slate-700">Default retention (days)
                <input required min={1} max={3650} type="number" value={retentionDays} onChange={(event) => setRetentionDays(event.target.value)} className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm" />
              </label>
              <fieldset>
                <legend className="text-sm font-medium text-slate-700">Per-level overrides (days)</legend>
                <p className="mt-1 text-xs text-slate-500">Leave blank to use the project default.</p>
                <div className="mt-2 grid grid-cols-2 gap-3 sm:grid-cols-3">
                  {RETENTION_LEVELS.map((level) => <label key={level} className="text-xs font-semibold text-slate-600">{level}
                    <input aria-label={`${level} retention days`} min={1} max={3650} type="number" value={retentionOverrides[level] ?? ''} onChange={(event) => setRetentionOverrides({ ...retentionOverrides, [level]: event.target.value })} placeholder={retentionDays} className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm font-normal" />
                  </label>)}
                </div>
              </fieldset>
              {storageWindowRatio !== null && <p className="rounded-lg bg-slate-50 p-3 text-xs text-slate-600">At a steady event rate, the default policy retains roughly <strong>{storageWindowRatio.toFixed(1)}×</strong> the event-days of a 7-day policy. This is a planning ratio, not a byte estimate.</p>}
              {!retentionValid && <p role="alert" className="text-sm text-rose-700">Retention values must be whole days from 1 to 3650.</p>}
              <button disabled={retentionMutation.isPending || !retentionValid} className="rounded-lg bg-sky-700 px-4 py-2 text-sm font-semibold text-white disabled:opacity-50">{retentionMutation.isPending ? 'Saving…' : 'Save retention'}</button>
              {retentionMutation.error && <p className="text-sm text-rose-700">{retentionMutation.error.message}</p>}
            </form>
          </div>
          <div className="mt-6 grid gap-3 text-sm sm:grid-cols-3">
            <div className="rounded-lg bg-slate-50 p-3"><p className="text-xs text-slate-500">Services discovered</p><p className="mt-1 font-semibold text-slate-900">{selectedProject.services.length ? selectedProject.services.join(', ') : 'None yet'}</p></div>
            <div className="rounded-lg bg-slate-50 p-3"><p className="text-xs text-slate-500">Events / last 24h</p><p className="mt-1 font-semibold text-slate-900">{selectedProject.recentIngestion.eventsLast24Hours.toLocaleString()} ({selectedProject.recentIngestion.errorEventsLast24Hours.toLocaleString()} errors)</p></div>
            <div className="rounded-lg bg-slate-50 p-3"><p className="text-xs text-slate-500">Last received</p><p className="mt-1 font-semibold text-slate-900">{formatTimestamp(selectedProject.recentIngestion.lastReceivedAt)}</p></div>
          </div>
          {deactivateMutation.error && <p className="mt-4 text-sm text-rose-700">{deactivateMutation.error.message}</p>}
        </section>
      )}
    </main>
  )
}
