package io.github.jakobfa.webtechproject.model;

import java.util.List;

import org.hibernate.annotations.ColumnDefault;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Convert;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Das Profil einer Radfahrerin bzw. eines Radfahrers mit Leistungsdaten, Ziel-Event und Verfügbarkeit.
 * Heißt bewusst nicht "User", weil "user" in Postgres ein reserviertes Wort ist.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Athlete {

	@Id
	@GeneratedValue
	private Long id;

	/** Der Nutzer, dem dieses Profil gehört (wird nie ans Frontend geschickt) */
	@JsonIgnore
	@OneToOne
	@JoinColumn(unique = true)
	private AppUser owner;

	/** Functional Threshold Power in Watt; im Puls-Modus optional */
	@Min(50)
	@Max(600)
	private Integer ftp;

	// ---------- Hinweis "FTP wahrscheinlich zu niedrig" (setzt nur quaelmich, nie der Nutzer) ----------

	/** geschätzte FTP aus einer Fahrt, die mit der eingetragenen FTP eigentlich nicht möglich ist */
	private Integer suggestedFtp;

	/** Begründung, z. B. "Am Sa, 24.10. bist du 75 min mit 272 W (Normalized Power) gefahren." */
	private String suggestedFtpNote;

	/** Vorschlag, den der Nutzer ausgeblendet hat – derselbe Wert wird nicht wieder gezeigt */
	private Integer dismissedSuggestedFtp;

	/** Training nach Watt oder Puls; null wird wie POWER behandelt (ältere Profile) */
	@Enumerated(EnumType.STRING)
	@ColumnDefault("'POWER'")
	private TrainingMode trainingMode = TrainingMode.POWER;

	/** Maximalpuls in Schlägen pro Minute, optional – Grundlage für den Puls-TSS */
	@Min(120)
	@Max(230)
	private Integer maxHeartRate;

	/**
	 * Schwellenpuls (LTHR), optional – nur nötig, wenn er aus einem Test genau bekannt ist.
	 * Sonst gilt 90 % des Maximalpulses.
	 */
	@Min(100)
	@Max(220)
	private Integer thresholdHeartRate;

	/**
	 * Eigene Pulszonen, optional: Beginn von Zone 2, 3, 4 und 5 in bpm (Zone 1 beginnt bei 0),
	 * z. B. [123, 153, 168, 183]. Ohne eigene Zonen werden sie aus dem Maximalpuls berechnet.
	 */
	@Convert(converter = IntegerListConverter.class)
	private List<Integer> heartRateZoneStarts;

	/** Zonen müssen aus 4 aufsteigenden, sinnvollen Pulswerten bestehen (oder fehlen) */
	@JsonIgnore
	@AssertTrue(message = "Pulszonen: 4 aufsteigende Werte zwischen 60 und 230")
	public boolean isHeartRateZoneStartsValid() {
		if (heartRateZoneStarts == null || heartRateZoneStarts.isEmpty()) {
			return true;
		}
		if (heartRateZoneStarts.size() != 4 || heartRateZoneStarts.stream().anyMatch(java.util.Objects::isNull)) {
			return false;
		}
		for (int i = 0; i < 4; i++) {
			int value = heartRateZoneStarts.get(i);
			if (value < 60 || value > 230 || (i > 0 && value <= heartRateZoneStarts.get(i - 1))) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Eigene Leistungszonen für ambitionierte Fahrer: Beginn von Zone 2–7 in % der FTP, z. B. aus einem
	 * Leistungstest. Fehlen sie, gelten die klassischen Zonen nach Coggan (56/76/91/106/121/151 %).
	 */
	@Convert(converter = IntegerListConverter.class)
	private List<Integer> powerZoneStarts;

	@JsonIgnore
	@AssertTrue(message = "Leistungszonen: 6 aufsteigende Werte zwischen 30 und 250 % FTP")
	public boolean isPowerZoneStartsValid() {
		if (powerZoneStarts == null || powerZoneStarts.isEmpty()) {
			return true;
		}
		if (powerZoneStarts.size() != 6 || powerZoneStarts.stream().anyMatch(java.util.Objects::isNull)) {
			return false;
		}
		for (int i = 0; i < 6; i++) {
			int value = powerZoneStarts.get(i);
			if (value < 30 || value > 250 || (i > 0 && value <= powerZoneStarts.get(i - 1))) {
				return false;
			}
		}
		return true;
	}

	/** Mit Wattmesser braucht es die FTP, ohne Wattmesser Maximalpuls oder Pulszonen */
	@JsonIgnore
	@AssertTrue(message = "Training nach Watt braucht die FTP, nach Puls den Maximalpuls oder Pulszonen")
	public boolean isTrainingModeComplete() {
		return isHeartRateMode() ? getHeartRateTargets() != null : ftp != null;
	}

	@JsonIgnore
	public boolean isHeartRateMode() {
		return trainingMode == TrainingMode.HEART_RATE;
	}

	/** Anteile des Maximalpulses, bei denen Zone 2–5 beginnen (wie bei Strava) */
	private static final double[] ZONE_SHARES_OF_MAX = { 0.65, 0.81, 0.89, 0.97 };

	/**
	 * Pulsbereiche fürs Training: eigene Zonen, sonst aus dem Maximalpuls berechnet.
	 * Null, wenn weder Zonen noch Maximalpuls bekannt sind.
	 */
	@JsonIgnore
	public HeartRateTargets getHeartRateTargets() {
		if (heartRateZoneStarts != null && heartRateZoneStarts.size() == 4 && isHeartRateZoneStartsValid()) {
			int max = maxHeartRate != null ? maxHeartRate
					: (int) Math.round(heartRateZoneStarts.get(3) / ZONE_SHARES_OF_MAX[3]);
			return new HeartRateTargets(heartRateZoneStarts, Math.max(max, heartRateZoneStarts.get(3) + 1));
		}
		if (maxHeartRate == null) {
			return null;
		}
		List<Integer> starts = java.util.Arrays.stream(ZONE_SHARES_OF_MAX)
				.mapToObj(share -> (int) Math.round(maxHeartRate * share)).toList();
		return new HeartRateTargets(starts, maxHeartRate);
	}

	/** Schwellenpuls ≈ 90 % des Maximalpulses (Faustregel) */
	public static final double THRESHOLD_SHARE_OF_MAX = 0.9;

	/** Eingetragener Schwellenpuls, sonst 90 % des Maximalpulses; null, wenn beides fehlt */
	@JsonIgnore
	public Integer getEffectiveThresholdHeartRate() {
		if (thresholdHeartRate != null) {
			return thresholdHeartRate;
		}
		return maxHeartRate == null ? null : (int) Math.round(maxHeartRate * THRESHOLD_SHARE_OF_MAX);
	}

	@NotNull
	@DecimalMin("30")
	@DecimalMax("200")
	private Double weightKg;

	@NotNull
	@Enumerated(EnumType.STRING)
	private Level level;

	@Valid
	@NotNull
	@OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
	private Event event;

	@Valid
	@NotNull
	@Embedded
	private WeeklyAvailability availability;

}
