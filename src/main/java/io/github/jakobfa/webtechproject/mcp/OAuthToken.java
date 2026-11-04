package io.github.jakobfa.webtechproject.mcp;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Der Zugang einer KI-App zum Konto eines Nutzers (nur Hashes der Token werden gespeichert) */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class OAuthToken {

	@Id
	@GeneratedValue
	private Long id;

	@Column(unique = true)
	private String accessHash;

	@Column(unique = true)
	private String refreshHash;

	private Long userId;
	private String clientId;
	private String scope;
	private Instant createdAt;
	private Instant accessExpiresAt;
	private Instant refreshExpiresAt;
	private Instant lastUsedAt;
	private boolean revoked;

	public OAuthToken(String accessHash, String refreshHash, Long userId, String clientId, String scope,
			Instant createdAt, Instant accessExpiresAt, Instant refreshExpiresAt) {
		this.accessHash = accessHash;
		this.refreshHash = refreshHash;
		this.userId = userId;
		this.clientId = clientId;
		this.scope = scope;
		this.createdAt = createdAt;
		this.accessExpiresAt = accessExpiresAt;
		this.refreshExpiresAt = refreshExpiresAt;
	}
}
