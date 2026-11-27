package io.github.jakobfa.webtechproject.web;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.jakobfa.webtechproject.auth.CurrentUser;
import io.github.jakobfa.webtechproject.service.FormService;
import io.github.jakobfa.webtechproject.service.ProgressService;
import io.github.jakobfa.webtechproject.service.ProgressService.Progress;

@RestController
public class ProgressController {

	private final ProgressService progressService;
	private final FormService formService;
	private final io.github.jakobfa.webtechproject.service.AiAssessmentService aiAssessments;
	private final io.github.jakobfa.webtechproject.repository.AppUserRepository users;

	public ProgressController(ProgressService progressService, FormService formService,
			io.github.jakobfa.webtechproject.service.AiAssessmentService aiAssessments,
			io.github.jakobfa.webtechproject.repository.AppUserRepository users) {
		this.progressService = progressService;
		this.formService = formService;
		this.aiAssessments = aiAssessments;
		this.users = users;
	}

	/**
	 * Einschätzung der KI für die Planseite – nur im KI-Modus. 204, wenn es (noch) keine gibt oder der KI-Modus aus
	 * ist; fresh = false heißt: zu alt, die Planseite zeigt dann die eigene Rechnung.
	 */
	@GetMapping(path = "/me/ai-assessment")
	public ResponseEntity<io.github.jakobfa.webtechproject.service.AiAssessmentService.View> aiAssessment(
			@AuthenticationPrincipal Jwt jwt) {
		Long userId = CurrentUser.id(jwt);
		boolean aiMode = users.findById(userId).map(user -> user.isAiModeEnabled()).orElse(false);
		if (!aiMode) {
			return ResponseEntity.noContent().build();
		}
		return aiAssessments.current(userId).map(ResponseEntity::ok).orElse(ResponseEntity.noContent().build());
	}

	/** Fitness, Ermüdung und Form – bis heute gefahren, danach geplant. 404 ohne Plan. */
	@GetMapping(path = "/me/form")
	public ResponseEntity<FormService.FormCurve> form(@AuthenticationPrincipal Jwt jwt) {
		return formService.curveOf(CurrentUser.id(jwt))
				.map(ResponseEntity::ok)
				.orElse(ResponseEntity.notFound().build());
	}

	/** Level, Erfahrungspunkte, Serie und Stand im Plan. 404, solange es kein Profil gibt. */
	@GetMapping(path = "/me/progress")
	public ResponseEntity<Progress> progress(@AuthenticationPrincipal Jwt jwt) {
		return progressService.progressOf(CurrentUser.id(jwt))
				.map(ResponseEntity::ok)
				.orElse(ResponseEntity.notFound().build());
	}

}
