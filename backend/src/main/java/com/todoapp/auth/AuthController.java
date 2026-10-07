package com.todoapp.auth;

import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.todoapp.auth.dto.LoginRequest;
import com.todoapp.auth.dto.RegisterRequest;
import com.todoapp.auth.dto.UserResponse;
import com.todoapp.common.NotFoundException;
import com.todoapp.config.AppProperties;
import com.todoapp.domain.User;
import com.todoapp.repository.UserRepository;
import com.todoapp.security.JwtService;
import com.todoapp.security.TodoPrincipal;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService authService;
	private final UserRepository userRepository;
	private final JwtService jwtService;
	private final AppProperties properties;

	public AuthController(AuthService authService, UserRepository userRepository, JwtService jwtService,
			AppProperties properties) {
		this.authService = authService;
		this.userRepository = userRepository;
		this.jwtService = jwtService;
		this.properties = properties;
	}

	@PostMapping("/register")
	public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
		return sessionFor(this.authService.register(request));
	}

	@PostMapping("/login")
	public ResponseEntity<UserResponse> login(@Valid @RequestBody LoginRequest request) {
		return sessionFor(this.authService.login(request.email(), request.password()));
	}

	/**
	 * Clears the cookie. Safe to call when already logged out, so the frontend
	 * can fire it on a best-effort basis without special-casing errors.
	 */
	@PostMapping("/logout")
	public ResponseEntity<Void> logout() {
		return ResponseEntity.noContent()
				.header(HttpHeaders.SET_COOKIE, clearedCookie().toString())
				.build();
	}

	/** Lets the frontend re-hydrate its session on page load. */
	@GetMapping("/me")
	public UserResponse me(@AuthenticationPrincipal TodoPrincipal principal) {
		// Defence in depth: the security rules already require authentication here,
		// so a null principal would mean the rules were loosened by mistake. Fail
		// loudly rather than letting a NullPointerException become a 500.
		if (principal == null) {
			throw new AuthenticationCredentialsNotFoundException("You must be signed in");
		}
		return this.userRepository.findById(principal.getId())
				.map(UserResponse::from)
				.orElseThrow(() -> new NotFoundException("Account no longer exists"));
	}

	/**
	 * The token goes out as a Set-Cookie header, never in the JSON body. That is
	 * the whole point: a cookie marked httpOnly is unreadable by JavaScript, so
	 * an XSS bug cannot exfiltrate the session.
	 */
	private ResponseEntity<UserResponse> sessionFor(User user) {
		String token = this.jwtService.generate(user);

		ResponseCookie cookie = ResponseCookie.from(this.properties.jwt().cookieName(), token)
				.httpOnly(true)
				.secure(this.properties.jwt().secure())
				// Lax lets the browser attach the cookie to same-site calls while
				// blocking it on cross-site POSTs - our CSRF mitigation.
				.sameSite("Lax")
				.path("/")
				.maxAge(this.properties.jwt().expiration())
				.build();

		return ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE, cookie.toString())
				.body(UserResponse.from(user));
	}

	private ResponseCookie clearedCookie() {
		return ResponseCookie.from(this.properties.jwt().cookieName(), "")
				.httpOnly(true)
				.secure(this.properties.jwt().secure())
				.sameSite("Lax")
				.path("/")
				.maxAge(Duration.ZERO)
				.build();
	}

}
