package com.todoapp.security;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import com.todoapp.config.AppProperties;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * OAuth2 happens as a browser redirect, so a failure cannot be reported as a JSON
 * body - the SPA is not the caller. Instead we bounce back to the frontend with
 * the reason in the query string and let it show a message.
 */
@Component
public class OAuth2AuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {

	private static final Logger log = LoggerFactory.getLogger(OAuth2AuthenticationFailureHandler.class);

	private final AppProperties properties;
	private final RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();

	public OAuth2AuthenticationFailureHandler(AppProperties properties) {
		this.properties = properties;
	}

	@Override
	public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException exception) throws java.io.IOException {

		String reason = describe(exception);
		log.warn("Google sign-in failed: {}", reason);

		String target = this.properties.frontendUrl()
				+ "/auth/callback?error=" + URLEncoder.encode(reason, StandardCharsets.UTF_8);

		this.redirectStrategy.sendRedirect(request, response, target);
	}

	/**
	 * Our own exceptions (see TodoOAuth2UserService) carry a message written for
	 * the user, so surface that. Anything else is an unexpected fault and gets a
	 * generic message rather than leaking internals.
	 */
	private String describe(AuthenticationException exception) {
		if (exception instanceof OAuth2AuthenticationException oauth2Exception) {
			String errorCode = oauth2Exception.getError().getErrorCode();
			return switch (errorCode) {
				case "account_linking_disabled", "unverified_email" ->
						String.valueOf(oauth2Exception.getError().getDescription());
				case "access_denied" -> "Google sign-in was cancelled";
				default -> "Google sign-in failed. Please try again.";
			};
		}
		return "Google sign-in failed. Please try again.";
	}

}
