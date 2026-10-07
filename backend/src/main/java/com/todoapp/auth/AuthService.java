package com.todoapp.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.todoapp.auth.dto.RegisterRequest;
import com.todoapp.common.ConflictException;
import com.todoapp.domain.User;
import com.todoapp.repository.UserRepository;

@Service
public class AuthService {

	private static final Logger log = LoggerFactory.getLogger(AuthService.class);

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final AuthenticationManager authenticationManager;

	public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
			AuthenticationManager authenticationManager) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.authenticationManager = authenticationManager;
	}

	@Transactional
	public User register(RegisterRequest request) {
		String email = request.email().trim().toLowerCase();

		if (this.userRepository.existsByEmailIgnoreCase(email)) {
			// The database has a unique index too - this check is just a friendlier
			// error than letting the constraint violation surface as a 500.
			throw new ConflictException("An account with that email already exists");
		}

		User user = new User(email, this.passwordEncoder.encode(request.password()),
				request.displayName().trim());

		// TODO: flip this to true once email verification is wired up. Leaving it
		// false is what currently blocks account linking on unverified addresses.
		user.setEmailVerified(false);

		User saved = this.userRepository.save(user);
		log.info("Registered user id={} email={}", saved.getId(), saved.getEmail());
		return saved;
	}

	/**
	 * Verifies the password through Spring's AuthenticationManager rather than
	 * calling the encoder directly. That way the account-status checks
	 * (enabled, not locked) still apply, and the failure mode stays consistent
	 * with the rest of Spring Security.
	 */
	@Transactional(readOnly = true)
	public User login(String email, String password) {
		String normalized = email.trim().toLowerCase();

		User existing = this.userRepository.findByEmailIgnoreCase(normalized)
				.orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

		if (!existing.hasPassword()) {
			// Google-only account. Point the user at the button they should have used
			// rather than pretending the password was simply wrong.
			throw new BadCredentialsException(
					"This account uses Google sign-in. Use \"Continue with Google\" instead.");
		}

		try {
			this.authenticationManager.authenticate(
					new UsernamePasswordAuthenticationToken(normalized, password));
		}
		catch (BadCredentialsException ex) {
			throw ex;
		}

		return existing;
	}

}
