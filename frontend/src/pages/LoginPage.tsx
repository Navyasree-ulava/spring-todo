import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { AlertCircle, Loader2 } from 'lucide-react'

import { ApiError } from '@/lib/api'
import { useAuth } from '@/lib/auth'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

type Mode = 'login' | 'register'

export default function LoginPage() {
  const { login, register, googleEnabled } = useAuth()
  const navigate = useNavigate()

  const [mode, setMode] = useState<Mode>('login')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    setPending(true)
    setError(null)
    setFieldErrors({})
    try {
      if (mode === 'login') {
        await login(email, password)
      } else {
        await register(email, password, displayName)
      }
      navigate('/', { replace: true })
    } catch (caught) {
      if (caught instanceof ApiError) {
        setError(caught.message)
        setFieldErrors(caught.fieldErrors)
      } else {
        setError('Something went wrong. Please try again.')
      }
    } finally {
      setPending(false)
    }
  }

  function switchMode(next: Mode) {
    setMode(next)
    setError(null)
    setFieldErrors({})
  }

  return (
    <main className="grid min-h-dvh place-items-center px-6 py-16">
      <div className="w-full max-w-sm">
        <div className="mb-8">
          <div className="mb-5 inline-flex size-9 items-center justify-center rounded-lg border border-border bg-card">
            <svg viewBox="0 0 24 24" className="size-4.5 text-primary" fill="none" strokeWidth={2.2}>
              <path
                d="M4 6.5h2.5M9 6.5h11M4 12h2.5M9 12h11M4 17.5h2.5M9 17.5h6"
                stroke="currentColor"
                strokeLinecap="round"
              />
            </svg>
          </div>
          <h1 className="text-[1.375rem] font-semibold tracking-[-0.02em]">
            {mode === 'login' ? 'Welcome back' : 'Create your account'}
          </h1>
          <p className="mt-1.5 text-sm text-muted-foreground">
            {mode === 'login'
              ? 'Sign in to pick up where you left off.'
              : 'A few details and you are set.'}
          </p>
        </div>

        <form onSubmit={onSubmit} className="space-y-4" noValidate>
          {mode === 'register' && (
            <Field label="Name" id="name" error={fieldErrors.displayName}>
              <Input
                id="name"
                value={displayName}
                onChange={(e) => setDisplayName(e.target.value)}
                placeholder="Ada Lovelace"
                autoComplete="name"
                required
              />
            </Field>
          )}

          <Field label="Email" id="email" error={fieldErrors.email}>
            <Input
              id="email"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="you@example.com"
              autoComplete="email"
              required
            />
          </Field>

          <Field label="Password" id="password" error={fieldErrors.password}>
            <Input
              id="password"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder={mode === 'register' ? 'At least 8 characters' : '••••••••'}
              autoComplete={mode === 'register' ? 'new-password' : 'current-password'}
              required
            />
          </Field>

          {error && (
            <div
              role="alert"
              className="flex items-start gap-2 rounded-lg border border-destructive/25 bg-destructive/8 px-3 py-2.5 text-sm text-destructive"
            >
              <AlertCircle className="mt-0.5 size-4 shrink-0" />
              <span className="leading-snug">{error}</span>
            </div>
          )}

          <Button type="submit" className="h-9 w-full" disabled={pending}>
            {pending && <Loader2 className="size-4 animate-spin" />}
            {mode === 'login' ? 'Sign in' : 'Create account'}
          </Button>
        </form>

        {googleEnabled && (
          <>
            <div className="my-5 flex items-center gap-3">
              <span className="h-px flex-1 bg-border" />
              <span className="label-xs text-muted-foreground">or</span>
              <span className="h-px flex-1 bg-border" />
            </div>
            <GoogleButton />
          </>
        )}

        <p className="mt-7 text-center text-sm text-muted-foreground">
          {mode === 'login' ? "Don't have an account?" : 'Already have one?'}{' '}
          <button
            type="button"
            onClick={() => switchMode(mode === 'login' ? 'register' : 'login')}
            className="font-medium text-foreground underline-offset-4 hover:underline"
          >
            {mode === 'login' ? 'Sign up' : 'Sign in'}
          </button>
        </p>
      </div>
    </main>
  )
}

function GoogleButton() {
  const [pending, setPending] = useState(false)

  return (
    <Button
      type="button"
      variant="outline"
      className="h-9 w-full"
      disabled={pending}
      onClick={() => {
        setPending(true)
        // A full-page redirect, not fetch: Google needs a top-level navigation.
        // Same-origin thanks to the Vite proxy; Spring sends us back to
        // /auth/callback, which the router turns into a normal signed-in view.
        window.location.href = '/oauth2/authorization/google'
      }}
    >
      <GoogleMark />
      Continue with Google
    </Button>
  )
}

function GoogleMark() {
  return (
    <svg viewBox="0 0 24 24" className="size-4" aria-hidden="true">
      <path
        fill="#4285F4"
        d="M23.5 12.3c0-.8-.1-1.6-.2-2.3H12v4.5h6.5a5.6 5.6 0 0 1-2.4 3.6v3h3.9c2.3-2.1 3.5-5.2 3.5-8.8z"
      />
      <path
        fill="#34A853"
        d="M12 24c3.2 0 5.9-1.1 7.9-2.9l-3.9-3c-1.1.7-2.4 1.2-4 1.2-3.1 0-5.7-2.1-6.6-4.9H1.4v3.1A12 12 0 0 0 12 24z"
      />
      <path fill="#FBBC05" d="M5.4 14.4a7.2 7.2 0 0 1 0-4.6V6.7H1.4a12 12 0 0 0 0 10.8l4-3.1z" />
      <path
        fill="#EA4335"
        d="M12 4.7c1.8 0 3.3.6 4.5 1.8l3.4-3.4A12 12 0 0 0 1.4 6.7l4 3.1C6.3 7 8.9 4.7 12 4.7z"
      />
    </svg>
  )
}

function Field({
  label,
  id,
  error,
  children,
}: {
  label: string
  id: string
  error?: string
  children: React.ReactNode
}) {
  return (
    <div className="space-y-1.5">
      <Label htmlFor={id}>{label}</Label>
      {children}
      {error && (
        <p role="alert" className="text-xs text-destructive">
          {error}
        </p>
      )}
    </div>
  )
}
