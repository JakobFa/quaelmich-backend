package io.github.jakobfa.webtechproject.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Der generierte Trainingsplan eines Athleten bis zu seinem Event.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class TrainingPlan {

	@Id
	@GeneratedValue
	private Long id;

	@OneToOne(optional = false)
	private Athlete athlete;

	private LocalDate startDate;

	@OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("date")
	private List<PlannedWorkout> workouts = new ArrayList<>();

	public TrainingPlan(Athlete athlete, LocalDate startDate) {
		this.athlete = athlete;
		this.startDate = startDate;
	}

	public void addWorkout(PlannedWorkout workout) {
		workout.setPlan(this);
		workouts.add(workout);
	}

}
