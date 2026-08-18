export function HomePage() {
  return (
    <main className="min-h-screen bg-transparent px-6 py-16 text-slate-800">
      <div className="mx-auto flex max-w-5xl flex-col gap-8 rounded-3xl border border-slate-200 bg-white/80 p-10 shadow-xl backdrop-blur">
        <div className="space-y-4">
          <span className="inline-flex items-center rounded-full bg-sky-100 px-3 py-1 text-sm font-medium text-sky-700">
            Centralized observability
          </span>
          <h1 className="text-4xl font-semibold tracking-tight sm:text-5xl">
            Centralized Log Monitoring System
          </h1>
          <p className="max-w-3xl text-lg text-slate-600">
            Ingest, search, stream, and alert on structured application events
            through one secure management console.
          </p>
        </div>

        <div className="grid gap-6 md:grid-cols-3">
          <div className="rounded-2xl border border-slate-200 bg-slate-50 p-5">
            <h2 className="text-xl font-semibold">Backend</h2>
            <p className="mt-2 text-sm text-slate-600">
              Java 21, Spring Boot 3.x, Actuator, MongoDB, security and
              WebSocket foundation.
            </p>
          </div>
          <div className="rounded-2xl border border-slate-200 bg-slate-50 p-5">
            <h2 className="text-xl font-semibold">Frontend</h2>
            <p className="mt-2 text-sm text-slate-600">
              React, TypeScript, Vite, TanStack Query, and Router scaffolding.
            </p>
          </div>
          <div className="rounded-2xl border border-slate-200 bg-slate-50 p-5">
            <h2 className="text-xl font-semibold">Operations</h2>
            <p className="mt-2 text-sm text-slate-600">
              Docker Compose, local MongoDB profile, health checks, and test
              containers.
            </p>
          </div>
        </div>
      </div>
    </main>
  );
}
