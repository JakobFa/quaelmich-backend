package io.github.jakobfa.webtechproject.custom;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import io.github.jakobfa.webtechproject.model.StepKind;
import io.github.jakobfa.webtechproject.model.WorkoutStep;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Speichert Abschnitte kompakt als Text: "WARMUP:600:50:75;INTERVAL:720:90:90;…" */
@Converter
public class StepListConverter implements AttributeConverter<List<WorkoutStep>, String> {

	@Override
	public String convertToDatabaseColumn(List<WorkoutStep> steps) {
		return steps == null ? null
				: steps.stream()
						.map(step -> step.kind() + ":" + step.durationSeconds() + ":" + step.powerStartPercent() + ":"
								+ step.powerEndPercent())
						.collect(Collectors.joining(";"));
	}

	@Override
	public List<WorkoutStep> convertToEntityAttribute(String text) {
		if (text == null || text.isBlank()) {
			return List.of();
		}
		return Arrays.stream(text.split(";")).map(part -> {
			String[] values = part.split(":");
			return new WorkoutStep(StepKind.valueOf(values[0]), Integer.parseInt(values[1]),
					Integer.parseInt(values[2]), Integer.parseInt(values[3]));
		}).toList();
	}

}
