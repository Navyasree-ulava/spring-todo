import { useEffect } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { toast } from 'sonner'

/**
 * Landing route after a Google round trip.
 *
 * Success: Spring sets the session cookie and redirects here with no params,
 * so we simply continue into the app.
 *
 * Failure: the backend bounces back with ?error=... instead, because a browser
 * redirect cannot return a JSON body to us.
 */
export default function OAuthCallbackPage() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const error = params.get('error')

  useEffect(() => {
    if (error) {
      toast.error(decodeURIComponent(error))
    }
    navigate('/', { replace: true })
  }, [error, navigate])

  return null
}
