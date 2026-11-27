package io.github.jakobfa.webtechproject.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.jakobfa.webtechproject.model.AiAssessment;
import io.github.jakobfa.webtechproject.repository.AiAssessmentRepository;
import io.github.jakobfa.webtechproject.repository.AppUserRepository;
import io.github.jakobfa.webtechproject.service.ProgressService.LoadStatus;

/**
 * Einschätzung der KI im KI-Modus. quaelmich kann die KI nicht selbst anstoßen – die KI meldet sich (täglicher Abgleich
 * oder im Gespräch). Wird sie alt, zeigt die Planseite wieder die eigene Rechnung und get_overview erinnert die KI.
 */
@Service
public class AiAssessmentService {

	/** So lange gilt die Einschätzung auf der Planseite */
	public static final Duration FRESH_FOR = Duration.ofHours(36);
	/** Ab hier bittet get_overview die KI um eine neue Einschätzung */
	public static final Duration RENEW_AFTER = Duration.ofHours(20);
	public static final int MAX_NOTE = 300;

	/** fresh = noch gültig für die Planseite */
	public record View(LoadStatus status, String note, Instant updatedAt, boolean fresh) {
	}

	private final AiAssessmentRepository repository;
	private final AppUserRepository users;
	private final Clock clock;

	public AiAssessmentService(AiAssessmentRepository repository, AppUserRepository users, Clock clock) {
		this.repository = repository;
		this.users = users;
		this.clock = clock;
	}

	@Transactional
	public View set(Long userId, LoadStatus status, String note) {
		String trimmed = note == null || note.isBlank() ? null : note.strip();
		if (trimmed == null) {
			throw new IllegalArgumentException("Bitte eine kurze Einschätzung als note mitschicken.");
		}
		if (trimmed.length() > MAX_NOTE) {
			throw new IllegalArgumentException("Die Notiz darf höchstens " + MAX_NOTE + " Zeichen haben.");
		}
		AiAssessment assessment = repository.findByOwnerId(userId).orElseGet(() -> {
			AiAssessment created = new AiAssessment();
			created.setOwner(users.getReferenceById(userId));
			return created;
		});
		assessment.setStatus(status);
		assessment.setNote(trimmed);
		assessment.setUpdatedAt(clock.instant());
		return view(repository.save(assessment));
	}

	@Transactional(readOnly = true)
	public Optional<View> current(Long userId) {
		return repository.findByOwnerId(userId).map(this::view);
	}

	/** Braucht die KI eine neue Einschätzung? (keine oder älter als RENEW_AFTER) */
	@Transactional(readOnly = true)
	public boolean needsRenewal(Long userId) {
		return repository.findByOwnerId(userId)
				.map(assessment -> assessment.getUpdatedAt().isBefore(clock.instant().minus(RENEW_AFTER)))
				.orElse(true);
	}

	@Transactional
	public void delete(Long userId) {
		repository.findByOwnerId(userId).ifPresent(repository::delete);
	}

	private View view(AiAssessment assessment) {
		boolean fresh = assessment.getUpdatedAt().isAfter(clock.instant().minus(FRESH_FOR));
		return new View(assessment.getStatus(), assessment.getNote(), assessment.getUpdatedAt(), fresh);
	}

}
