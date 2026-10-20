package io.github.jakobfa.webtechproject.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import io.github.jakobfa.webtechproject.model.AppUser;

/**
 * Stellt nach erfolgreichem Login ein signiertes JWT aus. Der "subject" ist die Nutzer-ID.
 */
@Service
public class TokenService {

	private final JwtEncoder encoder;
	private final Clock clock;
	private final Duration validity;

	public TokenService(JwtEncoder encoder, Clock clock, @Value("${app.jwt.validity-days:7}") long validityDays) {
		this.encoder = encoder;
		this.clock = clock;
		this.validity = Duration.ofDays(validityDays);
	}

	public String issue(AppUser user) {
		Instant now = clock.instant();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer("quaelmich")
				.subject(String.valueOf(user.getId()))
				.claim("email", user.getEmail())
				.issuedAt(now)
				.expiresAt(now.plus(validity))
				.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}

}
