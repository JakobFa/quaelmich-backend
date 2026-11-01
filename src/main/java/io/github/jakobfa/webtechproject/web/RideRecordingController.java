package io.github.jakobfa.webtechproject.web;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.web.client.RestClientException;

import io.github.jakobfa.webtechproject.auth.CurrentUser;
import io.github.jakobfa.webtechproject.integration.strava.StravaService;
import io.github.jakobfa.webtechproject.service.FitActivityExporter;
import io.github.jakobfa.webtechproject.web.dto.RideRecordingRequest;
import jakarta.validation.Valid;

/**
 * Macht aus einer im Player mit dem Trainer aufgezeichneten Fahrt eine .fit-Datei zum Hochladen bei
 * Strava oder intervals.icu. Gespeichert wird dabei nichts – die Datei geht direkt zurück an den Browser.
 */
@RestController
public class RideRecordingController {

	/** outcome: DONE, PROCESSING, DUPLICATE oder FAILED; activityId bei DONE (Link zur Fahrt bei Strava) */
	public record StravaUploadResponse(String outcome, Long activityId, String message) {
	}

	private final FitActivityExporter exporter;
	private final StravaService stravaService;

	public RideRecordingController(FitActivityExporter exporter, StravaService stravaService) {
		this.exporter = exporter;
		this.stravaService = stravaService;
	}

	/**
	 * Lädt die aufgezeichnete Fahrt direkt als Indoor-Aktivität zu Strava hoch.
	 * 404 ohne Strava-Verbindung, 409 ohne Erlaubnis activity:write, 502 wenn Strava nicht erreichbar ist.
	 */
	@PostMapping(path = "/me/recordings/strava")
	public ResponseEntity<StravaUploadResponse> uploadToStrava(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody RideRecordingRequest recording) {
		byte[] fit;
		try {
			fit = exporter.export(recording.startedAt(), recording.samples().stream()
					.map(s -> new FitActivityExporter.Sample(s.second(), s.power(), s.cadence(), s.heartRate(), s.speed()))
					.toList());
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().build();
		}
		String title = recording.title() == null || recording.title().isBlank() ? "Indoor-Training"
				: recording.title();
		try {
			StravaService.RideUpload upload = stravaService.uploadRide(CurrentUser.id(jwt), fit, title,
					"quaelmich-" + recording.startedAt().getEpochSecond());
			return ResponseEntity.ok(new StravaUploadResponse(upload.outcome().name(), upload.activityId(),
					upload.message()));
		} catch (StravaService.NotConnectedException e) {
			return ResponseEntity.notFound().build();
		} catch (StravaService.MissingScopeException e) {
			return ResponseEntity.status(HttpStatus.CONFLICT).build();
		} catch (RestClientException e) {
			return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
		}
	}

	@PostMapping(path = "/me/recordings/fit")
	public ResponseEntity<byte[]> fit(@Valid @RequestBody RideRecordingRequest recording) {
		byte[] fit;
		try {
			fit = exporter.export(recording.startedAt(), recording.samples().stream()
					.map(s -> new FitActivityExporter.Sample(s.second(), s.power(), s.cadence(), s.heartRate(), s.speed()))
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
