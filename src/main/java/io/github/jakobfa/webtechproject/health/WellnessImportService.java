package io.github.jakobfa.webtechproject.health;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.jakobfa.webtechproject.integration.intervals.IntervalsConnectionService;
import io.github.jakobfa.webtechproject.integration.intervals.IntervalsIcuClient;
import io.github.jakobfa.webtechproject.integration.intervals.IntervalsWellness;
import io.github.jakobfa.webtechproject.repository.AppUserRepository;

/**
 * Übernimmt Schlaf und Erholung aus dem Wellness-Bereich von intervals.icu – egal, aus welcher Quelle sie dort
 * stammen. Nur mit Einwilligung; Widerruf löscht alle Tageswerte.
 */
@Service
public class WellnessImportService {

	/** Beim Einschalten so weit zurück, danach reichen die letzten Tage */
	public static final int FIRST_IMPORT_DAYS = 28;
	static final int RECENT_DAYS = 7;
	/** Nachts etwas weiter zurück, falls Werte nachträglich bei intervals.icu ankommen */
	public static final int NIGHTLY_DAYS = 7;
	/** Beim Öffnen des Profils neu holen, wenn der letzte Abruf älter ist */
	static final Duration STALE_AFTER = Duration.ofHours(1);

	private static final Logger log = LoggerFactory.getLogger(WellnessImportService.class);

	private final WellnessImportRepository imports;
	private final DailyHealthRepository days;
	private final AppUserRepository users;
	private final IntervalsConnectionService intervals;
	private final IntervalsIcuClient client;
	private final Clock clock;

	public WellnessImportService(WellnessImportRepository imports, DailyHealthRepository days,
			AppUserRepository users, IntervalsConnectionService intervals, IntervalsIcuClient client, Clock clock) {
		this.imports = imports;
		this.days = days;
		this.users = users;
		this.intervals = intervals;
		this.client = client;
		this.clock = clock;
	}

	public Optional<WellnessImport> findByOwner(Long userId) {
		return imports.findByOwnerId(userId);
	}

	/** Einschalten (mit Einwilligung) und gleich die letzten Wochen holen. Leer ohne intervals.icu-Verbindung. */
	@Transactional
	public Optional<Integer> enable(Long userId) {
		if (intervals.credentialsOf(userId).isEmpty()) {
			return Optional.empty();
		}
		WellnessImport settings = imports.findByOwnerId(userId).orElseGet(() -> {
			WellnessImport created = new WellnessImport();
			created.setOwner(users.getReferenceById(userId));
			return created;
		});
		settings.setConsentAt(clock.instant());
		imports.save(settings);
		return importDays(userId, FIRST_IMPORT_DAYS);
	}

	/** Ausschalten: Einwilligung widerrufen, alle Tageswerte löschen */
	@Transactional
	public void disable(Long userId) {
		imports.findByOwnerId(userId).ifPresent(imports::delete);
		days.deleteAll(days.findByOwnerIdOrderByDate(userId));
	}

	/** Holt die Tage neu, wenn eingeschaltet; liefert die Zahl der übernommenen Tage. Leer, wenn aus. */
	@Transactional
	public Optional<Integer> importDays(Long userId, int count) {
		var settings = imports.findByOwnerId(userId);
		var credentials = intervals.credentialsOf(userId);
		if (settings.isEmpty() || credentials.isEmpty()) {
			return Optional.empty();
		}
		LocalDate today = LocalDate.now(clock);
		List<IntervalsWellness> wellness = client.listWellness(credentials.get(), today.minusDays(count - 1), today);
		int imported = 0;
		Instant now = clock.instant();
		for (IntervalsWellness entry : wellness) {
			if (store(userId, entry, now)) {
				imported++;
			}
		}
		settings.get().setLastImportAt(now);
		imports.save(settings.get());
		return Optional.of(imported);
	}

	/** Beim Öffnen des Profils: neu holen, wenn der letzte Abruf älter als eine Stunde ist. Fehler nur protokollieren. */
	public void importIfStale(Long userId) {
		var settings = imports.findByOwnerId(userId);
		if (settings.isEmpty()) {
			return;
		}
		Instant last = settings.get().getLastImportAt();
		if (last != null && last.isAfter(clock.instant().minus(STALE_AFTER))) {
			return;
		}
		try {
			importDays(userId, RECENT_DAYS);
		} catch (RuntimeException e) {
			log.warn("Wellness von intervals.icu für Nutzer {} nicht abrufbar: {}", userId, e.getMessage());
		}
	}

	/** Alle Nutzer mit eingeschaltetem Import (nächtlicher Lauf) */
	public List<Long> enabledUsers() {
		return imports.findAll().stream().map(settings -> settings.getOwner().getId()).toList();
	}

	private boolean store(Long userId, IntervalsWellness entry, Instant now) {
		LocalDate date;
		try {
			date = LocalDate.parse(entry.id());
		} catch (RuntimeException e) {
			return false;
		}
		Integer sleep = entry.sleepSecs() == null || entry.sleepSecs() <= 0 ? null
				: (int) Math.round(entry.sleepSecs() / 60.0);
		Integer sleepScore = round(entry.sleepScore());
		Integer restingHr = entry.restingHR();
		Integer hrv = round(entry.hrv());
		Integer hrvSdnn = round(entry.hrvSDNN());
		Integer steps = entry.steps();
		if (sleep == null && sleepScore == null && restingHr == null && hrv == null && hrvSdnn == null
				&& steps == null) {
			return false;
		}
		DailyHealth day = days.findByOwnerIdAndDate(userId, date)
				.orElseGet(() -> new DailyHealth(users.getReferenceById(userId), date));
		// intervals.icu ist die Quelle: was dort steht, gilt; Lücken lassen bestehende Werte stehen
		if (sleep != null && sleep <= 1440) {
			day.setSleepMinutes(sleep);
		}
		if (sleepScore != null) {
			day.setSleepScore(sleepScore);
		}
		if (restingHr != null && restingHr > 0) {
			day.setRestingHeartRate(restingHr);
		}
		if (hrv != null && hrv > 0) {
			day.setHrvMs(hrv);
		}
		if (hrvSdnn != null && hrvSdnn > 0) {
			day.setHrvSdnnMs(hrvSdnn);
		}
		if (steps != null && steps >= 0) {
			day.setSteps(steps);
		}
		day.setUpdatedAt(now);
		days.save(day);
		return true;
	}

	private static Integer round(Double value) {
		return value == null ? null : (int) Math.round(value);
	}

}
