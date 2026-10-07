package com.todoapp.security;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.todoapp.domain.User;

/**
 * Adapts our {@link User} entity to Spring Security's {@link UserDetails}.
 * <p>
 * Spring works with UserDetails so it can ask "is this account enabled / locked"
 * without knowing anything about our database schema.
 */
public class TodoPrincipal implements UserDetails {

	private final Long id;
	private final String email;
	private final String passwordHash;
	private final String displayName;
	private final boolean enabled;

	private TodoPrincipal(Long id, String email, String passwordHash, String displayName, boolean enabled) {
		this.id = id;
		this.email = email;
		this.passwordHash = passwordHash;
		this.displayName = displayName;
		this.enabled = enabled;
	}

	public static TodoPrincipal from(User user) {
		return new TodoPrincipal(user.getId(), user.getEmail(), user.getPasswordHash(),
				user.getDisplayName(), true);
	}

	public Long getId() {
		return id;
	}

	public String getEmail() {
		return email;
	}

	public String getDisplayName() {
		return displayName;
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of(new SimpleGrantedAuthority("ROLE_USER"));
	}

	@Override
	public String getPassword() {
		return this.passwordHash;
	}

	@Override
	public String getUsername() {
		return this.email;
	}

	@Override
	public boolean isAccountNonExpired() {
		return true;
	}

	@Override
	public boolean isAccountNonLocked() {
		return true;
	}

	@Override
	public boolean isCredentialsNonExpired() {
		return true;
	}

	@Override
	public boolean isEnabled() {
		return this.enabled;
	}

	/** Present so we can reject a password login against a Google-only account. */
	public boolean isPasswordLoginPossible() {
		return this.passwordHash != null && !this.passwordHash.isBlank();
	}

}
