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
	public StravaConnection connect(Long userId, String code) {
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
