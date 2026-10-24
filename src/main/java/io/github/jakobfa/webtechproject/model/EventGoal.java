package io.github.jakobfa.webtechproject.model;

/** Was beim Event das Ziel ist – bestimmt mit, wie hart der Plan ausfällt */
public enum EventGoal {
	/** Ankommen und genießen: eine Stufe leichter, lange Ausfahrten bleiben locker */
	FINISH,
	/** Eine gute Zeit fahren: der Plan wie berechnet (Standard) */
	GOOD_TIME,
	/** Vorne mitfahren: eine Stufe härter, mehr Renntempo, mit viel Zeit eine harte Einheit mehr */
	COMPETE
}
