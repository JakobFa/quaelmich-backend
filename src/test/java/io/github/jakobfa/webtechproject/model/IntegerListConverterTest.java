package io.github.jakobfa.webtechproject.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class IntegerListConverterTest {

	private final IntegerListConverter converter = new IntegerListConverter();

	@Test
	void storesListAsTextAndBack() {
		assertThat(converter.convertToDatabaseColumn(List.of(123, 153, 168, 183))).isEqualTo("123,153,168,183");
		assertThat(converter.convertToEntityAttribute("123,153,168,183")).containsExactly(123, 153, 168, 183);
	}

	@Test
	void emptyBecomesNull() {
		assertThat(converter.convertToDatabaseColumn(List.of())).isNull();
		assertThat(converter.convertToEntityAttribute(null)).isNull();
	}

}
