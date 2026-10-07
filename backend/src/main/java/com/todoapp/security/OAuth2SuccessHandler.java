package com.todoapp.security;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.todoapp.config.AppProperties;
import com.todoapp.config.Cookies;
import com.todoapp.domain.User;
import com.todoapp.repository.UserRepository;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Google hands control back to the browser at a backend URL, but this app's
 * session is our own JWT cookie - not whatever Spring set up for OAuth2.
 * <p>
 * On success we mint a JWT, set it as the same httpOnly cookie the password flow
 * uses, then bounce to the frontend. From that point on nothing can tell whether
 * you signed in with Google or a password, which is the point.
 */
@Component
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

	private static final Logger log = LoggerFactory.getLogger(OAuth2SuccessHandler.class);

	private final JwtService jwtService;
	private final UserRepository userRepository;
	private final AppProperties properties;

	public OAuth2SuccessHandler(JwtService jwtService, UserRepository userRepository, AppProperties properties) {
		this.jwtService = jwtService;
		this.userRepository = userRepository;
		this.properties = properties;
	}

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
			Authentication authentication) throws IOException, ServletException {

		String email = resolveEmail(authentication);

		User user = this.userRepository.findByEmailIgnoreCase(email)
				.orElseThrow(() -> new IllegalStateException(
						"Google sign-in succeeded but no account exists for " + email));

		String token = this.jwtService.generate(user);

		response.addHeader(HttpHeaders.SET_COOKIE, Cookies.sessionCookie(this.properties.jwt(), token).toString());

		// Land back on the SPA, which will call /api/auth/me and render as normal.
		setDefaultTargetUrl(this.properties.frontendUrl() + "/");
		log.info("Issued session cookie after Google sign-in for user id={}", user.getId());

		super.onAuthenticationSuccess(request, response, authentication);
	}

	/**
	 * The principal type depends on the scopes we asked for: requesting openid
	 * produces an OIDC user, omitting it produces a plain OAuth2 user. Casting to
	 * one specific type throws a ClassCastException on the other path, which
	 * surfaces as a 500 whitelabel page - so both are handled explicitly.
	 */
	private String resolveEmail(Authentication authentication) {
		Object principal = authentication.getPrincipal();

		if (principal instanceof TodoOAuth2User google) {
			return google.getEmail();
		}
		if (principal instanceof OidcUser oidc) {
			return AccountLinkService.claim(oidc.getClaims(), "email");
		}
		if (principal instanceof OAuth2User oauth2) {
			return oauth2.getName();
		}

		throw new IllegalStateException(
				"Unsupported principal type " + principal.getClass().getName() + " after Google sign-in");
	}

}
