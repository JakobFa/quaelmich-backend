package io.github.jakobfa.webtechproject.mcp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Eine KI-App, die sich selbst registriert hat (Dynamic Client Registration, RFC 7591) */
@Entity
@Getter
@NoArgsConstructor
public class OAuthClient {

	@Id
	private String clientId;

	@Column(length = 200)
	private String name;

	@ElementCollection(fetch = FetchType.EAGER)
	private List<String> redirectUris = new ArrayList<>();

	private Instant createdAt;

	public OAuthClient(String clientId, String name, List<String> redirectUris, Instant createdAt) {
		this.clientId = clientId;
		this.name = name;
		this.redirectUris = new ArrayList<>(redirectUris);
		this.createdAt = createdAt;
	}
}
