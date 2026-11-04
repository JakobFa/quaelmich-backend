package io.github.jakobfa.webtechproject.mail;

import org.springframework.stereotype.Service;

/**
 * Schriftliche Bestätigung, dass Daten gelöscht wurden. Strava verlangt sie, wenn ein Nutzer die Löschung seiner
 * Strava-Daten verlangt oder den Zugriff beendet (API Policy §2.1 und §2.5); beim Löschen des Kontos gilt sie
 * für alles.
 */
@Service
public class DeletionNotice {

	private final MailService mailService;

	public DeletionNotice(MailService mailService) {
		this.mailService = mailService;
	}

	/** Alle von Strava übernommenen Daten sind gelöscht (Trennen, Widerruf bei Strava) */
	public void stravaDataDeleted(String email) {
		String text = """
				Hallo,

				wir haben alle Daten gelöscht, die quaelmich von deinem Strava-Konto übernommen hatte:
				gefahrene Einheiten, Zusatzfahrten, die zugehörigen Werte (Dauer, Distanz, Trainingslast) und die
				Verbindung samt Zugangsdaten. Der Zugriff von quaelmich auf Strava ist beendet.

				Was du selbst bei quaelmich eingetragen hast (Profil, Plan, Bewertungen), bleibt bestehen. Das Konto
				kannst du jederzeit im Profil unter "Konto & Daten" löschen.

				Quäl dich!
				quaelmich
				""";
		mailService.send(email, "Deine Strava-Daten bei quaelmich sind gelöscht", text, html(text));
	}

	/** Das ganze Konto samt aller Daten ist gelöscht */
	public void accountDeleted(String email) {
		String text = """
				Hallo,

				dein quaelmich-Konto ist gelöscht, mit Profil, Plan, Einheiten, eigenen Workouts, Feedback und allen
				Verbindungen. Ein vorhandener Strava-Zugriff wurde beendet, alle von Strava übernommenen Daten sind
				gelöscht. Backups der Datenbank werden spätestens nach 30 Tagen überschrieben.

				Danke, dass du dabei warst.
				quaelmich
				""";
		mailService.send(email, "Dein quaelmich-Konto ist gelöscht", text, html(text));
	}

	private static String html(String text) {
		String escaped = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
		return "<div style=\"font-family:sans-serif;line-height:1.5\">"
				+ escaped.replace("\n\n", "</p><p>").replace("\n", "<br>") + "</div>";
	}

}
