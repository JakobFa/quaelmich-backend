package io.github.jakobfa.webtechproject.service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import io.github.jakobfa.webtechproject.model.PlannedWorkout;
import io.github.jakobfa.webtechproject.model.Workout;

/**
 * Der Trainingsplan als iCalendar-Datei (RFC 5545) zum Abonnieren: ganztägige Einträge, weil der Plan keine
 * Uhrzeiten kennt. Ausgefallene Einheiten (krank, Pause, nicht gemacht) tauchen nicht auf – der Kalender zeigt, was
 * ansteht.
 */
public final class CalendarFeed {

	private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;
	private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
	/** Kalender-Apps fragen höchstens so oft nach (viele halten sich nicht daran, Apple und Outlook schon) */
	private static final String REFRESH = "PT1H";

	private CalendarFeed() {
	}

	/** Alles bis zu den ausgewählten Einheiten – workoutOf löst Workout und Dauer auf */
	public static String build(List<PlannedWorkout> workouts, java.util.function.Function<PlannedWorkout, Workout> workoutOf,
			ZonedDateTime now) {
		List<String> lines = new ArrayList<>();
		lines.add("BEGIN:VCALENDAR");
		lines.add("VERSION:2.0");
		lines.add("PRODID:-//quaelmich//Trainingsplan//DE");
		lines.add("CALSCALE:GREGORIAN");
		lines.add("METHOD:PUBLISH");
		lines.add("X-WR-CALNAME:quaelmich Training");
		lines.add("REFRESH-INTERVAL;VALUE=DURATION:" + REFRESH);
		lines.add("X-PUBLISHED-TTL:" + REFRESH);
		String stamp = STAMP.format(now.withZoneSameInstant(ZoneOffset.UTC));
		for (PlannedWorkout planned : workouts) {
			if (planned.getSkipReason() != null) {
				continue;
			}
			Workout workout = workoutOf.apply(planned);
			LocalDate day = planned.getDate();
			String title = (planned.isCompleted() ? "✓ " : "🚴 ") + workout.getTitle() + " · "
					+ formatMinutes(workout.getDurationInMinutes());
			StringBuilder description = new StringBuilder(workout.getDescription() == null ? "" : workout.getDescription());
			description.append("\n\nDauer ").append(formatMinutes(workout.getDurationInMinutes()))
					.append(" · ca. ").append(workout.getTss()).append(" TSS");
			if (planned.getAdjustmentNote() != null) {
				description.append("\n").append(planned.getAdjustmentNote());
			}
			lines.add("BEGIN:VEVENT");
			lines.add("UID:workout-" + planned.getId() + "@quaelmich.com");
			lines.add("DTSTAMP:" + stamp);
			lines.add("DTSTART;VALUE=DATE:" + DAY.format(day));
			lines.add("DTEND;VALUE=DATE:" + DAY.format(day.plusDays(1)));
			lines.add("SUMMARY:" + escape(title));
			lines.add("DESCRIPTION:" + escape(description.toString()));
			lines.add("TRANSP:TRANSPARENT"); // blockiert den Tag nicht
			lines.add("END:VEVENT");
		}
		lines.add("END:VCALENDAR");
		StringBuilder out = new StringBuilder();
		lines.forEach(line -> out.append(fold(line)).append("\r\n"));
		return out.toString();
	}

	static String formatMinutes(int minutes) {
		return minutes >= 60 ? "%d:%02d h".formatted(minutes / 60, minutes % 60) : minutes + " min";
	}

	/** Text-Werte: Backslash, Semikolon, Komma und Zeilenumbrüche maskieren */
	public static String escape(String text) {
		return text.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,")
				.replace("\r\n", "\\n").replace("\n", "\\n");
	}

	/** Zeilen dürfen höchstens 75 Bytes lang sein; Fortsetzungszeilen beginnen mit einem Leerzeichen */
	public static String fold(String line) {
		StringBuilder out = new StringBuilder();
		int bytes = 0;
		int limit = 75;
		for (int i = 0; i < line.length(); ) {
			int codePoint = line.codePointAt(i);
			String character = new String(Character.toChars(codePoint));
			int size = character.getBytes(StandardCharsets.UTF_8).length;
			if (bytes + size > limit) {
				out.append("\r\n ");
				bytes = 1;
				limit = 75;
			}
			out.append(character);
			bytes += size;
			i += Character.charCount(codePoint);
		}
		return out.toString();
	}
}
