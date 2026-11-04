package io.github.jakobfa.webtechproject.mcp;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Die Anmeldung für KI-Apps (OAuth 2.1 mit PKCE, Autorisierungscode-Ablauf): Eine KI-App registriert sich selbst, der
 * Nutzer erlaubt ihr den Zugriff auf sein Konto, die App tauscht den Code gegen ein Zugriffstoken. Alle Geheimnisse
 * (Code, Zugriffs- und Erneuerungstoken) werden nur als Hash gespeichert.
 */
@Service
public class OAuthService {

	/** Lesender Zugriff auf Plan, Fahrten und Diagnose */
	public static final String SCOPE_READ = "plan:read";
	/** Änderungen am Plan (immer mit Prüfung) */
	public static final String SCOPE_WRITE = "plan:write";
	static final List<String> SCOPES = List.of(SCOPE_READ, SCOPE_WRITE);

	static final Duration CODE_VALIDITY = Duration.ofMinutes(5);
	static final Duration ACCESS_VALIDITY = Duration.ofHours(1);
	static final Duration REFRESH_VALIDITY = Duration.ofDays(30);
	/** So viele KI-Zugänge darf ein Nutzer gleichzeitig haben */
	static final int MAX_CONNECTIONS_PER_USER = 10;

	private static final SecureRandom RANDOM = new SecureRandom();

	/** Wird geworfen, wenn eine Anfrage nach OAuth ungültig ist (error/error_description, RFC 6749) */
	public static class OAuthException extends RuntimeException {
		private final String error;

		public OAuthException(String error, String description) {
			super(description);
			this.error = error;
		}

		public String error() {
			return error;
		}
	}

	/** Wer sich mit einem Zugriffstoken anmeldet */
	public record Principal(Long tokenId, Long userId, String clientId, List<String> scopes) {
		public boolean may(String scope) {
			return scopes.contains(scope);
		}
	}

	/** Neue Token nach dem Tausch */
	public record Tokens(String accessToken, String refreshToken, long expiresInSeconds, String scope) {
	}

	private final OAuthClientRepository clients;
	private final OAuthCodeRepository codes;
	private final OAuthTokenRepository tokens;
	private final Clock clock;

	public OAuthService(OAuthClientRepository clients, OAuthCodeRepository codes, OAuthTokenRepository tokens,
			Clock clock) {
		this.clients = clients;
		this.codes = codes;
		this.tokens = tokens;
		this.clock = clock;
	}

	// ---------- Registrierung der KI-App ----------

	@Transactional
	public OAuthClient register(String name, List<String> redirectUris) {
		if (redirectUris == null || redirectUris.isEmpty() || redirectUris.size() > 5) {
			throw new OAuthException("invalid_redirect_uri", "Bitte 1 bis 5 redirect_uris angeben");
		}
		redirectUris.forEach(OAuthService::requireAcceptableRedirect);
		String clientName = name == null || name.isBlank() ? "KI-App" : name.strip();
		if (clientName.length() > 100) {
			clientName = clientName.substring(0, 100);
		}
		return clients.save(new OAuthClient("qm_" + UUID.randomUUID().toString().replace("-", ""), clientName,
				redirectUris, clock.instant()));
	}

	/** Nur https (oder http auf dem eigenen Rechner), ohne Anker und ohne eingebettete Zugangsdaten */
	static void requireAcceptableRedirect(String uri) {
		try {
			URI parsed = URI.create(uri);
			boolean loopback = "localhost".equals(parsed.getHost()) || "127.0.0.1".equals(parsed.getHost());
			boolean secure = "https".equalsIgnoreCase(parsed.getScheme())
					|| ("http".equalsIgnoreCase(parsed.getScheme()) && loopback);
			if (!secure || parsed.getHost() == null || parsed.getFragment() != null || parsed.getUserInfo() != null) {
				throw new OAuthException("invalid_redirect_uri", "Die redirect_uri muss https sein: " + uri);
			}
		} catch (IllegalArgumentException e) {
			throw new OAuthException("invalid_redirect_uri", "Ungültige redirect_uri");
		}
	}

	public Optional<OAuthClient> findClient(String clientId) {
		return clientId == null ? Optional.empty() : clients.findById(clientId);
	}

	// ---------- Erlaubnis durch den Nutzer ----------

	/**
	 * Der Nutzer hat der App den Zugriff erlaubt: Ein einmaliger Code wird erzeugt (der Aufrufer gibt ihn über die
	 * redirect_uri an die App). Gültig sind nur registrierte redirect_uris, S256-PKCE ist Pflicht.
	 */
	@Transactional
	public String authorize(Long userId, String clientId, String redirectUri, String codeChallenge,
			String codeChallengeMethod, String scope) {
		OAuthClient client = clients.findById(clientId == null ? "" : clientId)
				.orElseThrow(() -> new OAuthException("invalid_client", "Unbekannte KI-App"));
		if (redirectUri == null || !client.getRedirectUris().contains(redirectUri)) {
			throw new OAuthException("invalid_request", "redirect_uri ist für diese KI-App nicht registriert");
		}
		if (codeChallenge == null || codeChallenge.length() < 43 || codeChallenge.length() > 128
				|| !"S256".equals(codeChallengeMethod)) {
			throw new OAuthException("invalid_request", "PKCE mit S256 ist erforderlich");
		}
		String granted = normalizeScope(scope);
		codes.deleteByExpiresAtBefore(clock.instant());
		String code = randomToken();
		codes.save(new OAuthCode(hash(code), userId, clientId, redirectUri, codeChallenge, granted,
				clock.instant().plus(CODE_VALIDITY)));
		return code;
	}

	/** Nur bekannte Bereiche; ohne Angabe nur lesen */
	static String normalizeScope(String scope) {
		if (scope == null || scope.isBlank()) {
			return SCOPE_READ;
		}
		List<String> requested = List.of(scope.trim().split("\\s+"));
		if (!SCOPES.containsAll(requested)) {
			throw new OAuthException("invalid_scope", "Unbekannter Bereich: " + scope);
		}
		return String.join(" ", requested.stream().distinct().toList());
	}

	// ---------- Token ----------

	/** Auch bei einem Fehler bleibt der Code verbraucht (noRollbackFor): Ein Fehlversuch macht ihn unbrauchbar */
	@Transactional(noRollbackFor = OAuthException.class)
	public Tokens exchangeCode(String code, String clientId, String redirectUri, String codeVerifier) {
		OAuthCode stored = codes.findById(hash(code == null ? "" : code))
				.orElseThrow(() -> new OAuthException("invalid_grant", "Ungültiger oder schon benutzter Code"));
		codes.delete(stored); // einmalig
		if (stored.getExpiresAt().isBefore(clock.instant()) || !stored.getClientId().equals(clientId)
				|| !stored.getRedirectUri().equals(redirectUri)) {
			throw new OAuthException("invalid_grant", "Code abgelaufen oder passt nicht zur Anfrage");
		}
		if (codeVerifier == null || !pkceMatches(codeVerifier, stored.getCodeChallenge())) {
			throw new OAuthException("invalid_grant", "PKCE-Prüfung fehlgeschlagen");
		}
		// zu viele alte Zugänge: der älteste wird entfernt
		var existing = tokens.findByUserIdAndRevokedFalseOrderByCreatedAtDesc(stored.getUserId());
		for (int i = MAX_CONNECTIONS_PER_USER - 1; i < existing.size(); i++) {
			existing.get(i).setRevoked(true);
		}
		return issue(stored.getUserId(), clientId, stored.getScope());
	}

	@Transactional(noRollbackFor = OAuthException.class)
	public Tokens refresh(String refreshToken, String clientId) {
		OAuthToken old = tokens.findByRefreshHash(hash(refreshToken == null ? "" : refreshToken))
				.orElseThrow(() -> new OAuthException("invalid_grant", "Ungültiges Erneuerungstoken"));
		if (old.isRevoked() || old.getRefreshExpiresAt().isBefore(clock.instant())
				|| !old.getClientId().equals(clientId)) {
			throw new OAuthException("invalid_grant", "Erneuerungstoken abgelaufen oder widerrufen");
		}
		old.setRevoked(true); // das Erneuerungstoken gilt nur einmal
		tokens.save(old);
		return issue(old.getUserId(), old.getClientId(), old.getScope());
	}

	private Tokens issue(Long userId, String clientId, String scope) {
		String access = randomToken();
		String refresh = randomToken();
		Instant now = clock.instant();
		tokens.save(new OAuthToken(hash(access), hash(refresh), userId, clientId, scope, now,
				now.plus(ACCESS_VALIDITY), now.plus(REFRESH_VALIDITY)));
		return new Tokens(access, refresh, ACCESS_VALIDITY.toSeconds(), scope);
	}

	/** Wer steckt hinter diesem Zugriffstoken? Leer bei unbekannt, abgelaufen oder widerrufen. */
	@Transactional
	public Optional<Principal> authenticate(String accessToken) {
		if (accessToken == null || accessToken.isBlank()) {
			return Optional.empty();
		}
		return tokens.findByAccessHash(hash(accessToken))
				.filter(token -> !token.isRevoked() && token.getAccessExpiresAt().isAfter(clock.instant()))
				.map(token -> {
					Instant now = clock.instant();
					// nicht bei jedem Aufruf schreiben
					if (token.getLastUsedAt() == null
							|| Duration.between(token.getLastUsedAt(), now).compareTo(Duration.ofMinutes(5)) > 0) {
						token.setLastUsedAt(now);
						tokens.save(token);
					}
					return new Principal(token.getId(), token.getUserId(), token.getClientId(),
							List.of(token.getScope().split(" ")));
				});
	}

	// ---------- Verwaltung durch den Nutzer ----------

	/** Die KI-Zugänge eines Nutzers (ohne Token), neueste zuerst */
	@Transactional(readOnly = true)
	public List<Connection> connectionsOf(Long userId) {
		return tokens.findByUserIdAndRevokedFalseOrderByCreatedAtDesc(userId).stream()
				.filter(token -> token.getRefreshExpiresAt().isAfter(clock.instant()))
				.map(token -> new Connection(token.getId(),
						clients.findById(token.getClientId()).map(OAuthClient::getName).orElse("KI-App"),
						token.getScope(), token.getCreatedAt(), token.getLastUsedAt()))
				.toList();
	}

	public record Connection(Long id, String clientName, String scope, Instant createdAt, Instant lastUsedAt) {
	}

	/** Zugang beenden. true, wenn es ihn gab und er dem Nutzer gehörte. */
	@Transactional
	public boolean revoke(Long userId, Long tokenId) {
		return tokens.findById(tokenId).filter(token -> token.getUserId().equals(userId)).map(token -> {
			token.setRevoked(true);
			tokens.save(token);
			return true;
		}).orElse(false);
	}

	/** Beim Löschen des Kontos: alle Zugänge und offene Codes weg */
	@Transactional
	public void deleteAllOf(Long userId) {
		tokens.deleteByUserId(userId);
	}

	// ---------- Hilfen ----------

	static boolean pkceMatches(String verifier, String challenge) {
		return MessageDigest.isEqual(
				Base64.getUrlEncoder().withoutPadding().encodeToString(sha256(verifier)).getBytes(StandardCharsets.UTF_8),
				challenge.getBytes(StandardCharsets.UTF_8));
	}

	static String hash(String secret) {
		return HexFormat.of().formatHex(sha256(secret));
	}

	private static byte[] sha256(String value) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private static String randomToken() {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}
}
