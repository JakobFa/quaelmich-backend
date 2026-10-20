package io.github.jakobfa.webtechproject.web;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import io.github.jakobfa.webtechproject.model.Workout;
import io.github.jakobfa.webtechproject.service.FitWorkoutExporter;
import io.github.jakobfa.webtechproject.service.WorkoutLibrary;

@RestController
public class WorkoutController {

	private final WorkoutLibrary workoutLibrary;
	private final FitWorkoutExporter fitExporter;

	public WorkoutController(WorkoutLibrary workoutLibrary, FitWorkoutExporter fitExporter) {
		this.workoutLibrary = workoutLibrary;
		this.fitExporter = fitExporter;
	}

	@GetMapping(path = "/workouts")
	public ResponseEntity<List<Workout>> getWorkouts() {
		return ResponseEntity.ok(workoutLibrary.findAll());
	}

	@GetMapping(path = "/workouts/{id}")
	public ResponseEntity<Workout> getWorkout(@PathVariable Long id) {
		return workoutLibrary.findById(id)
				.map(ResponseEntity::ok)
				.orElse(ResponseEntity.notFound().build());
	}

	@GetMapping(path = "/workouts/{id}/fit")
	public ResponseEntity<byte[]> downloadFit(@PathVariable Long id) {
		return workoutLibrary.findById(id)
				.map(workout -> FitDownload.of(workout, fitExporter))
				.orElse(ResponseEntity.notFound().build());
	}

}
