package cc.ocage.plugin;

/**
 * When the plugin opens its own side panel. Labels are short for the same
 * reason as {@link ChatPrivacy}'s.
 */
public enum PanelOpen
{
	/** Once each time the player logs in (and is linked). */
	AT_LOGIN("At login"),
	/** Once for each event that's running: a bingo they're on a team for, or a boss event. Never upcoming ones. */
	DURING_EVENTS("During events"),
	/** Only when the player clicks its button (the default, and what every version before 1.0.3 did). */
	NEVER("Never");

	private final String label;

	PanelOpen(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
