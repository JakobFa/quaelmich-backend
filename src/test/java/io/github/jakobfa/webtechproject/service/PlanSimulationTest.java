package io.github.jakobfa.webtechproject.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Jeder erfundene Fahrer fährt seinen Plan mit Abweichungen; hart verboten sind mehr Minuten als der Tag hat und
 * eine Erschöpfung unter {@link PlanSimulation#HARD_MIN_TSB}. Dieselbe Simulation läuft täglich in GitHub Actions
 * und schreibt den Bericht für die Plan-Prüfung.
 */
class PlanSimulationTest {

	@Test
	void noSimulatedRiderBreaksTheHardLimits() {
		PlanReport.profiles().forEach(profile -> assertThat(PlanSimulation.simulate(profile).hard())
				.as(profile.name()).isEmpty());
	}

	@Test
	void theSimulationIsReproducible() {
		var profile = PlanReport.profiles().getFirst();
		assertThat(PlanSimulation.simulate(profile).weeks()).isEqualTo(PlanSimulation.simulate(profile).weeks());
	}

	@Test
	void noRiderTypeBreaksTheHardLimitsInTheWeeklyLoop() {
		for (RiderType type : RiderType.all()) {
			for (PlanReport.Profile profile : PlanReport.profiles()) {
				assertThat(LoopSimulation.run(profile, type).hard()).as(profile.name() + " | " + type.name()).isEmpty();
			}
		}
	}

	@Test
	void theWeeklyLoopIsReproducible() {
		var profile = PlanReport.profiles().getLast();
		var type = RiderType.all().get(4);
		assertThat(LoopSimulation.run(profile, type).weeks()).isEqualTo(LoopSimulation.run(profile, type).weeks());
	}

	@Test
	void afterAHolidayWithLotsOfRidingTheLoadIsEased() {
		RiderType holiday = RiderType.all().stream().filter(type -> type.vacationWeek() >= 0).findFirst().orElseThrow();
		long withoutRelief = PlanReport.profiles().stream()
				.filter(profile -> LoopSimulation.run(profile, holiday).warnings().stream()
						.anyMatch(warning -> warning.startsWith("nach dem Urlaub")))
				.count();
		assertThat(withoutRelief).as("Läufe ohne Entlastung nach dem Urlaub").isLessThanOrEqualTo(2);
	}

	@Test
	void randomRidersAreReproducibleAlsoInParallel() {
		var seeds = java.util.stream.LongStream.range(4242, 4282).boxed().toList();
		var sequential = seeds.stream().map(seed -> LoopSimulation.run(RandomRider.of(seed).profile(),
				RandomRider.of(seed).type()).weeks()).toList();
		var parallel = seeds.parallelStream().map(seed -> LoopSimulation.run(RandomRider.of(seed).profile(),
				RandomRider.of(seed).type()).weeks()).toList();
		assertThat(parallel).isEqualTo(sequential);
		assertThat(RandomRider.of(4242).description()).isEqualTo(RandomRider.of(4242).description());
	}

	@Test
	void aFewHundredRandomRidersKeepTheHardLimits() {
		java.util.stream.LongStream.range(1, 300).parallel().forEach(seed -> {
			RandomRider rider = RandomRider.of(seed);
			assertThat(LoopSimulation.run(rider.profile(), rider.type()).hard()).as(rider.description()).isEmpty();
		});
	}
}
