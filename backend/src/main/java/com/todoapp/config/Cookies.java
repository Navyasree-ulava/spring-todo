package com.todoapp.config;

import java.time.Duration;

import org.springframework.http.ResponseCookie;

/**
 * Single place that knows how the session cookie is built.
 * <p>
 * The password flow and the Google flow both need to produce the exact same
 * cookie, otherwise signing in one way would leave you signed out of the rest of
 * the app.
 */
public final class Cookies {

	private Cookies() {
	}

	public static ResponseCookie sessionCookie(AppProperties.Jwt jwt, String token) {
		return base(jwt, token)
				.maxAge(jwt.expiration())
				.build();
	}

	/** A zero-max-age cookie with the same name/path replaces the real one. */
	public static ResponseCookie clearedCookie(AppProperties.Jwt jwt) {
		return base(jwt, "")
				.maxAge(Duration.ZERO)
				.build();
	}

	private static ResponseCookie.ResponseCookieBuilder base(AppProperties.Jwt jwt, String value) {
		return ResponseCookie.from(jwt.cookieName(), value)
				// Invisible to JavaScript, so an XSS bug cannot steal the session.
				.httpOnly(true)
				// Only meaningful over HTTPS; left off in local dev where we are on
				// plain http, because a Secure cookie is never sent over http.
				.secure(jwt.secure())
				// Lets the browser attach the cookie to same-site calls while blocking
				// it on cross-site state-changing requests - our CSRF mitigation.
				.sameSite("Lax")
				.path("/");
	}

}
