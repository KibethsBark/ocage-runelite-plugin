package cc.ocage.plugin;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * How the bingo overlay shows the time. Always UTC and in English, so every
 * team's screenshots read the same whatever the player's own settings.
 */
public enum OverlayTime
{
	/** 14 Oct 18:42 UTC */
	SHORT("Short", "d MMM HH:mm 'UTC'"),
	/** Tue 14 Oct 2026 18:42:05 UTC: the seconds help show a screenshot was taken live. */
	LONG("Long", "EEE d MMM yyyy HH:mm:ss 'UTC'");

	private final String label;
	private final DateTimeFormatter format;

	OverlayTime(String label, String pattern)
	{
		this.label = label;
		this.format = DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH);
	}

	DateTimeFormatter format()
	{
		return format;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
