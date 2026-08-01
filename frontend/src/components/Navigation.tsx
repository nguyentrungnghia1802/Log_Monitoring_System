import { Link, useLocation } from 'react-router-dom'

export function Navigation() {
    const location = useLocation()

    const navItems = [
        { label: 'Overview', path: '/' },
        { label: 'Log Explorer', path: '/logs' },
        { label: 'Analytics', path: '/dashboard' },
        { label: 'Live Tail', path: '/live-tail' },
        { label: 'Alert Rules', path: '/alerts/rules' },
        { label: 'Alert History', path: '/alerts' },
        { label: 'Organization', path: '/organization' },
    ]

    return (
        <header className="sticky top-0 z-40 border-b border-slate-200/80 bg-white/80 backdrop-blur-md">
            <div className="mx-auto flex max-w-7xl items-center justify-between px-6 py-3.5">
                <div className="flex items-center gap-6">
                    <Link to="/" className="flex items-center gap-2.5 text-slate-900 font-bold text-lg">
                        <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-sky-600 text-white font-black text-sm shadow-md">
                            LM
                        </span>
                        <span>LogMonitor</span>
                    </Link>

                    <nav className="flex items-center gap-1">
                        {navItems.map((item) => {
                            const isActive = location.pathname === item.path
                            return (
                                <Link
                                    key={item.path}
                                    to={item.path}
                                    className={`rounded-lg px-3 py-1.5 text-sm font-medium transition-all ${isActive
                                        ? 'bg-sky-50 text-sky-700 font-semibold'
                                        : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900'
                                        }`}
                                >
                                    {item.label}
                                </Link>
                            )
                        })}
                    </nav>
                </div>

                <div className="flex items-center gap-3">
                    <span className="flex items-center gap-1.5 rounded-full bg-emerald-50 px-2.5 py-1 text-xs font-semibold text-emerald-700 border border-emerald-200">
                        <span className="h-2 w-2 rounded-full bg-emerald-500 animate-pulse"></span>
                        Demo Project
                    </span>
                </div>
            </div>
        </header>
    )
}
