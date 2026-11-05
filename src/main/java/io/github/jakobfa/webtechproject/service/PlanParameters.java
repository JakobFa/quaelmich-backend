package io.github.jakobfa.webtechproject.service;

import java.util.function.Supplier;

/**
 * Die Stellschrauben des Plans an einer Stelle. Die App rechnet immer mit {@link #DEFAULT}; nur die Optimierung
 * (Test-Code) bindet für einzelne Läufe andere Werte – über einen ScopedValue, damit parallele Läufe sich nicht
 * gegenseitig stören und nichts davon in die App gelangt.
 *
 * @param loadWeeksPerBlock          Belastungswochen, dann eine Erholungswoche
 * @param volumeStep                 Umfangszuwachs je Belastungswoche (Anteil der verfügbaren Zeit)
 * @param recoveryVolume             Erholungswoche: so viel vom Umfang (und der langen Ausfahrt) der Belastungswochen
 * @param recoveryAcuteRatio         Erholungswoche: höchstens so viel vom Schnitt der letzten 28 Tage; ebenso die
 *                                   Grenze, ab der eine gefahrene Woche "so ruhig wie eine Erholungswoche" war
 * @param acuteChronicRatio          Belastungswoche: höchstens so viel vom Schnitt der letzten 28 Tage in 7 Tagen
 * @param restedBlockRatio           unter so viel der 3 Wochen davor gelten die letzten 3 Wochen als ruhig
 * @param spikeRatio                 ab so viel mehr als die Wochen davor ist es eine Spitze (Erholung früher)
 * @param spikeMinExtraMinutes       ... und mindestens so viele Minuten pro Woche mehr
 * @param maxLoadWeeksWithoutRecovery höchstens so viele Belastungswochen am Stück
 * @param memoryWeeklyDecay          Trainingsgedächtnis: so viel bleibt je Woche Abstand
 * @param taperWeeks                 Wochen Tapering vor dem Event (bei Plänen ab 3 Wochen)
 */
public record PlanParameters(int loadWeeksPerBlock, double volumeStep, double recoveryVolume,
		double recoveryAcuteRatio, double acuteChronicRatio, double restedBlockRatio, double spikeRatio,
		int spikeMinExtraMinutes, int maxLoadWeeksWithoutRecovery, double memoryWeeklyDecay, int taperWeeks) {

	/** Die Werte der App */
	public static final PlanParameters DEFAULT = new PlanParameters(3, 0.05, 0.6, 0.75, 1.18, 0.9, 1.35, 90, 6,
			0.95, 2);

	private static final ScopedValue<PlanParameters> CURRENT = ScopedValue.newInstance();

	/** Die Werte für den laufenden Plan: DEFAULT, außer ein Test hat für diesen Lauf andere gebunden */
	static PlanParameters current() {
		return CURRENT.orElse(DEFAULT);
	}

	/** Rechnet supplier mit diesen Werten (nur für Simulation und Optimierung) */
	public <T> T apply(Supplier<T> supplier) {
		return ScopedValue.where(CURRENT, this).call(supplier::get);
	}
}
