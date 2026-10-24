package io.github.jakobfa.webtechproject.demo;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.jakobfa.webtechproject.model.AppUser;
import io.github.jakobfa.webtechproject.model.Athlete;
import io.github.jakobfa.webtechproject.model.Event;
import io.github.jakobfa.webtechproject.model.EventType;
import io.github.jakobfa.webtechproject.model.ExtraRide;
import io.github.jakobfa.webtechproject.model.Level;
import io.github.jakobfa.webtechproject.model.PlannedWorkout;
import io.github.jakobfa.webtechproject.model.SkipReason;
import io.github.jakobfa.webtechproject.model.Sport;
import io.github.jakobfa.webtechproject.model.TrainingMode;
import io.github.jakobfa.webtechproject.model.TrainingPlan;
import io.github.jakobfa.webtechproject.model.WeeklyAvailability;
import io.github.jakobfa.webtechproject.model.Workout;
import io.github.jakobfa.webtechproject.repository.AppUserRepository;
import io.github.jakobfa.webtechproject.repository.AthleteRepository;
import io.github.jakobfa.webtechproject.repository.ExtraRideRepository;
import io.github.jakobfa.webtechproject.repository.TrainingPlanRepository;
import io.github.jakobfa.webtechproject.service.PlanAdaptationService;
import io.github.jakobfa.webtechproject.service.PlanGenerator;
import io.github.jakobfa.webtechproject.service.WorkoutLibrary;

/**
 * NUR für die Test-Seite (Staging): sorgt dafür, dass dort immer realistische Daten da sind.
 *
 * Jeder Plan beginnt rückwirkend vor 4 Wochen; vergangene Tage werden mit simulierten Fahrten gefüllt
 * (meist wie geplant, manchmal härter oder leichter, ab und zu ausgelassen oder ohne Rückmeldung),
 * dazu Pendel- und Laufeinheiten. Läuft beim Start, stündlich und direkt nach "Plan erstellen".
 * Außerdem gibt es ein festes Demo-Konto (demo@quaelmich.test, Passwort aus DEMO_PASSWORD).
 *
 * Doppelt abgesichert: nur mit DEMO_DATA=true UND nie, wenn das Backend zur Live-Seite gehört.
 */
@Component
public class DemoData {

	private static final Logger log = LoggerFactory.getLogger(DemoData.class);

	static final String LIVE_FRONTEND = "https://quaelmich.com";
	static final String DEMO_EMAIL = "demo@quaelmich.test";
	static final String PREFIX = "demo-";
	static final int HISTORY_DAYS = 28;
	/** ältere Tage ohne Rückmeldung wären unrealistisch viele Erinnerungen */
	static final int OPEN_FEEDBACK_DAYS = 10;

	private final boolean enabled;
	private final String demoPassword;
	private final AppUserRepository users;
	private final AthleteRepository athletes;
	private final TrainingPlanRepository plans;
	private final ExtraRideRepository extraRides;
	private final PlanGenerator planGenerator;
	private final PlanAdaptationService adaptation;
	private final WorkoutLibrary library;
	private final PasswordEncoder passwordEncoder;
	private final Clock clock;
	/** interne Aufrufe (Start, stündlich) laufen sonst ohne Transaktion */
	private final TransactionTemplate transaction;

	public DemoData(@Value("${app.demo.enabled:false}") boolean demoFlag,
			@Value("${app.mail.frontend-base-url:https://quaelmich.com}") String frontendBaseUrl,
			@Value("${app.demo.password:}") String demoPassword, AppUserRepository users, AthleteRepository athletes,
			TrainingPlanRepository plans, ExtraRideRepository extraRides, PlanGenerator planGenerator,
			PlanAdaptationService adaptation, WorkoutLibrary library, PasswordEncoder passwordEncoder, Clock clock,
			PlatformTransactionManager transactionManager) {
		this.transaction = new TransactionTemplate(transactionManager);
		boolean live = frontendBaseUrl == null || frontendBaseUrl.strip().replaceAll("/+$", "").equals(LIVE_FRONTEND);
		this.enabled = demoFlag && !live;
		if (demoFlag && live) {
			log.error("DEMO_DATA ist gesetzt, aber das hier ist die Live-Seite – Testdaten bleiben aus.");
		}
		this.demoPassword = demoPassword == null ? "" : demoPassword;
		this.users = users;
		this.athletes = athletes;
		this.plans = plans;
		this.extraRides = extraRides;
		this.planGenerator = planGenerator;
		this.adaptation = adaptation;
		this.library = library;
		this.passwordEncoder = passwordEncoder;
		this.clock = clock;
	}

	public boolean isEnabled() {
		return enabled;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void onStart() {
		if (!enabled) {
			return;
		}
		log.info("Testdaten aktiv (Staging)");
		transaction.executeWithoutResult(status -> ensureDemoAccount());
		transaction.executeWithoutResult(status -> fillAll());
	}

	/** stündlich: die Tage bis heute auffüllen */
	@Scheduled(cron = "0 5 * * * *")
	public void hourly() {
		if (enabled) {
			transaction.executeWithoutResult(status -> fillAll());
		}
	}

	@Transactional
	public void fillAll() {
		if (!enabled) {
			return;
		}
		plans.findAll().forEach(this::fill);
	}

	/** Für einen Plan: rückwirkend beginnen lassen und vergangene Tage füllen. Nichts tun, wenn aus. */
	@Transactional
	public void fill(TrainingPlan plan) {
		if (!enabled) {
			return;
		}
		LocalDate today = LocalDate.now(clock);
		Athlete athlete = plan.getAthlete();
		if (plan.getStartDate().isAfter(today.minusDays(HISTORY_DAYS))) {
			backdate(plan, today.minusDays(HISTORY_DAYS));
		}
		for (PlannedWorkout workout : plan.getWorkouts()) {
			if (!workout.getDate().isAfter(today)) {
				simulate(workout, plan.getWorkouts(), today);
			}
		}
		addSideSessions(athlete, plan.getStartDate(), today);
		plans.save(plan);
	}

	private void backdate(TrainingPlan plan, LocalDate start) {
		plan.setStartDate(start);
		plan.getWorkouts().clear();
		planGenerator.generate(plan.getAthlete(), start, plan.getWeekAvailability()).forEach(plan::addWorkout);
		plan.sortByDate();
	}

	/** Gleiches Workout → immer gleiches Ergebnis (so bleibt es über die stündlichen Läufe stabil) */
	private void simulate(PlannedWorkout workout, List<PlannedWorkout> all, LocalDate today) {
		if (workout.getActivityId() != null || workout.getSkipReason() != null || workout.isCompleted()) {
			return;
		}
		Workout planned = library.resolve(workout.getWorkoutId(), workout.getDurationMinutes()).orElseThrow();
		Random random = seeded(workout.getId(), workout.getDate());
		double roll = random.nextDouble();
		boolean isToday = workout.getDate().equals(today);
		boolean recent = workout.getDate().isAfter(today.minusDays(OPEN_FEEDBACK_DAYS));

		if (roll < 0.62 || isToday && roll < 0.8 || !recent && roll >= 0.8) {
			ride(workout, planned, 0.9 + random.nextDouble() * 0.2, random, isToday);
		} else if (roll < 0.72) {
			ride(workout, planned, 1.35 + random.nextDouble() * 0.25, random, isToday);
			adaptation.adaptAfter(workout, all); // harte Fahrt → nächstes hartes Training wird leichter
		} else if (roll < 0.8) {
			ride(workout, planned, 0.6 + random.nextDouble() * 0.15, random, isToday);
		} else if (roll < 0.88 && !isToday) {
			workout.setSkipReason(List.of(SkipReason.NO_TIME, SkipReason.TIRED, SkipReason.NO_TIME)
					.get(random.nextInt(3)));
		}
		// sonst: ohne Rückmeldung – so erscheint die Erinnerung
	}

	private static void ride(PlannedWorkout workout, Workout planned, double factor, Random random, boolean isToday) {
		int minutes = (int) Math.round(planned.getDurationInMinutes() * (0.95 + random.nextDouble() * 0.12));
		workout.setActivityId(PREFIX + workout.getId());
		workout.setCompleted(true);
		workout.setActualTss((int) Math.round(planned.getTss() * factor));
		workout.setActualDurationMinutes(minutes);
		workout.setActualDistanceKm(Math.round(minutes / 60.0 * (27 + random.nextInt(5)) * 10) / 10.0);
		// heute: noch nicht bestätigt, damit "War das dieses Training?" erscheint
		workout.setActivityConfirmed(isToday ? null : Boolean.TRUE);
		workout.setRating(isToday ? null : 3 + random.nextInt(5));
	}

	/** Pendelfahrten (Puls-TSS) und ab und zu ein Lauf */
	private void addSideSessions(Athlete athlete, LocalDate from, LocalDate today) {
		Set<String> existing = extraRides.findByAthleteId(athlete.getId()).stream().map(ExtraRide::getActivityId)
				.collect(Collectors.toSet());
		for (LocalDate date = from; !date.isAfter(today); date = date.plusDays(1)) {
			Random random = seeded(athlete.getId(), date, "extra");
			double roll = random.nextDouble();
			String id = PREFIX + "extra-" + athlete.getId() + "-" + date;
			if (roll >= 0.4 || existing.contains(id)) {
				continue;
			}
			ExtraRide ride = new ExtraRide(athlete, id);
			ride.setDate(date);
			ride.setAdaptationApplied(true);
			if (roll < 0.32) {
				ride.setSport(Sport.RIDE);
				ride.setDurationMinutes(22 + random.nextInt(12));
				ride.setDistanceKm(Math.round((7 + random.nextDouble() * 4) * 10) / 10.0);
				ride.setTss(12 + random.nextInt(10));
				ride.setTssFromHeartRate(true);
			} else {
				ride.setSport(Sport.RUN);
				ride.setDurationMinutes(30 + random.nextInt(20));
				ride.setDistanceKm(Math.round((5 + random.nextDouble() * 4) * 10) / 10.0);
				ride.setTss(Sport.RUN.estimateTss(ride.getDurationMinutes() * 60));
				ride.setTssFromDuration(true);
			}
			extraRides.save(ride);
		}
	}

	/** Ähnliche Startwerte ergeben bei Random fast gleiche erste Zahlen – deshalb vorher durchmischen */
	private static Random seeded(Object... parts) {
		long seed = Objects.hash(parts) * 0x9E3779B97F4A7C15L;
		return new Random(seed ^ (seed >>> 29));
	}

	/** Festes Demo-Konto – nur wenn DEMO_PASSWORD gesetzt ist */
	@Transactional
	public void ensureDemoAccount() {
		if (!enabled || demoPassword.length() < 8 || users.findByEmail(DEMO_EMAIL).isPresent()) {
			return;
		}
		AppUser user = users.save(new AppUser(DEMO_EMAIL, passwordEncoder.encode(demoPassword), Instant.now(clock)));
		Athlete athlete = new Athlete();
		athlete.setOwner(user);
		athlete.setFtp(250);
		athlete.setWeightKg(72.0);
		athlete.setMaxHeartRate(189);
		athlete.setLevel(Level.AMBITIOUS);
		athlete.setTrainingMode(TrainingMode.POWER);
		Event event = new Event();
		event.setName("Demo-Radmarathon");
		event.setDate(LocalDate.now(clock).plusWeeks(14));
		event.setDistanceKm(200.0);
		event.setElevationMeters(2500);
		event.setType(EventType.GRAN_FONDO);
		athlete.setEvent(event);
		WeeklyAvailability availability = new WeeklyAvailability();
		availability.setTuesday(1.5);
		availability.setWednesday(1);
		availability.setThursday(1.5);
		availability.setSaturday(3.5);
		availability.setSunday(2);
		athlete.setAvailability(availability);
		athletes.save(athlete);
		TrainingPlan plan = new TrainingPlan(athlete, LocalDate.now(clock));
		planGenerator.generate(athlete, plan.getStartDate()).forEach(plan::addWorkout);
		plans.save(plan);
		log.info("Demo-Konto {} angelegt", DEMO_EMAIL);
	}

}
