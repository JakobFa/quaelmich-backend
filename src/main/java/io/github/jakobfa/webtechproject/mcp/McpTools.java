package io.github.jakobfa.webtechproject.mcp;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.jakobfa.webtechproject.custom.CustomWorkoutService;
import io.github.jakobfa.webtechproject.model.Athlete;
import io.github.jakobfa.webtechproject.model.PlannedWorkout;
import io.github.jakobfa.webtechproject.model.TrainingPlan;
import io.github.jakobfa.webtechproject.model.Workout;
import io.github.jakobfa.webtechproject.model.WorkoutType;
import io.github.jakobfa.webtechproject.service.AthleteService;
import io.github.jakobfa.webtechproject.service.RaceEventService;
import io.github.jakobfa.webtechproject.service.TrainingPlanService;
import io.github.jakobfa.webtechproject.service.WorkoutLibrary;

/**
 * Die Werkzeuge, die eine KI-App über MCP sieht: nur der Plan, das Profil und die Workout-Bibliothek. Bewusst keine
 * gefahrenen Fahrten, keine Diagnose und keine Werte aus Strava: Die API-Policy von Strava (Stand 1. Juni 2026, §5.3
 * und §5.10) verbietet, Strava-Daten und daraus Abgeleitetes in eine KI zu laden. Dazu keine Freitexte der Nutzer
 * (Rückmeldungen, Notizen), damit nichts darin wie eine Anweisung wirken kann.
 */
@Component
public class McpTools {

	/**
	 * Beschreibung eines Werkzeugs (Name, Text, JSON-Schema der Eingaben). annotations sagen der KI-App, ob das
	 * Werkzeug etwas ändert (readOnlyHint, destructiveHint), damit sie vorher beim Nutzer nachfragt.
	 */
	public record Tool(String name, String description, Map<String, Object> inputSchema, String scope,
			Map<String, Object> annotations) {

		/** Ein Lesewerkzeug */
		public Tool(String name, String description, Map<String, Object> inputSchema, String scope) {
			this(name, description, inputSchema, scope, Map.of("readOnlyHint", true));
		}
	}

	/** Wird geworfen, wenn die Eingaben nicht passen oder es nichts zu zeigen gibt (kommt als Fehlertext zurück) */
	public static class ToolException extends RuntimeException {
		public ToolException(String message) {
			super(message);
		}
	}

	static final int MAX_DAYS = 92;

	private final AthleteService athletes;
	private final TrainingPlanService plans;
	private final WorkoutLibrary library;
	private final CustomWorkoutService customWorkouts;
	private final RaceEventService events;
	private final TransactionTemplate tx;
	private final Clock clock;
	private final io.github.jakobfa.webtechproject.service.AiAssessmentService aiAssessments;
	private final io.github.jakobfa.webtechproject.health.WellnessImportService wellness;
	private final io.github.jakobfa.webtechproject.health.HealthService health;

	public McpTools(AthleteService athletes, TrainingPlanService plans,
			WorkoutLibrary library, CustomWorkoutService customWorkouts, RaceEventService events, TransactionTemplate tx, Clock clock,
			io.github.jakobfa.webtechproject.service.AiAssessmentService aiAssessments,
			io.github.jakobfa.webtechproject.health.WellnessImportService wellness,
			io.github.jakobfa.webtechproject.health.HealthService health) {
		this.aiAssessments = aiAssessments;
		this.wellness = wellness;
		this.health = health;
		this.athletes = athletes;
		this.plans = plans;
		this.library = library;
		this.customWorkouts = customWorkouts;
		this.events = events;
		this.tx = tx;
		this.clock = clock;
	}

	private static Map<String, Object> schema(Map<String, Object> properties) {
		Map<String, Object> schema = new LinkedHashMap<>();
		schema.put("type", "object");
		schema.put("properties", properties);
		schema.put("additionalProperties", false);
		return schema;
	}

	private static Map<String, Object> text(String description) {
		return Map.of("type", "string", "description", description);
	}

	/** Alle Werkzeuge (die Aufrufer filtern nach ihrem Zugriffsbereich) */
	public List<Tool> tools() {
		return List.of(
				new Tool("get_overview", "Überblick über den Athleten: Profil, Ziel/Event, Verfügbarkeit, deine letzte Einschätzung, Stand des Plans "
						+ "(Woche, Phase, krank gemeldet) und die nächsten 7 Tage. Guter Startpunkt.",
						schema(Map.of()), OAuthService.SCOPE_READ),
				new Tool("get_plan", "Der Trainingsplan für einen Zeitraum (Standard: heute bis in 14 Tagen, höchstens "
						+ "92 Tage): je Tag die Einheit mit Art, Dauer, Belastung (TSS), Phase, Status und Anpassung.",
						schema(Map.of("from", text("Erster Tag, JJJJ-MM-TT"), "to", text("Letzter Tag, JJJJ-MM-TT"))),
						OAuthService.SCOPE_READ),
				new Tool("list_workouts", "Die Workout-Bibliothek: Titel, Art, Dauer, Belastung und Beschreibung. Optional "
						+ "nach Art gefiltert (z. B. SWEET_SPOT, THRESHOLD, VO2MAX, ENDURANCE, LONG_RIDE).",
						schema(Map.of("type", text("Trainingsart, z. B. THRESHOLD"))), OAuthService.SCOPE_READ),
				new Tool("list_my_workouts", "Die eigenen Workouts des Nutzers (selbst gebaut oder von dir mit create_workout "
						+ "angelegt), mit Abschnitten. Die id geht in replace_workout.", schema(Map.of()),
						OAuthService.SCOPE_READ),
				new Tool("list_events", "Die weiteren Events des Nutzers (zusätzlich zum ersten im Profil, das get_overview "
						+ "zeigt) mit id. Die id geht in update_event und delete_event.", schema(Map.of()),
						OAuthService.SCOPE_READ),
				new Tool("get_wellness", "Schlaf und Erholung je Tag (Standard: die letzten 14 Tage, höchstens 92): "
						+ "Schlafdauer in Minuten, Schlafbewertung, Ruhepuls, HRV (rMSSD; bei Apple Watch SDNN in hrvSdnnMs) "
						+ "und Schritte – aus intervals.icu, egal aus welcher Quelle. Nur wenn der Nutzer es in quaelmich "
						+ "freigegeben hat. Fehlende Werte sind null.",
						schema(Map.of("from", text("Erster Tag, JJJJ-MM-TT"), "to", text("Letzter Tag, JJJJ-MM-TT"))),
						OAuthService.SCOPE_READ));
	}

	/** Führt ein Werkzeug für einen Nutzer aus; Ergebnis ist eine JSON-fähige Struktur */
	public Object call(Long userId, String name, Map<String, Object> arguments) {
		return switch (name) {
			case "get_overview" -> overview(userId);
			case "get_plan" -> plan(userId, date(arguments, "from", today()), date(arguments, "to", today().plusDays(14)));
			case "list_workouts" -> workouts(arguments == null ? null : (String) arguments.get("type"));
			case "list_events" -> eventRows(userId);
			case "list_my_workouts" -> workoutRows(customWorkouts.listOwn(userId), true);
			case "get_wellness" -> wellnessDays(userId, date(arguments, "from", today().minusDays(13)),
					date(arguments, "to", today()));
			default -> throw new ToolException("Unbekanntes Werkzeug: " + name);
		};
	}

	// ---------- Werkzeuge ----------

	private Object overview(Long userId) {
		Athlete athlete = athleteOf(userId);
		LocalDate today = today();
		return tx.execute(status -> {
			Map<String, Object> out = new LinkedHashMap<>();
			out.put("heute", today.toString());
			Map<String, Object> profile = new LinkedHashMap<>();
			profile.put("level", valueOrNull(athlete.getLevel()));
			profile.put("ftpWatt", athlete.getFtp());
			profile.put("gewichtKg", athlete.getWeightKg());
			profile.put("ziel", valueOrNull(athlete.getTrainingGoal()));
			profile.put("trainingsmodus", valueOrNull(athlete.getTrainingMode()));
			if (athlete.getEvent() != null) {
				var event = athlete.getEvent();
				Map<String, Object> e = new LinkedHashMap<>();
				e.put("name", event.getName());
				e.put("datum", valueOrNull(event.getDate()));
				e.put("distanzKm", event.getDistanceKm());
				e.put("hoehenmeter", event.getElevationMeters());
				e.put("art", valueOrNull(event.getType()));
				e.put("ziel", valueOrNull(event.getGoal()));
				profile.put("event", e);
			}
			if (athlete.getAvailability() != null) {
				var a = athlete.getAvailability();
				Map<String, Object> hours = new LinkedHashMap<>();
				hours.put("montag", a.getMonday());
				hours.put("dienstag", a.getTuesday());
				hours.put("mittwoch", a.getWednesday());
				hours.put("donnerstag", a.getThursday());
				hours.put("freitag", a.getFriday());
				hours.put("samstag", a.getSaturday());
				hours.put("sonntag", a.getSunday());
				profile.put("stundenProTag", hours);
			}
			out.put("athlet", profile);
			Optional<TrainingPlan> plan = plans.findByAthlete(athlete.getId());
			if (plan.isPresent()) {
				out.put("planStart", valueOrNull(plan.get().getStartDate()));
				out.put("krankSeit", plan.get().getSickSince() == null ? null : plan.get().getSickSince().toString());
				out.put("naechste7Tage", days(plan.get(), today, today.plusDays(6)));
			} else {
				out.put("plan", "Es gibt noch keinen Plan.");
			}
			out.put("einschaetzung", assessment(userId));
			out.put("schlafUndErholung", wellness.isSharedWithAi(userId)
					? "freigegeben – hol die Tage mit get_wellness"
					: "nicht freigegeben (der Nutzer kann es in quaelmich unter Einstellungen → Schlaf & Erholung einschalten)");
			return out;
		});
	}

	private Object wellnessDays(Long userId, LocalDate from, LocalDate to) {
		checkRange(from, to);
		if (!wellness.isSharedWithAi(userId)) {
			throw new ToolException("Der Nutzer hat Schlaf und Erholung nicht für dich freigegeben. Er kann das in "
					+ "quaelmich unter Einstellungen → Schlaf & Erholung einschalten.");
		}
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("von", from.toString());
		out.put("bis", to.toString());
		out.put("tage", health.between(userId, from, to).stream().map(day -> {
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("datum", day.date().toString());
			row.put("schlafMinuten", day.sleepMinutes());
			row.put("schlafBewertung", day.sleepScore());
			row.put("ruhepuls", day.restingHeartRate());
			row.put("hrvMs", day.hrvMs());
			row.put("hrvSdnnMs", day.hrvSdnnMs());
			row.put("schritte", day.steps());
			return row;
		}).toList());
		return out;
	}

	/** Deine letzte Einschätzung (set_assessment) und ob eine neue fällig ist */
	private Map<String, Object> assessment(Long userId) {
		Map<String, Object> out = new LinkedHashMap<>();
		var current = aiAssessments.current(userId);
		out.put("notiz", current.map(view -> view.note()).orElse(null));
		out.put("stand", current.map(view -> view.updatedAt().toString()).orElse(null));
		if (aiAssessments.needsRenewal(userId)) {
			out.put("hinweis", "Deine Einschätzung zur Form auf der Planseite ist alt oder fehlt. Schau dir die "
					+ "letzten 7 Tage an (gefahrene Fahrten aus dem Strava- oder Garmin-Konnektor, Schlaf und Erholung mit "
					+ "get_wellness, falls freigegeben) und schreib mit set_assessment ein, zwei Sätze dazu.");
		}
		return out;
	}

	private Object plan(Long userId, LocalDate from, LocalDate to) {
		checkRange(from, to);
		Athlete athlete = athleteOf(userId);
		return tx.execute(status -> {
			TrainingPlan plan = plans.findByAthlete(athlete.getId())
					.orElseThrow(() -> new ToolException("Es gibt noch keinen Plan."));
			Map<String, Object> out = new LinkedHashMap<>();
			out.put("von", from.toString());
			out.put("bis", to.toString());
			out.put("tage", days(plan, from, to));
			return out;
		});
	}

	private Object workouts(String type) {
		List<Workout> all = library.findAll();
		WorkoutType filter = null;
		if (type != null && !type.isBlank()) {
			try {
				filter = WorkoutType.valueOf(type.trim().toUpperCase());
			} catch (IllegalArgumentException e) {
				throw new ToolException("Unbekannte Art: " + type);
			}
		}
		WorkoutType wanted = filter;
		return workoutRows(all.stream().filter(workout -> wanted == null || workout.getType() == wanted).toList(), false);
	}

	private Object eventRows(Long userId) {
		List<Map<String, Object>> rows = events.list(userId).orElseThrow(() -> new ToolException("Es gibt noch kein Profil."))
				.stream().map(McpTools::eventRow).toList();
		return Map.of("anzahl", rows.size(), "events", rows);
	}

	static Map<String, Object> eventRow(io.github.jakobfa.webtechproject.model.RaceEvent event) {
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("id", event.getId());
		row.put("name", event.getName());
		row.put("datum", valueOrNull(event.getDate()));
		row.put("distanzKm", event.getDistanceKm());
		row.put("hoehenmeter", event.getElevationMeters());
		row.put("art", valueOrNull(event.getType()));
		row.put("ziel", valueOrNull(event.getGoal()));
		return row;
	}

	static Map<String, Object> workoutRows(List<Workout> workouts, boolean withSteps) {
		List<Map<String, Object>> rows = workouts.stream().map(workout -> {
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("id", workout.getId());
			row.put("titel", workout.getTitle());
			row.put("art", workout.getType().name());
			row.put("dauerMinuten", workout.getDurationInMinutes());
			row.put("tss", workout.getTss());
			row.put("beschreibung", workout.getDescription());
			if (withSteps) {
				row.put("abschnitte", workout.getSteps().stream().map(step -> Map.of("art", step.kind().name(),
						"minuten", Math.round(step.durationSeconds() / 6f) / 10.0, "von%FTP", step.powerStartPercent(),
						"bis%FTP", step.powerEndPercent())).toList());
			}
			return row;
		}).toList();
		return Map.of("anzahl", rows.size(), "workouts", rows);
	}

	// ---------- Hilfen ----------

	private List<Map<String, Object>> days(TrainingPlan plan, LocalDate from, LocalDate to) {
		List<Map<String, Object>> rows = new ArrayList<>();
		plan.getWorkouts().stream().filter(workout -> !workout.getDate().isBefore(from) && !workout.getDate().isAfter(to))
				.sorted((a, b) -> a.getDate().compareTo(b.getDate())).forEach(workout -> rows.add(day(workout)));
		return rows;
	}

	/** Die nächsten 7 Tage eines Plans (für die Antworten der Schreibwerkzeuge) */
	List<Map<String, Object>> nextDays(TrainingPlan plan) {
		return days(plan, today(), today().plusDays(6));
	}

	Map<String, Object> day(PlannedWorkout planned) {
		Workout workout = library.resolve(planned.getWorkoutId(), planned.getDurationMinutes()).orElseThrow();
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("tag", planned.getDate().toString());
		row.put("wochentag", planned.getDate().getDayOfWeek().toString());
		row.put("planId", planned.getId());
		row.put("phase", valueOrNull(planned.getPhase()));
		row.put("einheit", workout.getTitle());
		row.put("art", workout.getType().name());
		row.put("minuten", workout.getDurationInMinutes());
		row.put("tss", workout.getTss());
		if (planned.getSkipReason() != null) {
			row.put("status", "ausgefallen (" + planned.getSkipReason() + ")");
		}
		if (planned.getAdjustmentNote() != null) {
			// Die Texte der automatischen Anpassung nennen gefahrene Werte (aus Strava) – die geben wir nicht weiter
			row.put("anpassung", planned.getAdjustmentNote().startsWith("Von dir") ? planned.getAdjustmentNote()
					: "automatisch angepasst");
		}
		return row;
	}

	private Athlete athleteOf(Long userId) {
		return athletes.findByOwner(userId).orElseThrow(() -> new ToolException("Es gibt noch kein Profil."));
	}

	private LocalDate today() {
		return LocalDate.now(clock);
	}

	private static LocalDate date(Map<String, Object> arguments, String key, LocalDate fallback) {
		Object value = arguments == null ? null : arguments.get(key);
		if (value == null || value.toString().isBlank()) {
			return fallback;
		}
		try {
			return LocalDate.parse(value.toString().trim());
		} catch (java.time.format.DateTimeParseException e) {
			throw new ToolException("Ungültiges Datum für '" + key + "' (erwartet JJJJ-MM-TT): " + value);
		}
	}

	private static void checkRange(LocalDate from, LocalDate to) {
		if (to.isBefore(from)) {
			throw new ToolException("'to' liegt vor 'from'");
		}
		if (java.time.temporal.ChronoUnit.DAYS.between(from, to) > MAX_DAYS) {
			throw new ToolException("Der Zeitraum darf höchstens " + MAX_DAYS + " Tage lang sein");
		}
	}

	/** Text eines Werts, null bleibt null (nicht der Text "null") */
	private static String valueOrNull(Object value) {
		return value == null ? null : value.toString();
	}
}
