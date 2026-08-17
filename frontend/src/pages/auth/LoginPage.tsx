import { useState, type FormEvent } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../../auth/authContextValue'

export function LoginPage() {
  const { login, ready, user } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const destination = (location.state as { from?: string } | null)?.from ?? '/'

  if (ready && user) return <Navigate to={destination} replace />

  async function submit(event: FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      await login(email, password)
      navigate(destination, { replace: true })
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Sign in failed')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="grid min-h-screen place-items-center bg-slate-950 px-5">
      <section className="w-full max-w-md rounded-2xl bg-white p-8 shadow-2xl">
        <div className="mb-7 flex items-center gap-3">
          <span className="grid h-11 w-11 place-items-center rounded-xl bg-sky-600 font-black text-white">LM</span>
          <div><h1 className="text-xl font-bold text-slate-950">LogMonitor</h1><p className="text-sm text-slate-500">Management console</p></div>
        </div>
        <form className="space-y-5" onSubmit={submit}>
          <div>
            <label htmlFor="email" className="mb-1.5 block text-sm font-semibold text-slate-700">Email</label>
            <input id="email" name="email" type="email" autoComplete="username" required value={email} onChange={(event) => setEmail(event.target.value)} className="w-full rounded-lg border border-slate-300 px-3 py-2.5 outline-none focus:border-sky-500 focus:ring-2 focus:ring-sky-100" />
          </div>
          <div>
            <label htmlFor="password" className="mb-1.5 block text-sm font-semibold text-slate-700">Password</label>
            <input id="password" name="password" type="password" autoComplete="current-password" required value={password} onChange={(event) => setPassword(event.target.value)} className="w-full rounded-lg border border-slate-300 px-3 py-2.5 outline-none focus:border-sky-500 focus:ring-2 focus:ring-sky-100" />
          </div>
          {error && <p role="alert" className="rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-700">{error}</p>}
          <button type="submit" disabled={submitting || !ready} className="w-full rounded-lg bg-sky-600 px-4 py-2.5 font-semibold text-white hover:bg-sky-700 disabled:cursor-not-allowed disabled:opacity-60">
            {submitting ? 'Signing in...' : 'Sign in'}
          </button>
        </form>
      </section>
    </main>
  )
}
