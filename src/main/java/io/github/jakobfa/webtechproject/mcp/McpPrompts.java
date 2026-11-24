package io.github.jakobfa.webtechproject.mcp;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

/**
 * Fertige Aufträge, die der Nutzer in seiner KI-App auswählen kann (MCP-Prompts). Jeder erklärt der KI den Ablauf mit
 * den Werkzeugen von quaelmich, damit sie sofort weiß, wie sie vorgehen soll.
 */
@Component
public class McpPrompts {

	public record Argument(String name, String description, boolean required) {
	}

	public record Prompt(String name, String title, String description, List<Argument> arguments) {
	}

	private static final String RULES = """
			Regeln für quaelmich:
			- Ändere nur, was der Nutzer will, und sag vorher kurz, was du ändern wirst. Die KI-App fragt ihn bei jeder Änderung ohnehin.
			- Die planId einer Einheit wechselt, wenn der Plan neu gerechnet wird: ruf get_plan direkt vor einer Änderung noch einmal auf.
			- quaelmich liefert keine gefahrenen Werte und keine Strava-Daten. Brauchst du sie, frag den Nutzer oder nutze einen Strava-Zugang, den er selbst eingerichtet hat.
			- Ist der KI-Modus an, ändert quaelmich den Plan nicht mehr von selbst; neue Wochen und Anpassungen planst du (zum Beispiel mit regenerate_plan).
			- Erfinde nichts, was nicht in den Ergebnissen steht.""";

	private static final List<Prompt> PROMPTS = List.of(
			new Prompt("plan_pruefen", "Plan prüfen", "Schaut sich Profil und Plan an und sagt, ob beides zum Ziel passt.",
					List.of()),
			new Prompt("woche_umplanen", "Woche umplanen",
					"Plant die kommenden Tage nach einem Wunsch um (zum Beispiel weniger Zeit, müde, Urlaub).",
					List.of(new Argument("wunsch", "Was soll sich ändern?", true))),
			new Prompt("workout_bauen", "Eigenes Workout bauen",
					"Baut ein eigenes Workout und tauscht es in den Plan.",
					List.of(new Argument("beschreibung", "Was für ein Workout soll es werden?", true),
							new Argument("tag", "An welchem Tag soll es stattfinden (JJJJ-MM-TT)?", false))),
			new Prompt("einrichten", "Mit quaelmich starten",
					"Erklärt, was die KI mit quaelmich kann, und fragt nach dem, was sie für den Anfang braucht.",
					List.of()));

	public List<Map<String, Object>> list() {
		return PROMPTS.stream().map(prompt -> {
			Map<String, Object> row = new java.util.LinkedHashMap<>();
			row.put("name", prompt.name());
			row.put("title", prompt.title());
			row.put("description", prompt.description());
			row.put("arguments", prompt.arguments().stream().map(argument -> Map.of("name", argument.name(),
					"description", argument.description(), "required", argument.required())).toList());
			return row;
		}).toList();
	}

	/** Der Text des Auftrags oder leer, wenn es den Prompt nicht gibt */
	public Optional<Map<String, Object>> get(String name, Map<String, String> arguments) {
		String text = switch (name) {
			case "plan_pruefen" -> "Prüfe meinen Trainingsplan in quaelmich. Ruf get_overview und get_plan auf, schau, ob "
					+ "Ziel, Zeit pro Woche und die nächsten Wochen zusammenpassen, und sag mir in kurzen Sätzen, was "
					+ "gut ist und was du ändern würdest. Ändere noch nichts, ohne dass ich es sage.";
			case "woche_umplanen" -> "Plane meine kommenden Tage in quaelmich um. Mein Wunsch: "
					+ argument(arguments, "wunsch") + ". Ruf zuerst get_overview und get_plan auf, schlage vor, was du "
					+ "ändern würdest, und führe es nach meinem Ja mit den Werkzeugen aus (zum Beispiel "
					+ "set_week_availability, adjust_workout, skip_workout oder replace_workout).";
			case "workout_bauen" -> "Baue mir ein eigenes Workout in quaelmich: " + argument(arguments, "beschreibung")
					+ (argument(arguments, "tag").isEmpty() ? "" : ". Es soll am " + argument(arguments, "tag") + " stattfinden")
					+ ". Ruf get_overview und list_my_workouts auf, lege es mit create_workout an (Abschnitte in Minuten "
					+ "und Prozent der FTP) und tausche es mit replace_workout in den Plan. Sag mir vorher, wie es aufgebaut ist.";
			case "einrichten" -> "Ich möchte quaelmich mit dir benutzen. Ruf get_overview auf, erkläre mir kurz, was du "
					+ "dort lesen und ändern kannst (Plan, Profil, sportliches Ziel, Krankheit, Pause, eigene Workouts), und "
					+ "frag mich, was ich als Erstes möchte.";
			default -> null;
		};
		if (text == null) {
			return Optional.empty();
		}
		String description = PROMPTS.stream().filter(prompt -> prompt.name().equals(name)).findFirst().orElseThrow()
				.description();
		return Optional.of(Map.of("description", description, "messages", List.of(Map.of("role", "user", "content",
				Map.of("type", "text", "text", text + "\n\n" + RULES)))));
	}

	/** Pflichtargumente prüft der Aufrufer über {@link #missing} */
	public Optional<String> missing(String name, Map<String, String> arguments) {
		return PROMPTS.stream().filter(prompt -> prompt.name().equals(name)).findFirst()
				.flatMap(prompt -> prompt.arguments().stream()
						.filter(argument -> argument.required() && argument(arguments, argument.name()).isBlank())
						.map(Argument::name).findFirst());
	}

	private static String argument(Map<String, String> arguments, String key) {
		String value = arguments == null ? null : arguments.get(key);
		return value == null ? "" : value.strip();
	}

}
