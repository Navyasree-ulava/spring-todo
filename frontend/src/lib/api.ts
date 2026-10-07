import type { ApiErrorBody } from './types'

/**
 * Error carrying the HTTP status, so callers can tell "signed out, bounce to
 * login" (401) apart from "that title was blank" (400).
 */
export class ApiError extends Error {
  readonly status: number
  readonly fieldErrors: Record<string, string>

  constructor(status: number, message: string, fieldErrors: Record<string, string> = {}) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.fieldErrors = fieldErrors
  }
}

type UnauthorizedHandler = () => void
let onUnauthorized: UnauthorizedHandler | null = null

/** Lets the app drop to the signed-out state from anywhere, without prop drilling. */
export function setUnauthorizedHandler(handler: UnauthorizedHandler | null) {
  onUnauthorized = handler
}

const BASE = '/api'

/**
 * `credentials: 'include'` is what makes the httpOnly session cookie travel on
 * cross-origin dev requests. Without it the cookie is silently dropped and every
 * call comes back 401.
 */
async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(`${BASE}${path}`, {
    credentials: 'include',
    headers: init.body ? { 'Content-Type': 'application/json' } : undefined,
    ...init,
  })

  if (response.status === 204) {
    return undefined as T
  }

  if (!response.ok) {
    let body: ApiErrorBody | null = null
    try {
      body = (await response.json()) as ApiErrorBody
    } catch {
      // Non-JSON error page; fall back to the status text below.
    }
    if (response.status === 401) {
      onUnauthorized?.()
    }
    throw new ApiError(
      response.status,
      body?.message ?? `Request failed (${response.status})`,
      body?.fieldErrors ?? {},
    )
  }

  return (await response.json()) as T
}

export const api = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: 'POST', body: body === undefined ? undefined : JSON.stringify(body) }),
  put: <T>(path: string, body: unknown) =>
    request<T>(path, { method: 'PUT', body: JSON.stringify(body) }),
  patch: <T>(path: string) => request<T>(path, { method: 'PATCH' }),
  delete: <T>(path: string) => request<T>(path, { method: 'DELETE' }),
}
