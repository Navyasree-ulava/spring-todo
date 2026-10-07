package com.todoapp.config;

import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

// Boot 4 ships Jackson 3, which moved to the tools.jackson namespace.
import tools.jackson.databind.ObjectMapper;

import com.todoapp.common.ApiError;
import com.todoapp.security.AppUserDetailsService;
import com.todoapp.security.GoogleUserServices;
import com.todoapp.security.JwtAuthenticationFilter;
import com.todoapp.security.OAuth2AuthenticationFailureHandler;
import com.todoapp.security.OAuth2SuccessHandler;

import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

	private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

	/**
	 * The Vite dev server and nginx are different origins from the API, so CORS
	 * applies even though everything runs on localhost.
	 */
	private static final List<String> ALLOWED_ORIGINS =
			List.of("http://localhost:5173", "http://127.0.0.1:5173", "http://localhost:4173");

	private final AppProperties properties;

	public SecurityConfig(AppProperties properties) {
		this.properties = properties;
	}

	@Bean
	SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter,
			ObjectMapper objectMapper, GoogleUserServices googleUserServices,
			OAuth2SuccessHandler oAuth2SuccessHandler, OAuth2AuthenticationFailureHandler oAuth2FailureHandler)
			throws Exception {

		// CSRF is off because the session cookie is SameSite=Lax, which already stops
		// the browser attaching it to cross-site state-changing requests. If the API
		// and frontend ever live on different *sites*, a real CSRF token scheme is
		// required instead - revisit this before deploying that way.
		http.csrf(csrf -> csrf.disable())
				.cors(cors -> cors.configurationSource(corsConfigurationSource()))
				// No server-side session: the JWT cookie is the entire session.
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						// Only the public entry points. /api/auth/me is deliberately
						// NOT here - it needs a session, so it falls through to
						// authenticated() below and returns 401 when signed out.
						.requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login", "/api/auth/logout")
						.permitAll()
						// Public so the sign-in screen can hide the Google button when
						// no credentials are configured.
						.requestMatchers(HttpMethod.GET, "/api/auth/providers").permitAll()
						.requestMatchers("/oauth2/**", "/login/**", "/.well-known/**").permitAll()
						.requestMatchers("/api/**").authenticated()
						.anyRequest().permitAll())
				.exceptionHandling(ex -> ex
						.authenticationEntryPoint((request, response, authException) -> writeError(
								objectMapper, response, HttpStatus.UNAUTHORIZED,
								"You must be signed in", request.getRequestURI()))
						.accessDeniedHandler((request, response, deniedException) -> writeError(
								objectMapper, response, HttpStatus.FORBIDDEN,
								"You do not have access to this resource", request.getRequestURI())))
				.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
				.httpBasic(basic -> basic.disable())
				.formLogin(form -> form.disable())
				.logout(logout -> logout.disable());

		// Applied only once real Google credentials exist. Otherwise Spring fails at
		// startup over a missing client registration, which would stop password
		// login from working at all.
		if (this.properties.google().isConfigured()) {
			log.info("Google sign-in is enabled");
			http.oauth2Login(oauth2 -> oauth2
					// Both services are registered because Spring picks one based on the
					// requested scopes: openid routes through OIDC, its absence through
					// plain OAuth2. Registering only the OAuth2 one left the OIDC path
					// building a default user that our success handler could not read.
					.userInfoEndpoint(userInfo -> userInfo
							.userService(googleUserServices.oauth2UserService())
							.oidcUserService(googleUserServices.oidcUserService()))
					// Google returns to a backend URL, but the session is our JWT cookie.
					// The success handler mints it and bounces back to the SPA.
					.successHandler(oAuth2SuccessHandler)
					.failureHandler(oAuth2FailureHandler));
		}
		else {
			log.warn("""
					Google sign-in is DISABLED - app.google.client-id / client-secret are still placeholders.
					Set GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET (see .env.example) to enable it.
					Email + password login works regardless.""");
		}

		return http.build();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		// BCrypt salts each hash internally, so two users with the same password
		// still get different stored values.
		return new BCryptPasswordEncoder();
	}

	@Bean
	AuthenticationManager authenticationManager(AppUserDetailsService userDetailsService,
			PasswordEncoder passwordEncoder) {
		DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
		provider.setPasswordEncoder(passwordEncoder);
		return new ProviderManager(provider);
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOrigins(ALLOWED_ORIGINS);
		// Without this the browser drops our Set-Cookie and the session never sticks.
		config.setAllowCredentials(true);
		config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		config.setAllowedHeaders(List.of("*"));
		config.setMaxAge(3600L);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", config);
		return source;
	}

	/**
	 * Answers 401/403 with the same JSON shape as the rest of the API. Without
	 * these handlers Spring returns HTML, and the frontend has to guess.
	 */
	private void writeError(ObjectMapper objectMapper, HttpServletResponse response, HttpStatus status,
			String message, String path) throws IOException {
		response.setStatus(status.value());
		response.setContentType("application/json");
		objectMapper.writeValue(response.getOutputStream(),
				ApiError.of(status.value(), status.getReasonPhrase(), message, path));
	}

}
