package io.github.jakobfa.webtechproject.custom;

import java.time.Instant;
import java.util.List;

import io.github.jakobfa.webtechproject.model.AppUser;
import io.github.jakobfa.webtechproject.model.Workout;
import io.github.jakobfa.webtechproject.model.WorkoutStep;
import io.github.jakobfa.webtechproject.model.WorkoutType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Ein selbst gebautes Workout. Nach außen hat es die ID {@link #ID_OFFSET} + Datenbank-ID, damit es sich nie
 * mit den Workouts aus der Bibliothek überschneidet.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class CustomWorkout {

	/** IDs ab hier sind eigene Workouts */
	public static final long ID_OFFSET = io.github.jakobfa.webtechproject.service.WorkoutLibrary.CUSTOM_ID_OFFSET;

	@Id
	@GeneratedValue
	private Long id;

	@ManyToOne(optional = false)
	private AppUser owner;

	@Column(length = 80, nullable = false)
	private String title;

	@Column(length = 500)
	private String description;

	@Enumerated(EnumType.STRING)
	private WorkoutType type;

	@Convert(converter = StepListConverter.class)
	@Column(length = 4000)
	private List<WorkoutStep> steps;

	private Instant createdAt;

	/** gelöscht = nicht mehr in der Liste, aber eingeplante Tage bleiben lesbar */
	private boolean deleted;

	public static boolean isCustom(Long workoutId) {
		return workoutId != null && workoutId >= ID_OFFSET;
	}

	public long publicId() {
		return ID_OFFSET + id;
	}

	public Workout toWorkout() {
		return new Workout(publicId(), title, type, description == null ? "" : description, List.copyOf(steps));
	}

}
