import { Navigate, Outlet, useLocation } from "react-router-dom";
import { Navigation } from "../components/Navigation";
import { useAuth } from "./authContextValue";

export function ProtectedRoute() {
  const { ready, user } = useAuth();
  const location = useLocation();

  if (!ready) {
    return (
      <main className="grid min-h-screen place-items-center text-sm text-slate-600">
        Restoring your session...
      </main>
    );
  }
  if (!user)
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;

  return (
    <div className="min-h-screen flex flex-col">
      <Navigation />
      <div className="flex-1">
        <Outlet />
      </div>
    </div>
  );
}
