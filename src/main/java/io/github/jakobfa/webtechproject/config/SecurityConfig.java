package io.github.jakobfa.webtechproject.config;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import io.github.jakobfa.webtechproject.repository.AppUserRepository;

/**
 * Zugriffsschutz: Ohne Login sind nur Registrierung, Anmeldung und die Workout-Bibliothek erreichbar.
 * Alles andere braucht ein gültiges JWT im Header "Authorization: Bearer …".
 *
 * Die Tokens signiert das Backend selbst mit einem geheimen Schlüssel (HMAC-SHA256) aus der
 * Umgebungsvariable JWT_SECRET. Fehlt sie (lokale Entwicklung), wird beim Start ein zufälliger
 * Schlüssel erzeugt – dann sind Tokens nach einem Neustart ungültig, aber nie fälschbar.
 */
@Configuration
public class SecurityConfig {

	private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		return http
				.cors(Customizer.withDefaults()) // nutzt die CORS-Regeln aus WebConfig
				.csrf(csrf -> csrf.disable()) // kein Cookie-Login → kein CSRF-Risiko
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
						.requestMatchers(HttpMethod.POST, "/auth/register", "/auth/login", "/auth/password-reset",
								"/auth/password-reset/confirm").permitAll()
						// prüft selbst ein eigenes Geheimnis (X-Sync-Token), siehe InternalSyncController
						.requestMatchers(HttpMethod.POST, "/internal/intervals/sync-all").permitAll()
						// prüfen selbst das Geheimnis, siehe InternalInvitationController
						.requestMatchers("/internal/invitations", "/internal/invitations/**").permitAll()
						.requestMatchers(HttpMethod.GET, "/workouts", "/workouts/**", "/auth/registration").permitAll()
						.requestMatchers("/error").permitAll()
						.anyRequest().authenticated())
				.oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()))
				.build();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	public SecretKey jwtSigningKey(@Value("${app.jwt.secret:}") String secret) {
		byte[] keyBytes;
		if (secret == null || secret.isBlank()) {
			log.warn("JWT_SECRET ist nicht gesetzt – es wird ein zufälliger Schlüssel verwendet. "
					+ "Logins gelten dann nur bis zum nächsten Neustart.");
			keyBytes = new byte[32];
			new SecureRandom().nextBytes(keyBytes);
		} else {
			keyBytes = secret.getBytes(StandardCharsets.UTF_8);
			if (keyBytes.length < 32) {
				throw new IllegalStateException("JWT_SECRET muss mindestens 32 Zeichen lang sein");
			}
		}
		return new SecretKeySpec(keyBytes, "HmacSHA256");
	}

	@Bean
	public JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
		return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey));
	}

	/**
	 * Prüft Signatur und Ablauf – und zusätzlich, dass das Token nicht vor der letzten Passwort-Änderung
	 * ausgestellt wurde. So sind nach "Passwort vergessen" alle alten Anmeldungen ungültig.
	 */
	@Bean
	public JwtDecoder jwtDecoder(SecretKey jwtSigningKey, AppUserRepository userRepository) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey).macAlgorithm(MacAlgorithm.HS256)
				.build();
		OAuth2TokenValidator<Jwt> notBeforePasswordChange = jwt -> {
			var user = userRepository.findById(Long.valueOf(jwt.getSubject()));
			boolean valid = user.isPresent() && (user.get().getPasswordChangedAt() == null
					|| (jwt.getIssuedAt() != null && !jwt.getIssuedAt().isBefore(user.get().getPasswordChangedAt())));
			return valid ? OAuth2TokenValidatorResult.success()
					: OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token",
							"Anmeldung ist nach einer Passwort-Änderung abgelaufen", null));
		};
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefault(),
				notBeforePasswordChange));
		return decoder;
	}

}
