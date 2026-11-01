package io.github.jakobfa.webtechproject.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.garmin.fit.Decode;
import com.garmin.fit.File;
import com.garmin.fit.FileIdMesg;
import com.garmin.fit.FileIdMesgListener;
import com.garmin.fit.Intensity;
import com.garmin.fit.MesgBroadcaster;
import com.garmin.fit.Sport;
import com.garmin.fit.WktStepDuration;
import com.garmin.fit.WktStepTarget;
import com.garmin.fit.WorkoutMesg;
import com.garmin.fit.WorkoutMesgListener;
import com.garmin.fit.WorkoutStepMesg;
import com.garmin.fit.WorkoutStepMesgListener;

import io.github.jakobfa.webtechproject.model.HeartRateTargets;
import io.github.jakobfa.webtechproject.model.Workout;

/**
 * Exportiert Workouts und liest die Datei mit dem Garmin-Decoder wieder ein – so wie ein Radcomputer.
 */
class FitWorkoutExporterTest {

	private final FitWorkoutExporter exporter = new FitWorkoutExporter(
			Clock.fixed(Instant.parse("2026-10-20T10:00:00Z"), ZoneOffset.UTC));
	private final WorkoutLibrary library = new WorkoutLibrary();

	private record Decoded(List<FileIdMesg> fileIds, List<WorkoutMesg> workouts, List<WorkoutStepMesg> steps) {
	}

	private static Decoded decode(byte[] bytes) {
		Decoded decoded = new Decoded(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
		Decode decode = new Decode();
		MesgBroadcaster broadcaster = new MesgBroadcaster(decode);
		broadcaster.addListener((FileIdMesgListener) decoded.fileIds()::add);
		broadcaster.addListener((WorkoutMesgListener) decoded.workouts()::add);
		broadcaster.addListener((WorkoutStepMesgListener) decoded.steps()::add);
		decode.read(new ByteArrayInputStream(bytes), broadcaster, broadcaster);
		return decoded;
	}

	@Test
	void producesAValidFitFile() {
		byte[] bytes = exporter.export(library.findById(5L).orElseThrow());

		assertThat(new Decode().checkFileIntegrity(new ByteArrayInputStream(bytes))).isTrue();
	}

	@Test
	void containsWorkoutWithNameSportAndAllSteps() {
		Workout sweetSpot = library.findById(5L).orElseThrow();

		Decoded decoded = decode(exporter.export(sweetSpot));

		assertThat(decoded.fileIds()).singleElement()
				.satisfies(fileId -> assertThat(fileId.getType()).isEqualTo(File.WORKOUT));
		assertThat(decoded.workouts()).singleElement().satisfies(workout -> {
			assertThat(workout.getWktName()).isEqualTo("Sweet Spot 3 × 12");
			assertThat(workout.getSport()).isEqualTo(Sport.CYCLING);
			assertThat(workout.getNumValidSteps()).isEqualTo(sweetSpot.getSteps().size());
		});
		assertThat(decoded.steps()).hasSize(sweetSpot.getSteps().size());
	}

	@Test
	void writesDurationAndPowerInPercentOfFtp() {
		Decoded decoded = decode(exporter.export(library.findById(5L).orElseThrow()));

		WorkoutStepMesg warmup = decoded.steps().get(0);
		assertThat(warmup.getIntensity()).isEqualTo(Intensity.WARMUP);
		assertThat(warmup.getDurationType()).isEqualTo(WktStepDuration.TIME);
		assertThat(warmup.getDurationTime()).isEqualTo(600f);
		assertThat(warmup.getTargetType()).isEqualTo(WktStepTarget.POWER);
		assertThat(warmup.getCustomTargetPowerLow()).isEqualTo(50L);
		assertThat(warmup.getCustomTargetPowerHigh()).isEqualTo(75L);

		WorkoutStepMesg interval = decoded.steps().get(1);
		assertThat(interval.getIntensity()).isEqualTo(Intensity.INTERVAL);
		assertThat(interval.getDurationTime()).isEqualTo(720f);
		assertThat(interval.getCustomTargetPowerLow()).isEqualTo(90L);
		assertThat(interval.getCustomTargetPowerHigh()).isEqualTo(90L);
	}

	@Test
	void writesHeartRateRangesForTrainingWithoutPowerMeter() {
		var heartRate = new HeartRateTargets(List.of(123, 153, 168, 183), 189);

		Decoded decoded = decode(exporter.export(library.findById(5L).orElseThrow(), heartRate));

		WorkoutStepMesg warmup = decoded.steps().get(0); // 50 → 75 % FTP → Zone 2
		assertThat(warmup.getTargetType()).isEqualTo(WktStepTarget.HEART_RATE);
		assertThat(warmup.getCustomTargetHeartRateLow()).isEqualTo(123L + 100);
		assertThat(warmup.getCustomTargetHeartRateHigh()).isEqualTo(152L + 100);

		WorkoutStepMesg interval = decoded.steps().get(1); // 90 % FTP → Zone 3
		assertThat(interval.getCustomTargetHeartRateLow()).isEqualTo(153L + 100);
		assertThat(interval.getCustomTargetHeartRateHigh()).isEqualTo(167L + 100);
	}

	@Test
	void everyLibraryWorkoutCanBeExportedWithHeartRate() {
		var heartRate = new HeartRateTargets(List.of(123, 153, 168, 183), 189);
		assertThat(library.findAll()).allSatisfy(workout -> assertThat(new Decode()
				.checkFileIntegrity(new ByteArrayInputStream(exporter.export(workout, heartRate))))
				.as(workout.getTitle()).isTrue());
	}

	@Test
	void everyLibraryWorkoutCanBeExported() {
		assertThat(library.findAll()).allSatisfy(workout -> {
			byte[] bytes = exporter.export(workout);
			assertThat(new Decode().checkFileIntegrity(new ByteArrayInputStream(bytes)))
					.as(workout.getTitle()).isTrue();
		});
	}

	@Test
	void buildsReadableFileNames() {
		assertThat(FitWorkoutExporter.fileName(library.findById(5L).orElseThrow()))
				.isEqualTo("sweet-spot-3x12.fit");
		assertThat(FitWorkoutExporter.fileName(WorkoutLibrary.longRide(210)))
				.isEqualTo("lange-ausfahrt-3-30-h.fit");
	}

}
