package com.todoapp.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the `app.*` block from application.yml.
 * <p>
 * A record works nicely here: Spring constructs it via the canonical
 * constructor, so validation is just a matter of adding constraints to the
 * components rather than writing setters.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Google google, String frontendUrl, AccountLinking accountLinking) {

	/**
	 * @param secure set to true only behind HTTPS. A "Secure" cookie is not
	 *               transmitted over plain http, so leaving it on in local dev
	 *               would silently break every request.
	 */
	public record Jwt(String secret, Duration expiration, String cookieName, boolean secure) {
	}

	public record Google(String clientId, String clientSecret) {

		/** False while the placeholders are still unfilled, so we can fail loudly. */
		public boolean isConfigured() {
			return clientId != null
					&& !clientId.isBlank()
					&& !clientId.startsWith("PASTE_")
					&& clientSecret != null
					&& !clientSecret.isBlank()
					&& !clientSecret.startsWith("PASTE_");
		}
	}

	/**
	 * Controls whether a Google login may attach itself to an account that already
	 * exists with the same email.
	 * <p>
	 * Linking is what lets one person sign in with either method and see the same
	 * to-dos. It is only safe while the Google address is verified AND the existing
	 * account's own address has been proven. This app never sends verification
	 * emails, so set {@code ACCOUNT_LINKING_ENABLED=false} before exposing it to the
	 * internet, or add real email verification first.
	 */
	public record AccountLinking(boolean enabled) {
	}

}
