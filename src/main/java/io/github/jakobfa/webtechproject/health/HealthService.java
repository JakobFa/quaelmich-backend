package io.github.jakobfa.webtechproject.health;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.jakobfa.webtechproject.repository.AppUserRepository;

/**
 * Gesundheitsdaten je Tag. Dazu der (derzeit abgeschaltete) Weg über einen iPhone-Kurzbefehl: Schlüssel erzeugen
 * und zurückziehen, Tageswerte annehmen.
 * Die Werte nimmt quaelmich großzügig an (Zahl oder Text, Komma oder Punkt), weil Kurzbefehle Zahlen je nach
 * Einstellung als Text schicken.
 */
@Service
public class HealthService {

	/** So viele Tage zeigt das Profil */
	static final int RECENT_DAYS = 14;

	private static final SecureRandom RANDOM = new SecureRandom();
	private static final Pattern NUMBER = Pattern.compile("-?[0-9][0-9.,]*");
	private static final Pattern THOUSANDS = Pattern.compile("[0-9]{1,3}([.,][0-9]{3})+");
	private static final Pattern CLOCK = Pattern.compile("([0-9]{1,2}):([0-9]{2})(:[0-9]{2})?");
	private static final Pattern HOURS = Pattern.compile("([0-9]+(?:[.,][0-9]+)?)\\s*(?:h|hr|hrs|std|stunde|stunden)\\b");
	private static final Pattern MINUTES = Pattern.compile("([0-9]+(?:[.,][0-9]+)?)\\s*(?:m|min|mins|minute|minuten)\\b");

	/** Eine Angabe passt nicht (z. B. 3000 Minuten Schlaf) – die Meldung landet im Kurzbefehl */
	public static class InvalidValueException extends RuntimeException {
		public InvalidValueException(String message) {
			super(message);
		}
	}

	/** Was der Kurzbefehl schickt; alle Felder optional */
	public record Upload(Object date, Object sleep, Object sleepMinutes, Object sleepHours, Object sleepSeconds, Object steps, Object activeEnergyKcal,
			Object restingHeartRate, Object hrv) {
	}

	public record Day(LocalDate date, Integer sleepMinutes, Integer sleepScore, Integer steps,
			Integer activeEnergyKcal, Integer restingHeartRate, Integer hrvMs, Integer hrvSdnnMs) {
		static Day of(DailyHealth day) {
			return new Day(day.getDate(), day.getSleepMinutes(), day.getSleepScore(), day.getSteps(),
					day.getActiveEnergyKcal(), day.getRestingHeartRate(), day.getHrvMs(), day.getHrvSdnnMs());
		}
	}

	private final HealthConnectionRepository connections;
	private final DailyHealthRepository days;
	private final AppUserRepository users;
	private final Clock clock;

	public HealthService(HealthConnectionRepository connections, DailyHealthRepository days, AppUserRepository users,
			Clock clock) {
		this.connections = connections;
		this.days = days;
		this.users = users;
		this.clock = clock;
	}

	/** Die letzten zwei Wochen, neueste zuerst */
	@Transactional(readOnly = true)
	public List<Day> recent(Long userId) {
		LocalDate from = LocalDate.now(clock).minusDays(RECENT_DAYS - 1);
		return days.findByOwnerIdAndDateGreaterThanEqualOrderByDateDesc(userId, from).stream().map(Day::of).toList();
	}

	/** Neuen Schlüssel erzeugen (ein alter wird ungültig). Liefert den Schlüssel im Klartext – nur dieses eine Mal. */
	@Transactional
	public String connect(Long userId) {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		String key = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		HealthConnection connection = connections.findByOwnerId(userId).orElseGet(() -> {
			HealthConnection created = new HealthConnection();
			created.setOwner(users.getReferenceById(userId));
			return created;
		});
		connection.setKeyHash(hash(key));
		connection.setLastChars(key.substring(key.length() - 4));
		connection.setConsentAt(clock.instant());
		connections.save(connection);
		return key;
	}

	/** Verbindung beenden und alle Gesundheitsdaten löschen (Widerruf der Einwilligung) */
	@Transactional
	public void disconnect(Long userId) {
		connections.findByOwnerId(userId).ifPresent(connections::delete);
		days.deleteAll(days.findByOwnerIdOrderByDate(userId));
	}

	/** Gibt es einen Schlüssel? Dann der Nutzer dazu. */
	@Transactional(readOnly = true)
	public Optional<Long> ownerOf(String key) {
		if (key == null || key.isBlank()) {
			return Optional.empty();
		}
		return connections.findByKeyHash(hash(key.trim())).map(connection -> connection.getOwner().getId());
	}

	/** Tageswerte übernehmen; nur mitgeschickte Werte ändern etwas. Liefert den gespeicherten Tag. */
	@Transactional
	public Day upload(Long userId, Upload upload) {
		LocalDate date = date(upload.date());
		Integer sleep = present(upload.sleep()) ? sleep(upload.sleep())
				: minutes(upload.sleepMinutes(), upload.sleepHours(), upload.sleepSeconds());
		Integer steps = whole(upload.steps(), "steps", 0, 200_000);
		Integer energy = whole(upload.activeEnergyKcal(), "activeEnergyKcal", 0, 20_000);
		Integer restingHr = decimal(upload.restingHeartRate(), "restingHeartRate", 20, 250);
		Integer hrv = decimal(upload.hrv(), "hrv", 1, 500);
		if (sleep == null && steps == null && energy == null && restingHr == null && hrv == null) {
			throw new InvalidValueException("Keine Werte mitgeschickt.");
		}
		DailyHealth day = days.findByOwnerIdAndDate(userId, date)
				.orElseGet(() -> new DailyHealth(users.getReferenceById(userId), date));
		if (sleep != null) {
			day.setSleepMinutes(sleep);
		}
		if (steps != null) {
			day.setSteps(steps);
		}
		if (energy != null) {
			day.setActiveEnergyKcal(energy);
		}
		if (restingHr != null) {
			day.setRestingHeartRate(restingHr);
		}
		if (hrv != null) {
			day.setHrvSdnnMs(hrv); // Apple Watch misst SDNN
		}
		Instant now = clock.instant();
		day.setUpdatedAt(now);
		days.save(day);
		connections.findByOwnerId(userId).ifPresent(connection -> connection.setLastUploadAt(now));
		return Day.of(day);
	}

	/** Tage im Zeitraum, älteste zuerst (für die KI) */
	@Transactional(readOnly = true)
	public List<Day> between(Long userId, LocalDate from, LocalDate to) {
		return days.findByOwnerIdAndDateBetweenOrderByDate(userId, from, to).stream().map(Day::of).toList();
	}

	/** Für den Datenexport */
	@Transactional(readOnly = true)
	public List<Day> allOf(Long userId) {
		return days.findByOwnerIdOrderByDate(userId).stream().map(Day::of).toList();
	}

	private LocalDate date(Object value) {
		LocalDate today = LocalDate.now(clock);
		if (value == null || value.toString().isBlank()) {
			return today;
		}
		String text = value.toString().trim();
		List<DateTimeFormatter> formats = new ArrayList<>(List.of(DateTimeFormatter.ISO_LOCAL_DATE,
				DateTimeFormatter.ofPattern("d.M.uuuu"), DateTimeFormatter.ofPattern("d.M.uu")));
		String candidate = text.length() >= 10 && text.charAt(4) == '-' ? text.substring(0, 10) : text;
		for (DateTimeFormatter format : formats) {
			try {
				LocalDate date = LocalDate.parse(candidate, format);
				if (date.isAfter(today) || date.isBefore(today.minusDays(60))) {
					throw new InvalidValueException("Datum " + text + " liegt nicht in den letzten 60 Tagen.");
				}
				return date;
			} catch (DateTimeParseException ignored) {
				// nächstes Format probieren
			}
		}
		throw new InvalidValueException("Datum " + text + " nicht lesbar – bitte als 2026-11-27 schicken.");
	}

	/**
	 * Schlaf in Minuten, Stunden oder Sekunden. Als Text geht auch eine Dauer wie "7:30", "7 h 30 min" oder
	 * "7 Std. 30 Min." – so geben Kurzbefehle Zeiträume manchmal aus.
	 */
	private Integer minutes(Object sleepMinutes, Object sleepHours, Object sleepSeconds) {
		for (Object value : new Object[] { sleepMinutes, sleepHours, sleepSeconds }) {
			Integer duration = duration(value);
			if (duration != null) {
				return check(duration, "sleep", 0, 1440);
			}
		}
		if (present(sleepMinutes)) {
			return decimal(sleepMinutes, "sleepMinutes", 0, 1440);
		}
		if (present(sleepHours)) {
			return check((int) Math.round(number(sleepHours, false) * 60), "sleepHours", 0, 1440);
		}
		if (present(sleepSeconds)) {
			return check((int) Math.round(number(sleepSeconds, false) / 60), "sleepSeconds", 0, 1440);
		}
		return null;
	}

	/**
	 * Schlaf ohne Einheit, wie der Kurzbefehl ihn gerade liefert: eine Dauer als Text, sonst nach Größe –
	 * bis 24 sind es Stunden, bis 1440 Minuten, darüber Sekunden.
	 */
	private Integer sleep(Object value) {
		Integer duration = duration(value);
		if (duration != null) {
			return check(duration, "sleep", 0, 1440);
		}
		double number = number(value, false);
		double minutes = number <= 24 ? number * 60 : number <= 1440 ? number : number / 60;
		return check((int) Math.round(minutes), "sleep", 0, 1440);
	}

	private static boolean present(Object value) {
		return value != null && !value.toString().isBlank();
	}

	/** Dauer aus Text wie "7:30", "7 h 30 min", "7 Std. 30 Min.", "450 min"; null, wenn es keine solche Angabe ist */
	static Integer duration(Object value) {
		if (!(value instanceof String text)) {
			return null;
		}
		String lower = text.trim().toLowerCase(java.util.Locale.ROOT);
		Matcher clock = CLOCK.matcher(lower);
		if (clock.matches()) {
			return Integer.parseInt(clock.group(1)) * 60 + Integer.parseInt(clock.group(2));
		}
		Matcher hours = HOURS.matcher(lower);
		Matcher mins = MINUTES.matcher(lower);
		boolean hasHours = hours.find();
		boolean hasMinutes = mins.find();
		if (!hasHours && !hasMinutes) {
			return null;
		}
		double total = 0;
		if (hasHours) {
			total += Double.parseDouble(hours.group(1).replace(',', '.')) * 60;
		}
		if (hasMinutes) {
			total += Double.parseDouble(mins.group(1).replace(',', '.'));
		}
		return (int) Math.round(total);
	}

	/** Ganzzahl, bei der "8.432" Tausenderpunkte sind (Schritte, Kalorien) */
	private Integer whole(Object value, String field, int min, int max) {
		Double number = number(value, true);
		return number == null ? null : check((int) Math.round(number), field, min, max);
	}

	/** Kommazahl, gerundet ("54,5" Ruhepuls) */
	private Integer decimal(Object value, String field, int min, int max) {
		Double number = number(value, false);
		return number == null ? null : check((int) Math.round(number), field, min, max);
	}

	private static Integer check(int value, String field, int min, int max) {
		if (value < min || value > max) {
			throw new InvalidValueException(field + " = " + value + " liegt nicht zwischen " + min + " und " + max + ".");
		}
		return value;
	}

	/** Zahl aus Zahl oder Text wie "7,5", "7.5 h", "8.432 Schritte"; leer = null */
	static Double number(Object value, boolean thousandsSeparators) {
		if (value == null) {
			return null;
		}
		if (value instanceof Number number) {
			return number.doubleValue();
		}
		String text = value.toString().trim();
		if (text.isEmpty()) {
			return null;
		}
		Matcher match = NUMBER.matcher(text);
		if (!match.find()) {
			throw new InvalidValueException("\"" + text + "\" ist keine Zahl.");
		}
		String digits = match.group();
		if (thousandsSeparators && THOUSANDS.matcher(digits).matches()) {
			digits = digits.replace(".", "").replace(",", "");
		} else if (digits.contains(",") && digits.contains(".")) {
			// 1.234,5 oder 1,234.5: das letzte Zeichen trennt die Nachkommastellen
			boolean commaDecimal = digits.lastIndexOf(',') > digits.lastIndexOf('.');
			digits = commaDecimal ? digits.replace(".", "").replace(',', '.') : digits.replace(",", "");
		} else {
			digits = digits.replace(',', '.');
		}
		try {
			return Double.parseDouble(digits);
		} catch (NumberFormatException e) {
			throw new InvalidValueException("\"" + text + "\" ist keine Zahl.");
		}
	}

	static String hash(String key) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

}
