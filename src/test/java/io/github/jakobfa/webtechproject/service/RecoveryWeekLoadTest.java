package io.github.jakobfa.webtechproject.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Eine Erholungswoche ist nie schwerer als die Belastungswochen davor – auch nicht bei wenig Zeit, wenn die
 * Belastungswochen schon auf kurzes Ausrollen gekürzt sind. Die Fahrer stammen aus der Zufallssuche, bei der das
 * vorher vorkam (Startwerte zum Nachstellen).
 */
class RecoveryWeekLoadTest {

	@ParameterizedTest
	@ValueSource(longs = { 20763063930L, 20763064090L, 20763067217L })
	void aRecoveryWeekIsLighterThanTheLoadWeeksBefore(long seed) {
		check(RandomRider.of(seed));
	}

	@ParameterizedTest
	@ValueSource(longs = { 20763063071L, 20763064085L, 20763064230L })
	void alsoForRidersWhoFollowThePlan(long seed) {
		check(RandomRider.follower(seed));
	}

	private static void check(RandomRider rider) {
		List<LoopSimulation.Week> weeks = LoopSimulation.run(rider.profile(), rider.type()).weeks();
		int recoveryWeeks = 0;
		for (int i = 1; i < weeks.size(); i++) {
			LoopSimulation.Week week = weeks.get(i);
			if (!"RECOVERY".equals(week.phase())) {
				continue;
			}
			List<LoopSimulation.Week> before = weeks.subList(Math.max(0, i - 3), i).stream()
					.filter(w -> isLoad(w.phase()) && w.plannedTss() > 0).toList();
			if (before.isEmpty()) {
				continue;
			}
			recoveryWeeks++;
			double load = before.stream().mapToInt(LoopSimulation.Week::plannedTss).average().orElseThrow();
			assertThat(week.plannedTss()).as("Erholungswoche ab %s (%s)", week.monday(), rider.description())
					.isLessThan((int) Math.round(load));
		}
		assertThat(recoveryWeeks).as("Erholungswochen mit Belastungswochen davor").isPositive();
	}

	private static boolean isLoad(String phase) {
		return "BASE".equals(phase) || "BUILD".equals(phase) || "PEAK".equals(phase);
	}
}
