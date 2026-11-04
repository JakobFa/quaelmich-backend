package io.github.jakobfa.webtechproject.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.jakobfa.webtechproject.model.TrainingPlan;
import io.github.jakobfa.webtechproject.repository.TrainingPlanRepository;

/** Kalender-Abo: ein geheimer Link je Nutzer, der den aktuellen Plan als iCal liefert */
@Service
public class CalendarService {

	/** Vergangene Tage, die noch im Kalender stehen bleiben */
	static final int DAYS_BACK = 28;

	private static final SecureRandom RANDOM = new SecureRandom();

	private final TrainingPlanRepository plans;
	private final AthleteService athletes;
	private final WorkoutLibrary library;
	private final Clock clock;

	public CalendarService(TrainingPlanRepository plans, AthleteService athletes, WorkoutLibrary library, Clock clock) {
		this.plans = plans;
		this.athletes = athletes;
		this.library = library;
		this.clock = clock;
	}

	/** Der Token des Nutzers, oder leer ohne Plan/Abo */
	@Transactional(readOnly = true)
	public Optional<String> tokenOf(Long userId) {
		return planOf(userId).map(TrainingPlan::getCalendarToken);
	}

	/** Abo einrichten oder einen neuen Link erzeugen (der alte funktioniert dann nicht mehr). Leer ohne Plan. */
	@Transactional
	public Optional<String> renew(Long userId) {
		return planOf(userId).map(plan -> {
			byte[] bytes = new byte[32];
			RANDOM.nextBytes(bytes);
			plan.setCalendarToken(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
			return plans.save(plan).getCalendarToken();
		});
	}

	/** Abo beenden: der Link liefert nichts mehr */
	@Transactional
	public boolean revoke(Long userId) {
		return planOf(userId).map(plan -> {
			plan.setCalendarToken(null);
			plans.save(plan);
			return true;
		}).orElse(false);
	}

	/** Gibt es ein Abo mit diesem Token? (billige Prüfung, bevor der Feed gebaut wird) */
	@Transactional(readOnly = true)
	public boolean isActive(String token) {
		return plans.existsByCalendarToken(token);
	}

	/** Die iCal-Datei zum Token, leer bei unbekanntem oder zurückgezogenem Token */
	@Transactional(readOnly = true)
	public Optional<String> feed(String token) {
		return plans.findByCalendarToken(token).map(plan -> {
			LocalDate from = LocalDate.now(clock).minusDays(DAYS_BACK);
			var workouts = plan.getWorkouts().stream().filter(planned -> !planned.getDate().isBefore(from)).toList();
			return CalendarFeed.build(workouts,
					planned -> library.resolve(planned.getWorkoutId(), planned.getDurationMinutes()).orElseThrow(),
					ZonedDateTime.now(clock));
		});
	}

	private Optional<TrainingPlan> planOf(Long userId) {
		return athletes.findByOwner(userId).flatMap(athlete -> plans.findByAthleteId(athlete.getId()));
	}
}
