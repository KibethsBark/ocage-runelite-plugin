package cc.ocage.plugin;

/** How much the bingo overlay shows. Labels are short for the same reason as {@link ChatPrivacy}'s. */
public enum OverlayStyle
{
	/** The title, then each bingo's event, team and password, then the time. */
	FULL("Full"),
	/** One line per bingo: the event and its password. */
	COMPACT("Compact");

	private final String label;

	OverlayStyle(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
