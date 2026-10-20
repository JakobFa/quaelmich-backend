package io.github.jakobfa.webtechproject.web;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import io.github.jakobfa.webtechproject.model.Workout;
import io.github.jakobfa.webtechproject.service.FitWorkoutExporter;

/**
 * Baut die HTTP-Antwort für den Download einer .fit-Datei.
 */
final class FitDownload {

	private FitDownload() {
	}

	static ResponseEntity<byte[]> of(Workout workout, FitWorkoutExporter exporter) {
		ContentDisposition disposition = ContentDisposition.attachment()
				.filename(FitWorkoutExporter.fileName(workout))
				.build();
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
				.contentType(MediaType.APPLICATION_OCTET_STREAM)
				.body(exporter.export(workout));
	}

}
