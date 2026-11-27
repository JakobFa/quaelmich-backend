package io.github.jakobfa.webtechproject.health;

import java.time.Instant;
import java.time.LocalDate;

import io.github.jakobfa.webtechproject.model.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Der Nutzer hat eingewilligt, dass quaelmich seine Schlaf- und Erholungsdaten von intervals.icu übernimmt */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class WellnessImport {

	@Id
	@GeneratedValue
	private Long id;

	@OneToOne(optional = false)
	@JoinColumn(unique = true)
	private AppUser owner;

	/** ausdrückliche Einwilligung in die Verarbeitung von Gesundheitsdaten (Art. 9 DSGVO) */
	@Column(nullable = false)
	private Instant consentAt;

	private Instant lastImportAt;

	/** Bis zu diesem Tag ist der Verlauf lückenlos von intervals.icu geholt; null = noch nicht zurückgeladen */
	private LocalDate historyFrom;

	/** Auch der KI im KI-Modus zeigen (get_wellness) – eigene, ausdrückliche Einwilligung; null = nicht freigegeben */
	private Instant sharedWithAiAt;

}
