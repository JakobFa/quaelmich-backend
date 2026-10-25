package io.github.jakobfa.webtechproject.service;

import java.util.Map;

import io.github.jakobfa.webtechproject.model.Athlete;
import io.github.jakobfa.webtechproject.model.Event;
import io.github.jakobfa.webtechproject.model.EventGoal;
import io.github.jakobfa.webtechproject.model.EventType;
import io.github.jakobfa.webtechproject.model.Level;
import io.github.jakobfa.webtechproject.model.TrainingGoal;

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
		return athlete.hasEventGoal() ? of(athlete.getEvent(), athlete.getLevel()) : forGoal(athlete.getTrainingGoal());
	}

	/**
	 * Für ein Ziel ohne Event: geplant, als ginge es auf eine typische Ausfahrt dieser Art – das bestimmt
	 * Schwerpunkt und Länge der langen Ausfahrt.
	 * FTP: kurze, harte Schwelle; Ausdauer: lang und eher flach; fit bleiben: ausgewogen und lockerer;
	 * Berg: lange Anstiege.
	 */
	public static EventProfile forGoal(TrainingGoal goal) {
		return switch (goal) {
			case FTP -> new EventProfile(90, Focus.TIME_TRIAL, EventGoal.GOOD_TIME);
			case ENDURANCE -> new EventProfile(300, Focus.LONG_FLAT, EventGoal.GOOD_TIME);
			case FITNESS -> new EventProfile(120, Focus.MIXED, EventGoal.FINISH);
			case CLIMBING -> new EventProfile(180, Focus.LONG_CLIMBING, EventGoal.GOOD_TIME);
			case EVENT -> throw new IllegalArgumentException("Ein Event-Ziel braucht ein Event");
		};
	}

	/**
	 * Das nächste anstehende Event (ab heute) – das erste aus dem Profil oder ein weiteres.
	 * Sind alle vorbei, das zuletzt vergangene.
	 */
	public static Event nextEvent(Event first, java.util.List<Event> more, java.time.LocalDate today) {
		java.util.List<Event> all = new java.util.ArrayList<>(more);
		all.add(first);
		all.sort(java.util.Comparator.comparing(Event::getDate));
		return all.stream().filter(event -> !event.getDate().isBefore(today)).findFirst()
				.orElse(all.getLast());
	}

	public static EventProfile of(Event event, Level athleteLevel) {
		Level level = athleteLevel == null ? Level.HOBBY : athleteLevel;
		EventType type = event.getType();

		double speed = SPEED.get(level);
		if (type == EventType.GRAVEL) {
			speed *= 0.8; // Schotter ist langsamer
		} else if (type == EventType.ULTRA) {
			speed *= 0.85; // Pausen
		} else if (type == EventType.BIKEPACKING) {
			speed *= 0.7; // Gepäck, eigenes Tempo, lange Pausen
		}
		double distance = event.getDistanceKm() == null ? 0 : event.getDistanceKm();
		int elevation = event.getElevationMeters() == null ? 0 : event.getElevationMeters();
		double hours = distance / speed + elevation / 1000.0 * HOURS_PER_1000_METERS.get(level);
		double metersPerKm = distance > 0 ? elevation / distance : 0;

		return new EventProfile((int) Math.round(hours * 60), focusFor(type, hours, metersPerKm),
				event.getGoal() != null ? event.getGoal() : defaultGoal(type));
	}

	/** Bikepacking ist kein Rennen: ohne eigene Angabe "Ankommen" statt "gute Zeit" */
	static EventGoal defaultGoal(EventType type) {
		return type == EventType.BIKEPACKING ? EventGoal.FINISH : EventGoal.GOOD_TIME;
	}

	static Focus focusFor(EventType type, double hours, double metersPerKm) {
		if (type == EventType.CRITERIUM) {
			return Focus.SHORT_INTENSE;
		}
		if (type == EventType.TIME_TRIAL) {
			return Focus.TIME_TRIAL;
		}
		if (type == EventType.ULTRA || type == EventType.BIKEPACKING) {
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
