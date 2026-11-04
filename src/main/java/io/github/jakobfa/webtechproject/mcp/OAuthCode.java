package io.github.jakobfa.webtechproject.mcp;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Ein einmal nutzbarer Autorisierungscode (nur der Hash wird gespeichert), gültig wenige Minuten */
@Entity
@Getter
@NoArgsConstructor
public class OAuthCode {

	@Id
	private String codeHash;

	private Long userId;
	private String clientId;
	private String redirectUri;
	private String codeChallenge;
	private String scope;
	private Instant expiresAt;

	public OAuthCode(String codeHash, Long userId, String clientId, String redirectUri, String codeChallenge,
			String scope, Instant expiresAt) {
		this.codeHash = codeHash;
		this.userId = userId;
		this.clientId = clientId;
		this.redirectUri = redirectUri;
		this.codeChallenge = codeChallenge;
		this.scope = scope;
		this.expiresAt = expiresAt;
	}
}
