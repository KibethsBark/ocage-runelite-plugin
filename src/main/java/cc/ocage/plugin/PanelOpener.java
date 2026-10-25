package cc.ocage.plugin;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Decides when the plugin opens its own side panel ({@link PanelOpen}). It
 * opens it, never keeps it open: each login, and each running event, opens
 * it at most once, so a player who closes it isn't fought. Upcoming events
 * never open it. Swing thread only.
 */
final class PanelOpener
{
	private boolean openedAtLogin;
	/** The running events the panel was already opened for, this login. */
	private final Set<String> openedFor = new LinkedHashSet<>();

	/** A new login (or account): everything may open the panel once again. */
	void reset()
	{
		openedAtLogin = false;
		openedFor.clear();
	}

	/** Whether these standings, just fetched, should open the panel; remembers that they did. */
	boolean shouldOpen(PanelOpen mode, Api.Me me)
	{
		if (mode == PanelOpen.AT_LOGIN)
		{
			boolean open = !openedAtLogin;
			openedAtLogin = true;
			return open;
		}
		if (mode == PanelOpen.DURING_EVENTS)
		{
			Set<String> running = runningEvents(me);
			boolean open = !openedFor.containsAll(running);
			openedFor.addAll(running);
			return open;
		}
		return false;
	}

	/** The running events in the standings: active bingos the player is on a team for, and active boss events. */
	static Set<String> runningEvents(Api.Me me)
	{
		Set<String> running = new LinkedHashSet<>();
		if (me.bingo != null)
		{
			for (Api.MeBingo bingo : me.bingo)
			{
				running.add("bingo:" + bingo.event);
			}
		}
		if (me.events != null)
		{
			for (Api.MeEvent event : me.events)
			{
				running.add("boss:" + event.name);
			}
		}
		return running;
	}
}
