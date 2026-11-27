package io.github.jakobfa.webtechproject.health;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

import io.github.jakobfa.webtechproject.integration.intervals.IntervalsConnectionService;
import io.github.jakobfa.webtechproject.integration.intervals.IntervalsCredentials;
import io.github.jakobfa.webtechproject.integration.intervals.IntervalsIcuClient;
import io.github.jakobfa.webtechproject.integration.intervals.IntervalsWellness;
import io.github.jakobfa.webtechproject.repository.AppUserRepository;

/**
 * Übernimmt Schlaf und Erholung aus dem Wellness-Bereich von intervals.icu – egal, aus welcher Quelle sie dort
 * stammen. Nur mit Einwilligung; Widerruf löscht alle Tageswerte.
 */
@Service
public class WellnessImportService {

	/** So weit reicht der Verlauf zurück, den quaelmich beim Einschalten und bei „Jetzt abrufen“ auffüllt */
	public static final int WELLNESS_BACKFILL_DAYS = 365;
	/** Höchstens so viele Tage pro Anfrage an intervals.icu */
	static final int BLOCK_DAYS = 90;
	/** „Jetzt abrufen“ holt diese letzten Tage immer neu, auch wenn der Verlauf schon vollständig ist */
	static final int REFRESH_DAYS = 28;
	/** Regelmäßiger Abruf (nachts und beim Öffnen): nur die letzten Tage, fängt nachträgliche Korrekturen der Uhr ab */
	public static final int SYNC_DAYS = 7;
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

	/** Ein Zeitraum für eine Anfrage, beide Tage eingeschlossen */
	record Block(LocalDate oldest, LocalDate newest) {
	}

	/** Teilt [oldest, newest] in Blöcke von höchstens size Tagen, der neueste zuerst – lückenlos und ohne Überlappung */
	static List<Block> blocks(LocalDate oldest, LocalDate newest, int size) {
		List<Block> out = new ArrayList<>();
		LocalDate end = newest;
		while (!end.isBefore(oldest)) {
			LocalDate start = end.minusDays(size - 1L);
			if (start.isBefore(oldest)) {
				start = oldest;
			}
			out.add(new Block(start, end));
			end = start.minusDays(1);
		}
		return out;
	}

	public Optional<WellnessImport> findByOwner(Long userId) {
		return imports.findByOwnerId(userId);
	}

	/** Einschalten (mit Einwilligung) und gleich den Verlauf holen. Leer ohne intervals.icu-Verbindung. */
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
		return importHistory(userId);
	}

	/** Ausschalten: Einwilligung widerrufen, alle Tageswerte löschen */
	@Transactional
	public void disable(Long userId) {
		imports.findByOwnerId(userId).ifPresent(imports::delete);
		days.deleteAll(days.findByOwnerIdOrderByDate(userId));
	}

	/** Regelmäßiger Abruf: nur die letzten SYNC_DAYS Tage. Leer, wenn aus. */
	@Transactional
	public Optional<Integer> syncRecent(Long userId) {
		var settings = imports.findByOwnerId(userId);
		var credentials = intervals.credentialsOf(userId);
		if (settings.isEmpty() || credentials.isEmpty()) {
			return Optional.empty();
		}
		LocalDate today = LocalDate.now(clock);
		Instant now = clock.instant();
		int imported = fetch(userId, credentials.get(), new Block(today.minusDays(SYNC_DAYS - 1L), today), now);
		settings.get().setLastImportAt(now);
		imports.save(settings.get());
		return Optional.of(imported);
	}

	/**
	 * Einschalten und „Jetzt abrufen“: holt die letzten REFRESH_DAYS Tage neu und füllt den Verlauf bis
	 * WELLNESS_BACKFILL_DAYS zurück auf, wo er noch fehlt – in Blöcken, der neueste zuerst. Scheitert ein älterer Block
	 * (etwa 429 oder 5xx), bricht der Rückimport ohne Wiederholung ab; der nächste Abruf macht dort weiter. Scheitert
	 * schon der erste, wirft er wie bisher. Liefert die Zahl der übernommenen Tage, leer, wenn aus.
	 */
	@Transactional
	public Optional<Integer> importHistory(Long userId) {
		var found = imports.findByOwnerId(userId);
		var credentials = intervals.credentialsOf(userId);
		if (found.isEmpty() || credentials.isEmpty()) {
			return Optional.empty();
		}
		WellnessImport settings = found.get();
		LocalDate today = LocalDate.now(clock);
		LocalDate target = today.minusDays(WELLNESS_BACKFILL_DAYS - 1L);
		LocalDate from = settings.getHistoryFrom();
		List<Block> todo = new ArrayList<>();
		if (from == null) {
			todo.addAll(blocks(target, today, BLOCK_DAYS));
		} else {
			LocalDate recent = today.minusDays(REFRESH_DAYS - 1L);
			todo.add(new Block(recent, today));
			LocalDate gapEnd = (from.isBefore(recent) ? from : recent).minusDays(1);
			todo.addAll(blocks(target, gapEnd, BLOCK_DAYS));
		}
		Instant now = clock.instant();
		int imported = 0;
		for (int i = 0; i < todo.size(); i++) {
			Block block = todo.get(i);
			try {
				imported += fetch(userId, credentials.get(), block, now);
			} catch (RestClientException e) {
				if (i == 0) {
					throw e;
				}
				log.warn("Rückimport Wellness für Nutzer {} bei {} bis {} abgebrochen, der nächste Abruf macht weiter: {}",
						userId, block.oldest(), block.newest(), e.getMessage());
				break;
			}
			LocalDate covered = settings.getHistoryFrom();
			boolean contiguous = covered == null ? block.newest().equals(today)
					: !block.newest().isBefore(covered.minusDays(1));
			if (contiguous && (covered == null || block.oldest().isBefore(covered))) {
				settings.setHistoryFrom(block.oldest());
			}
		}
		settings.setLastImportAt(now);
		imports.save(settings);
		return Optional.of(imported);
	}

	private int fetch(Long userId, IntervalsCredentials credentials, Block block, Instant now) {
		int imported = 0;
		for (IntervalsWellness entry : client.listWellness(credentials, block.oldest(), block.newest())) {
			if (store(userId, entry, now)) {
				imported++;
			}
		}
		return imported;
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
			syncRecent(userId);
		} catch (RuntimeException e) {
			log.warn("Wellness von intervals.icu für Nutzer {} nicht abrufbar: {}", userId, e.getMessage());
		}
	}

	/** Freigabe für die KI setzen oder zurücknehmen. Leer, wenn die Übernahme aus ist. */
	@Transactional
	public Optional<WellnessImport> shareWithAi(Long userId, boolean share) {
		return imports.findByOwnerId(userId).map(settings -> {
			settings.setSharedWithAiAt(share ? clock.instant() : null);
			return imports.save(settings);
		});
	}

	@Transactional(readOnly = true)
	public boolean isSharedWithAi(Long userId) {
		return imports.findByOwnerId(userId).map(settings -> settings.getSharedWithAiAt() != null).orElse(false);
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
