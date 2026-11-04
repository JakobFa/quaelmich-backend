package io.github.jakobfa.webtechproject.web;

import static io.github.jakobfa.webtechproject.web.TestUsers.registerWithProfile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import com.jayway.jsonpath.JsonPath;

import io.github.jakobfa.webtechproject.model.ExtraRide;
import io.github.jakobfa.webtechproject.model.PlannedWorkout;
import io.github.jakobfa.webtechproject.model.Sport;
import io.github.jakobfa.webtechproject.model.TrainingPlan;
import io.github.jakobfa.webtechproject.repository.ExtraRideRepository;
import io.github.jakobfa.webtechproject.repository.TrainingPlanRepository;
import io.github.jakobfa.webtechproject.service.PlanHistory;
import io.github.jakobfa.webtechproject.service.TrainingPlanService;

/** Der Plan richtet sich nach dem, was gefahren wurde: Zusatzfahrten, laufende Woche, sofortige Anpassung */
@SpringBootTest
@AutoConfigureMockMvc
class ActualsAdaptationTest {

	private static final LocalDate TODAY = LocalDate.now(java.time.ZoneId.of("Europe/Berlin"));
	private static final LocalDate THIS_MONDAY = TODAY.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private TrainingPlanRepository plans;
	@Autowired
	private ExtraRideRepository extraRides;
	@Autowired
	private TrainingPlanService planService;
	@Autowired
	private TransactionTemplate tx;

	private String auth;
	private Long planId;
	private Long athleteId;
	private long firstWorkoutId;

	@BeforeEach
	void createPlan() throws Exception {
		auth = registerWithProfile(mockMvc);
		String plan = mockMvc.perform(post("/me/plan").header("Authorization", auth)).andReturn().getResponse()
				.getContentAsString();
		firstWorkoutId = ((Number) JsonPath.read(plan, "$.workouts[0].id")).longValue();
		planId = ((Number) JsonPath.read(plan, "$.id")).longValue();
		tx.executeWithoutResult(status -> athleteId = plans.findById(planId).orElseThrow().getAthlete().getId());
	}

	/** Verschiebt den Plan um 14 Tage nach hinten: Es gibt zwei vergangene Wochen */
	private void shiftPlanBack() {
		tx.executeWithoutResult(status -> {
			TrainingPlan plan = plans.findById(planId).orElseThrow();
			plan.setStartDate(plan.getStartDate().minusDays(14));
			plan.getWorkouts().forEach(planned -> planned.setDate(planned.getDate().minusDays(14)));
			plans.save(plan);
		});
	}

	private ExtraRide extraRide(LocalDate date, int minutes, Integer tss, Sport sport) {
		return tx.execute(status -> {
			ExtraRide ride = new ExtraRide(plans.findById(planId).orElseThrow().getAthlete(), "x" + date + minutes + sport);
			ride.setDate(date);
			ride.setDurationMinutes(minutes);
			ride.setTss(tss);
			ride.setSport(sport);
			return extraRides.save(ride);
		});
	}

	private PlanHistory history() {
		return tx.execute(status -> planService.historyOf(plans.findById(planId).orElseThrow()));
	}

	@Test
	void extraRidesCountTowardsTheVolumeOfAPastWeek() {
		shiftPlanBack();
		LocalDate lastWeek = THIS_MONDAY.minusWeeks(1);
		int before = history().weeks().get(lastWeek).actualMinutes();

		extraRide(lastWeek.plusDays(2), 120, 90, Sport.RIDE);

		var week = history().weeks().get(lastWeek);
		assertThat(week.actualMinutes()).isEqualTo(before + 120);
		assertThat(week.actualTss()).isGreaterThanOrEqualTo(90);
	}

	@Test
	void otherSportsCountTooWithTheirOwnWeight() {
		shiftPlanBack();
		LocalDate lastWeek = THIS_MONDAY.minusWeeks(1);
		var base = history().weeks().get(lastWeek);

		extraRide(lastWeek.plusDays(2), 60, 50, Sport.RUN); // 60 min Laufen: 42 Radminuten, volle gemessene Belastung
		extraRide(lastWeek.plusDays(3), 100, null, Sport.SWIM); // ohne Messung: aus der Dauer geschätzt, anteilig
		extraRide(lastWeek.plusDays(4), 60, null, Sport.STRENGTH);

		var week = history().weeks().get(lastWeek);
		assertThat(week.actualMinutes()).isEqualTo(base.actualMinutes() + 42 + 60 + 18);
		assertThat(week.actualTss()).isEqualTo(base.actualTss() + 50 + 30 + 9);
	}

	@Test
	void theRunningWeekCountsWhatWasRiddenUntilToday() {
		extraRide(TODAY, 90, 70, Sport.RIDE);

		var current = history().currentWeek();
		assertThat(history().today()).isEqualTo(TODAY);
		assertThat(current.actualMinutes()).isEqualTo(90);
		assertThat(current.actualTss()).isEqualTo(70);
		assertThat(current.plannedMinutes()).isPositive(); // die ganze Woche ist geplant
	}

	@Test
	void adaptingRunsOnlyWhenSomethingChanged() {
		planService.adaptToActuals(athleteId);
		Integer first = fingerprint();
		assertThat(first).isNotNull();
		List<Long> idsBefore = futureWorkoutIds();

		planService.adaptToActuals(athleteId); // nichts Neues: nichts passiert
		assertThat(fingerprint()).isEqualTo(first);
		assertThat(futureWorkoutIds()).isEqualTo(idsBefore);

		extraRide(TODAY, 60, 50, Sport.RIDE); // neue Fahrt: Abgleich läuft
		planService.adaptToActuals(athleteId);
		assertThat(fingerprint())
				.isNotEqualTo(first);
	}

	private Integer fingerprint() {
		return tx.execute(status -> plans.findById(planId).orElseThrow().getActualsFingerprint());
	}

	private List<Long> futureWorkoutIds() {
		return tx.execute(status -> plans.findById(planId).orElseThrow().getWorkouts().stream()
				.filter(planned -> planned.getDate().isAfter(TODAY)).map(PlannedWorkout::getId).sorted().toList());
	}

	@Test
	void whileSickThePlanStaysHeldAndIsNotAdapted() throws Exception {
		mockMvc.perform(put("/me/plan/sickness").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
				.content("{ \"from\": \"%s\" }".formatted(TODAY))).andExpect(status().isOk());

		planService.adaptToActuals(athleteId);

		assertThat(fingerprint()).isNull();
	}

	@Test
	void checkingOffAWorkoutAdaptsThePlanImmediately() throws Exception {
		mockMvc.perform(put("/planned-workouts/" + firstWorkoutId + "/progress").header("Authorization", auth)
				.contentType(MediaType.APPLICATION_JSON).content("{ \"completed\": true, \"rating\": 4 }"))
				.andExpect(status().isOk());

		assertThat(fingerprint()).isNotNull();
	}

	@Test
	void whenTheRecoveryWeekIsDroppedTheWholeWeekShowsTheSamePhase() {
		// 21 Tage zurück: die laufende Woche ist die Erholungswoche, die Woche davor wurde nicht gefahren
		tx.executeWithoutResult(status -> {
			TrainingPlan plan = plans.findById(planId).orElseThrow();
			plan.setStartDate(plan.getStartDate().minusDays(21));
			plan.getWorkouts().forEach(planned -> planned.setDate(planned.getDate().minusDays(21)));
			plans.save(plan);
		});

		planService.currentPlan(athleteId); // erstes Öffnen der Woche: Anpassung

		var phases = tx.execute(status -> plans.findById(planId).orElseThrow().getWorkouts().stream()
				.filter(planned -> !planned.getDate().isBefore(THIS_MONDAY)
						&& !planned.getDate().isAfter(THIS_MONDAY.plusDays(6)))
				.map(PlannedWorkout::getPhase).distinct().toList());
		assertThat(phases).hasSize(1).doesNotContain(io.github.jakobfa.webtechproject.model.Phase.RECOVERY);
	}
}
