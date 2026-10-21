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

import io.github.jakobfa.webtechproject.integration.RecordedActivity;
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
	 * @param profileAccess      false = ohne Erlaubnis profile:read_all verbunden → neu verbinden
	 * @param maxHeartRate       Maximalpuls
	 * @param maxHeartRateSource ZONES (aus den Strava-Pulszonen zurückgerechnet) oder ACTIVITIES (höchster
	 *                           gemessener Puls der letzten 90 Tage)
	 * @param heartRateZoneStarts Beginn von Zone 2–5 laut Strava, null ohne Zonen
	 * @param customZones        true, wenn bei Strava eigene Zonen eingetragen sind
	 */
	public record ProfileSuggestion(boolean profileAccess, Integer ftp, Double weightKg, Integer maxHeartRate,
			MaxHeartRateSource maxHeartRateSource, List<Integer> heartRateZoneStarts, boolean customZones) {

		static ProfileSuggestion noAccess() {
			return new ProfileSuggestion(false, null, null, null, null, null, false);
		}
	}

	public enum MaxHeartRateSource {
		ZONES, ACTIVITIES
	}

	/** Strava legt seine Standard-Pulszonen bei diesen Anteilen des Maximalpulses fest (Beginn Zone 2 bis 5) */
	private static final double[] STRAVA_ZONE_SHARES = { 0.65, 0.81, 0.89, 0.97 };

	/** So weit zurück wird nach dem höchsten Puls gesucht */
	static final int MAX_HEART_RATE_DAYS = 90;

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
	public Optional<List<RecordedActivity>> recentRides(Long userId, LocalDate from, LocalDate to, Integer ftp,
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
					.filter(StravaActivity::isUsable)
					.map(activity -> activity.toRecordedActivity(ftp, thresholdHeartRate))
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

		StravaClient.Zones zones = null;
		try {
			zones = client.zones(accessToken);
		} catch (RestClientException e) {
			log.warn("Strava-Pulszonen für Nutzer {} nicht lesbar: {}", userId, e.getMessage());
		}
		List<Integer> zoneStarts = zoneStarts(zones);
		boolean custom = zones != null && zones.heartRate() != null
				&& Boolean.TRUE.equals(zones.heartRate().customZones());
		Integer fromZones = maxHeartRateFromZones(zones);
		if (fromZones != null) {
			return Optional.of(new ProfileSuggestion(true, ftp, weight, fromZones, MaxHeartRateSource.ZONES,
					zoneStarts, custom));
		}
		// eigene Zonen oder keine: höchster gemessener Puls der letzten Wochen
		Integer maxSeen = maxHeartRateSeen(accessToken);
		return Optional.of(new ProfileSuggestion(true, ftp, weight, maxSeen,
				maxSeen == null ? null : MaxHeartRateSource.ACTIVITIES, zoneStarts, custom));
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

	/** Beginn von Zone 2–5 (Strava liefert 5 Zonen, Zone 1 beginnt bei 0); null, wenn unbrauchbar */
	static List<Integer> zoneStarts(StravaClient.Zones zones) {
		if (zones == null || zones.heartRate() == null || zones.heartRate().zones() == null
				|| zones.heartRate().zones().size() != 5) {
			return null;
		}
		List<Integer> starts = zones.heartRate().zones().subList(1, 5).stream().map(StravaClient.ZoneRange::min)
				.toList();
		for (int i = 0; i < starts.size(); i++) {
			Integer value = starts.get(i);
			if (value == null || value < 60 || value > 230 || (i > 0 && value <= starts.get(i - 1))) {
				return null;
			}
		}
		return starts;
	}

	/**
	 * Rechnet den Maximalpuls aus Stravas Standardzonen zurück: Die Zonen 2–5 beginnen dort bei 65, 81, 89 und
	 * 97 % des Maximalpulses (bei 189 also 123/153/168/183). Bei eigenen Zonen geht das nicht → null.
	 */
	static Integer maxHeartRateFromZones(StravaClient.Zones zones) {
		if (zones == null || zones.heartRate() == null || zones.heartRate().zones() == null
				|| Boolean.TRUE.equals(zones.heartRate().customZones())
				|| zones.heartRate().zones().size() != STRAVA_ZONE_SHARES.length + 1) {
			return null;
		}
		double sum = 0;
		for (int i = 0; i < STRAVA_ZONE_SHARES.length; i++) {
			Integer start = zones.heartRate().zones().get(i + 1).min();
			if (start == null || start <= 0) {
				return null;
			}
			sum += start / STRAVA_ZONE_SHARES[i];
		}
		int max = (int) Math.round(sum / STRAVA_ZONE_SHARES.length);
		return max >= 120 && max <= 230 ? max : null;
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
					.findByPlanAthleteIdAndActivityIdStartingWith(athlete.getId(), RecordedActivity.STRAVA_PREFIX)
					.forEach(planned -> planned.clearActivity());
			extraRideRepository.deleteByAthleteIdAndActivityIdStartingWith(athlete.getId(),
					RecordedActivity.STRAVA_PREFIX);
		});
	}

}
