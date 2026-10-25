package cc.ocage.plugin;

/**
 * When the Ocage button shows in RuneLite's sidebar. Hiding it only
 * removes the button: the plugin keeps reporting, and the overlay and chat
 * messages still work. Labels are short for the same reason as
 * {@link ChatPrivacy}'s.
 */
public enum SidebarButton
{
	/** Always shown (the default, and what every version before 1.0.3 did). */
	ALWAYS("Always"),
	/** Only while an event is running: a bingo the player is on a team for, or a boss event. Not upcoming ones. */
	DURING_EVENTS("During events"),
	/** Never shown. */
	NEVER("Never");

	private final String label;

	SidebarButton(String label)
	{
		this.label = label;
	}

	/**
	 * Whether the button shows now. It always does while the logged-in
	 * account is known not to be linked ({@code unlinked}): linking is done
	 * in its panel, so hiding it would leave the player no way to link.
	 * {@code me}: the last standings fetched (null: none yet).
	 */
	boolean shown(boolean unlinked, Api.Me me)
	{
		if (unlinked)
		{
			return true;
		}
		switch (this)
		{
			case ALWAYS:
				return true;
			case DURING_EVENTS:
				return me != null && eventRunning(me);
			default:
				return false;
		}
	}

	/** Whether the standings have a running event: an active bingo the player is on a team for, or a boss event. */
	static boolean eventRunning(Api.Me me)
	{
		return (me.bingo != null && !me.bingo.isEmpty()) || (me.events != null && !me.events.isEmpty());
	}

	@Override
	public String toString()
	{
		return label;
	}
}
