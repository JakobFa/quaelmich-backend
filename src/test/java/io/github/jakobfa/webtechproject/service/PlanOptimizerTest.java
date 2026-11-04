package io.github.jakobfa.webtechproject.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Random;

import org.junit.jupiter.api.Test;

/** Die Suche bleibt in ihren Bereichen, verschlechtert nie und schlägt nichts vor, was sie nicht bewertet hat */
class PlanOptimizerTest {

	@Test
	void neighboursStayInsideTheAllowedRanges() {
		Random random = new Random(1);
		PlanParameters p = PlanParameters.DEFAULT;
		for (int i = 0; i < 500; i++) {
			p = PlanOptimizer.neighbour(p, random, 3);
			var values = PlanOptimizer.values(p);
			for (PlanOptimizer.Dim dim : PlanOptimizer.DIMS) {
				assertThat(values.get(dim.name())).as(dim.name()).isBetween(dim.min(), dim.max());
			}
		}
	}

	@Test
	void valuesAndBuildRoundTrip() {
		assertThat(PlanOptimizer.build(PlanOptimizer.values(PlanParameters.DEFAULT))).isEqualTo(PlanParameters.DEFAULT);
		assertThat(PlanOptimizer.changes(PlanParameters.DEFAULT)).isEqualTo("keine Änderung");
		assertThat(PlanOptimizer.changes(ParameterEvaluation.parse("taperWeeks=3"))).isEqualTo("taperWeeks 2 → 3");
	}

	@Test
	void theSearchNeverReturnsSomethingWorseThanTheAppValues() {
		var result = PlanOptimizer.search(8, 20, 42);
		assertThat(result.evaluations()).isGreaterThan(1);
		assertThat(result.trainBest().objective()).isGreaterThanOrEqualTo(result.trainDefault().objective());
		assertThat(ParameterEvaluation.evaluate(result.best(), PlanOptimizer.TRAIN_SEED, 20, true).objective())
				.isEqualTo(result.trainBest().objective());
	}
}
