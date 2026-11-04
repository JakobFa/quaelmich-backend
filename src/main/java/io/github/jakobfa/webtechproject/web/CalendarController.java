package io.github.jakobfa.webtechproject.web;

import org.springframework.http.CacheControl;
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
import io.github.jakobfa.webtechproject.service.CalendarService;

/** Kalender-Abo (iCal): Link einrichten, erneuern, beenden – und der öffentliche Feed selbst */
@RestController
public class CalendarController {

	/** token = null: kein Abo eingerichtet */
	public record CalendarSubscription(String token) {
	}

	private final CalendarService calendar;

	public CalendarController(CalendarService calendar) {
		this.calendar = calendar;
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

	/** Der Feed für Kalender-Apps: ohne Login, der Token im Pfad ist das Geheimnis. 404 bei unbekanntem Token. */
	@GetMapping(path = "/calendar/{token}.ics", produces = "text/calendar;charset=UTF-8")
	public ResponseEntity<String> feed(@PathVariable String token) {
		return calendar.feed(token)
				.map(ics -> ResponseEntity.ok()
						.contentType(MediaType.parseMediaType("text/calendar;charset=UTF-8"))
						.cacheControl(CacheControl.noCache())
						.header("Content-Disposition", "inline; filename=\"quaelmich.ics\"")
						.body(ics))
				.orElse(ResponseEntity.notFound().build());
	}
}
