package io.github.jakobfa.webtechproject.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Prüft das Geheimnis ADMIN_TOKEN im Header "X-Admin-Token" für Betreiber-Routen (Einladungscodes).
 * Bewusst getrennt vom Sync-Token: Das liegt auch bei GitHub, das Admin-Token nur bei Render und beim Betreiber.
 */
@Component
public class AdminAccess {

	private final String token;

	public AdminAccess(@Value("${app.admin.token:}") String token) {
		this.token = token;
	}

	public boolean accepts(String candidate) {
		return token != null && !token.isBlank() && candidate != null
				&& InternalAccess.constantTimeEquals(candidate, token);
	}

}
