package com.todoapp.security;

import java.util.Collection;
import java.util.Map;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

/**
 * Wraps the attributes Google returns so we can read them by name without
 * casting to Map at every call site.
 */
public class TodoOAuth2User implements OAuth2User {

	private final OAuth2User delegate;
	private final String email;
	private final String displayName;
	private final String avatarUrl;

	public TodoOAuth2User(OAuth2User delegate) {
		this.delegate = delegate;

		Map<String, Object> attributes = delegate.getAttributes();
		// userNameAttributeName is set to "email" in GoogleOAuth2Config, so Spring
		// guarantees this one is present.
		this.email = delegate.getName();
		this.displayName = firstNonBlank(
				attributes.get("name"),
				attributes.get("given_name"),
				this.email);
		this.avatarUrl = (String) attributes.get("picture");
	}

	private static String firstNonBlank(Object... candidates) {
		for (Object candidate : candidates) {
			if (candidate != null && !candidate.toString().isBlank()) {
				return candidate.toString();
			}
		}
		return "";
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return this.delegate.getAuthorities();
	}

	@Override
	public Map<String, Object> getAttributes() {
		return this.delegate.getAttributes();
	}

	@Override
	public String getName() {
		return this.email;
	}

	public String getEmail() {
		return email;
	}

	public String getDisplayName() {
		return displayName;
	}

	public String getAvatarUrl() {
		return avatarUrl;
	}

}
