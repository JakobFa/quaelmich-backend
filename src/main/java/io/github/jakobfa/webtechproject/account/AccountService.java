package io.github.jakobfa.webtechproject.account;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.jakobfa.webtechproject.auth.InvitationRepository;
import io.github.jakobfa.webtechproject.auth.PasswordResetTokenRepository;
import io.github.jakobfa.webtechproject.custom.CustomWorkoutRepository;
import io.github.jakobfa.webtechproject.feedback.FeedbackRepository;
import io.github.jakobfa.webtechproject.integration.intervals.IntervalsConnectionService;
import io.github.jakobfa.webtechproject.integration.strava.StravaService;
import io.github.jakobfa.webtechproject.model.AppUser;
import io.github.jakobfa.webtechproject.repository.AppUserRepository;
import io.github.jakobfa.webtechproject.repository.AthleteRepository;
import io.github.jakobfa.webtechproject.repository.ExtraRideRepository;
import io.github.jakobfa.webtechproject.repository.TrainingPlanRepository;
import io.github.jakobfa.webtechproject.web.PlanResponseMapper;

/** Konto löschen und alle Daten exportieren (DSGVO: Recht auf Löschung und Datenübertragbarkeit) */
@Service
public class AccountService {

	private static final Logger log = LoggerFactory.getLogger(AccountService.class);

	/** Wird geworfen, wenn das Passwort zur Bestätigung nicht stimmt */
	public static class WrongPasswordException extends RuntimeException {
	}

	private final AppUserRepository users;
	private final PasswordEncoder passwordEncoder;
	private final AthleteRepository athletes;
	private final TrainingPlanRepository plans;
	private final ExtraRideRepository extraRides;
	private final CustomWorkoutRepository customWorkouts;
	private final FeedbackRepository feedback;
	private final InvitationRepository invitations;
	private final PasswordResetTokenRepository resetTokens;
	private final StravaService strava;
	private final IntervalsConnectionService intervals;
	private final PlanResponseMapper planMapper;
	private final io.github.jakobfa.webtechproject.repository.RaceEventRepository raceEvents;

	public AccountService(AppUserRepository users, PasswordEncoder passwordEncoder, AthleteRepository athletes,
			TrainingPlanRepository plans, ExtraRideRepository extraRides, CustomWorkoutRepository customWorkouts,
			FeedbackRepository feedback, InvitationRepository invitations, PasswordResetTokenRepository resetTokens,
			StravaService strava, IntervalsConnectionService intervals, PlanResponseMapper planMapper,
			io.github.jakobfa.webtechproject.repository.RaceEventRepository raceEvents) {
		this.raceEvents = raceEvents;
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.athletes = athletes;
		this.plans = plans;
		this.extraRides = extraRides;
		this.customWorkouts = customWorkouts;
		this.feedback = feedback;
		this.invitations = invitations;
		this.resetTokens = resetTokens;
		this.strava = strava;
		this.intervals = intervals;
		this.planMapper = planMapper;
	}

	/**
	 * Löscht das Konto mit allen Daten: Profil, Plan, Fahrten, eigene Workouts, Feedback, Verbindungen (bei Strava
	 * wird der Zugriff auch dort entzogen). Benutzte Einladungscodes bleiben als "benutzt", ohne Bezug zur Person.
	 */
	@Transactional
	public void delete(Long userId, String password) {
		AppUser user = users.findById(userId).orElseThrow(WrongPasswordException::new);
		if (password == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
			throw new WrongPasswordException();
		}
		strava.disconnect(userId);
		intervals.disconnect(userId);
		athletes.findByOwnerId(userId).ifPresent(athlete -> {
			plans.findByAthleteId(athlete.getId()).ifPresent(plans::delete);
			raceEvents.deleteAll(raceEvents.findByAthleteIdOrderByDate(athlete.getId()));
			extraRides.deleteAll(extraRides.findByAthleteId(athlete.getId()));
			athletes.delete(athlete);
		});
		customWorkouts.deleteAll(customWorkouts.findByOwnerId(userId));
		feedback.deleteAll(feedback.findByAuthorId(userId));
		invitations.findByUsedById(userId).forEach(invitation -> invitation.setUsedBy(null));
		resetTokens.deleteAll(resetTokens.findByUserId(userId));
		users.delete(user);
		log.info("Konto {} gelöscht", userId);
	}

	/** Alle gespeicherten Daten als eine JSON-Struktur – ohne Passwort-Hash und ohne Zugangsschlüssel */
	@Transactional(readOnly = true)
	public Optional<Map<String, Object>> export(Long userId) {
		return users.findById(userId).map(user -> {
			Map<String, Object> data = new LinkedHashMap<>();
			data.put("konto", Map.of("email", user.getEmail(), "registriert", String.valueOf(user.getCreatedAt())));
			athletes.findByOwnerId(userId).ifPresent(athlete -> {
				data.put("profil", athlete);
				plans.findByAthleteId(athlete.getId()).ifPresent(plan -> data.put("plan", planMapper.toResponse(plan)));
				data.put("weitereEvents", raceEvents.findByAthleteIdOrderByDate(athlete.getId()));
				data.put("fahrten", extraRides.findByAthleteId(athlete.getId()).stream()
						.map(PlanResponseMapper::toResponse).toList());
			});
			data.put("eigeneWorkouts", customWorkouts.findByOwnerId(userId).stream()
					.filter(workout -> !workout.isDeleted()).map(workout -> workout.toWorkout()).toList());
			data.put("feedback", feedback.findByAuthorId(userId).stream()
					.map(entry -> Map.of("zeitpunkt", String.valueOf(entry.getCreatedAt()), "seite",
							String.valueOf(entry.getPage()), "text", entry.getMessage()))
					.toList());
			Map<String, Object> connections = new LinkedHashMap<>();
			connections.put("strava", strava.isConnected(userId));
			connections.put("intervalsIcu", intervals.findByOwner(userId)
					.map(connection -> (Object) Map.of("athletId", String.valueOf(connection.getIntervalsAthleteId())))
					.orElse(false));
			data.put("verbindungen", connections);
			return data;
		});
	}

}
