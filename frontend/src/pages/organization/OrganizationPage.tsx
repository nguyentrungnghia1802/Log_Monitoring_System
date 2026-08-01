import { useEffect, useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  fetchCurrentOrganization,
  fetchOrganizationMembers,
  inviteOrganizationMember,
  removeOrganizationMember,
  updateCurrentOrganization,
  updateOrganizationMember,
} from '../../services/organizationApi'
import type { OrganizationRole } from '../../types/organization'

const roles: OrganizationRole[] = ['ORGANIZATION_ADMIN', 'PROJECT_OPERATOR', 'VIEWER']

export function OrganizationPage() {
  const queryClient = useQueryClient()
  const [name, setName] = useState('')
  const [invite, setInvite] = useState({ username: '', email: '', password: '', role: 'VIEWER' as OrganizationRole })

  const organizationQuery = useQuery({
    queryKey: ['organization', 'current'],
    queryFn: fetchCurrentOrganization,
  })
  const membersQuery = useQuery({
    queryKey: ['organization', 'members'],
    queryFn: fetchOrganizationMembers,
  })

  useEffect(() => {
    if (organizationQuery.data) setName(organizationQuery.data.name)
  }, [organizationQuery.data])

  const organizationMutation = useMutation({
    mutationFn: () => updateCurrentOrganization(name, organizationQuery.data?.settings ?? {}),
    onSuccess: (updated) => {
      queryClient.setQueryData(['organization', 'current'], updated)
    },
  })
  const inviteMutation = useMutation({
    mutationFn: () => inviteOrganizationMember(invite),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['organization', 'members'] })
      queryClient.invalidateQueries({ queryKey: ['organization', 'current'] })
      setInvite({ username: '', email: '', password: '', role: 'VIEWER' })
    },
  })
  const memberMutation = useMutation({
    mutationFn: ({ userId, changes }: { userId: string; changes: { active?: boolean; role?: OrganizationRole } }) =>
      updateOrganizationMember(userId, changes),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['organization', 'members'] })
      queryClient.invalidateQueries({ queryKey: ['organization', 'current'] })
    },
  })
  const removeMutation = useMutation({
    mutationFn: (userId: string) => removeOrganizationMember(userId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['organization', 'members'] })
      queryClient.invalidateQueries({ queryKey: ['organization', 'current'] })
    },
  })

  const error = organizationQuery.error ?? membersQuery.error
  const retry = () => {
    void organizationQuery.refetch()
    void membersQuery.refetch()
  }

  const submitOrganization = (event: FormEvent) => {
    event.preventDefault()
    if (name.trim()) organizationMutation.mutate()
  }

  const submitInvite = (event: FormEvent) => {
    event.preventDefault()
    inviteMutation.mutate()
  }

  if (organizationQuery.isLoading || membersQuery.isLoading) {
    return <main className="mx-auto max-w-6xl p-6 text-slate-600">Loading organization settings…</main>
  }

  if (error || !organizationQuery.data || !membersQuery.data) {
    return (
      <main className="mx-auto max-w-6xl space-y-4 p-6">
        <h1 className="text-2xl font-bold text-slate-900">Organization</h1>
        <div className="rounded-xl border border-rose-200 bg-rose-50 p-5 text-rose-800">
          <p>{error instanceof Error ? error.message : 'Organization data could not be loaded.'}</p>
          <button onClick={retry} className="mt-3 rounded-lg bg-rose-700 px-3 py-2 text-sm font-semibold text-white">
            Retry
          </button>
        </div>
      </main>
    )
  }

  const members = membersQuery.data
  return (
    <main className="mx-auto max-w-6xl space-y-6 p-6">
      <div>
        <p className="text-sm font-semibold uppercase tracking-wide text-sky-700">Administration</p>
        <h1 className="mt-1 text-3xl font-bold text-slate-900">{organizationQuery.data.name}</h1>
        <p className="mt-1 text-sm text-slate-600">Manage organization settings and access without editing MongoDB directly.</p>
      </div>

      <section className="grid gap-6 lg:grid-cols-[0.8fr_1.2fr]">
        <form onSubmit={submitOrganization} className="space-y-4 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
          <div>
            <h2 className="text-lg font-semibold text-slate-900">Organization settings</h2>
            <p className="mt-1 text-xs text-slate-500">Slug: {organizationQuery.data.slug}</p>
          </div>
          <label className="block text-sm font-medium text-slate-700">
            Display name
            <input
              value={name}
              onChange={(event) => setName(event.target.value)}
              maxLength={200}
              className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
            />
          </label>
          <button disabled={organizationMutation.isPending} className="rounded-lg bg-sky-700 px-4 py-2 text-sm font-semibold text-white disabled:opacity-50">
            {organizationMutation.isPending ? 'Saving…' : 'Save settings'}
          </button>
          {organizationMutation.error && <p className="text-sm text-rose-700">{organizationMutation.error.message}</p>}
        </form>

        <form onSubmit={submitInvite} className="space-y-4 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
          <div>
            <h2 className="text-lg font-semibold text-slate-900">Invite management user</h2>
            <p className="mt-1 text-xs text-slate-500">The password is sent once to create the account and is never displayed in the member list.</p>
          </div>
          <div className="grid gap-3 sm:grid-cols-2">
            <input required placeholder="Username" value={invite.username} onChange={(event) => setInvite({ ...invite, username: event.target.value })} className="rounded-lg border border-slate-300 px-3 py-2 text-sm" />
            <input required type="email" placeholder="Email" value={invite.email} onChange={(event) => setInvite({ ...invite, email: event.target.value })} className="rounded-lg border border-slate-300 px-3 py-2 text-sm" />
            <input required minLength={12} type="password" placeholder="Temporary password (12+ chars)" value={invite.password} onChange={(event) => setInvite({ ...invite, password: event.target.value })} className="rounded-lg border border-slate-300 px-3 py-2 text-sm" />
            <select value={invite.role} onChange={(event) => setInvite({ ...invite, role: event.target.value as OrganizationRole })} className="rounded-lg border border-slate-300 px-3 py-2 text-sm">
              {roles.map((role) => <option key={role} value={role}>{role}</option>)}
            </select>
          </div>
          <button disabled={inviteMutation.isPending} className="rounded-lg bg-slate-900 px-4 py-2 text-sm font-semibold text-white disabled:opacity-50">
            {inviteMutation.isPending ? 'Creating…' : 'Create user'}
          </button>
          {inviteMutation.error && <p className="text-sm text-rose-700">{inviteMutation.error.message}</p>}
        </form>
      </section>

      <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
        <div className="flex items-center justify-between gap-4">
          <div>
            <h2 className="text-lg font-semibold text-slate-900">Members</h2>
            <p className="text-sm text-slate-500">{organizationQuery.data.memberCount} account(s) in this organization.</p>
          </div>
        </div>
        {members.length === 0 ? (
          <p className="mt-5 rounded-lg bg-slate-50 p-5 text-sm text-slate-600">No organization members found.</p>
        ) : (
          <div className="mt-5 overflow-x-auto">
            <table className="w-full min-w-[680px] text-left text-sm">
              <thead className="border-b border-slate-200 text-xs uppercase tracking-wide text-slate-500">
                <tr><th className="px-3 py-3">User</th><th className="px-3 py-3">Role</th><th className="px-3 py-3">Status</th><th className="px-3 py-3 text-right">Actions</th></tr>
              </thead>
              <tbody>
                {members.map((member) => (
                  <tr key={member.id} className="border-b border-slate-100 last:border-0">
                    <td className="px-3 py-3"><div className="font-medium text-slate-900">{member.username}</div><div className="text-xs text-slate-500">{member.email}</div></td>
                    <td className="px-3 py-3">
                      <select
                        value={member.role ?? 'VIEWER'}
                        onChange={(event) => memberMutation.mutate({ userId: member.id, changes: { role: event.target.value as OrganizationRole } })}
                        className="rounded border border-slate-200 px-2 py-1 font-mono text-xs text-slate-600"
                      >
                        {roles.map((role) => <option key={role} value={role}>{role}</option>)}
                      </select>
                    </td>
                    <td className="px-3 py-3"><span className={member.active ? 'text-emerald-700' : 'text-slate-500'}>{member.active ? 'Active' : 'Disabled'}</span></td>
                    <td className="px-3 py-3 text-right">
                      <button
                        onClick={() => {
                          if (!member.active || window.confirm(`Disable ${member.username}?`)) {
                            memberMutation.mutate({ userId: member.id, changes: { active: !member.active } })
                          }
                        }}
                        className="mr-3 text-xs font-semibold text-sky-700 hover:text-sky-900"
                      >{member.active ? 'Disable' : 'Enable'}</button>
                      <button
                        onClick={() => { if (window.confirm(`Remove ${member.username} from this organization?`)) removeMutation.mutate(member.id) }}
                        className="text-xs font-semibold text-rose-700 hover:text-rose-900"
                      >Remove</button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {(memberMutation.error || removeMutation.error) && <p className="mt-4 text-sm text-rose-700">{(memberMutation.error ?? removeMutation.error)?.message}</p>}
      </section>
    </main>
  )
}
