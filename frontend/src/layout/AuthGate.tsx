import { ReactNode } from 'react';
import { Navigate } from 'react-router-dom';
import { ErrorState, LoadingState } from '../components/State';
import { AuthContextValue, useAuth } from '../context/AuthContext';

export function AuthGate({ children }: { children: ReactNode }) {
  const auth = useAuth();
  return <AuthGateView auth={auth}>{children}</AuthGateView>;
}

export function AuthGateView({ children, auth }: { children: ReactNode; auth: AuthContextValue }) {
  if (auth.sessionExpired || auth.authenticationFailed) {
    return <Navigate to="/login" state={{ expired: true }} replace />;
  }
  if (auth.isLoading) {
    return <div className="flex min-h-screen items-center justify-center bg-[#edf3f2] p-6"><div className="w-full max-w-lg"><LoadingState label="Loading administrator permissions and tenant context" /></div></div>;
  }
  if (auth.error) {
    return <div className="flex min-h-screen items-center justify-center bg-[#edf3f2] p-6"><div className="w-full max-w-lg"><ErrorState error={auth.error} onRetry={auth.retry} /></div></div>;
  }
  if (!auth.isAuthenticated) {
    return <Navigate to="/login" replace />;
  }
  if (!auth.isAdmin) {
    return <Navigate to="/access-denied" replace />;
  }
  return <>{children}</>;
}
