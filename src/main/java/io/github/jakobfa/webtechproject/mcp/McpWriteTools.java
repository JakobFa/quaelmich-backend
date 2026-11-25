package io.github.jakobfa.webtechproject.mcp;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.jakobfa.webtechproject.custom.CustomWorkoutService;
import io.github.jakobfa.webtechproject.integration.intervals.IntervalsAutoSyncService;
import io.github.jakobfa.webtechproject.integration.intervals.IntervalsFtpSync;
import io.github.jakobfa.webtechproject.model.Athlete;
import io.github.jakobfa.webtechproject.model.PlannedWorkout;
import io.github.jakobfa.webtechproject.model.RaceEvent;
import io.github.jakobfa.webtechproject.model.SkipReason;
import io.github.jakobfa.webtechproject.model.StepKind;
import io.github.jakobfa.webtechproject.model.TrainingPlan;
import io.github.jakobfa.webtechproject.model.WeeklyAvailability;
import io.github.jakobfa.webtechproject.model.Workout;
import io.github.jakobfa.webtechproject.model.WorkoutStep;
import io.github.jakobfa.webtechproject.model.WorkoutType;
import io.github.jakobfa.webtechproject.service.AbsenceService;
import io.github.jakobfa.webtechproject.service.AthleteService;
import io.github.jakobfa.webtechproject.service.PlanEditService;
import io.github.jakobfa.webtechproject.service.RaceEventService;
import io.github.jakobfa.webtechproject.service.TrainingPlanService;
import io.github.jakobfa.webtechproject.service.WorkoutCustomizationService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Die Werkzeuge, mit denen eine KI-App den Plan und das Profil ändern darf (Bereich plan:write). Jede Änderung
 * bestätigt der Nutzer in seiner KI-App, bevor das Werkzeug läuft; quaelmich führt sie dann direkt aus. Die Antworten
 * zeigen nur, was geändert wurde (Plan-Tage im selben Format wie get_plan, Profil wie get_overview), nie gefahrene
 * Werte oder Strava-Daten.
 */
@Component
public class McpWriteTools {

	private static final Set<String> PROFILE_FIELDS = Set.of("ftp", "weightKg", "level", "trainingGoal",
			"trainingMode", "maxHeartRate", "thresholdHeartRate", "availability", "event");

	private final McpTools read;
	private final AthleteService athletes;
	private final TrainingPlanService plans;
	private final AbsenceService absence;
	private final WorkoutCustomizationService customization;
	private final IntervalsAutoSyncService autoSync;
	private final IntervalsFtpSync ftpSync;
	private final CustomWorkoutService customWorkouts;
	private final PlanEditService planEdit;
	private final RaceEventService raceEvents;
	private final ObjectMapper mapper;
	private final Validator validator;
	private final TransactionTemplate tx;

	public McpWriteTools(McpTools read, AthleteService athletes, TrainingPlanService plans, AbsenceService absence,
			WorkoutCustomizationService customization, IntervalsAutoSyncService autoSync, IntervalsFtpSync ftpSync,
			CustomWorkoutService customWorkouts, PlanEditService planEdit, RaceEventService raceEvents,
			ObjectMapper mapper, Validator validator, TransactionTemplate tx) {
		this.read = read;
		this.athletes = athletes;
		this.plans = plans;
		this.absence = absence;
		this.customization = customization;
		this.autoSync = autoSync;
		this.ftpSync = ftpSync;
		this.customWorkouts = customWorkouts;
		this.planEdit = planEdit;
		this.raceEvents = raceEvents;
		this.mapper = mapper;
		this.validator = validator;
		this.tx = tx;
	}

	// ---------- Beschreibung der Werkzeuge ----------

	private static Map<String, Object> prop(String type, String description) {
		return Map.of("type", type, "description", description);
	}

	private static Map<String, Object> object(Map<String, Object> properties, String... required) {
		Map<String, Object> schema = new LinkedHashMap<>();
		schema.put("type", "object");
		schema.put("properties", properties);
		schema.put("required", List.of(required));
		schema.put("additionalProperties", false);
		return schema;
	}

	private static Map<String, Object> hours(String description) {
		Map<String, Object> days = new LinkedHashMap<>();
		for (String day : List.of("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")) {
			days.put(day, prop("number", "Stunden an diesem Tag (0 = frei)"));
		}
		return Map.of("type", "object", "description", description, "properties", days);
	}

	private static McpTools.Tool tool(String name, String description, Map<String, Object> schema, boolean destructive) {
		return new McpTools.Tool(name, description, schema, OAuthService.SCOPE_WRITE,
				Map.of("readOnlyHint", false, "destructiveHint", destructive, "idempotentHint", false));
	}

	private static Map<String, Object> workoutProperties(boolean withId) {
		Map<String, Object> properties = new LinkedHashMap<>();
		if (withId) {
			properties.put("workoutId", prop("integer", "Die id aus list_my_workouts"));
		}
		properties.put("title", prop("string", "Name, höchstens 80 Zeichen"));
		properties.put("description", prop("string", "Beschreibung, höchstens 500 Zeichen, optional"));
		properties.put("type", prop("string",
				"Art, zum Beispiel ENDURANCE, SWEET_SPOT, THRESHOLD, VO2MAX (siehe list_workouts)"));
		properties.put("steps", Map.of("type", "array", "description", "Abschnitte in Reihenfolge, 1 bis 100", "items",
				object(Map.of("kind", prop("string", "WARMUP, STEADY, INTERVAL, RECOVERY oder COOLDOWN"),
						"minutes", prop("number", "Dauer in Minuten (mindestens 10 Sekunden, höchstens 12 Stunden)"),
						"powerStartPercent", prop("integer", "Leistung am Anfang in % der FTP"),
						"powerEndPercent", prop("integer", "Leistung am Ende in % der FTP, optional (sonst gleich dem Anfang)")),
						"kind", "minutes", "powerStartPercent")));
		return properties;
	}

	private static Map<String, Object> eventProperties(boolean withId) {
		Map<String, Object> properties = new LinkedHashMap<>();
		if (withId) {
			properties.put("eventId", prop("integer", "Die id aus list_events"));
		}
		properties.put("name", prop("string", "Name des Events"));
		properties.put("date", prop("string", "Datum JJJJ-MM-TT (heute oder später)"));
		properties.put("distanceKm", prop("number", "Distanz in km"));
		properties.put("elevationMeters", prop("integer", "Höhenmeter"));
		properties.put("type", prop("string",
				"GRAN_FONDO, ROAD_RACE, CRITERIUM, TIME_TRIAL, GRAVEL, ULTRA oder BIKEPACKING"));
		properties.put("goal", prop("string", "FINISH (ankommen) oder COMPETE (gute Zeit)"));
		return properties;
	}

	/** Alle Schreibwerkzeuge (die Aufrufer filtern nach ihrem Zugriffsbereich) */
	public List<McpTools.Tool> tools() {
		Map<String, Object> event = new LinkedHashMap<>();
		event.put("type", "object");
		event.put("description", "Das Ziel-Event; null entfernt es (dann muss trainingGoal ein anderes Ziel sein)");
		event.put("properties", Map.of("name", prop("string", "Name des Events"),
				"date", prop("string", "Datum JJJJ-MM-TT"), "distanceKm", prop("number", "Distanz in km"),
				"elevationMeters", prop("integer", "Höhenmeter"),
				"type", prop("string", "GRAN_FONDO, ROAD_RACE, CRITERIUM, TIME_TRIAL, GRAVEL, ULTRA oder BIKEPACKING"),
				"goal", prop("string", "FINISH (ankommen) oder COMPETE (gute Zeit) – siehe get_overview")));
		Map<String, Object> profile = new LinkedHashMap<>();
		profile.put("ftp", prop("integer", "FTP in Watt (50 bis 600)"));
		profile.put("weightKg", prop("number", "Gewicht in kg"));
		profile.put("level", prop("string", "BEGINNER, HOBBY, AMBITIOUS oder PERFORMANCE"));
		profile.put("trainingGoal", prop("string", "Sportliches Ziel: EVENT, FTP, ENDURANCE, FITNESS oder CLIMBING"));
		profile.put("trainingMode", prop("string", "POWER (nach Watt) oder HEART_RATE (nach Puls)"));
		profile.put("maxHeartRate", prop("integer", "Maximalpuls (120 bis 230)"));
		profile.put("thresholdHeartRate", prop("integer", "Schwellenpuls (100 bis 220)"));
		profile.put("availability", hours("Zeit pro Wochentag in Stunden (normale Woche)"));
		profile.put("event", event);
		return List.of(
				tool("update_profile", "Ändert das Profil: nur die angegebenen Felder, alles andere bleibt. Dazu gehören das "
						+ "sportliche Ziel, das Event, die FTP und die Zeit pro Wochentag. Ändert noch nicht den Plan: danach "
						+ "regenerate_plan aufrufen, wenn der Plan zur Änderung passen soll.", object(profile), true),
				tool("regenerate_plan", "Erstellt den ganzen Trainingsplan neu aus dem aktuellen Profil (Ziel, Event, "
						+ "Zeit, FTP). Der bisherige Plan wird ersetzt, Erledigtes bleibt erhalten.", object(Map.of()), true),
				tool("skip_workout", "Markiert eine Einheit als nicht gemacht (mit Grund). Bei krank oder verletzt wird das "
						+ "nächste harte Training sanfter. planId aus get_plan.",
						object(Map.of("planId", prop("integer", "Die planId der Einheit"),
								"reason", prop("string", "NO_TIME, SICK, TIRED, INJURED oder OTHER")), "planId", "reason"),
						false),
				tool("undo_skip", "Nimmt 'nicht gemacht' für eine Einheit zurück.",
						object(Map.of("planId", prop("integer", "Die planId der Einheit")), "planId"), false),
				tool("adjust_workout", "Passt eine Einheit an: leichter, härter, kürzer oder länger.",
						object(Map.of("planId", prop("integer", "Die planId der Einheit"),
								"direction", prop("string", "EASIER, HARDER, SHORTER oder LONGER")), "planId", "direction"),
						false),
				tool("reset_adjustment", "Setzt eine angepasste oder getauschte Einheit auf das geplante Workout zurück.",
						object(Map.of("planId", prop("integer", "Die planId der Einheit")), "planId"), false),
				tool("replace_workout", "Tauscht eine Einheit gegen ein Workout aus der Bibliothek (list_workouts). Nur für "
						+ "Einheiten, die noch nicht erledigt sind.",
						object(Map.of("planId", prop("integer", "Die planId der Einheit"),
								"workoutId", prop("integer", "Die id aus list_workouts"),
								"durationMinutes", prop("integer", "Dauer in Minuten, nur bei Workouts mit variabler Dauer")),
								"planId", "workoutId"),
						false),
				tool("create_workout", "Legt ein eigenes Workout für den Nutzer an (erscheint in seiner Liste eigener Workouts "
						+ "und im Workout-Builder). Danach mit replace_workout oder add_workout in den Plan setzen. Leistung "
						+ "in Prozent der FTP (30 bis 250); unterschiedliche Start- und Endwerte ergeben eine Rampe. Höchstens "
						+ "12 Stunden, kein FTP-Test.", object(workoutProperties(false), "title", "type", "steps"), false),
				tool("update_workout", "Ändert ein eigenes Workout (alle Angaben wie bei create_workout, id aus "
						+ "list_my_workouts). Gilt auch für Tage im Plan, an denen es schon steht.",
						object(workoutProperties(true), "workoutId", "title", "type", "steps"), true),
				tool("delete_workout", "Löscht ein eigenes Workout aus der Liste. Tage im Plan, an denen es steht, bleiben "
						+ "lesbar.", object(Map.of("workoutId", prop("integer", "Die id aus list_my_workouts")), "workoutId"),
						true),
				tool("add_workout", "Setzt eine Einheit an einen beliebigen Tag (heute bis ein Jahr voraus), auch an einen "
						+ "freien Tag. Workout aus list_workouts oder list_my_workouts. Eine offene oder selbst gewählte "
						+ "Einheit an diesem Tag weicht (eine erledigte, gefahrene oder ausgefallene nicht); die neue bleibt "
						+ "stehen, auch wenn der Plan neu verteilt wird.",
						object(Map.of("date", prop("string", "Tag JJJJ-MM-TT"),
								"workoutId", prop("integer", "Die id des Workouts"),
								"durationMinutes", prop("integer", "Dauer in Minuten (Vielfache von 15), nur bei Workouts mit variabler Dauer; Grundlage ab 30")),
								"date", "workoutId"),
						false),
				tool("move_workout", "Schiebt eine offene Einheit auf einen anderen Tag (heute oder später). Der alte Tag "
						+ "bleibt leer, bis der Plan neu verteilt wird.",
						object(Map.of("planId", prop("integer", "Die planId der Einheit"),
								"date", prop("string", "Neuer Tag JJJJ-MM-TT")), "planId", "date"),
						false),
				tool("remove_workout", "Entfernt eine offene Einheit (freier Tag). Eine Neuberechnung des Plans (zum Beispiel "
						+ "regenerate_plan) kann den Tag wieder füllen; für einen festen freien Tag set_day_free "
						+ "benutzen.",
						object(Map.of("planId", prop("integer", "Die planId der Einheit")), "planId"), true),
				tool("set_day_free", "Hält einen einzelnen Tag fest frei (free=true) oder plant ihn wieder wie im Profil "
						+ "(free=false). Gilt für diesen Tag, bleibt bei jeder Neuberechnung bestehen; die anderen Tage der "
						+ "Woche bleiben unverändert. Eine selbst gewählte Einheit an dem Tag zuerst mit remove_workout entfernen.",
						object(Map.of("date", prop("string", "Tag JJJJ-MM-TT (heute oder später)"),
								"free", prop("boolean", "true = frei halten, false = wieder wie im Profil")), "date", "free"),
						false),
				tool("shuffle_workout", "Tauscht eine offene Einheit gegen ein zufälliges anderes Workout, das in die Zeit "
						+ "des Tages passt und gleich hart ist.",
						object(Map.of("planId", prop("integer", "Die planId der Einheit")), "planId"), false),
				tool("add_event", "Legt ein weiteres Event an (zusätzlich zum ersten im Profil, höchstens 12, pro Tag eins). "
						+ "Der Plan wird danach ab heute neu verteilt.", object(eventProperties(false), "name", "date",
								"distanceKm", "elevationMeters", "type"), true),
				tool("update_event", "Ändert ein weiteres Event (id aus list_events). Der Plan wird danach ab heute neu "
						+ "verteilt.", object(eventProperties(true), "eventId", "name", "date", "distanceKm",
								"elevationMeters", "type"), true),
				tool("delete_event", "Löscht ein weiteres Event (id aus list_events). Der Plan wird danach ab heute neu "
						+ "verteilt.", object(Map.of("eventId", prop("integer", "Die id aus list_events")), "eventId"), true),
				tool("report_sick", "Meldet Krankheit (oder Verletzung). Ohne until bleibt der Plan stehen, bis "
						+ "report_healthy kommt; from darf bis zu 8 Wochen zurückliegen. Mit until ein abgeschlossener "
						+ "Zeitraum.",
						object(Map.of("from", prop("string", "Beginn JJJJ-MM-TT"),
								"until", prop("string", "Letzter kranker Tag JJJJ-MM-TT, optional")), "from"),
						false),
				tool("report_healthy", "Meldet 'wieder gesund': ohne since ab heute, sonst 'seit diesem Tag' (auch für eine "
						+ "schon beendete Krankheit). Die Tage davor bleiben krank.",
						object(Map.of("since", prop("string", "Gesund seit JJJJ-MM-TT, optional"))), false),
				tool("pause_plan", "Pausiert den Plan (z. B. Urlaub), höchstens 8 Wochen.",
						object(Map.of("from", prop("string", "Erster Pausentag JJJJ-MM-TT"),
								"until", prop("string", "Letzter Pausentag JJJJ-MM-TT")), "from", "until"),
						false),
				tool("resume_plan", "Beendet die Pause: ab heute gilt der Plan wieder.", object(Map.of()), false),
				tool("set_week_availability", "Andere Zeit nur für eine Woche (ab heute neu verteilt). Ohne hours wird die "
						+ "Ausnahme entfernt und die normale Zeit gilt wieder.",
						object(Map.of("monday", prop("string", "Montag der Woche JJJJ-MM-TT"),
								"hours", hours("Stunden pro Wochentag, fehlt = Ausnahme entfernen")), "monday"),
						false));
	}

	public boolean handles(String name) {
		return tools().stream().anyMatch(tool -> tool.name().equals(name));
	}

	// ---------- Ausführen ----------

	/** Führt ein Schreibwerkzeug für einen Nutzer aus; Ergebnis ist eine JSON-fähige Struktur */
	public Object call(Long userId, String name, Map<String, Object> arguments) {
		Map<String, Object> args = arguments == null ? Map.of() : arguments;
		Object result = switch (name) {
			case "update_profile" -> updateProfile(userId, args);
			case "regenerate_plan" -> regeneratePlan(userId);
			case "skip_workout" -> workoutResult(userId, "Als nicht gemacht markiert.", planId(args), id -> {
				SkipReason reason = enumOf(SkipReason.class, text(args, "reason"), "reason");
				if (reason == SkipReason.PAUSE) {
					throw new McpTools.ToolException("Für eine Pause pause_plan benutzen");
				}
				return absence.skip(id, userId, reason);
			});
			case "undo_skip" -> workoutResult(userId, "Nicht gemacht zurückgenommen.", planId(args),
					id -> absence.undoSkip(id, userId));
			case "adjust_workout" -> workoutResult(userId, "Einheit angepasst.", planId(args), id -> customization
					.adjust(id, userId, enumOf(WorkoutCustomizationService.Direction.class, text(args, "direction"),
							"direction")));
			case "reset_adjustment" -> workoutResult(userId, "Zurück zum geplanten Workout.", planId(args),
					id -> customization.reset(id, userId));
			case "replace_workout" -> workoutResult(userId, "Einheit getauscht.", planId(args),
					id -> customization.replace(id, userId, number(args, "workoutId", true),
							args.get("durationMinutes") == null ? null : number(args, "durationMinutes", true).intValue()));
			case "create_workout" -> createWorkout(userId, args);
			case "update_workout" -> updateWorkout(userId, args);
			case "delete_workout" -> deleteWorkout(userId, args);
			case "add_workout" -> editResult("Einheit eingeplant.", () -> planEdit.add(userId, date(args, "date", true),
					number(args, "workoutId", true), args.get("durationMinutes") == null ? null
							: number(args, "durationMinutes", true).intValue()));
			case "set_day_free" -> {
				LocalDate day = date(args, "date", true);
				if (!(args.get("free") instanceof Boolean free)) {
					throw new McpTools.ToolException("'free' muss true oder false sein");
				}
				try {
					yield planResult(userId, "Tag aktualisiert.", () -> planEdit.setDayFree(userId, day, free));
				} catch (WorkoutCustomizationService.NotPossibleException e) {
					throw new McpTools.ToolException("Der Tag muss heute oder später sein (höchstens ein Jahr voraus).");
				}
			}
			case "move_workout" -> editResult("Einheit verschoben.", () -> planEdit.move(userId, planId(args),
					date(args, "date", true)));
			case "remove_workout" -> removeWorkout(userId, args);
			case "shuffle_workout" -> workoutResult(userId, "Einheit getauscht.", planId(args),
					id -> customization.shuffle(id, userId));
			case "add_event", "update_event" -> saveEvent(userId, name, args);
			case "delete_event" -> deleteEvent(userId, args);
			case "report_sick" -> planResult(userId, "Krank gemeldet.", () -> absence.startSickness(userId,
					date(args, "from", true), date(args, "until", false)));
			case "report_healthy" -> planResult(userId, "Wieder gesund gemeldet.", () -> {
				LocalDate since = date(args, "since", false);
				return since == null ? absence.endSickness(userId, null) : absence.healthySince(userId, since);
			});
			case "pause_plan" -> planResult(userId, "Plan pausiert.",
					() -> absence.pause(userId, date(args, "from", true), date(args, "until", true)));
			case "resume_plan" -> planResult(userId, "Pause beendet.", () -> absence.resume(userId));
			case "set_week_availability" -> setWeekAvailability(userId, args);
			default -> throw new McpTools.ToolException("Unbekanntes Werkzeug: " + name);
		};
		autoSync.syncUserIfEnabled(userId); // wer die Anbindung an intervals.icu eingeschaltet hat, bekommt den neuen Plan
		return result;
	}

	private Object updateProfile(Long userId, Map<String, Object> args) {
		Map<String, Object> unknown = new LinkedHashMap<>(args);
		unknown.keySet().removeAll(PROFILE_FIELDS);
		if (!unknown.isEmpty()) {
			throw new McpTools.ToolException("Unbekannte Felder: " + unknown.keySet());
		}
		if (args.isEmpty()) {
			throw new McpTools.ToolException("Keine Änderung angegeben");
		}
		Athlete existing = athletes.findByOwner(userId)
				.orElseThrow(() -> new McpTools.ToolException("Es gibt noch kein Profil."));
		Integer oldFtp = existing.getFtp();
		@SuppressWarnings("unchecked")
		Map<String, Object> merged = mapper.convertValue(existing, Map.class);
		args.forEach((key, value) -> {
			if (value instanceof Map<?, ?> changes && merged.get(key) instanceof Map<?, ?> current) {
				Map<String, Object> both = new LinkedHashMap<>();
				current.forEach((k, v) -> both.put(String.valueOf(k), v));
				changes.forEach((k, v) -> both.put(String.valueOf(k), v));
				merged.put(key, both);
			} else {
				merged.put(key, value);
			}
		});
		Athlete changed;
		try {
			changed = mapper.convertValue(merged, Athlete.class);
		} catch (JacksonException | IllegalArgumentException e) {
			throw new McpTools.ToolException("Ungültiger Wert im Profil (zum Beispiel eine unbekannte Auswahl).");
		}
		List<String> problems = validator.validate(changed).stream().map(ConstraintViolation::getMessage)
				.sorted().collect(Collectors.toList());
		if (!problems.isEmpty()) {
			throw new McpTools.ToolException("Das Profil wäre ungültig: " + String.join("; ", problems));
		}
		Athlete saved = athletes.saveForOwner(userId, changed);
		if (saved.getFtp() != null && !saved.getFtp().equals(oldFtp)) {
			ftpSync.pushFtp(userId, saved.getFtp());
		}
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("erledigt", "Profil geändert. Der Plan ist noch der alte: bei Änderungen an Ziel, Event, Zeit oder FTP "
				+ "regenerate_plan aufrufen.");
		out.put("profil", read.call(userId, "get_overview", Map.of()));
		return out;
	}

	/** Die Angaben eines eigenen Workouts, geprüft */
	private record WorkoutSpec(String title, String description, WorkoutType type, List<WorkoutStep> steps) {
	}

	private WorkoutSpec workoutSpec(Map<String, Object> args) {
		String title = text(args, "title");
		if (title == null || title.isBlank() || title.strip().length() > 80) {
			throw new McpTools.ToolException("'title' fehlt oder ist länger als 80 Zeichen");
		}
		String description = args.get("description") == null ? null : args.get("description").toString();
		if (description != null && description.length() > 500) {
			throw new McpTools.ToolException("'description' ist länger als 500 Zeichen");
		}
		WorkoutType type = enumOf(WorkoutType.class, text(args, "type"), "type");
		if (!(args.get("steps") instanceof List<?> rawSteps) || rawSteps.isEmpty() || rawSteps.size() > 100) {
			throw new McpTools.ToolException("'steps' braucht 1 bis 100 Abschnitte");
		}
		List<WorkoutStep> steps = rawSteps.stream().map(raw -> {
			if (!(raw instanceof Map<?, ?> step)) {
				throw new McpTools.ToolException("Jeder Abschnitt braucht kind, minutes und powerStartPercent");
			}
			@SuppressWarnings("unchecked")
			Map<String, Object> values = (Map<String, Object>) step;
			StepKind kind = enumOf(StepKind.class, text(values, "kind"), "kind");
			if (!(values.get("minutes") instanceof Number minutes)) {
				throw new McpTools.ToolException("'minutes' fehlt");
			}
			int seconds = (int) Math.round(minutes.doubleValue() * 60);
			int start = percent(values, "powerStartPercent", null);
			int end = percent(values, "powerEndPercent", start);
			if (seconds < 10 || seconds > 12 * 3600) {
				throw new McpTools.ToolException("Ein Abschnitt dauert 10 Sekunden bis 12 Stunden");
			}
			return new WorkoutStep(kind, seconds, start, end);
		}).toList();
		return new WorkoutSpec(title, description, type, steps);
	}

	private Object createWorkout(Long userId, Map<String, Object> args) {
		WorkoutSpec spec = workoutSpec(args);
		try {
			Workout created = customWorkouts.create(userId, spec.title(), spec.description(), spec.type(), spec.steps());
			return Map.of("erledigt", "Workout angelegt. Mit replace_workout oder add_workout und dieser id in den Plan "
					+ "setzen.", "workout", McpTools.workoutRows(List.of(created), true).get("workouts"));
		} catch (CustomWorkoutService.InvalidWorkoutException e) {
			throw new McpTools.ToolException(e.getMessage());
		}
	}

	private Object updateWorkout(Long userId, Map<String, Object> args) {
		WorkoutSpec spec = workoutSpec(args);
		try {
			Workout changed = customWorkouts.update(number(args, "workoutId", true), userId, spec.title(),
					spec.description(), spec.type(), spec.steps())
					.orElseThrow(() -> new McpTools.ToolException("Kein eigenes Workout mit dieser id"));
			return Map.of("erledigt", "Workout geändert.", "workout",
					McpTools.workoutRows(List.of(changed), true).get("workouts"));
		} catch (CustomWorkoutService.InvalidWorkoutException e) {
			throw new McpTools.ToolException(e.getMessage());
		}
	}

	private Object deleteWorkout(Long userId, Map<String, Object> args) {
		if (!customWorkouts.delete(number(args, "workoutId", true), userId)) {
			throw new McpTools.ToolException("Kein eigenes Workout mit dieser id");
		}
		return Map.of("erledigt", "Workout gelöscht.");
	}

	private Object editResult(String done, java.util.function.Supplier<java.util.Optional<PlannedWorkout>> action) {
		try {
			return tx.execute(status -> {
				PlannedWorkout changed = action.get()
						.orElseThrow(() -> new McpTools.ToolException("Es gibt noch keinen Plan oder keine solche Einheit."));
				return Map.of("erledigt", done, "einheit", read.day(changed));
			});
		} catch (WorkoutCustomizationService.NotPossibleException e) {
			throw new McpTools.ToolException("Das geht nicht: Der Tag muss heute oder später sein (höchstens ein Jahr "
					+ "voraus), das Workout muss es geben (und gehören dir, wenn es ein eigenes ist), die Dauer muss zum "
					+ "Workout passen, und an dem Tag darf keine abgehakte, gefahrene, ausgefallene oder selbst gewählte "
					+ "Einheit stehen. Eine Einheit, die schon erledigt ist, lässt sich nicht verschieben.");
		}
	}

	private Object removeWorkout(Long userId, Map<String, Object> args) {
		try {
			LocalDate day = tx.execute(status -> planEdit.remove(userId, planId(args))
					.orElseThrow(() -> new McpTools.ToolException("Keine Einheit mit dieser planId: " + planId(args))));
			return Map.of("erledigt", "Einheit entfernt.", "tag", day.toString());
		} catch (WorkoutCustomizationService.NotPossibleException e) {
			throw new McpTools.ToolException("Das geht nur bei einer offenen Einheit ab heute (nicht erledigt, nicht "
					+ "gefahren, nicht ausgefallen).");
		}
	}

	private Object saveEvent(Long userId, String tool, Map<String, Object> args) {
		Map<String, Object> values = new LinkedHashMap<>(args);
		Long eventId = "update_event".equals(tool) ? number(args, "eventId", true) : null;
		values.remove("eventId");
		RaceEvent event;
		try {
			event = mapper.convertValue(values, RaceEvent.class);
		} catch (JacksonException | IllegalArgumentException e) {
			throw new McpTools.ToolException("Ungültiger Wert im Event (zum Beispiel eine unbekannte Auswahl oder ein "
					+ "Datum ohne Format JJJJ-MM-TT).");
		}
		List<String> problems = validator.validate(event).stream().map(ConstraintViolation::getMessage).sorted().toList();
		if (!problems.isEmpty()) {
			throw new McpTools.ToolException("Das Event wäre ungültig: " + String.join("; ", problems));
		}
		try {
			RaceEvent saved = (eventId == null ? raceEvents.add(userId, event) : raceEvents.update(userId, eventId, event))
					.orElseThrow(() -> new McpTools.ToolException(eventId == null ? "Es gibt noch kein Profil."
							: "Kein Event mit dieser id"));
			return Map.of("erledigt", eventId == null ? "Event angelegt. Der Plan wird neu verteilt."
					: "Event geändert. Der Plan wird neu verteilt.", "event", McpTools.eventRow(saved));
		} catch (RaceEventService.ConflictException e) {
			throw new McpTools.ToolException("An diesem Tag ist schon ein Event, oder es gibt schon zu viele (höchstens 12).");
		}
	}

	private Object deleteEvent(Long userId, Map<String, Object> args) {
		if (!raceEvents.delete(userId, number(args, "eventId", true))) {
			throw new McpTools.ToolException("Kein Event mit dieser id");
		}
		return Map.of("erledigt", "Event gelöscht. Der Plan wird neu verteilt.");
	}

	private static int percent(Map<String, Object> values, String key, Integer fallback) {
		Object value = values.get(key);
		if (value == null && fallback != null) {
			return fallback;
		}
		if (!(value instanceof Number number) || number.intValue() < 30 || number.intValue() > 250) {
			throw new McpTools.ToolException("'" + key + "' muss zwischen 30 und 250 liegen");
		}
		return number.intValue();
	}

	private Object regeneratePlan(Long userId) {
		Athlete athlete = athletes.findByOwner(userId)
				.orElseThrow(() -> new McpTools.ToolException("Es gibt noch kein Profil."));
		TrainingPlan plan = plans.generate(athlete.getId())
				.orElseThrow(() -> new McpTools.ToolException("Es gibt noch kein Profil."));
		return Map.of("erledigt", "Plan neu erstellt.", "naechste7Tage", read.nextDays(plan));
	}

	private Object setWeekAvailability(Long userId, Map<String, Object> args) {
		LocalDate monday = date(args, "monday", true);
		WeeklyAvailability availability = null;
		if (args.get("hours") != null) {
			try {
				availability = mapper.convertValue(args.get("hours"), WeeklyAvailability.class);
			} catch (JacksonException | IllegalArgumentException e) {
				throw new McpTools.ToolException("Ungültige Stunden");
			}
			if (!validator.validate(availability).isEmpty()) {
				throw new McpTools.ToolException("Ungültige Stunden (0 bis 12 pro Tag)");
			}
		}
		Athlete athlete = athletes.findByOwner(userId)
				.orElseThrow(() -> new McpTools.ToolException("Es gibt noch kein Profil."));
		WeeklyAvailability week = availability;
		return planResult(userId, week == null ? "Ausnahme für die Woche entfernt." : "Zeit für die Woche geändert.", () -> {
			try {
				return plans.setWeekAvailability(athlete.getId(), monday, week);
			} catch (IllegalArgumentException e) {
				throw new McpTools.ToolException("Das ist kein Montag oder die Woche ist schon vorbei.");
			}
		});
	}

	// ---------- Hilfen ----------

	private Object workoutResult(Long userId, String done, Long planId,
			java.util.function.Function<Long, java.util.Optional<PlannedWorkout>> action) {
		try {
			return tx.execute(status -> {
				PlannedWorkout changed = action.apply(planId)
						.orElseThrow(() -> new McpTools.ToolException("Keine Einheit mit dieser planId: " + planId));
				return Map.of("erledigt", done, "einheit", read.day(changed));
			});
		} catch (AbsenceService.NotPossibleException | WorkoutCustomizationService.NotPossibleException e) {
			throw new McpTools.ToolException("Das geht bei dieser Einheit gerade nicht (zum Beispiel weil sie schon "
					+ "erledigt ist oder nicht weiter angepasst werden kann).");
		}
	}

	private Object planResult(Long userId, String done,
			java.util.function.Supplier<java.util.Optional<TrainingPlan>> action) {
		try {
			TrainingPlan plan = tx.execute(status -> action.get()
					.orElseThrow(() -> new McpTools.ToolException("Es gibt noch keinen Plan.")));
			return Map.of("erledigt", done, "naechste7Tage", read.nextDays(plan));
		} catch (AbsenceService.NotPossibleException e) {
			throw new McpTools.ToolException("Das geht so nicht (zum Beispiel ein Datum in der Zukunft oder zu weit "
					+ "zurück, eine schon laufende Krankheit oder gar keine Krankheit).");
		}
	}

	private static Long planId(Map<String, Object> args) {
		return number(args, "planId", true);
	}

	private static Long number(Map<String, Object> args, String key, boolean required) {
		Object value = args.get(key);
		if (value == null) {
			if (required) {
				throw new McpTools.ToolException("'" + key + "' fehlt");
			}
			return null;
		}
		if (value instanceof Number n) {
			return n.longValue();
		}
		try {
			return Long.parseLong(value.toString().trim());
		} catch (NumberFormatException e) {
			throw new McpTools.ToolException("'" + key + "' muss eine Zahl sein");
		}
	}

	private static String text(Map<String, Object> args, String key) {
		Object value = args.get(key);
		if (value == null || value.toString().isBlank()) {
			throw new McpTools.ToolException("'" + key + "' fehlt");
		}
		return value.toString().trim();
	}

	private static <E extends Enum<E>> E enumOf(Class<E> type, String value, String key) {
		try {
			return Enum.valueOf(type, value.toUpperCase());
		} catch (IllegalArgumentException e) {
			throw new McpTools.ToolException("Ungültiger Wert für '" + key + "': " + value);
		}
	}

	private static LocalDate date(Map<String, Object> args, String key, boolean required) {
		Object value = args.get(key);
		if (value == null || value.toString().isBlank()) {
			if (required) {
				throw new McpTools.ToolException("'" + key + "' fehlt");
			}
			return null;
		}
		try {
			return LocalDate.parse(value.toString().trim());
		} catch (java.time.format.DateTimeParseException e) {
			throw new McpTools.ToolException("Ungültiges Datum für '" + key + "' (erwartet JJJJ-MM-TT): " + value);
		}
	}
}
