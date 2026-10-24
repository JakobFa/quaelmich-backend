package io.github.jakobfa.webtechproject.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
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

	/** Abweichende Zeit für einzelne Wochen (Schlüssel: Montag der Woche) – sonst gilt die normale Verfügbarkeit */
	@ElementCollection(fetch = FetchType.EAGER) // klein, wird bei jeder Plan-Antwort gebraucht
	@CollectionTable(name = "plan_week_availability")
	@MapKeyColumn(name = "week_start")
	private Map<LocalDate, WeeklyAvailability> weekAvailability = new HashMap<>();

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

	/** Nach Änderungen wieder nach Datum sortieren (wie beim Laden mit @OrderBy) */
	public void sortByDate() {
		workouts.sort(java.util.Comparator.comparing(PlannedWorkout::getDate));
	}

}
