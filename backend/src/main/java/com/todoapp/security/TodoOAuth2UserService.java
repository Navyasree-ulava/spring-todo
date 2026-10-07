package com.todoapp.security;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.todoapp.config.AppProperties;
import com.todoapp.domain.User;
import com.todoapp.repository.UserRepository;

/**
 * Turns a Google login into one of our {@link User} rows.
 * <p>
 * Three cases, in order:
 * <ol>
 * <li>Google id already known - return it, refreshing the cached avatar/name.</li>
 * <li>Email matches an existing local account - link Google onto it, so one
 * person signing in either way sees the same to-dos.</li>
 * <li>Completely new - create an account with no password, flagged as verified
 * because Google asserts the address is.</li>
 * </ol>
 */
@Service
public class TodoOAuth2UserService extends DefaultOAuth2UserService {

	private static final Logger log = LoggerFactory.getLogger(TodoOAuth2UserService.class);

	private final UserRepository userRepository;
	private final AppProperties properties;

	public TodoOAuth2UserService(UserRepository userRepository, AppProperties properties) {
		this.userRepository = userRepository;
		this.properties = properties;
	}

	@Override
	@Transactional
	public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
		OAuth2User delegate = super.loadUser(userRequest);
		TodoOAuth2User googleUser = new TodoOAuth2User(delegate);

		// userNameAttributeName is "email", so getName() is the address. Google's
		// stable per-person id lives in the "sub" claim.
		String googleId = (String) delegate.getAttributes().get("sub");
		String email = googleUser.getEmail() == null ? null : googleUser.getEmail().toLowerCase();

		if (googleId == null || email == null || email.isBlank()) {
			// Without an address we cannot key the account, and without a subject
			// we cannot recognise this person again on their next visit.
			throw new OAuth2AuthenticationException(
					new OAuth2Error("invalid_user_info"),
					"Google did not return an email address for this account");
		}

		boolean googleVerified = isVerifiedByGoogle(delegate);

		Optional<User> byGoogleId = this.userRepository.findByGoogleId(googleId);
		if (byGoogleId.isPresent()) {
			User existing = byGoogleId.get();
			refreshProfile(existing, googleUser);
			return googleUser;
		}

		Optional<User> byEmail = this.userRepository.findByEmailIgnoreCase(email);
		if (byEmail.isPresent()) {
			return linkToExisting(byEmail.get(), googleId, googleUser, googleVerified);
		}

		User created = new User(email, null, googleUser.getDisplayName());
		created.setEmailVerified(googleVerified);
		created.setAvatarUrl(googleUser.getAvatarUrl());
		created.linkGoogle(googleId, googleUser.getAvatarUrl());

		User saved = this.userRepository.save(created);
		log.info("Created account from Google sign-in id={} email={}", saved.getId(), saved.getEmail());
		return googleUser;
	}

	private OAuth2User linkToExisting(User existing, String googleId, TodoOAuth2User googleUser,
			boolean googleVerified) {
		if (!this.properties.accountLinking().enabled()) {
			// Refuse rather than silently creating a duplicate account.
			throw new OAuth2AuthenticationException(
					new OAuth2Error("account_linking_disabled"),
					"An account already exists for " + existing.getEmail()
							+ ". Sign in with your password instead.");
		}

		// Only link on an address Google has verified. Linking an unverified
		// address would let anyone claim an account they do not control.
		if (!googleVerified) {
			throw new OAuth2AuthenticationException(
					new OAuth2Error("unverified_email"),
					"Google has not verified this email address, so it cannot be linked "
							+ "to the existing account. Sign in with your password instead.");
		}

		existing.linkGoogle(googleId, googleUser.getAvatarUrl());
		existing.setEmailVerified(true);
		this.userRepository.save(existing);

		log.info("Linked Google account to existing user id={} email={}", existing.getId(), existing.getEmail());
		return googleUser;
	}

	/** Keeps the stored avatar and display name current with what Google reports. */
	private void refreshProfile(User existing, TodoOAuth2User googleUser) {
		boolean dirty = false;
		if (googleUser.getAvatarUrl() != null && !googleUser.getAvatarUrl().equals(existing.getAvatarUrl())) {
			existing.setAvatarUrl(googleUser.getAvatarUrl());
			dirty = true;
		}
		if (dirty) {
			this.userRepository.save(existing);
		}
	}

	private boolean isVerifiedByGoogle(OAuth2User delegate) {
		Object claim = delegate.getAttributes().get("email_verified");
		return Boolean.TRUE.equals(claim);
	}

}
