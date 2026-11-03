package io.github.jakobfa.webtechproject.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import io.github.jakobfa.webtechproject.model.Athlete;
import io.github.jakobfa.webtechproject.model.Event;
import io.github.jakobfa.webtechproject.model.EventGoal;
import io.github.jakobfa.webtechproject.model.EventType;
import io.github.jakobfa.webtechproject.model.Level;
import io.github.jakobfa.webtechproject.model.Phase;
import io.github.jakobfa.webtechproject.model.PlannedWorkout;
import io.github.jakobfa.webtechproject.model.WeeklyAvailability;

/**
 * Simuliert einen echten Nutzer aus seinem Daten-Export ("Daten herunterladen" im Profil): seine Einstellungen,
 * sein Plan, wie er in der App steht, und der Plan, den der aktuelle Generator heute erzeugen würde. Die Startform
 * (CTL/ATL) kommt aus seinen gefahrenen Einheiten und wird mitgegeben.
 *
 * Die Daten sind persönlich: nur lokal ausführen, den Export und den Bericht nicht einchecken.
 *
 * Aufruf: ./gradlew exportSimulation -PexportFile=/Pfad/export.json -Pctl=96 -Patl=55 [-Pfrom=2026-11-03]
 */
public final class ExportSimulation {

	private static final WorkoutLibrary LIBRARY = new WorkoutLibrary();
	private static final PlanGenerator GENERATOR = new PlanGenerator(LIBRARY);

	private ExportSimulation() {
	}

	public static void main(String[] args) throws IOException {
		JsonNode export = new ObjectMapper().readTree(Files.readString(Path.of(args[0])));
		double ctl = Double.parseDouble(args[1]);
		double atl = Double.parseDouble(args[2]);
		LocalDate from = LocalDate.parse(args[3]);
		String target = args[4];

		JsonNode profile = export.get("profil");
		WeeklyAvailability availability = availabilityOf(profile.get("availability"));
		Athlete athlete = new Athlete();
		athlete.setFtp(profile.get("ftp").asInt());
		athlete.setWeightKg(profile.get("weightKg").asDouble());
		athlete.setLevel(Level.valueOf(profile.get("level").asText()));
		athlete.setAvailability(availability);
		JsonNode eventNode = profile.get("event");
		Event event = new Event();
		event.setName(eventNode.get("name").asText());
		event.setDate(LocalDate.parse(eventNode.get("date").asText()));
		event.setDistanceKm(eventNode.get("distanceKm").asDouble());
		event.setElevationMeters(eventNode.get("elevationMeters").asInt());
		event.setType(EventType.valueOf(eventNode.get("type").asText()));
		event.setGoal(eventNode.get("goal").isNull() ? EventGoal.GOOD_TIME : EventGoal.valueOf(eventNode.get("goal").asText()));
		athlete.setEvent(event);

		Map<LocalDate, WeeklyAvailability> weekAvailability = new TreeMap<>();
		export.get("plan").get("weekAvailability").properties()
				.forEach(entry -> weekAvailability.put(LocalDate.parse(entry.getKey()), availabilityOf(entry.getValue())));

		// 1. der Plan, wie er in der App steht (ab dem Starttag)
		List<PlannedWorkout> stored = new ArrayList<>();
		for (JsonNode node : export.get("plan").get("workouts")) {
			LocalDate date = LocalDate.parse(node.get("date").asText());
			if (date.isBefore(from)) {
				continue;
			}
			var workout = LIBRARY.findById(node.get("workout").get("id").asLong()).orElseThrow();
			int minutes = node.get("workout").get("durationInMinutes").asInt();
			stored.add(new PlannedWorkout(date, workout, minutes == workout.getDurationInMinutes() ? null : minutes,
					Phase.valueOf(node.get("phase").asText())));
		}
		// 2. der Plan, den der Generator heute für ihn erzeugen würde
		List<PlannedWorkout> fresh = GENERATOR.generateSeason(athlete, from, weekAvailability, List.of(),
				event.getDate().plusDays(1));

		StringBuilder out = new StringBuilder("# Simulation mit den Daten aus dem Export\n\n");
		out.append("Start ").append(from).append(", CTL ").append(ctl).append(", ATL ").append(atl)
				.append(" (geschätzt aus den gefahrenen Strava-Einheiten).\n\n");
		append(out, "Plan wie in der App", PlanSimulation.simulate("App-Plan", athlete, stored, ctl, atl, weekAvailability));
		append(out, "Plan mit dem aktuellen Generator", PlanSimulation.simulate("Neuer Plan", athlete, fresh, ctl, atl, weekAvailability));
		Files.createDirectories(Path.of(target).toAbsolutePath().getParent());
		Files.writeString(Path.of(target), out);
		System.out.println("Bericht: " + Path.of(target).toAbsolutePath());
	}

	private static WeeklyAvailability availabilityOf(JsonNode node) {
		WeeklyAvailability availability = new WeeklyAvailability();
		availability.setMonday(node.get("monday").asDouble());
		availability.setTuesday(node.get("tuesday").asDouble());
		availability.setWednesday(node.get("wednesday").asDouble());
		availability.setThursday(node.get("thursday").asDouble());
		availability.setFriday(node.get("friday").asDouble());
		availability.setSaturday(node.get("saturday").asDouble());
		availability.setSunday(node.get("sunday").asDouble());
		return availability;
	}

	private static void append(StringBuilder out, String title, PlanSimulation.Result result) {
		out.append("## ").append(title).append("\n\n");
		result.hard().forEach(line -> out.append("- **HART** ").append(line).append('\n'));
		result.warnings().forEach(line -> out.append("- Warnung: ").append(line).append('\n'));
		out.append("\nausgefallen ").append(result.missed()).append(", angepasst ").append(result.adapted()).append("\n\n");
		out.append("| Woche | Phase | geplant h | gefahren h | geplant TSS | gefahren TSS | CTL | Anstieg | tiefster TSB |\n");
		out.append("|---|---|---|---|---|---|---|---|---|\n");
		for (PlanSimulation.Week week : result.weeks()) {
			out.append("| %s | %s | %.1f | %.1f | %d | %d | %.0f | %+.1f | %.0f |\n".formatted(week.monday(), week.phase(),
					week.plannedMinutes() / 60.0, week.actualMinutes() / 60.0, week.plannedTss(), week.actualTss(),
					week.ctl(), week.ctlRamp(), week.minTsb()));
		}
		out.append('\n');
	}
}
