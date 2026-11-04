package io.github.jakobfa.webtechproject.service;

import java.util.Random;

import io.github.jakobfa.webtechproject.model.EventGoal;
import io.github.jakobfa.webtechproject.model.EventType;
import io.github.jakobfa.webtechproject.model.Level;
import io.github.jakobfa.webtechproject.model.TrainingGoal;

/**
 * Ein zufälliger erfundener Fahrer aus einem Startwert: Profil (Level, Ziel, Event, Wochenzeit) und Verhalten
 * (Ausfälle, Pendeln, Urlaub, Rennen, Krankheit, Reisewoche, Schichtdienst, Vorgeschichte). Derselbe Startwert ergibt
 * immer denselben Fahrer – so lässt sich jeder auffällige Fall genau nachstellen.
 */
record RandomRider(long seed, PlanReport.Profile profile, RiderType type, String description) {

	static final int MAX_EVENT_WEEKS = 45;

	static RandomRider of(long seed) {
		Random r = new Random(seed);
		Level level = pick(r, Level.values());
		double[] hours = hours(r, level);
		PlanReport.Profile profile;
		String goalText;
		if (r.nextDouble() < 0.7) {
			EventType type = pick(r, EventType.values());
			EventGoal goal = pick(r, EventGoal.values());
			double km = switch (type) {
				case CRITERIUM -> 30 + r.nextInt(40);
				case TIME_TRIAL -> 10 + r.nextInt(80);
				case ROAD_RACE -> 60 + r.nextInt(160);
				case GRAN_FONDO -> 80 + r.nextInt(170);
				case GRAVEL -> 50 + r.nextInt(200);
				case ULTRA -> 300 + r.nextInt(1200);
				case BIKEPACKING -> 200 + r.nextInt(1500);
			};
			int meters = (int) (km * (2 + r.nextInt(25)));
			int weeks = 2 + r.nextInt(MAX_EVENT_WEEKS - 1);
			profile = new PlanReport.Profile("Zufall " + seed, level, TrainingGoal.EVENT, type, goal, km, meters, weeks,
					hours);
			goalText = "%s %s %.0f km/%d hm in %d Wochen".formatted(type, goal, km, meters, weeks);
		} else {
			TrainingGoal goal = pick(r, new TrainingGoal[] { TrainingGoal.FTP, TrainingGoal.ENDURANCE,
					TrainingGoal.FITNESS, TrainingGoal.CLIMBING });
			profile = new PlanReport.Profile("Zufall " + seed, level, goal, null, null, 0, 0, 0, hours);
			goalText = goal.name();
		}
		int weeks = profile.goal() == TrainingGoal.EVENT ? profile.weeks() + 1 : LoopSimulation.OPEN_WEEKS;
		RiderType type = behaviour(r, seed, weeks);
		String description = "%s, %s, Zeit Mo–So %s (%.1f h), %s".formatted(level, goalText,
				java.util.Arrays.toString(hours), sum(hours), type.name());
		return new RandomRider(seed, profile, type, description);
	}

	/** Wochenzeit: übliche Muster (unter der Woche wenig, Wochenende viel), nur Wochenende, wenige Tage, sehr viel */
	private static double[] hours(Random r, Level level) {
		double scale = switch (level) {
			case BEGINNER -> 0.6;
			case HOBBY -> 0.8;
			case AMBITIOUS -> 1.0;
			case PERFORMANCE -> 1.4;
		};
		double[] hours = new double[7];
		int pattern = r.nextInt(5);
		for (int d = 0; d < 7; d++) {
			boolean weekend = d >= 5;
			double h = switch (pattern) {
				case 0 -> weekend ? 1 + r.nextDouble() * 4 : r.nextDouble() < 0.35 ? 0 : 0.5 + r.nextDouble() * 1.5;
				case 1 -> weekend ? 1.5 + r.nextDouble() * 4 : 0; // nur am Wochenende
				case 2 -> r.nextDouble() < 0.55 ? 0 : 0.75 + r.nextDouble() * 3; // wenige, zufällige Tage
				case 3 -> weekend ? 2 + r.nextDouble() * 4 : 1 + r.nextDouble() * 1.5; // jeden Tag viel
				default -> r.nextDouble() < 0.3 ? 0 : 0.5 + r.nextDouble() * 2.5; // bunt gemischt
			};
			hours[d] = Math.round(Math.min(h * scale, 8) * 4) / 4.0;
		}
		if (sum(hours) < 1.5) {
			hours[5] = Math.max(hours[5], 1.5);
		}
		return hours;
	}

	/** Verhalten: Ausfälle, Pünktlichkeit, Pendeln, Vorgeschichte und Ereignisse an zufälligen Stellen im Plan */
	private static RiderType behaviour(Random r, long seed, int weeks) {
		double miss = r.nextDouble() * 0.25;
		double weekdayMiss = r.nextDouble() < 0.2 ? 0.2 + r.nextDouble() * 0.4 : 0;
		double compliance = 0.75 + r.nextDouble() * 0.55;
		int commute = r.nextDouble() < 0.35 ? 20 + r.nextInt(8) * 10 : 0;
		double[] pre = switch (r.nextInt(5)) {
			case 0 -> new double[] { 1.1, 1.1, 1, 0.35, 0.3, 0.3 }; // nach Off-Season
			case 1 -> new double[] { 0.5, 0.6, 0.7, 0.8, 0.9, 1 }; // steigend
			case 2 -> new double[] { 1, 1, 1, 1, 1.5, 1.6 }; // zuletzt viel (z. B. Rennen, Urlaub)
			case 3 -> new double[] { r.nextDouble() * 1.5, r.nextDouble() * 1.5, r.nextDouble() * 1.5,
					r.nextDouble() * 1.5, r.nextDouble() * 1.5, r.nextDouble() * 1.5 }; // unregelmäßig
			default -> new double[] { 1, 1, 1, 1, 1, 1 };
		};
		int vacation = weeks > 4 && r.nextDouble() < 0.2 ? 1 + r.nextInt(weeks - 3) : -1;
		int vacationWeeks = vacation >= 0 ? 1 + r.nextInt(3) : 0;
		int race = weeks > 3 && r.nextDouble() < 0.25 ? 1 + r.nextInt(weeks - 2) : -1;
		int illness = weeks > 3 && r.nextDouble() < 0.25 ? r.nextInt(weeks - 1) : -1;
		int illnessDays = illness >= 0 ? 3 + r.nextInt(18) : 0;
		int travel = weeks > 3 && r.nextDouble() < 0.2 ? r.nextInt(weeks - 1) : -1;
		boolean shift = r.nextDouble() < 0.15;
		StringBuilder name = new StringBuilder("Ausfälle %.0f %%".formatted(miss * 100));
		if (weekdayMiss > 0) {
			name.append(", unter der Woche +%.0f %%".formatted(weekdayMiss * 100));
		}
		name.append(", fährt %.0f %% der Dauer".formatted(compliance * 100));
		if (commute > 0) {
			name.append(", pendelt %d min".formatted(commute));
		}
		if (vacation >= 0) {
			name.append(", Urlaub Woche %d (%d Wo.)".formatted(vacation + 1, vacationWeeks));
		}
		if (race >= 0) {
			name.append(", Rennen Woche %d".formatted(race + 1));
		}
		if (illness >= 0) {
			name.append(", krank Woche %d (%d Tage)".formatted(illness + 1, illnessDays));
		}
		if (travel >= 0) {
			name.append(", Reisewoche %d".formatted(travel + 1));
		}
		if (shift) {
			name.append(", Schichtdienst");
		}
		name.append(" [#").append(seed).append(']');
		return new RiderType(name.toString(), miss, weekdayMiss, compliance, commute, pre, vacation, vacationWeeks,
				race, illness, illnessDays, travel, shift);
	}

	private static <T> T pick(Random r, T[] values) {
		return values[r.nextInt(values.length)];
	}

	private static double sum(double[] values) {
		double total = 0;
		for (double value : values) {
			total += value;
		}
		return total;
	}
}
