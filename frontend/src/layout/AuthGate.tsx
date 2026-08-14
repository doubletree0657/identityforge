import { ReactNode } from 'react';
import { Navigate } from 'react-router-dom';
import { LoadingState } from '../components/State';
import { useAuth } from '../context/AuthContext';

export function AuthGate({ children }: { children: ReactNode }) {
  const auth = useAuth();
  if (auth.sessionExpired) {
    return <Navigate to="/login" state={{ expired: true }} replace />;
  }
  if (auth.isLoading) {
    return <div className="flex min-h-screen items-center justify-center bg-[#edf3f2] p-6"><div className="w-full max-w-lg"><LoadingState label="Loading administrator permissions and tenant context" /></div></div>;
  }
  if (!auth.isAuthenticated) {
    return <Navigate to="/login" state={{ expired: Boolean(auth.error) }} replace />;
  }
  if (!auth.isAdmin) {
    return <Navigate to="/access-denied" replace />;
  }
  return <>{children}</>;
}
