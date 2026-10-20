package io.github.jakobfa.webtechproject;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@CrossOrigin(origins = { "http://localhost:5173", "https://quaelmich-frontend.onrender.com" })
public class WorkoutController {

	@GetMapping(path = "/workouts")
	public ResponseEntity<List<Workout>> getWorkouts() {
		final List<Workout> workouts = List.of(
				new Workout("Grundlagenrunde", 90, 40),
				new Workout("Intervalle", 60, 85),
				new Workout("Regeneration", 45, 20));
		return ResponseEntity.ok(workouts);
	}

}
