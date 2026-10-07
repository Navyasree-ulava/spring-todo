package com.todoapp.security;

import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.todoapp.config.AppProperties;
import com.todoapp.domain.User;
import com.todoapp.repository.UserRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Runs once per request. If a valid session cookie is present it populates the
 * SecurityContext, so downstream code can just ask "who is this?".
 * <p>
 * Without a filter like this, Spring Security only knows the user for form
 * login or OAuth2 redirects - it has no idea how to read our own cookie.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

	private final JwtService jwtService;
	private final UserRepository userRepository;
	private final AppProperties properties;

	public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository,
			AppProperties properties) {
		this.jwtService = jwtService;
		this.userRepository = userRepository;
		this.properties = properties;
	}

	@Override
	protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
			@NonNull FilterChain filterChain) throws ServletException, IOException {

		String token = extractToken(request);
		if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
			authenticate(token, request);
		}
		filterChain.doFilter(request, response);
	}

	private void authenticate(String token, HttpServletRequest request) {
		Jwt jwt = this.jwtService.verify(token);
		if (jwt == null) {
			return;
		}

		Long userId;
		try {
			userId = Long.valueOf(jwt.getSubject());
		}
		catch (NumberFormatException ex) {
			log.warn("JWT subject was not a user id: {}", jwt.getSubject());
			return;
		}

		// Re-read from the database rather than trusting the token body. A token
		// stays valid until expiry, so this is how a deleted account loses access
		// immediately rather than in seven days.
		User user = this.userRepository.findById(userId).orElse(null);
		if (user == null) {
			return;
		}

		TodoPrincipal principal = TodoPrincipal.from(user);
		UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
				principal, null, List.of());
		auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
		SecurityContextHolder.getContext().setAuthentication(auth);
	}

	private String extractToken(HttpServletRequest request) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return null;
		}
		String name = this.properties.jwt().cookieName();
		for (Cookie cookie : cookies) {
			if (name.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
				return cookie.getValue();
			}
		}
		return null;
	}

}
