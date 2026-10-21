package io.github.jakobfa.webtechproject.integration.intervals;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import io.github.jakobfa.webtechproject.integration.intervals.IntervalsSyncService.SyncResult;
import io.github.jakobfa.webtechproject.integration.strava.StravaService;
import io.github.jakobfa.webtechproject.model.Athlete;
import io.github.jakobfa.webtechproject.service.AthleteService;

/**
 * Sendet Workouts automatisch an intervals.icu – für einen Nutzer (z. B. nach "Plan erstellen")
 * oder für alle Nutzer mit eingeschaltetem Auto-Sync (nächtlicher Lauf).
 * Fehler bei einem Nutzer brechen den Lauf für die anderen nicht ab.
 */
@Service
public class IntervalsAutoSyncService {

	public static final int DAYS = 14;

	public record SyncAllResult(int users, int uploaded, int imported, int failed) {
	}

	private static final Logger log = LoggerFactory.getLogger(IntervalsAutoSyncService.class);

	private final IntervalsConnectionService connectionService;
	private final IntervalsSyncService syncService;
	private final AthleteService athleteService;
	private final ActivityImportService activityImport;
	private final StravaService stravaService;

	public IntervalsAutoSyncService(IntervalsConnectionService connectionService, IntervalsSyncService syncService,
			AthleteService athleteService, ActivityImportService activityImport, StravaService stravaService) {
		this.connectionService = connectionService;
		this.syncService = syncService;
		this.athleteService = athleteService;
		this.activityImport = activityImport;
		this.stravaService = stravaService;
	}

	/** Sendet die nächsten Tage eines Nutzers und merkt sich den Zeitpunkt. Leer, wenn nichts zu senden war. */
	public Optional<SyncResult> syncUser(Long userId) {
		var credentials = connectionService.credentialsOf(userId);
		var athleteId = athleteService.findByOwner(userId).map(Athlete::getId);
		if (credentials.isEmpty() || athleteId.isEmpty()) {
			return Optional.empty();
		}
		Optional<SyncResult> result = syncService.syncUpcoming(credentials.get(), athleteId.get(), DAYS);
		result.ifPresent(r -> connectionService.markSynced(userId));
		return result;
	}

	/** Für den automatischen Sync nach "Plan erstellen": nur wenn eingeschaltet, Fehler werden nur protokolliert. */
	public void syncUserIfEnabled(Long userId) {
		boolean enabled = connectionService.findByOwner(userId).map(IntervalsConnection::isAutoSync).orElse(false);
		if (!enabled) {
			return;
		}
		try {
			syncUser(userId);
		} catch (RuntimeException e) {
			log.warn("Automatischer intervals.icu-Sync für Nutzer {} fehlgeschlagen: {}", userId, e.getMessage());
		}
	}

	/**
	 * Nächtlicher Lauf: erst gefahrene Einheiten abholen (intervals.icu mit Auto-Sync und alle Strava-Nutzer),
	 * dann die nächsten Tage an intervals.icu senden.
	 */
	public SyncAllResult syncAll() {
		int users = 0;
		int uploaded = 0;
		int imported = 0;
		int failed = 0;
		Set<Long> autoSyncUsers = new LinkedHashSet<>(connectionService.usersWithAutoSync());
		Set<Long> allUsers = new LinkedHashSet<>(autoSyncUsers);
		allUsers.addAll(stravaService.connectedUsers());
		for (Long userId : allUsers) {
			users++;
			try {
				imported += activityImport.importRecent(userId)
						.map(result -> result.matched() + result.extra()).orElse(0);
				if (autoSyncUsers.contains(userId)) {
					uploaded += syncUser(userId).map(SyncResult::uploaded).orElse(0);
				}
			} catch (RuntimeException e) {
				failed++;
				log.warn("Nächtlicher intervals.icu-Sync für Nutzer {} fehlgeschlagen: {}", userId, e.getMessage());
			}
		}
		log.info("intervals.icu-Sync: {} Nutzer, {} Workouts gesendet, {} Fahrten zugeordnet, {} fehlgeschlagen",
				users, uploaded, imported, failed);
		return new SyncAllResult(users, uploaded, imported, failed);
	}

}
