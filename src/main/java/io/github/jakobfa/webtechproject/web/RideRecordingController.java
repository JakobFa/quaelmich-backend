package io.github.jakobfa.webtechproject.web;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import io.github.jakobfa.webtechproject.service.FitActivityExporter;
import io.github.jakobfa.webtechproject.web.dto.RideRecordingRequest;
import jakarta.validation.Valid;

/**
 * Macht aus einer im Player mit dem Trainer aufgezeichneten Fahrt eine .fit-Datei zum Hochladen bei
 * Strava oder intervals.icu. Gespeichert wird dabei nichts – die Datei geht direkt zurück an den Browser.
 */
@RestController
public class RideRecordingController {

	private final FitActivityExporter exporter;

	public RideRecordingController(FitActivityExporter exporter) {
		this.exporter = exporter;
	}

	@PostMapping(path = "/me/recordings/fit")
	public ResponseEntity<byte[]> fit(@Valid @RequestBody RideRecordingRequest recording) {
		byte[] fit;
		try {
			fit = exporter.export(recording.startedAt(), recording.samples().stream()
					.map(s -> new FitActivityExporter.Sample(s.second(), s.power(), s.cadence(), s.heartRate()))
					.toList());
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().build();
		}
		ContentDisposition disposition = ContentDisposition.attachment()
				.filename(exporter.fileName(recording.startedAt(), recording.title()))
				.build();
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
				.contentType(MediaType.APPLICATION_OCTET_STREAM)
				.body(fit);
	}

}
