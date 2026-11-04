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

import io.github.jakobfa.webtechproject.model.Athlete;
import io.github.jakobfa.webtechproject.model.ExtraRide;
import io.github.jakobfa.webtechproject.model.PlannedWorkout;
import io.github.jakobfa.webtechproject.model.TrainingPlan;
import io.github.jakobfa.webtechproject.model.Workout;
import io.github.jakobfa.webtechproject.model.WorkoutType;
import io.github.jakobfa.webtechproject.repository.ExtraRideRepository;
import io.github.jakobfa.webtechproject.service.AthleteService;
import io.github.jakobfa.webtechproject.service.TrainingPlanService;
import io.github.jakobfa.webtechproject.service.WorkoutLibrary;

/**
 * Die Werkzeuge, die eine KI-App über MCP sieht. Nur Zahlen, Daten und Titel aus der Bibliothek – keine Freitexte der
 * Nutzer (Rückmeldungen, Notizen) und keine Strava-Links, damit nichts darin wie eine Anweisung wirken kann und
 * keine Strava-Rohdaten weitergegeben werden.
 */
@Component
public class McpTools {

	/** Beschreibung eines Werkzeugs (Name, Text, JSON-Schema der Eingaben) */
	public record Tool(String name, String description, Map<String, Object> inputSchema, String scope) {
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
	private final ExtraRideRepository extraRides;
	private final WorkoutLibrary library;
	private final TransactionTemplate tx;
	private final Clock clock;

	public McpTools(AthleteService athletes, TrainingPlanService plans, ExtraRideRepository extraRides,
			WorkoutLibrary library, TransactionTemplate tx, Clock clock) {
		this.athletes = athletes;
		this.plans = plans;
		this.extraRides = extraRides;
		this.library = library;
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
				new Tool("get_overview", "Überblick über den Athleten: Profil, Ziel/Event, Verfügbarkeit, Stand des Plans "
						+ "(Woche, Phase, krank gemeldet) und die nächsten 7 Tage. Guter Startpunkt.",
						schema(Map.of()), OAuthService.SCOPE_READ),
				new Tool("get_plan", "Der Trainingsplan für einen Zeitraum (Standard: heute bis in 14 Tagen, höchstens "
						+ "92 Tage): je Tag die Einheit mit Art, Dauer, Belastung (TSS), Phase, Status und Anpassung.",
						schema(Map.of("from", text("Erster Tag, JJJJ-MM-TT"), "to", text("Letzter Tag, JJJJ-MM-TT"))),
						OAuthService.SCOPE_READ),
				new Tool("get_diagnosis", "Die internen Werte, nach denen sich der Plan richtet: Belastung je Tag, Schnitt "
						+ "der letzten 28 Tage, Grenzen für 7 Tage, vergangene Wochen mit Erfüllung und Härte, vergangene "
						+ "Einheiten geplant gegen gefahren. Damit lässt sich erklären, warum der Plan so aussieht.",
						schema(Map.of()), OAuthService.SCOPE_READ),
				new Tool("get_rides", "Gefahrene Einheiten und Zusatzfahrten im Zeitraum (Standard: die letzten 28 Tage): "
						+ "Dauer, Distanz, Belastung, Sportart, Zuordnung zum Plan. Keine Strava-Daten im Detail.",
						schema(Map.of("from", text("Erster Tag, JJJJ-MM-TT"), "to", text("Letzter Tag, JJJJ-MM-TT"))),
						OAuthService.SCOPE_READ),
				new Tool("list_workouts", "Die Workout-Bibliothek: Titel, Art, Dauer, Belastung und Beschreibung. Optional "
						+ "nach Art gefiltert (z. B. SWEET_SPOT, THRESHOLD, VO2MAX, ENDURANCE, LONG_RIDE).",
						schema(Map.of("type", text("Trainingsart, z. B. THRESHOLD"))), OAuthService.SCOPE_READ));
	}

	/** Führt ein Werkzeug für einen Nutzer aus; Ergebnis ist eine JSON-fähige Struktur */
	public Object call(Long userId, String name, Map<String, Object> arguments) {
		return switch (name) {
			case "get_overview" -> overview(userId);
			case "get_plan" -> plan(userId, date(arguments, "from", today()), date(arguments, "to", today().plusDays(14)));
			case "get_diagnosis" -> diagnosis(userId);
			case "get_rides" -> rides(userId, date(arguments, "from", today().minusDays(28)),
					date(arguments, "to", today()));
			case "list_workouts" -> workouts(arguments == null ? null : (String) arguments.get("type"));
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
			profile.put("level", String.valueOf(athlete.getLevel()));
			profile.put("ftpWatt", athlete.getFtp());
			profile.put("gewichtKg", athlete.getWeightKg());
			profile.put("ziel", String.valueOf(athlete.getTrainingGoal()));
			profile.put("trainingsmodus", String.valueOf(athlete.getTrainingMode()));
			if (athlete.getEvent() != null) {
				var event = athlete.getEvent();
				Map<String, Object> e = new LinkedHashMap<>();
				e.put("name", event.getName());
				e.put("datum", String.valueOf(event.getDate()));
				e.put("distanzKm", event.getDistanceKm());
				e.put("hoehenmeter", event.getElevationMeters());
				e.put("art", String.valueOf(event.getType()));
				e.put("ziel", String.valueOf(event.getGoal()));
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
				out.put("planStart", String.valueOf(plan.get().getStartDate()));
				out.put("krankSeit", plan.get().getSickSince() == null ? null : plan.get().getSickSince().toString());
				out.put("naechste7Tage", days(plan.get(), today, today.plusDays(6)));
			} else {
				out.put("plan", "Es gibt noch keinen Plan.");
			}
			return out;
		});
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

	private Object diagnosis(Long userId) {
		Athlete athlete = athleteOf(userId);
		return tx.execute(status -> {
			TrainingPlan plan = plans.findByAthlete(athlete.getId())
					.orElseThrow(() -> new ToolException("Es gibt noch keinen Plan."));
			return plans.diagnosis(plan);
		});
	}

	private Object rides(Long userId, LocalDate from, LocalDate to) {
		checkRange(from, to);
		Athlete athlete = athleteOf(userId);
		return tx.execute(status -> {
			List<Map<String, Object>> planned = new ArrayList<>();
			plans.findByAthlete(athlete.getId()).ifPresent(plan -> plan.getWorkouts().stream()
					.filter(workout -> !workout.getDate().isBefore(from) && !workout.getDate().isAfter(to))
					.filter(workout -> workout.getActivityId() != null || workout.isCompleted())
					.forEach(workout -> {
						Map<String, Object> row = new LinkedHashMap<>();
						Workout resolved = library.resolve(workout.getWorkoutId(), workout.getDurationMinutes())
								.orElseThrow();
						row.put("tag", workout.getDate().toString());
						row.put("einheit", resolved.getTitle());
						row.put("geplantMinuten", resolved.getDurationInMinutes());
						row.put("geplantTss", resolved.getTss());
						row.put("gefahrenMinuten", workout.getActualDurationMinutes());
						row.put("gefahrenTss", workout.getActualTss());
						row.put("distanzKm", workout.getActualDistanceKm());
						row.put("bewertung", workout.getRating());
						planned.add(row);
					}));
			List<Map<String, Object>> extra = new ArrayList<>();
			for (ExtraRide ride : extraRides.findByAthleteIdAndDateBetween(athlete.getId(), from, to)) {
				Map<String, Object> row = new LinkedHashMap<>();
				row.put("tag", String.valueOf(ride.getDate()));
				row.put("sportart", String.valueOf(ride.getSport()));
				row.put("minuten", ride.getDurationMinutes());
				row.put("distanzKm", ride.getDistanceKm());
				row.put("tss", ride.getTss());
				row.put("tssAusPuls", ride.isTssFromHeartRate());
				extra.add(row);
			}
			Map<String, Object> out = new LinkedHashMap<>();
			out.put("von", from.toString());
			out.put("bis", to.toString());
			out.put("geplanteEinheiten", planned);
			out.put("zusatzfahrten", extra);
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
		List<Map<String, Object>> rows = all.stream().filter(workout -> wanted == null || workout.getType() == wanted)
				.map(workout -> {
					Map<String, Object> row = new LinkedHashMap<>();
					row.put("id", workout.getId());
					row.put("titel", workout.getTitle());
					row.put("art", workout.getType().name());
					row.put("dauerMinuten", workout.getDurationInMinutes());
					row.put("tss", workout.getTss());
					row.put("beschreibung", workout.getDescription());
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

	private Map<String, Object> day(PlannedWorkout planned) {
		Workout workout = library.resolve(planned.getWorkoutId(), planned.getDurationMinutes()).orElseThrow();
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("tag", planned.getDate().toString());
		row.put("wochentag", planned.getDate().getDayOfWeek().toString());
		row.put("planId", planned.getId());
		row.put("phase", String.valueOf(planned.getPhase()));
		row.put("einheit", workout.getTitle());
		row.put("art", workout.getType().name());
		row.put("minuten", workout.getDurationInMinutes());
		row.put("tss", workout.getTss());
		row.put("status", planned.getSkipReason() != null ? "ausgefallen (" + planned.getSkipReason() + ")"
				: planned.isCompleted() ? "erledigt" : "offen");
		if (planned.getAdjustmentNote() != null) {
			row.put("anpassung", planned.getAdjustmentNote());
		}
		if (planned.getActualDurationMinutes() != null || planned.getActualTss() != null) {
			row.put("gefahrenMinuten", planned.getActualDurationMinutes());
			row.put("gefahrenTss", planned.getActualTss());
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
}
