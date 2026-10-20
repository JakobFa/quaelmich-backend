package io.github.jakobfa.webtechproject.auth;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Liest die Nutzer-ID aus dem geprüften Token der aktuellen Anfrage.
 */
public final class CurrentUser {

	private CurrentUser() {
	}

	public static Long id(Jwt jwt) {
		return Long.valueOf(jwt.getSubject());
	}

}
