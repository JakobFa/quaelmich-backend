package io.github.jakobfa.webtechproject.feedback;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import io.github.jakobfa.webtechproject.auth.CurrentUser;
import io.github.jakobfa.webtechproject.auth.RateLimiter;
import io.github.jakobfa.webtechproject.web.AdminAccess;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Feedback aus der App (angemeldete Nutzer) und zum Abarbeiten für den Betreiber (X-Admin-Token, sonst 404).
 */
@RestController
public class FeedbackController {

	/** genug für echtes Feedback, zu wenig für Spam */
	static final RateLimiter.Limit LIMIT = new RateLimiter.Limit(10, java.time.Duration.ofHours(1));

	private final FeedbackService feedback;
	private final RateLimiter rateLimiter;
	private final AdminAccess access;

	public FeedbackController(FeedbackService feedback, RateLimiter rateLimiter, AdminAccess access) {
		this.feedback = feedback;
		this.rateLimiter = rateLimiter;
		this.access = access;
	}

	public record FeedbackRequest(@NotBlank @Size(max = 2000) String message, @Size(max = 200) String page) {
	}

	/** 201 = angekommen, 429 = zu viel auf einmal */
	@PostMapping(path = "/me/feedback")
	public ResponseEntity<Void> submit(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody FeedbackRequest request) {
		Long userId = CurrentUser.id(jwt);
		if (!rateLimiter.tryAcquire("feedback:" + userId, LIMIT)) {
			return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
		}
		feedback.submit(userId, request.message(), request.page());
		return ResponseEntity.status(HttpStatus.CREATED).build();
	}

	public record FeedbackResponse(Long id, String createdAt, String page, String message, String from,
			boolean done) {

		static FeedbackResponse of(Feedback feedback) {
			return new FeedbackResponse(feedback.getId(), feedback.getCreatedAt().toString(), feedback.getPage(),
					feedback.getMessage(), feedback.getAuthor().getEmail(), feedback.getDoneAt() != null);
		}
	}

	@GetMapping(path = "/internal/feedback")
	public ResponseEntity<List<FeedbackResponse>> list(
			@RequestHeader(name = "X-Admin-Token", required = false) String token) {
		if (!access.accepts(token)) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok(feedback.listAll().stream().map(FeedbackResponse::of).toList());
	}

	@PutMapping(path = "/internal/feedback/{id}/done")
	public ResponseEntity<FeedbackResponse> markDone(
			@RequestHeader(name = "X-Admin-Token", required = false) String token, @PathVariable Long id) {
		if (!access.accepts(token)) {
			return ResponseEntity.notFound().build();
		}
		return feedback.markDone(id).map(FeedbackResponse::of).map(ResponseEntity::ok)
				.orElse(ResponseEntity.notFound().build());
	}

}
