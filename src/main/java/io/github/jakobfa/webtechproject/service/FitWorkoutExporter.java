package io.github.jakobfa.webtechproject.service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Component;

import com.garmin.fit.BufferEncoder;
import com.garmin.fit.DateTime;
import com.garmin.fit.File;
import com.garmin.fit.FileIdMesg;
import com.garmin.fit.Fit;
import com.garmin.fit.Intensity;
import com.garmin.fit.Manufacturer;
import com.garmin.fit.Sport;
import com.garmin.fit.SubSport;
import com.garmin.fit.WktStepDuration;
import com.garmin.fit.WktStepTarget;
import com.garmin.fit.WorkoutMesg;
import com.garmin.fit.WorkoutStepMesg;

import io.github.jakobfa.webtechproject.model.HeartRateTargets;
import io.github.jakobfa.webtechproject.model.StepKind;
import io.github.jakobfa.webtechproject.model.Workout;
import io.github.jakobfa.webtechproject.model.WorkoutStep;

/**
 * Schreibt ein Workout als .fit-Datei, die Garmin-, Wahoo- und andere Radcomputer als
 * geplantes Workout laden können.
 *
 * Die Leistung wird in Prozent der FTP angegeben (FIT-Werte 0–1000 bedeuten "% FTP");
 * das Gerät rechnet mit der dort eingestellten FTP selbst in Watt um.
 * Jeder Abschnitt hat genau einen festen Wert: Ein Zielbereich (z. B. "140-210") ist für den Radcomputer nicht
 * eindeutig, er weiß dann nicht, welche Leistung er dem Trainer im ERG-Modus vorgeben soll. Rampen
 * (z. B. Aufwärmen 50 → 75 %) werden deshalb als Treppe aus festen Blöcken geschrieben.
 *
 * Für Training ohne Wattmesser werden stattdessen Pulsbereiche in bpm geschrieben
 * (FIT-Werte über 100 bedeuten "bpm + 100"); kurze harte Abschnitte bleiben ohne Ziel.
 */
@Component
public class FitWorkoutExporter {

	/** so lang ist ein Treppenblock, in den eine Rampe zerlegt wird */
	private static final int RAMP_BLOCK_SECONDS = 120;

	private final Clock clock;

	public FitWorkoutExporter(Clock clock) {
		this.clock = clock;
	}

	public byte[] export(Workout workout) {
		return export(workout, null);
	}

	/** @param heartRate Pulsbereiche fürs Training ohne Wattmesser; null = nach Watt */
	public byte[] export(Workout workout, HeartRateTargets heartRate) {
		List<WorkoutStep> steps = heartRate == null ? withoutRamps(workout.getSteps()) : workout.getSteps();

		BufferEncoder encoder = new BufferEncoder(Fit.ProtocolVersion.V2_0);
		encoder.write(fileId());
		encoder.write(workoutMesg(workout, steps.size()));
		for (int i = 0; i < steps.size(); i++) {
			encoder.write(stepMesg(steps.get(i), i, heartRate));
		}
		return encoder.close();
	}

	/** Rampen werden zu Blöcken mit festem Wert (Mitte des jeweiligen Stücks), alle anderen Abschnitte bleiben */
	static List<WorkoutStep> withoutRamps(List<WorkoutStep> steps) {
		List<WorkoutStep> result = new ArrayList<>();
		for (WorkoutStep step : steps) {
			if (step.powerStartPercent() == step.powerEndPercent()) {
				result.add(step);
				continue;
			}
			int blocks = Math.max(1, Math.round(step.durationSeconds() / (float) RAMP_BLOCK_SECONDS));
			int from = 0;
			for (int i = 0; i < blocks; i++) {
				int to = (int) Math.round((i + 1) * (double) step.durationSeconds() / blocks);
				double middle = (from + to) / 2.0 / step.durationSeconds();
				int percent = (int) Math.round(
						step.powerStartPercent() + (step.powerEndPercent() - step.powerStartPercent()) * middle);
				result.add(WorkoutStep.steady(step.kind(), to - from, percent));
				from = to;
			}
		}
		return result;
	}

	private FileIdMesg fileId() {
		FileIdMesg fileId = new FileIdMesg();
		fileId.setType(File.WORKOUT);
		fileId.setManufacturer(Manufacturer.DEVELOPMENT);
		fileId.setProduct(0);
		fileId.setSerialNumber(1L);
		fileId.setTimeCreated(new DateTime(Date.from(clock.instant())));
		return fileId;
	}

	private WorkoutMesg workoutMesg(Workout workout, int stepCount) {
		WorkoutMesg mesg = new WorkoutMesg();
		mesg.setWktName(workout.getTitle());
		mesg.setWktDescription(workout.getDescription());
		mesg.setSport(Sport.CYCLING);
		mesg.setSubSport(SubSport.ROAD);
		mesg.setNumValidSteps(stepCount);
		return mesg;
	}

	private WorkoutStepMesg stepMesg(WorkoutStep step, int index, HeartRateTargets heartRate) {
		WorkoutStepMesg mesg = new WorkoutStepMesg();
		mesg.setMessageIndex(index);
		mesg.setWktStepName(stepName(step.kind()));
		mesg.setIntensity(intensity(step.kind()));

		mesg.setDurationType(WktStepDuration.TIME);
		mesg.setDurationTime((float) step.durationSeconds());

		if (heartRate != null) {
			HeartRateTargets.Range range = heartRate.forStep(step);
			if (range == null) {
				mesg.setTargetType(WktStepTarget.OPEN); // kurz und hart: nach Gefühl
			} else {
				mesg.setTargetType(WktStepTarget.HEART_RATE);
				mesg.setTargetValue(0L);
				mesg.setCustomTargetHeartRateLow((long) range.low() + 100);
				mesg.setCustomTargetHeartRateHigh((long) range.high() + 100);
			}
			return mesg;
		}

		long low = Math.min(step.powerStartPercent(), step.powerEndPercent());
		long high = Math.max(step.powerStartPercent(), step.powerEndPercent());
		mesg.setTargetType(WktStepTarget.POWER);
		mesg.setTargetValue(0L); // 0 = eigener Bereich statt vordefinierter Zone
		mesg.setCustomTargetPowerLow(low);
		mesg.setCustomTargetPowerHigh(high);
		return mesg;
	}

	static Intensity intensity(StepKind kind) {
		return switch (kind) {
			case WARMUP -> Intensity.WARMUP;
			case COOLDOWN -> Intensity.COOLDOWN;
			case RECOVERY -> Intensity.RECOVERY;
			case INTERVAL -> Intensity.INTERVAL;
			case STEADY -> Intensity.ACTIVE;
		};
	}

	private static String stepName(StepKind kind) {
		return switch (kind) {
			case WARMUP -> "Aufwaermen";
			case COOLDOWN -> "Ausfahren";
			case RECOVERY -> "Pause";
			case INTERVAL -> "Intervall";
			case STEADY -> "Gleichmaessig";
		};
	}

	/** Dateiname ohne Sonderzeichen, z. B. "Sweet Spot 3 × 12" → "sweet-spot-3x12.fit" */
	public static String fileName(Workout workout) {
		String slug = workout.getTitle().toLowerCase()
				.replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
				.replace(" × ", "x").replace(":", "-")
				.replaceAll("[^a-z0-9]+", "-")
				.replaceAll("(^-|-$)", "");
		return slug + ".fit";
	}

}
