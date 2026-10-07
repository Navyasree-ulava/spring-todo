package com.todoapp.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

import com.todoapp.config.AppProperties;
import com.todoapp.domain.User;

/**
 * Issues and verifies the session JWTs we hand out at login.
 * <p>
 * We use Spring Security's Nimbus wrapper rather than pulling in a separate JWT
 * library, so there is one crypto implementation on the classpath.
 */
@Service
public class JwtService {

	private static final Logger log = LoggerFactory.getLogger(JwtService.class);

	private static final String ISSUER = "todo-app";

	private final NimbusJwtEncoder encoder;
	private final NimbusJwtDecoder decoder;
	private final AppProperties.Jwt config;

	public JwtService(AppProperties properties) {
		this.config = properties.jwt();
		SecretKey key = new SecretKeySpec(
				this.config.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");

		// Nimbus requires 256-bit keys for HS256; anything shorter is rejected at runtime.
		if (key.getEncoded().length < 32) {
			throw new IllegalStateException(
					"app.jwt.secret must be at least 32 bytes for HS256 (got " + key.getEncoded().length + ")");
		}

		this.encoder = NimbusJwtEncoder.withSecretKey(key)
				.algorithm(MacAlgorithm.HS256)
				.build();
		this.decoder = NimbusJwtDecoder.withSecretKey(key)
				.macAlgorithm(MacAlgorithm.HS256)
				.build();
	}

	public String generate(User user) {
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(ISSUER)
				.subject(user.getId().toString())
				.issuedAt(now)
				.expiresAt(now.plus(this.config.expiration()))
				.claim("email", user.getEmail())
				.claim("name", user.getDisplayName())
				.claim("provider", user.getAuthProvider().name())
				.build();

		// Must match the algorithm the encoder was built with, or Nimbus rejects it.
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		return this.encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}

	/**
	 * @return the verified token, or null when it is missing, malformed, expired
	 *         or signed with the wrong key. Returning null instead of throwing
	 *         keeps a bad cookie from turning every request into a 500.
	 */
	public Jwt verify(String token) {
		try {
			return this.decoder.decode(token);
		}
		catch (Exception ex) {
			log.debug("Rejected JWT: {}", ex.getMessage());
			return null;
		}
	}

}
