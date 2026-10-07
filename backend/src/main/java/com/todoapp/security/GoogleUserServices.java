package com.todoapp.security;

import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

/**
 * Account resolution for Google sign-in.
 * <p>
 * Two services exist because Spring Security picks one based on the requested
 * scopes. Asking for {@code openid} - which Google requires for a trustworthy
 * {@code email_verified} claim - routes the login through OIDC, while omitting it
 * uses plain OAuth2. Only one is invoked per login, but both must behave
 * identically, so the logic lives in {@link AccountLinkService} and these are thin
 * adapters.
 * <p>
 * Registering only the OAuth2 service was a real bug: the OIDC path silently
 * built its own user, so account linking never ran and the success handler threw
 * a ClassCastException.
 */
@Service
public class GoogleUserServices {

	private final AccountLinkService accountLinkService;

	public GoogleUserServices(AccountLinkService accountLinkService) {
		this.accountLinkService = accountLinkService;
	}

	/** Used when the scopes include {@code openid} - the configuration we ship. */
	@Bean
	public OidcUserService oidcUserService() {
		// An anonymous subclass rather than a lambda: OidcUserService is a class
		// with concrete super.loadUser behaviour we want to keep, not a functional
		// interface.
		return new OidcUserService() {
			@Override
			public OidcUser loadUser(OidcUserRequest userRequest) {
				OidcUser delegate = super.loadUser(userRequest);
				Map<String, Object> claims = delegate.getClaims();

				// sub comes from the signed ID token, so it is verified by signature -
				// a stronger guarantee than the same claim in a userinfo response.
				String googleId = delegate.getIdToken().getSubject();
				String email = AccountLinkService.claim(claims, "email");
				String name = AccountLinkService.claim(claims, "name");
				String avatar = AccountLinkService.claim(claims, "picture");
				boolean verified = Boolean.TRUE.equals(claims.get("email_verified"));

				accountLinkService.resolve(googleId, email, name, avatar, verified);

				// Rebuild with ROLE_USER so downstream checks see our own authority
				// rather than OIDC scopes like SCOPE_email. "email" is the attribute
				// key, matching userNameAttributeName in GoogleOAuth2Config.
				return new DefaultOidcUser(
						List.of(new SimpleGrantedAuthority("ROLE_USER")),
						delegate.getIdToken(),
						delegate.getUserInfo(),
						"email");
			}
		};
	}

	/** Used when openid is not requested. */
	@Bean
	public DefaultOAuth2UserService oauth2UserService() {
		return new DefaultOAuth2UserService() {
			@Override
			public OAuth2User loadUser(OAuth2UserRequest userRequest) {
				OAuth2User delegate = super.loadUser(userRequest);
				Map<String, Object> claims = delegate.getAttributes();

				String googleId = AccountLinkService.claim(claims, "sub");
				String email = delegate.getName();
				String name = AccountLinkService.claim(claims, "name");
				String avatar = AccountLinkService.claim(claims, "picture");
				boolean verified = Boolean.TRUE.equals(claims.get("email_verified"));

				accountLinkService.resolve(googleId, email, name, avatar, verified);

				return new TodoOAuth2User(delegate);
			}
		};
	}

}
