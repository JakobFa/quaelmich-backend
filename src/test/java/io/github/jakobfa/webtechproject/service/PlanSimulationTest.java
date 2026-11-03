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
}
