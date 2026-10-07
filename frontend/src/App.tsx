import { Navigate, Route, Routes } from 'react-router-dom'
import { ThemeProvider } from 'next-themes'
import { Toaster } from '@/components/ui/sonner'

import { AuthProvider, useAuth } from '@/lib/auth'
import LandingPage from '@/pages/LandingPage'
import LoginPage from '@/pages/LoginPage'
import TodoPage from '@/pages/TodoPage'
import OAuthCallbackPage from '@/pages/OAuthCallbackPage'

function FullPageSpinner() {
  return (
    <div className="grid min-h-dvh place-items-center">
      <span
        className="size-4 animate-spin rounded-full border-2 border-muted-foreground/30 border-t-foreground"
        aria-label="Loading"
      />
    </div>
  )
}

function RequireAuth({
  children,
  fallback = null,
}: {
  children: React.ReactNode
  fallback?: React.ReactNode
}) {
  const { user, loading } = useAuth()
  // Wait for the /me check before deciding, otherwise a refresh would flash the
  // login screen for a signed-in user.
  if (loading) return <FullPageSpinner />
  if (!user) return <>{fallback}</>
  return <>{children}</>
}

function RequireAnonymous({ children }: { children: React.ReactNode }) {
  const { user, loading } = useAuth()
  if (loading) return <FullPageSpinner />
  if (user) return <Navigate to="/" replace />
  return <>{children}</>
}

export default function App() {
  return (
    <ThemeProvider attribute="class" defaultTheme="system" enableSystem disableTransitionOnChange>
      <AuthProvider>
        <Routes>
          {/* Signed out visitors get the landing page; signed-in users skip it
              straight to their list, so / never shows marketing to a real user. */}
          <Route
            path="/"
            element={
              <RequireAuth fallback={<LandingPage />}>
                <TodoPage />
              </RequireAuth>
            }
          />
          <Route
            path="/login"
            element={
              <RequireAnonymous>
                <LoginPage />
              </RequireAnonymous>
            }
          />
          <Route
            path="/auth/callback"
            element={
              <RequireAuth>
                <OAuthCallbackPage />
              </RequireAuth>
            }
          />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
        {/* Positioned top-centre so it never covers the list or the composer */}
        <Toaster position="top-center" richColors closeButton />
      </AuthProvider>
    </ThemeProvider>
  )
}
