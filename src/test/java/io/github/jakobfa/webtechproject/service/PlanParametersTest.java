package io.github.jakobfa.webtechproject.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import io.github.jakobfa.webtechproject.model.Phase;

/** Stellschrauben: ohne Bindung gelten die Werte der App, gebunden nur im eigenen Lauf; dazu das Leistungsmodell */
class PlanParametersTest {

	@Test
	void withoutBindingTheAppValuesApply() {
		assertThat(PlanParameters.current()).isEqualTo(PlanParameters.DEFAULT);
		var rider = RandomRider.of(77);
		var unbound = LoopSimulation.run(rider.profile(), rider.type()).weeks();
		var bound = PlanParameters.DEFAULT.apply(() -> LoopSimulation.run(rider.profile(), rider.type()).weeks());
		assertThat(bound).isEqualTo(unbound);
	}

	@Test
	void boundValuesChangeThePlanOnlyInsideTheirRun() {
		PlanParameters four = ParameterEvaluation.parse("loadWeeksPerBlock=4");
		List<Phase> phases = four.apply(() -> PlanGenerator.phasesFor(20));
		assertThat(phases.subList(0, 5)).containsExactly(Phase.BASE, Phase.BASE, Phase.BASE, Phase.BASE,
				Phase.RECOVERY);
		assertThat(PlanGenerator.phasesFor(20).get(3)).as("außerhalb wieder die App-Werte").isEqualTo(Phase.RECOVERY);
		assertThat(PlanParameters.current()).isEqualTo(PlanParameters.DEFAULT);
	}

	@Test
	void parallelRunsDoNotInfluenceEachOther() {
		PlanParameters four = ParameterEvaluation.parse("loadWeeksPerBlock=4");
		var results = java.util.stream.IntStream.range(0, 200).parallel()
				.mapToObj(i -> (i % 2 == 0 ? four : PlanParameters.DEFAULT).apply(() -> PlanGenerator.phasesFor(20)))
				.toList();
		for (int i = 0; i < results.size(); i++) {
			assertThat(results.get(i).get(3)).isEqualTo(i % 2 == 0 ? Phase.BASE : Phase.RECOVERY);
		}
	}

	@Test
	void parseRejectsUnknownNames() {
		org.assertj.core.api.Assertions.assertThatThrownBy(() -> ParameterEvaluation.parse("gibtsnicht=1"))
				.isInstanceOf(IllegalArgumentException.class);
		assertThat(ParameterEvaluation.parse("recoveryVolume=0.5").recoveryVolume()).isEqualTo(0.5);
	}

	@Test
	void theModelRewardsATaper() {
		LocalDate start = LocalDate.of(2026, 11, 2);
		LocalDate target = start.plusWeeks(8);
		Map<LocalDate, Integer> steady = new TreeMap<>();
		Map<LocalDate, Integer> tapered = new TreeMap<>();
		for (LocalDate day = start.minusDays(42); day.isBefore(target); day = day.plusDays(1)) {
			steady.put(day, 80);
			tapered.put(day, day.isBefore(target.minusDays(10)) ? 80 : 30);
		}
		var model = PerformanceModel.typical();
		var withoutTaper = model.evaluate(steady, start, target);
		var withTaper = model.evaluate(tapered, start, target);
		assertThat(withTaper.formAtTarget()).isGreaterThan(withoutTaper.formAtTarget());
		assertThat(withoutTaper.overload()).isZero();
	}

	@Test
	void buildingUpTooFastIsPenalised() {
		LocalDate start = LocalDate.of(2026, 11, 2);
		Map<LocalDate, Integer> jump = new TreeMap<>();
		for (LocalDate day = start.minusDays(42); day.isBefore(start.plusWeeks(4)); day = day.plusDays(1)) {
			jump.put(day, day.isBefore(start) ? 30 : 150);
		}
		assertThat(PerformanceModel.typical().evaluate(jump, start, start.plusWeeks(4)).overload()).isPositive();
	}
}
