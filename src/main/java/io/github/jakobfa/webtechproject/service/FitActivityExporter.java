package io.github.jakobfa.webtechproject.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.garmin.fit.Activity;
import com.garmin.fit.ActivityMesg;
import com.garmin.fit.BufferEncoder;
import com.garmin.fit.DateTime;
import com.garmin.fit.Event;
import com.garmin.fit.EventMesg;
import com.garmin.fit.EventType;
import com.garmin.fit.File;
import com.garmin.fit.FileIdMesg;
import com.garmin.fit.Fit;
import com.garmin.fit.LapMesg;
import com.garmin.fit.Manufacturer;
import com.garmin.fit.RecordMesg;
import com.garmin.fit.SessionMesg;
import com.garmin.fit.Sport;
import com.garmin.fit.SubSport;
import com.garmin.fit.TimerTrigger;

/**
 * Schreibt eine im Workout-Player aufgezeichnete Indoor-Fahrt als .fit-Aktivität – so, wie sie ein
 * Radcomputer speichern würde. Strava, intervals.icu und Garmin Connect nehmen die Datei beim Hochladen an.
 *
 * Ein Record je gefahrener Sekunde; Lücken zwischen den Sekunden werden als Pause (Timer aus/an) geschrieben,
 * damit die Fahrzeit stimmt und Strava die Pause nicht als Stillstand mitrechnet.
 */
@Component
public class FitActivityExporter {

	/** Ein Messpunkt: Sekunden seit dem Start (Uhrzeit), Werte null = nicht gemessen */
	/** @param speed km/h laut Trainer (virtuelle Geschwindigkeit), null = nicht gemessen */
	public record Sample(int second, Integer power, Integer cadence, Integer heartRate, Double speed) {
	}

	private final Clock clock;

	public FitActivityExporter(Clock clock) {
		this.clock = clock;
	}

	/** @throws IllegalArgumentException wenn die Sekunden nicht streng aufsteigend sind */
	public byte[] export(Instant startedAt, List<Sample> samples) {
		if (samples.isEmpty()) {
			throw new IllegalArgumentException("keine Messpunkte");
		}
		for (int i = 1; i < samples.size(); i++) {
			if (samples.get(i).second() <= samples.get(i - 1).second()) {
				throw new IllegalArgumentException("Sekunden müssen aufsteigend sein");
			}
		}

		Sample last = samples.getLast();
		DateTime start = at(startedAt, samples.getFirst().second());
		DateTime end = at(startedAt, last.second() + 1);
		float timerSeconds = samples.size();
		float elapsedSeconds = last.second() + 1 - samples.getFirst().second();
		Summary summary = Summary.of(samples);

		BufferEncoder encoder = new BufferEncoder(Fit.ProtocolVersion.V2_0);
		encoder.write(fileId(start));
		encoder.write(timer(start, EventType.START));

		Sample previous = null;
		double meters = 0;
		for (Sample sample : samples) {
			if (previous != null && sample.second() > previous.second() + 1) {
				encoder.write(timer(at(startedAt, previous.second() + 1), EventType.STOP_ALL));
				encoder.write(timer(at(startedAt, sample.second()), EventType.START));
			}
			// jeder Messpunkt steht für eine gefahrene Sekunde: km/h geteilt durch 3,6 sind Meter in dieser Sekunde
			meters += sample.speed() == null ? 0 : sample.speed() / 3.6;
			encoder.write(record(at(startedAt, sample.second()), sample, meters));
			previous = sample;
		}

		encoder.write(timer(end, EventType.STOP_ALL));
		encoder.write(lap(start, end, elapsedSeconds, timerSeconds, summary, meters));
		encoder.write(session(start, end, elapsedSeconds, timerSeconds, summary, meters));
		encoder.write(activity(end, timerSeconds));
		return encoder.close();
	}

	private static DateTime at(Instant startedAt, int second) {
		return new DateTime(Date.from(startedAt.plusSeconds(second)));
	}

	private FileIdMesg fileId(DateTime created) {
		FileIdMesg fileId = new FileIdMesg();
		fileId.setType(File.ACTIVITY);
		fileId.setManufacturer(Manufacturer.DEVELOPMENT);
		fileId.setProduct(0);
		fileId.setSerialNumber(1L);
		fileId.setTimeCreated(created);
		return fileId;
	}

	private static EventMesg timer(DateTime timestamp, EventType type) {
		EventMesg event = new EventMesg();
		event.setTimestamp(timestamp);
		event.setEvent(Event.TIMER);
		event.setEventType(type);
		event.setTimerTrigger(TimerTrigger.MANUAL);
		return event;
	}

	private static RecordMesg record(DateTime timestamp, Sample sample, double meters) {
		RecordMesg record = new RecordMesg();
		record.setTimestamp(timestamp);
		if (sample.speed() != null) {
			record.setSpeed((float) (sample.speed() / 3.6));
			record.setDistance((float) meters);
		}
		if (sample.power() != null) {
			record.setPower(sample.power());
		}
		if (sample.cadence() != null) {
			record.setCadence(sample.cadence().shortValue());
		}
		if (sample.heartRate() != null) {
			record.setHeartRate(sample.heartRate().shortValue());
		}
		return record;
	}

	private static LapMesg lap(DateTime start, DateTime end, float elapsed, float timer, Summary summary,
			double meters) {
		LapMesg lap = new LapMesg();
		lap.setMessageIndex(0);
		lap.setTimestamp(end);
		lap.setStartTime(start);
		lap.setEvent(Event.LAP);
		lap.setEventType(EventType.STOP);
		lap.setSport(Sport.CYCLING);
		lap.setSubSport(SubSport.INDOOR_CYCLING);
		lap.setTotalElapsedTime(elapsed);
		lap.setTotalTimerTime(timer);
		lap.setAvgPower(summary.avgPower());
		lap.setMaxPower(summary.maxPower());
		lap.setNormalizedPower(summary.normalizedPower());
		lap.setTotalWork(summary.work());
		lap.setAvgCadence(summary.avgCadence());
		lap.setMaxCadence(summary.maxCadence());
		lap.setAvgHeartRate(summary.avgHeartRate());
		lap.setMaxHeartRate(summary.maxHeartRate());
		setDistance(lap, summary, meters, timer);
		return lap;
	}

	private static SessionMesg session(DateTime start, DateTime end, float elapsed, float timer, Summary summary,
			double meters) {
		SessionMesg session = new SessionMesg();
		session.setMessageIndex(0);
		session.setTimestamp(end);
		session.setStartTime(start);
		session.setEvent(Event.SESSION);
		session.setEventType(EventType.STOP);
		session.setSport(Sport.CYCLING);
		session.setSubSport(SubSport.INDOOR_CYCLING);
		session.setTotalElapsedTime(elapsed);
		session.setTotalTimerTime(timer);
		session.setFirstLapIndex(0);
		session.setNumLaps(1);
		session.setAvgPower(summary.avgPower());
		session.setMaxPower(summary.maxPower());
		session.setNormalizedPower(summary.normalizedPower());
		session.setTotalWork(summary.work());
		session.setAvgCadence(summary.avgCadence());
		session.setMaxCadence(summary.maxCadence());
		session.setAvgHeartRate(summary.avgHeartRate());
		session.setMaxHeartRate(summary.maxHeartRate());
		setDistance(session, summary, meters, timer);
		return session;
	}

	/** Strecke und Geschwindigkeit nur, wenn der Trainer sie geliefert hat – sonst bleibt die Strecke leer */
	private static void setDistance(LapMesg lap, Summary summary, double meters, float timer) {
		if (summary.maxSpeed() == null) {
			return;
		}
		lap.setTotalDistance((float) meters);
		lap.setAvgSpeed((float) (meters / timer));
		lap.setMaxSpeed((float) (summary.maxSpeed() / 3.6));
	}

	private static void setDistance(SessionMesg session, Summary summary, double meters, float timer) {
		if (summary.maxSpeed() == null) {
			return;
		}
		session.setTotalDistance((float) meters);
		session.setAvgSpeed((float) (meters / timer));
		session.setMaxSpeed((float) (summary.maxSpeed() / 3.6));
	}

	private ActivityMesg activity(DateTime end, float timer) {
		ActivityMesg activity = new ActivityMesg();
		activity.setTimestamp(end);
		activity.setTotalTimerTime(timer);
		activity.setNumSessions(1);
		activity.setType(Activity.MANUAL);
		activity.setEvent(Event.ACTIVITY);
		activity.setEventType(EventType.STOP);
		long offset = clock.getZone().getRules().getOffset(end.getDate().toInstant()).getTotalSeconds();
		activity.setLocalTimestamp(end.getTimestamp() + offset);
		return activity;
	}

	/** Durchschnitte und Spitzen; null, wenn der Wert nie gemessen wurde */
	record Summary(Integer avgPower, Integer maxPower, Integer normalizedPower, Long work, Short avgCadence,
			Short maxCadence, Short avgHeartRate, Short maxHeartRate, Double maxSpeed) {

		static Summary of(List<Sample> samples) {
			List<Integer> power = samples.stream().map(Sample::power).filter(Objects::nonNull).toList();
			List<Integer> cadence = samples.stream().map(Sample::cadence).filter(Objects::nonNull).toList();
			List<Integer> heartRate = samples.stream().map(Sample::heartRate).filter(Objects::nonNull).toList();
			return new Summary(average(power), max(power), normalizedPower(power),
					power.isEmpty() ? null : power.stream().mapToLong(Integer::longValue).sum(),
					toShort(average(cadence)), toShort(max(cadence)), toShort(average(heartRate)),
					toShort(max(heartRate)),
					samples.stream().map(Sample::speed).filter(Objects::nonNull).max(Double::compare).orElse(null));
		}

		/** Gleitender 30-s-Schnitt, davon die vierte Potenz gemittelt – wie bei TrainingPeaks & Co. */
		static Integer normalizedPower(List<Integer> power) {
			int window = 30;
			if (power.size() < window) {
				return null;
			}
			double sum = 0;
			double fourth = 0;
			int count = 0;
			for (int i = 0; i < power.size(); i++) {
				sum += power.get(i);
				if (i >= window) {
					sum -= power.get(i - window);
				}
				if (i >= window - 1) {
					fourth += Math.pow(sum / window, 4);
					count++;
				}
			}
			return (int) Math.round(Math.pow(fourth / count, 0.25));
		}

		private static Integer average(List<Integer> values) {
			return values.isEmpty() ? null
					: (int) Math.round(values.stream().mapToInt(Integer::intValue).average().orElse(0));
		}

		private static Integer max(List<Integer> values) {
			return values.stream().max(Integer::compare).orElse(null);
		}

		private static Short toShort(Integer value) {
			return value == null ? null : value.shortValue();
		}
	}

	/** z. B. "quaelmich-2026-10-26-sweet-spot-3x12.fit" */
	public String fileName(Instant startedAt, String title) {
		String date = startedAt.atZone(clock.getZone()).toLocalDate().toString();
		String slug = title == null ? ""
				: title.toLowerCase()
						.replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
						.replace(" × ", "x")
						.replaceAll("[^a-z0-9]+", "-")
						.replaceAll("(^-|-$)", "");
		return "quaelmich-" + date + (slug.isEmpty() ? "" : "-" + slug) + ".fit";
	}

}
