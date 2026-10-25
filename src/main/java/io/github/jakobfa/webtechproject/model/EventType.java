package io.github.jakobfa.webtechproject.model;

/** Art des Ziel-Events – bestimmt zusammen mit Dauer und Höhenmetern den Trainingsschwerpunkt */
public enum EventType {
	/** Radmarathon / Jedermann (Gran Fondo) */
	GRAN_FONDO,
	ROAD_RACE,
	CRITERIUM,
	TIME_TRIAL,
	GRAVEL,
	/** Ultra-Rennen: lange Distanz auf Zeit, wenig Pausen */
	ULTRA,
	/** Bikepacking: lange Tour mit Gepäck, eigenes Tempo, viele Pausen – entspannter als ein Ultra-Rennen */
	BIKEPACKING
}
