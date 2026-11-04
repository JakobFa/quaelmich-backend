package io.github.jakobfa.webtechproject.integration.strava;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.jakobfa.webtechproject.integration.RecordedActivity;
import io.github.jakobfa.webtechproject.integration.intervals.ActivityImportService;
import io.github.jakobfa.webtechproject.repository.ExtraRideRepository;
import io.github.jakobfa.webtechproject.repository.PlannedWorkoutRepository;
import io.github.jakobfa.webtechproject.service.AthleteService;

/**
 * Hält uns an die API Policy von Strava (§6.2): Von Strava übernommene Daten dürfen höchstens 7 Tage
 * zwischengespeichert werden. Jeder Import holt die letzten {@link ActivityImportService#DAYS_BACK} Tage neu und
 * erneuert damit alle Werte. Hier wird gelöscht, was nicht erneuert wurde:
 * - Fahrten, die älter sind als das Importfenster, und
 * - alle Strava-Werte eines Nutzers, dessen letzter erfolgreicher Abruf länger als 6 Tage her ist
 *   (der Lauf ist täglich, so bleibt auch der älteste Wert unter 7 Tagen).
 * Dauerhaft bleiben nur Eingaben des Nutzers (Bewertung, Ausfallgrund, Bestätigung) und die Zuordnung zur Aktivität.
 */
@Service
public class StravaRetentionService {

	private static final Logger log = LoggerFactory.getLogger(StravaRetentionService.class);

	/** So alt dürfen Strava-Werte beim täglichen Lauf höchstens sein (Grenze von Strava: 7 Tage) */
	static final Duration MAX_AGE = Duration.ofDays(6);

	private final StravaConnectionRepository connections;
	private final AthleteService athleteService;
	private final PlannedWorkoutRepository plannedWorkouts;
	private final ExtraRideRepository extraRides;
	private final Clock clock;

	public StravaRetentionService(StravaConnectionRepository connections, AthleteService athleteService,
			PlannedWorkoutRepository plannedWorkouts, ExtraRideRepository extraRides, Clock clock) {
		this.connections = connections;
		this.athleteService = athleteService;
		this.plannedWorkouts = plannedWorkouts;
		this.extraRides = extraRides;
		this.clock = clock;
	}

	/** Zu alt, wenn noch nie oder zu lange nicht mehr erfolgreich bei Strava abgefragt */
	public static boolean isOutdated(StravaConnection connection, Instant now) {
		return connection.getLastSyncAt() == null || connection.getLastSyncAt().isBefore(now.minus(MAX_AGE));
	}

	@Scheduled(cron = "0 20 4 * * *", zone = "Europe/Berlin")
	public void purgeDaily() {
		int purged = purgeExpired();
		log.info("Strava-Werte: {} Einträge gelöscht (maximal 7 Tage alt erlaubt)", purged);
	}

	/** Löscht abgelaufene Strava-Werte; gibt die Zahl der gelöschten oder geleerten Einträge zurück */
	@Transactional
	public int purgeExpired() {
		Instant now = Instant.now(clock);
		LocalDate today = LocalDate.now(clock);
		int purged = 0;
		for (StravaConnection connection : connections.findAll()) {
			var athlete = athleteService.findByOwner(connection.getOwner().getId());
			if (athlete.isEmpty()) {
				continue;
			}
			// veraltet: alles bis heute; sonst nur, was älter ist als das Importfenster (wurde nicht erneuert)
			LocalDate before = isOutdated(connection, now) ? today.plusDays(1)
					: today.minusDays(ActivityImportService.DAYS_BACK);
			var oldRides = extraRides.findByAthleteId(athlete.get().getId()).stream()
					.filter(ride -> ride.getActivityId() != null
							&& ride.getActivityId().startsWith(RecordedActivity.STRAVA_PREFIX))
					.filter(ride -> ride.getDate().isBefore(before)).toList();
			extraRides.deleteAll(oldRides);
			purged += oldRides.size();
			var oldPlanned = plannedWorkouts
					.findByPlanAthleteIdAndActivityIdStartingWith(athlete.get().getId(), RecordedActivity.STRAVA_PREFIX)
					.stream().filter(planned -> planned.getDate().isBefore(before))
					.filter(planned -> planned.getActualDurationMinutes() != null || planned.getActualTss() != null
							|| planned.getActualDistanceKm() != null)
					.toList();
			oldPlanned.forEach(planned -> planned.clearStravaValues());
			purged += oldPlanned.size();
		}
		return purged;
	}

}
