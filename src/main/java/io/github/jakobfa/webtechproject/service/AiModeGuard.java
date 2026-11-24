package io.github.jakobfa.webtechproject.service;

import org.springframework.stereotype.Component;

import io.github.jakobfa.webtechproject.model.Athlete;

/**
 * Bei eingeschaltetem KI-Modus ändert quaelmich den Plan nicht mehr von selbst: Er gehört dann dem Nutzer und seiner
 * KI. Gesperrt ist nur die Automatik (Anpassung an gefahrene Werte, Neuberechnung pro Woche, Verlängerung ohne Event,
 * Anpassung nach dem Import von Fahrten). Was der Nutzer selbst in der App tut und was die KI ändert, gilt weiter.
 */
@Component
public class AiModeGuard {

	/** Hat der Besitzer dieses Profils den KI-Modus an? */
	public boolean automaticChangesBlocked(Athlete athlete) {
		return athlete != null && athlete.getOwner() != null && athlete.getOwner().isAiModeEnabled();
	}
}
