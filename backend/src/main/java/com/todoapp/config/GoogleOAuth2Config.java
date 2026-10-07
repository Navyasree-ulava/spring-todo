package com.todoapp.config;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

/**
 * Registers Google as an OAuth2 client, but only when real credentials have been
 * supplied.
 * <p>
 * This lives apart from {@link SecurityConfig} because {@code @Conditional}
 * works on configuration classes, not on individual bean methods.
 */
@Configuration
@Conditional(GoogleOAuth2Config.GoogleCredentialsPresent.class)
public class GoogleOAuth2Config {

	@Bean
	ClientRegistrationRepository clientRegistrationRepository(AppProperties properties) {
		ClientRegistration google = ClientRegistration.withRegistrationId("google")
				.clientId(properties.google().clientId())
				.clientSecret(properties.google().clientSecret())
				.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
				.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
				.redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
				.scope("openid", "email", "profile")
				// Key the lookup on email, so the user service receives the address
				// directly as the principal name.
				.userNameAttributeName("email")
				.clientName("Google")
				.build();

		return new InMemoryClientRegistrationRepository(List.of(google));
	}

	/**
	 * Checks the raw property values for our placeholder markers. Reading the
	 * Environment directly is required here - conditions run before beans such as
	 * AppProperties exist.
	 */
	static class GoogleCredentialsPresent extends SpringBootCondition {

		@Override
		public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
			String id = context.getEnvironment().getProperty("app.google.client-id");
			String secret = context.getEnvironment().getProperty("app.google.client-secret");

			boolean missing = isPlaceholder(id) || isPlaceholder(secret);
			return missing
					? ConditionOutcome.noMatch("app.google.* still contains placeholder values")
					: ConditionOutcome.match("app.google.* credentials found");
		}

		private boolean isPlaceholder(String value) {
			return value == null || value.isBlank() || value.startsWith("PASTE_");
		}

	}

}
