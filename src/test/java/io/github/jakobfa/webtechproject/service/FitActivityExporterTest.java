package io.github.jakobfa.webtechproject.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.garmin.fit.Decode;
import com.garmin.fit.EventMesg;
import com.garmin.fit.EventMesgListener;
import com.garmin.fit.EventType;
import com.garmin.fit.File;
import com.garmin.fit.FileIdMesg;
import com.garmin.fit.FileIdMesgListener;
import com.garmin.fit.MesgBroadcaster;
import com.garmin.fit.RecordMesg;
import com.garmin.fit.RecordMesgListener;
import com.garmin.fit.SessionMesg;
import com.garmin.fit.SessionMesgListener;
import com.garmin.fit.SubSport;

import io.github.jakobfa.webtechproject.service.FitActivityExporter.Sample;

/**
 * Schreibt aufgezeichnete Fahrten und liest sie mit dem Garmin-Decoder wieder ein – so wie Strava beim Hochladen.
 */
class FitActivityExporterTest {

	private static final Instant START = Instant.parse("2026-10-26T17:00:00Z");

	private final FitActivityExporter exporter = new FitActivityExporter(
			Clock.fixed(START, ZoneId.of("Europe/Berlin")));

	private record Decoded(List<FileIdMesg> fileIds, List<RecordMesg> records, List<EventMesg> events,
			List<SessionMesg> sessions) {
	}

	private static Decoded decode(byte[] bytes) {
		Decoded decoded = new Decoded(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
		Decode decode = new Decode();
		MesgBroadcaster broadcaster = new MesgBroadcaster(decode);
		broadcaster.addListener((FileIdMesgListener) decoded.fileIds()::add);
		broadcaster.addListener((RecordMesgListener) decoded.records()::add);
		broadcaster.addListener((EventMesgListener) decoded.events()::add);
		broadcaster.addListener((SessionMesgListener) decoded.sessions()::add);
		decode.read(new ByteArrayInputStream(bytes), broadcaster, broadcaster);
		return decoded;
	}

	/** seconds Sekunden ab from mit fester Leistung, Trittfrequenz 90 und Puls 140 */
	private static List<Sample> steady(int from, int seconds, int watts) {
		List<Sample> samples = new ArrayList<>();
		for (int s = from; s < from + seconds; s++) {
			samples.add(new Sample(s, watts, 90, 140, null));
		}
		return samples;
	}

	@Test
	void producesAValidActivityFile() {
		byte[] bytes = exporter.export(START, steady(0, 120, 200));

		assertThat(new Decode().checkFileIntegrity(new ByteArrayInputStream(bytes))).isTrue();
		assertThat(decode(bytes).fileIds()).singleElement()
				.satisfies(fileId -> assertThat(fileId.getType()).isEqualTo(File.ACTIVITY));
	}

	@Test
	void writesOneRecordPerSecondWithAllValues() {
		Decoded decoded = decode(exporter.export(START, steady(0, 60, 210)));

		assertThat(decoded.records()).hasSize(60).allSatisfy(record -> {
			assertThat(record.getPower()).isEqualTo(210);
			assertThat(record.getCadence()).isEqualTo((short) 90);
			assertThat(record.getHeartRate()).isEqualTo((short) 140);
		});
		assertThat(decoded.records().getFirst().getTimestamp().getDate().toInstant()).isEqualTo(START);
		assertThat(decoded.records().getLast().getTimestamp().getDate().toInstant())
				.isEqualTo(START.plusSeconds(59));
	}

	@Test
	void summarisesTheSessionAsIndoorRide() {
		List<Sample> samples = new ArrayList<>(steady(0, 600, 150));
		samples.addAll(steady(600, 600, 250));

		SessionMesg session = decode(exporter.export(START, samples)).sessions().getFirst();

		assertThat(session.getSubSport()).isEqualTo(SubSport.INDOOR_CYCLING);
		assertThat(session.getTotalTimerTime()).isEqualTo(1200f);
		assertThat(session.getAvgPower()).isEqualTo(200);
		assertThat(session.getMaxPower()).isEqualTo(250);
		// NP liegt über dem Schnitt, weil die harte Hälfte stärker zählt
		assertThat(session.getNormalizedPower()).isBetween(210, 220);
		assertThat(session.getAvgHeartRate()).isEqualTo((short) 140);
	}

	/** seconds Sekunden mit fester Geschwindigkeit (km/h vom Trainer) */
	private static List<Sample> ridingAt(int from, int seconds, double kmh) {
		List<Sample> samples = new ArrayList<>();
		for (int s = from; s < from + seconds; s++) {
			samples.add(new Sample(s, 200, 90, 140, kmh));
		}
		return samples;
	}

	@Test
	void writesDistanceAndSpeedFromTheTrainerSpeed() {
		// 36 km/h = 10 m/s, 10 Minuten lang = 6 km
		Decoded decoded = decode(exporter.export(START, ridingAt(0, 600, 36)));

		assertThat(decoded.records().getFirst().getSpeed()).isEqualTo(10f);
		assertThat(decoded.records().getLast().getDistance()).isEqualTo(6000f);
		SessionMesg session = decoded.sessions().getFirst();
		assertThat(session.getTotalDistance()).isEqualTo(6000f);
		assertThat(session.getAvgSpeed()).isEqualTo(10f);
		assertThat(session.getMaxSpeed()).isEqualTo(10f);
	}

	@Test
	void pausesAddNoDistance() {
		List<Sample> samples = new ArrayList<>(ridingAt(0, 300, 36));
		samples.addAll(ridingAt(420, 300, 36)); // 2 min Pause

		assertThat(decode(exporter.export(START, samples)).sessions().getFirst().getTotalDistance())
				.isEqualTo(6000f);
	}

	@Test
	void writesNoDistanceWithoutTrainerSpeed() {
		SessionMesg session = decode(exporter.export(START, steady(0, 300, 200))).sessions().getFirst();

		assertThat(session.getTotalDistance()).isNull();
		assertThat(session.getAvgSpeed()).isNull();
	}

	@Test
	void writesPausesAsTimerStopSoMovingTimeIsRight() {
		List<Sample> samples = new ArrayList<>(steady(0, 300, 200));
		samples.addAll(steady(420, 300, 200)); // 2 min Pause

		Decoded decoded = decode(exporter.export(START, samples));

		assertThat(decoded.events()).extracting(EventMesg::getEventType)
				.containsExactly(EventType.START, EventType.STOP_ALL, EventType.START, EventType.STOP_ALL);
		SessionMesg session = decoded.sessions().getFirst();
		assertThat(session.getTotalTimerTime()).isEqualTo(600f);
		assertThat(session.getTotalElapsedTime()).isEqualTo(720f);
	}

	@Test
	void keepsSecondsWithoutValues() {
		List<Sample> samples = List.of(new Sample(0, null, null, 120, null), new Sample(1, 180, null, 121, null));

		Decoded decoded = decode(exporter.export(START, samples));

		assertThat(decoded.records()).hasSize(2);
		assertThat(decoded.records().getFirst().getPower()).isNull();
		assertThat(decoded.sessions().getFirst().getAvgPower()).isEqualTo(180);
	}

	@Test
	void rejectsSecondsThatAreNotAscending() {
		List<Sample> samples = List.of(new Sample(5, 200, 90, null, null), new Sample(5, 200, 90, null, null));

		assertThatThrownBy(() -> exporter.export(START, samples)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void namesTheFileAfterDateAndWorkout() {
		assertThat(exporter.fileName(START, "Sweet Spot 3 × 12")).isEqualTo("quaelmich-2026-10-26-sweet-spot-3x12.fit");
		assertThat(exporter.fileName(START, null)).isEqualTo("quaelmich-2026-10-26.fit");
	}

}
