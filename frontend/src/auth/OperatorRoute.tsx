import { Navigate, Outlet } from "react-router-dom";
import { useAuth } from "./authContextValue";

export function OperatorRoute() {
  const { ready, user } = useAuth();

  if (!ready) {
    return (
      <main className="grid min-h-screen place-items-center text-sm text-slate-600">
        Restoring your session...
      </main>
    );
  }
  if (!user) return <Navigate to="/login" replace />;
  if (user.organizationRole !== "ORGANIZATION_ADMIN") {
    return (
      <main className="mx-auto max-w-3xl px-6 py-16">
        <div className="rounded-2xl border border-amber-200 bg-amber-50 p-8 text-amber-900">
          <h1 className="text-xl font-bold">Operator access required</h1>
          <p className="mt-2 text-sm">
            Platform health is available only to organization administrators.
          </p>
        </div>
      </main>
    );
  }

  return <Outlet />;
}
