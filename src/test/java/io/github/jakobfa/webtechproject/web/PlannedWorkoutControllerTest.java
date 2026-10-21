package io.github.jakobfa.webtechproject.web;

import static io.github.jakobfa.webtechproject.web.TestUsers.registerWithProfile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import io.github.jakobfa.webtechproject.model.ExtraRide;
import io.github.jakobfa.webtechproject.model.PlannedWorkout;
import io.github.jakobfa.webtechproject.model.Sport;
import io.github.jakobfa.webtechproject.repository.PlannedWorkoutRepository;

@SpringBootTest
@AutoConfigureMockMvc
class PlannedWorkoutControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private PlannedWorkoutRepository repository;

	@Autowired
	private io.github.jakobfa.webtechproject.repository.ExtraRideRepository extraRides;

	/** Eine nicht zugeordnete Fahrt am Tag des Workouts */
	private ExtraRide rideOnTheSameDay(Sport sport) {
		PlannedWorkout planned = repository.findById(plannedId).orElseThrow();
		ExtraRide ride = new ExtraRide(planned.getPlan().getAthlete(), "strava-" + sport + plannedId);
		ride.setDate(planned.getDate());
		ride.setDurationMinutes(40);
		ride.setDistanceKm(15.0);
		ride.setTss(30);
		ride.setSport(sport);
		ride.setAdaptationApplied(true);
		return extraRides.save(ride);
	}

	@Test
	void rideOfTheDayCanBeAssignedByHand() throws Exception {
		ExtraRide ride = rideOnTheSameDay(Sport.RIDE);
		rideOnTheSameDay(Sport.RUN); // ein Lauf kann kein Rad-Workout sein

		mockMvc.perform(get("/planned-workouts/" + plannedId + "/candidate-rides").header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].id").value(ride.getId()));

		mockMvc.perform(put("/planned-workouts/" + plannedId + "/activity").header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON).content("{ \"extraRideId\": %d }".formatted(ride.getId())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.completed").value(true))
				.andExpect(jsonPath("$.actual.tss").value(30))
				.andExpect(jsonPath("$.actual.confirmed").value(true));

		assertThat(extraRides.findById(ride.getId())).as("keine doppelte Belastung").isEmpty();
		assertThat(repository.findById(plannedId).orElseThrow().isAdaptationApplied())
				.as("hat den Plan schon angepasst – nicht nochmal").isTrue();
		mockMvc.perform(get("/planned-workouts/" + plannedId + "/candidate-rides").header("Authorization", token))
				.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void othersCannotAssignRides() throws Exception {
		ExtraRide ride = rideOnTheSameDay(Sport.RIDE);
		String other = registerWithProfile(mockMvc);

		mockMvc.perform(put("/planned-workouts/" + plannedId + "/activity").header("Authorization", other)
				.contentType(MediaType.APPLICATION_JSON).content("{ \"extraRideId\": %d }".formatted(ride.getId())))
				.andExpect(status().isNotFound());
		assertThat(extraRides.findById(ride.getId())).isPresent();
	}

	private String token;
	private long plannedId;

	@BeforeEach
	void createPlan() throws Exception {
		token = registerWithProfile(mockMvc);
		String plan = mockMvc.perform(post("/me/plan").header("Authorization", token)).andReturn().getResponse()
				.getContentAsString();
		plannedId = ((Number) JsonPath.read(plan, "$.workouts[0].id")).longValue();
	}

	private ResultActions saveProgress(String authorization, boolean completed, Integer rating) throws Exception {
		return mockMvc.perform(put("/planned-workouts/" + plannedId + "/progress")
				.header("Authorization", authorization)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{ \"completed\": %s, \"rating\": %s }".formatted(completed, rating)));
	}

	@Test
	void loadsPlannedWorkoutWithWorkoutDetails() throws Exception {
		mockMvc.perform(get("/planned-workouts/" + plannedId).header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(plannedId))
				.andExpect(jsonPath("$.completed").value(false))
				.andExpect(jsonPath("$.workout.steps").isArray());
	}

	@Test
	void marksWorkoutAsCompletedWithRating() throws Exception {
		saveProgress(token, true, 7)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.completed").value(true))
				.andExpect(jsonPath("$.rating").value(7));

		mockMvc.perform(get("/planned-workouts/" + plannedId).header("Authorization", token))
				.andExpect(jsonPath("$.completed").value(true))
				.andExpect(jsonPath("$.rating").value(7));
	}

	@Test
	void uncheckingRemovesTheRating() throws Exception {
		saveProgress(token, true, 7);

		saveProgress(token, false, 7)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.completed").value(false))
				.andExpect(jsonPath("$.rating").value(nullValue()));
	}

	@Test
	void rejectsRatingOutsideOneToTen() throws Exception {
		saveProgress(token, true, 11).andExpect(status().isBadRequest());
	}

	@Test
	void downloadsPlannedWorkoutAsFitFile() throws Exception {
		mockMvc.perform(get("/planned-workouts/" + plannedId + "/fit").header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Type", "application/octet-stream"))
				.andExpect(header().string("Content-Disposition", endsWith(".fit\"")));
	}

	@Test
	void otherUsersCannotSeeOrChangeTheWorkout() throws Exception {
		String otherUser = registerWithProfile(mockMvc);

		mockMvc.perform(get("/planned-workouts/" + plannedId).header("Authorization", otherUser))
				.andExpect(status().isNotFound());
		saveProgress(otherUser, true, 3).andExpect(status().isNotFound());
		mockMvc.perform(get("/planned-workouts/" + plannedId + "/fit").header("Authorization", otherUser))
				.andExpect(status().isNotFound());

		// unverändert für den Besitzer
		mockMvc.perform(get("/planned-workouts/" + plannedId).header("Authorization", token))
				.andExpect(jsonPath("$.completed").value(false));
	}

	@Test
	void unknownPlannedWorkoutIsNotFound() throws Exception {
		mockMvc.perform(get("/planned-workouts/99999").header("Authorization", token))
				.andExpect(status().isNotFound());
	}

	private ResultActions confirm(String authorization, boolean matchesPlan) throws Exception {
		return mockMvc.perform(put("/planned-workouts/" + plannedId + "/activity-confirmation")
				.header("Authorization", authorization)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{ \"matchesPlan\": %s }".formatted(matchesPlan)));
	}

	/** Simuliert eine von intervals.icu importierte Fahrt */
	private void linkRide() {
		PlannedWorkout planned = repository.findById(plannedId).orElseThrow();
		planned.setActivityId("i1");
		planned.setCompleted(true);
		planned.setActualDurationMinutes(60);
		planned.setActualTss(500);
		repository.save(planned);
	}

	@Test
	void showsAssessmentOfTheImportedRide() throws Exception {
		linkRide();

		mockMvc.perform(get("/planned-workouts/" + plannedId).header("Authorization", token))
				.andExpect(jsonPath("$.actual.assessment").value("MUCH_HARDER"))
				.andExpect(jsonPath("$.actual.confirmed").value(nullValue()));
	}

	@Test
	void confirmingKeepsTheWorkoutCompleted() throws Exception {
		linkRide();

		confirm(token, true)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.completed").value(true))
				.andExpect(jsonPath("$.actual.confirmed").value(true));
	}

	@Test
	void otherTrainingKeepsTheRideButUnchecksTheWorkout() throws Exception {
		linkRide();

		confirm(token, false)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.completed").value(false))
				.andExpect(jsonPath("$.actual.confirmed").value(false))
				.andExpect(jsonPath("$.actual.tss").value(500));
	}

	@Test
	void confirmationNeedsALinkedRideAndTheOwner() throws Exception {
		confirm(token, true).andExpect(status().isNotFound());

		linkRide();
		confirm(registerWithProfile(mockMvc), false).andExpect(status().isNotFound());
	}

	private ResultActions adjust(String direction) throws Exception {
		return mockMvc.perform(put("/planned-workouts/" + plannedId + "/adjustment")
				.header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{ \"direction\": \"%s\" }".formatted(direction)));
	}

	/** ein offenes Workout, das sich in alle Richtungen ändern lässt (Grundlage 90 min) */
	private void makeItEndurance() {
		PlannedWorkout planned = repository.findById(plannedId).orElseThrow();
		planned.setWorkoutId(2L);
		planned.setDurationMinutes(90);
		repository.save(planned);
	}

	@Test
	void shorterWorkoutCanBeResetToTheOriginal() throws Exception {
		makeItEndurance();

		adjust("SHORTER")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.workout.durationInMinutes").value(75))
				.andExpect(jsonPath("$.adjustment.note").value("Von dir angepasst: kürzer."))
				.andExpect(jsonPath("$.adjustment.originalTitle").value("Grundlage 90 min"))
				.andExpect(jsonPath("$.options.reset").value(true));

		mockMvc.perform(delete("/planned-workouts/" + plannedId + "/adjustment").header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.workout.durationInMinutes").value(90))
				.andExpect(jsonPath("$.adjustment").value(nullValue()))
				.andExpect(jsonPath("$.options.reset").value(false));
	}

	@Test
	void goingBackAndForthEndsAtTheOriginal() throws Exception {
		makeItEndurance();
		adjust("LONGER");

		adjust("SHORTER")
				.andExpect(jsonPath("$.workout.durationInMinutes").value(90))
				.andExpect(jsonPath("$.adjustment").value(nullValue()));
	}

	@Test
	void completedWorkoutsCannotBeAdjusted() throws Exception {
		makeItEndurance();
		saveProgress(token, true, 5);

		adjust("HARDER").andExpect(status().isConflict());
		mockMvc.perform(get("/planned-workouts/" + plannedId).header("Authorization", token))
				.andExpect(jsonPath("$.options.harder").value(false));
	}

	@Test
	void otherUsersCannotAdjust() throws Exception {
		String other = registerWithProfile(mockMvc);
		mockMvc.perform(put("/planned-workouts/" + plannedId + "/adjustment").header("Authorization", other)
				.contentType(MediaType.APPLICATION_JSON).content("{ \"direction\": \"EASIER\" }"))
				.andExpect(status().isNotFound());
	}

	@Test
	void needsLogin() throws Exception {
		mockMvc.perform(get("/planned-workouts/" + plannedId)).andExpect(status().isUnauthorized());
	}

}
