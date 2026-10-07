package com.todoapp.security;

import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.todoapp.config.AppProperties;
import com.todoapp.domain.User;
import com.todoapp.repository.UserRepository;

/**
 * Turns a verified identity from Google into one of our {@link User} rows.
 * <p>
 * Shared by both sign-in paths, because asking Google for the {@code openid}
 * scope routes the login through OIDC while omitting it uses plain OAuth2, and
 * both need identical account-resolution behaviour.
 * <p>
 * Three cases, in order:
 * <ol>
 * <li>Google id already known - refresh the cached avatar/name.</li>
 * <li>Email matches an existing local account - link Google onto it, so one
 * person signing in either way sees the same to-dos.</li>
 * <li>Completely new - create an account with no password, flagged verified
 * because Google asserts the address is.</li>
 * </ol>
 */
@Service
public class AccountLinkService {

	private static final Logger log = LoggerFactory.getLogger(AccountLinkService.class);

	private final UserRepository userRepository;
	private final AppProperties properties;

	public AccountLinkService(UserRepository userRepository, AppProperties properties) {
		this.userRepository = userRepository;
		this.properties = properties;
	}

	/**
	 * @param googleId    Google's stable per-person id (the {@code sub} claim)
	 * @param email       the address Google reports
	 * @param displayName best available human name
	 * @param avatarUrl   profile picture, may be null
	 * @param verified    whether Google asserts the address is verified. On the
	 *                    OIDC path this comes from a signed ID token, so it is
	 *                    trustworthy; on plain OAuth2 it is an HTTPS response.
	 */
	@Transactional
	public User resolve(String googleId, String email, String displayName, String avatarUrl, boolean verified) {
		if (googleId == null || googleId.isBlank() || email == null || email.isBlank()) {
			// Without an address we cannot key the account, and without a subject
			// we cannot recognise this person again on their next visit.
			throw new OAuth2AuthenticationException(
					new OAuth2Error("invalid_user_info"),
					"Google did not return an email address for this account");
		}

		String normalizedEmail = email.trim().toLowerCase();

		Optional<User> byGoogleId = this.userRepository.findByGoogleId(googleId);
		if (byGoogleId.isPresent()) {
			User existing = byGoogleId.get();
			refreshProfile(existing, avatarUrl);
			return existing;
		}

		Optional<User> byEmail = this.userRepository.findByEmailIgnoreCase(normalizedEmail);
		if (byEmail.isPresent()) {
			return linkToExisting(byEmail.get(), googleId, normalizedEmail, avatarUrl, verified);
		}

		User created = new User(normalizedEmail, null, displayName == null || displayName.isBlank()
				? normalizedEmail
				: displayName);
		created.setEmailVerified(verified);
		created.setAvatarUrl(avatarUrl);
		created.linkGoogle(googleId, avatarUrl);

		User saved = this.userRepository.save(created);
		log.info("Created account from Google sign-in id={} email={}", saved.getId(), saved.getEmail());
		return saved;
	}

	private User linkToExisting(User existing, String googleId, String email, String avatarUrl,
			boolean verified) {
		if (!this.properties.accountLinking().enabled()) {
			// Refuse rather than silently creating a duplicate account.
			throw new OAuth2AuthenticationException(
					new OAuth2Error("account_linking_disabled"),
					"An account already exists for " + email
							+ ". Sign in with your password instead.");
		}

		// Only link on an address Google has verified. Linking an unverified
		// address would let anyone claim an account they do not control.
		if (!verified) {
			throw new OAuth2AuthenticationException(
					new OAuth2Error("unverified_email"),
					"Google has not verified this email address, so it cannot be linked "
							+ "to the existing account. Sign in with your password instead.");
		}

		existing.linkGoogle(googleId, avatarUrl);
		existing.setEmailVerified(true);
		// Take the better display name if Google supplied one and we have none
		if ((existing.getDisplayName() == null || existing.getDisplayName().isBlank())
				&& avatarUrl != null) {
			existing.setDisplayName(email);
		}
		User saved = this.userRepository.save(existing);

		log.info("Linked Google account to existing user id={} email={}", saved.getId(), saved.getEmail());
		return saved;
	}

	private void refreshProfile(User existing, String avatarUrl) {
		if (avatarUrl != null && !avatarUrl.equals(existing.getAvatarUrl())) {
			existing.setAvatarUrl(avatarUrl);
			this.userRepository.save(existing);
		}
	}

	/** Shared claim-reading helper; the OIDC and OAuth2 payloads differ in shape. */
	public static String claim(Map<String, Object> claims, String key) {
		Object value = claims.get(key);
		return value == null ? null : value.toString();
	}

}
