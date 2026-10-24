package io.github.jakobfa.webtechproject.service;

import java.util.Map;

import io.github.jakobfa.webtechproject.model.Athlete;
import io.github.jakobfa.webtechproject.model.Event;
import io.github.jakobfa.webtechproject.model.EventGoal;
import io.github.jakobfa.webtechproject.model.EventType;
import io.github.jakobfa.webtechproject.model.Level;

/**
 * Was das Ziel-Event verlangt: geschätzte Dauer und daraus der Trainingsschwerpunkt.
 *
 * Die Dauer wird grob aus Distanz, Höhenmetern und Level geschätzt (Grundtempo plus Zeit für die
 * Anstiege). Sie bestimmt, wie lang die lange Ausfahrt im Lauf des Plans wird.
 */
public record EventProfile(int estimatedMinutes, Focus focus, EventGoal goal) {

	public EventProfile(int estimatedMinutes, Focus focus) {
		this(estimatedMinutes, focus, EventGoal.GOOD_TIME);
	}

	/** Schwerpunkt des Trainings */
	public enum Focus {
		/** lang und bergig (z. B. Ötztaler): Schwelle und Sweet Spot in langen Intervallen */
		LONG_CLIMBING,
		/** lang und eher flach: Tempo, Sweet Spot, sehr lange Grundlage */
		LONG_FLAT,
		/** mittlere Dauer oder Straßenrennen: ausgewogen bis VO2max */
		MIXED,
		/** kurz und intensiv (Kriterium, kurzes Rennen): VO2max, Anaerob, Sprints */
		SHORT_INTENSE,
		/** Zeitfahren: Schwelle */
		TIME_TRIAL,
		/** Ultra / Bikepacking: viel Grundlage, Tempo */
		ULTRA
	}

	/** Grundtempo in km/h ohne Anstiege */
	private static final Map<Level, Double> SPEED = Map.of(
			Level.BEGINNER, 23.0, Level.HOBBY, 26.0, Level.AMBITIOUS, 29.0, Level.PERFORMANCE, 32.0);

	/** Zusätzliche Stunden pro 1000 Höhenmeter */
	private static final Map<Level, Double> HOURS_PER_1000_METERS = Map.of(
			Level.BEGINNER, 0.5, Level.HOBBY, 0.42, Level.AMBITIOUS, 0.35, Level.PERFORMANCE, 0.3);

	/** Ab so vielen Höhenmetern pro km gilt ein Event als bergig */
	static final double CLIMBING_METERS_PER_KM = 10;

	public static EventProfile of(Athlete athlete) {
		Event event = athlete.getEvent();
		Level level = athlete.getLevel() == null ? Level.HOBBY : athlete.getLevel();
		EventType type = event.getType();

		double speed = SPEED.get(level);
		if (type == EventType.GRAVEL) {
			speed *= 0.8; // Schotter ist langsamer
		} else if (type == EventType.ULTRA) {
			speed *= 0.85; // Pausen
		}
		double distance = event.getDistanceKm() == null ? 0 : event.getDistanceKm();
		int elevation = event.getElevationMeters() == null ? 0 : event.getElevationMeters();
		double hours = distance / speed + elevation / 1000.0 * HOURS_PER_1000_METERS.get(level);
		double metersPerKm = distance > 0 ? elevation / distance : 0;

		return new EventProfile((int) Math.round(hours * 60), focusFor(type, hours, metersPerKm),
				event.getGoal() == null ? EventGoal.GOOD_TIME : event.getGoal());
	}

	static Focus focusFor(EventType type, double hours, double metersPerKm) {
		if (type == EventType.CRITERIUM) {
			return Focus.SHORT_INTENSE;
		}
		if (type == EventType.TIME_TRIAL) {
			return Focus.TIME_TRIAL;
		}
		if (type == EventType.ULTRA) {
			return Focus.ULTRA;
		}
		if (type == EventType.ROAD_RACE) {
			return hours < 2.5 ? Focus.SHORT_INTENSE : Focus.MIXED;
		}
		// Radmarathon, Gravel oder nicht angegeben: nach Dauer und Steigung
		if (hours >= 12) {
			return Focus.ULTRA;
		}
		if (hours >= 4) {
			return metersPerKm >= CLIMBING_METERS_PER_KM ? Focus.LONG_CLIMBING : Focus.LONG_FLAT;
		}
		if (hours >= 2.5 || type != null) {
			return Focus.MIXED;
		}
		return Focus.SHORT_INTENSE;
	}

}
