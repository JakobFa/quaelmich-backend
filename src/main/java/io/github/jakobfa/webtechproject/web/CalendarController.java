package io.github.jakobfa.webtechproject.web;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.jakobfa.webtechproject.auth.CurrentUser;
import io.github.jakobfa.webtechproject.auth.RateLimiter;
import io.github.jakobfa.webtechproject.service.CalendarService;

/** Kalender-Abo (iCal): Link einrichten, erneuern, beenden – und der öffentliche Feed selbst */
@RestController
public class CalendarController {

	/** token = null: kein Abo eingerichtet */
	public record CalendarSubscription(String token) {
	}

	/**
	 * Abrufe des Feeds je Link und Stunde. Kalender-Apps fragen höchstens alle 15 Minuten nach (4 pro Stunde); das
	 * Limit lässt Platz für mehrere Geräte und Neuladen von Hand. Gezählt wird nur für gültige Links – wer Tokens
	 * rät, bekommt 404, ohne dass sich der Server etwas merken muss.
	 */
	static final RateLimiter.Limit FEED_LIMIT = new RateLimiter.Limit(60, java.time.Duration.ofHours(1));

	private final CalendarService calendar;
	private final RateLimiter rateLimiter;

	public CalendarController(CalendarService calendar, RateLimiter rateLimiter) {
		this.calendar = calendar;
		this.rateLimiter = rateLimiter;
	}

	@GetMapping(path = "/me/calendar")
	public ResponseEntity<CalendarSubscription> subscription(@AuthenticationPrincipal Jwt jwt) {
		return calendar.tokenOf(CurrentUser.id(jwt)).map(token -> ResponseEntity.ok(new CalendarSubscription(token)))
				.orElseGet(() -> ResponseEntity.ok(new CalendarSubscription(null)));
	}

	/** Einrichten oder einen neuen Link erzeugen (der alte wird ungültig). 404 ohne Plan. */
	@PostMapping(path = "/me/calendar")
	public ResponseEntity<CalendarSubscription> renew(@AuthenticationPrincipal Jwt jwt) {
		return calendar.renew(CurrentUser.id(jwt)).map(token -> ResponseEntity.ok(new CalendarSubscription(token)))
				.orElse(ResponseEntity.notFound().build());
	}

	@DeleteMapping(path = "/me/calendar")
	public ResponseEntity<Void> revoke(@AuthenticationPrincipal Jwt jwt) {
		return calendar.revoke(CurrentUser.id(jwt)) ? ResponseEntity.noContent().build()
				: ResponseEntity.notFound().build();
	}

	/**
	 * Der Feed für Kalender-Apps: ohne Login, der Token im Pfad ist das Geheimnis. 404 bei unbekanntem Token,
	 * 429 (mit Retry-After) bei zu vielen Abrufen.
	 */
	@GetMapping(path = "/calendar/{token}.ics", produces = "text/calendar;charset=UTF-8")
	public ResponseEntity<String> feed(@PathVariable String token) {
		if (!calendar.isActive(token)) {
			return ResponseEntity.notFound().build();
		}
		if (!rateLimiter.tryAcquire("calendar:" + token, FEED_LIMIT)) {
			return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "900").build();
		}
		return calendar.feed(token)
				.map(ics -> ResponseEntity.ok()
						.contentType(MediaType.parseMediaType("text/calendar;charset=UTF-8"))
						.cacheControl(CacheControl.noCache())
						.header("Content-Disposition", "inline; filename=\"quaelmich.ics\"")
						.body(ics))
				.orElse(ResponseEntity.notFound().build());
	}
}
