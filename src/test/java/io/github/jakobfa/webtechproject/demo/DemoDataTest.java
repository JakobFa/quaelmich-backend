package io.github.jakobfa.webtechproject.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import io.github.jakobfa.webtechproject.repository.AppUserRepository;
import io.github.jakobfa.webtechproject.repository.AthleteRepository;
import io.github.jakobfa.webtechproject.repository.ExtraRideRepository;
import io.github.jakobfa.webtechproject.repository.TrainingPlanRepository;

/** Test-Seite: Demo-Konto mit 4 Wochen simulierter Vorgeschichte */
@SpringBootTest(properties = { "app.demo.enabled=true", "app.mail.frontend-base-url=https://staging.quaelmich.com",
		"app.demo.password=demo-passwort-123" })
class DemoDataTest {

	@Autowired
	private AppUserRepository users;

	@Autowired
	private AthleteRepository athletes;

	@Autowired
	private TrainingPlanRepository plans;

	@Autowired
	private ExtraRideRepository extraRides;

	@Test
	@Transactional
	void demoAccountHasFourWeeksOfHistory() {
		LocalDate today = LocalDate.now(ZoneId.of("Europe/Berlin"));
		var user = users.findByEmail(DemoData.DEMO_EMAIL).orElseThrow();
		var athlete = athletes.findByOwnerId(user.getId()).orElseThrow();
		var plan = plans.findByAthleteId(athlete.getId()).orElseThrow();

		assertThat(plan.getStartDate()).isEqualTo(today.minusDays(DemoData.HISTORY_DAYS));
		var past = plan.getWorkouts().stream().filter(w -> w.getDate().isBefore(today)).toList();
		long ridden = past.stream().filter(w -> w.getActivityId() != null).count();
		assertThat(ridden).as("die meisten vergangenen Workouts gefahren").isGreaterThan(past.size() / 2);
		assertThat(plan.getWorkouts()).filteredOn(w -> !w.getDate().isBefore(today))
				.allMatch(w -> w.getDate().isAfter(today) ? w.getActivityId() == null : true);
		assertThat(extraRides.findByAthleteId(athlete.getId())).isNotEmpty();
	}

	@Test
	void neverOnTheLiveSite() {
		DemoData live = new DemoData(true, "https://quaelmich.com/", "", null, null, null, null, null, null, null,
				null, null, new org.springframework.transaction.support.AbstractPlatformTransactionManager() {
					@Override
					protected Object doGetTransaction() {
						return new Object();
					}

					@Override
					protected void doBegin(Object transaction,
							org.springframework.transaction.TransactionDefinition definition) {
					}

					@Override
					protected void doCommit(org.springframework.transaction.support.DefaultTransactionStatus status) {
					}

					@Override
					protected void doRollback(org.springframework.transaction.support.DefaultTransactionStatus status) {
					}
				});
		assertThat(live.isEnabled()).isFalse();
		DemoData staging = new DemoData(false, "https://staging.quaelmich.com", "", null, null, null, null, null,
				null, null, null, null, null);
		assertThat(staging.isEnabled()).as("ohne DEMO_DATA aus").isFalse();
	}

}
