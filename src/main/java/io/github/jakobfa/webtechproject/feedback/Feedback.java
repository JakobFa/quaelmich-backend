package io.github.jakobfa.webtechproject.feedback;

import java.time.Instant;

import io.github.jakobfa.webtechproject.model.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Rückmeldung aus der App (Beta) – liest nur der Betreiber */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Feedback {

	@Id
	@GeneratedValue
	private Long id;

	@ManyToOne(optional = false)
	private AppUser author;

	@Column(length = 2000, nullable = false)
	private String message;

	/** Seite, auf der das Feedback geschrieben wurde, z. B. "/plan/42" */
	@Column(length = 200)
	private String page;

	private Instant createdAt;

	/** gesetzt, sobald der Betreiber es erledigt hat */
	private Instant doneAt;

	public Feedback(AppUser author, String message, String page, Instant createdAt) {
		this.author = author;
		this.message = message;
		this.page = page;
		this.createdAt = createdAt;
	}

}
