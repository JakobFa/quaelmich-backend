package io.github.jakobfa.webtechproject.model;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Speichert eine kurze Zahlenliste als Text, z. B. [123, 153, 168, 183] als "123,153,168,183" */
@Converter
public class IntegerListConverter implements AttributeConverter<List<Integer>, String> {

	@Override
	public String convertToDatabaseColumn(List<Integer> values) {
		return values == null || values.isEmpty() ? null
				: values.stream().map(String::valueOf).collect(Collectors.joining(","));
	}

	@Override
	public List<Integer> convertToEntityAttribute(String text) {
		return text == null || text.isBlank() ? null
				: Arrays.stream(text.split(",")).map(String::trim).map(Integer::valueOf).toList();
	}

}
