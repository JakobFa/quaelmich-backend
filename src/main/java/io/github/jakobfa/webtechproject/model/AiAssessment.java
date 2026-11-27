package io.github.jakobfa.webtechproject.model;

import java.time.Instant;

import io.github.jakobfa.webtechproject.service.ProgressService.LoadStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Die Einschätzung der letzten Tage, wie die KI des Nutzers sie im KI-Modus setzt (set_assessment). Ersetzt auf der
 * Planseite die Rechnung von quaelmich, solange sie frisch ist.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class AiAssessment {

	@Id
	@GeneratedValue
	private Long id;

	@OneToOne(optional = false)
	@JoinColumn(unique = true)
	private AppUser owner;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private LoadStatus status;

	/** ein, zwei Sätze der KI, z. B. "Zwei harte Tage, morgen locker" */
	@Column(length = 300)
	private String note;

	@Column(nullable = false)
	private Instant updatedAt;

}
