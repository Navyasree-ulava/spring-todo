import { useEffect } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import { ThemeProvider } from 'next-themes'
import { Toaster } from '@/components/ui/sonner'

import { AuthProvider, useAuth } from '@/lib/auth'
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

function RequireAuth({ children }: { children: React.ReactNode }) {
  const { user, loading } = useAuth()
  // Wait for the /me check before deciding, otherwise a refresh would flash the
  // login screen for a signed-in user.
  if (loading) return <FullPageSpinner />
  if (!user) return <Navigate to="/login" replace />
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
          <Route
            path="/"
            element={
              <RequireAuth>
                <TodoPage />
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

/** Keeps the document title in sync with the app name. */
export function useDocumentTitle() {
  useEffect(() => {
    document.title = 'Tasks'
  }, [])
}
