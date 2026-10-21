package io.github.jakobfa.webtechproject.integration.strava;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

import io.github.jakobfa.webtechproject.integration.RecordedRide;
import io.github.jakobfa.webtechproject.integration.intervals.SecretCipher;
import io.github.jakobfa.webtechproject.repository.AppUserRepository;
import io.github.jakobfa.webtechproject.repository.ExtraRideRepository;
import io.github.jakobfa.webtechproject.repository.PlannedWorkoutRepository;
import io.github.jakobfa.webtechproject.service.AthleteService;

/**
 * Verwaltet die Strava-Verbindungen der Nutzer und holt ihre Radfahrten ab.
 *
 * Laut Strava-Bedingungen werden beim Trennen alle von Strava stammenden Daten gelöscht.
 */
@Service
public class StravaService {

	/**
	 * Vorschlag fürs eigene Profil aus Strava. Werte sind null, wenn sie bei Strava nicht eingetragen sind.
	 *
	 * @param profileAccess        false = ohne Erlaubnis profile:read_all verbunden → neu verbinden
	 * @param thresholdHeartRate   geschätzter Schwellenpuls
	 * @param thresholdSource      ZONES (Strava-Pulszonen) oder ACTIVITIES (höchster Puls der letzten 90 Tage)
	 * @param maxHeartRateObserved höchster gemessener Puls, wenn aus den Aktivitäten geschätzt wurde
	 */
	public record ProfileSuggestion(boolean profileAccess, Integer ftp, Double weightKg, Integer thresholdHeartRate,
			ThresholdSource thresholdSource, Integer maxHeartRateObserved) {

		static ProfileSuggestion noAccess() {
			return new ProfileSuggestion(false, null, null, null, null, null);
		}
	}

	public enum ThresholdSource {
		ZONES, ACTIVITIES
	}

	/** So weit zurück wird nach dem höchsten Puls gesucht */
	static final int MAX_HEART_RATE_DAYS = 90;
	/** Schwellenpuls ≈ 90 % des Maximalpulses */
	static final double THRESHOLD_SHARE_OF_MAX = 0.9;

	/** Wird geworfen, wenn die Strava-Anbindung auf dem Server nicht eingerichtet ist. */
	public static class NotConfiguredException extends RuntimeException {
	}

	private static final Logger log = LoggerFactory.getLogger(StravaService.class);
	/** Token schon kurz vor Ablauf erneuern */
	private static final long REFRESH_MARGIN_SECONDS = 300;

	private final StravaConnectionRepository repository;
	private final AppUserRepository userRepository;
	private final StravaClient client;
	private final StravaProperties properties;
	private final SecretCipher cipher;
	private final AthleteService athleteService;
	private final PlannedWorkoutRepository plannedWorkoutRepository;
	private final ExtraRideRepository extraRideRepository;
	private final Clock clock;

	public StravaService(StravaConnectionRepository repository, AppUserRepository userRepository,
			StravaClient client, StravaProperties properties, SecretCipher cipher, AthleteService athleteService,
			PlannedWorkoutRepository plannedWorkoutRepository, ExtraRideRepository extraRideRepository, Clock clock) {
		this.repository = repository;
		this.userRepository = userRepository;
		this.client = client;
		this.properties = properties;
		this.cipher = cipher;
		this.athleteService = athleteService;
		this.plannedWorkoutRepository = plannedWorkoutRepository;
		this.extraRideRepository = extraRideRepository;
		this.clock = clock;
	}

	public boolean isConfigured() {
		return properties.isConfigured();
	}

	public String clientId() {
		return properties.clientId();
	}

	public Optional<StravaConnection> findByOwner(Long userId) {
		return repository.findByOwnerId(userId);
	}

	public boolean isConnected(Long userId) {
		return isConfigured() && repository.findByOwnerId(userId).isPresent();
	}

	/** IDs aller Nutzer mit Strava-Verbindung (für den nächtlichen Lauf) */
	public List<Long> connectedUsers() {
		if (!isConfigured()) {
			return List.of();
		}
		return repository.findAll().stream().map(connection -> connection.getOwner().getId()).toList();
	}

	/**
	 * Schließt die Anmeldung ab: tauscht den Code aus dem Rücksprung gegen Tokens und speichert sie.
	 * Lehnt Strava den Code ab, wirft der Client eine {@link HttpClientErrorException}.
	 */
	@Transactional
	public StravaConnection connect(Long userId, String code, String scope) {
		if (!isConfigured()) {
			throw new NotConfiguredException();
		}
		StravaClient.TokenResponse tokens = client.exchangeCode(code);
		StravaConnection connection = repository.findByOwnerId(userId).orElseGet(() -> {
			StravaConnection created = new StravaConnection();
			created.setOwner(userRepository.getReferenceById(userId));
			return created;
		});
		store(connection, tokens);
		if (tokens.athlete() != null) {
			connection.setStravaAthleteId(tokens.athlete().id());
			connection.setAthleteName(tokens.athlete().firstname());
		}
		connection.setConnectedAt(clock.instant());
		connection.setScope(scope);
		return repository.save(connection);
	}

	/** Trennt die Verbindung (auch bei Strava) und löscht alle von Strava übernommenen Fahrten. */
	@Transactional
	public void disconnect(Long userId) {
		repository.findByOwnerId(userId).ifPresent(connection -> {
			try {
				client.deauthorize(cipher.decrypt(connection.getEncryptedAccessToken()));
			} catch (RestClientException e) {
				log.info("Strava-Zugriff für Nutzer {} konnte nicht entzogen werden: {}", userId, e.getMessage());
			}
			removeConnectionAndData(userId, connection);
		});
	}

	/**
	 * Radfahrten im Zeitraum (beide Tage eingeschlossen). Leer, wenn nicht verbunden oder Strava gerade
	 * nicht erreichbar ist – ein Strava-Problem soll den Import aus intervals.icu nicht verhindern.
	 */
	@Transactional
	public Optional<List<RecordedRide>> recentRides(Long userId, LocalDate from, LocalDate to, Integer ftp,
			Integer thresholdHeartRate) {
		if (!isConfigured()) {
			return Optional.empty();
		}
		var connection = repository.findByOwnerId(userId);
		if (connection.isEmpty()) {
			return Optional.empty();
		}
		try {
			String accessToken = validAccessToken(userId, connection.get());
			if (accessToken == null) {
				return Optional.empty();
			}
			Instant after = from.atStartOfDay(clock.getZone()).toInstant();
			Instant before = to.plusDays(1).atStartOfDay(clock.getZone()).toInstant();
			return Optional.of(client.listActivities(accessToken, after, before).stream()
					.filter(StravaActivity::isUsableRide)
					.map(activity -> activity.toRecordedRide(ftp, thresholdHeartRate))
					.toList());
		} catch (RestClientException e) {
			log.warn("Strava-Fahrten für Nutzer {} nicht abrufbar: {}", userId, e.getMessage());
			return Optional.empty();
		}
	}

	/**
	 * Liest FTP, Gewicht und Pulszonen aus Strava. Leer, wenn nicht verbunden.
	 * Fehler beim Abruf werden weitergereicht (RestClientException).
	 */
	@Transactional
	public Optional<ProfileSuggestion> profileSuggestion(Long userId) {
		if (!isConfigured()) {
			return Optional.empty();
		}
		var connection = repository.findByOwnerId(userId);
		if (connection.isEmpty()) {
			return Optional.empty();
		}
		if (!connection.get().canReadProfile()) {
			return Optional.of(ProfileSuggestion.noAccess());
		}
		String accessToken = validAccessToken(userId, connection.get());
		if (accessToken == null) {
			return Optional.empty();
		}
		StravaClient.StravaProfile profile = client.profile(accessToken);
		Integer ftp = profile != null && profile.ftp() != null && profile.ftp() > 0 ? profile.ftp() : null;
		Double weight = profile != null && profile.weight() != null && profile.weight() > 0
				? Math.round(profile.weight() * 10) / 10.0
				: null;

		Integer fromZones = null;
		try {
			fromZones = thresholdFromZones(client.zones(accessToken));
		} catch (RestClientException e) {
			log.warn("Strava-Pulszonen für Nutzer {} nicht lesbar: {}", userId, e.getMessage());
		}
		if (fromZones != null) {
			return Optional.of(new ProfileSuggestion(true, ftp, weight, fromZones, ThresholdSource.ZONES, null));
		}
		// ohne Pulszonen bei Strava: höchster gemessener Puls der letzten Wochen
		Integer maxSeen = maxHeartRateSeen(accessToken);
		Integer fromActivities = maxSeen == null ? null : (int) Math.round(maxSeen * THRESHOLD_SHARE_OF_MAX);
		return Optional.of(new ProfileSuggestion(true, ftp, weight, fromActivities,
				fromActivities == null ? null : ThresholdSource.ACTIVITIES, maxSeen));
	}

	/** Höchster Puls aller Aktivitäten (auch Läufe) der letzten 90 Tage; null ohne Pulsdaten */
	private Integer maxHeartRateSeen(String accessToken) {
		Instant now = clock.instant();
		return client.listActivities(accessToken, now.minus(java.time.Duration.ofDays(MAX_HEART_RATE_DAYS)), now, 200)
				.stream()
				.map(StravaActivity::maxHeartRate)
				.filter(hr -> hr != null && hr >= 120 && hr <= 230)
				.max(Double::compare)
				.map(hr -> (int) Math.round(hr))
				.orElse(null);
	}

	/**
	 * Strava teilt den Puls in 5 Zonen; Zone 4 heißt dort "Schwelle". Ihre Untergrenze liegt bei den
	 * Standardzonen bei ca. 89 % des Maximalpulses – nah am Schwellenpuls (ca. 90 %).
	 * Genauer ist ein eigener Test.
	 */
	static Integer thresholdFromZones(StravaClient.Zones zones) {
		if (zones == null || zones.heartRate() == null || zones.heartRate().zones() == null
				|| zones.heartRate().zones().size() < 4) {
			return null;
		}
		Integer lowerBound = zones.heartRate().zones().get(3).min();
		return lowerBound != null && lowerBound >= 100 && lowerBound <= 220 ? lowerBound : null;
	}

	/** Liefert einen gültigen Access-Token; null, wenn der Nutzer den Zugriff bei Strava entzogen hat. */
	private String validAccessToken(Long userId, StravaConnection connection) {
		if (connection.getExpiresAt() != null
				&& connection.getExpiresAt().isAfter(clock.instant().plusSeconds(REFRESH_MARGIN_SECONDS))) {
			return cipher.decrypt(connection.getEncryptedAccessToken());
		}
		try {
			StravaClient.TokenResponse tokens = client.refresh(cipher.decrypt(connection.getEncryptedRefreshToken()));
			store(connection, tokens);
			repository.save(connection);
			return tokens.accessToken();
		} catch (HttpClientErrorException.BadRequest | HttpClientErrorException.Unauthorized e) {
			// Zugriff wurde in den Strava-Einstellungen entzogen → Verbindung und Strava-Daten löschen
			log.info("Strava-Zugriff von Nutzer {} wurde entzogen – Verbindung wird entfernt", userId);
			removeConnectionAndData(userId, connection);
			return null;
		}
	}

	private void store(StravaConnection connection, StravaClient.TokenResponse tokens) {
		connection.setEncryptedAccessToken(cipher.encrypt(tokens.accessToken()));
		connection.setEncryptedRefreshToken(cipher.encrypt(tokens.refreshToken()));
		connection.setExpiresAt(Instant.ofEpochSecond(tokens.expiresAt()));
	}

	private void removeConnectionAndData(Long userId, StravaConnection connection) {
		repository.delete(connection);
		athleteService.findByOwner(userId).ifPresent(athlete -> {
			plannedWorkoutRepository
					.findByPlanAthleteIdAndActivityIdStartingWith(athlete.getId(), RecordedRide.STRAVA_PREFIX)
					.forEach(planned -> planned.clearActivity());
			extraRideRepository.deleteByAthleteIdAndActivityIdStartingWith(athlete.getId(),
					RecordedRide.STRAVA_PREFIX);
		});
	}

}
