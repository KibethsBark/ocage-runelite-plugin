package cc.ocage.plugin;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.ArrayList;
import java.util.Collections;
import org.junit.Test;

public class SidebarButtonTest
{
	@Test
	public void alwaysAndNever()
	{
		assertTrue(SidebarButton.ALWAYS.shown(false, null));
		assertFalse(SidebarButton.NEVER.shown(false, running()));
	}

	@Test
	public void duringEventsOnlyWhileOneIsRunning()
	{
		assertFalse(SidebarButton.DURING_EVENTS.shown(false, null));  // no standings yet
		Api.Me none = standings();
		none.upcoming = new ArrayList<>(Collections.singletonList(new Api.MeUpcoming()));
		assertFalse(SidebarButton.DURING_EVENTS.shown(false, none));  // upcoming events don't count
		assertTrue(SidebarButton.DURING_EVENTS.shown(false, running()));
		Api.Me bingo = standings();
		bingo.bingo.add(new Api.MeBingo());
		assertTrue(SidebarButton.DURING_EVENTS.shown(false, bingo));
	}

	@Test
	public void anUnlinkedAccountAlwaysShowsItToLink()
	{
		assertTrue(SidebarButton.NEVER.shown(true, null));
		assertTrue(SidebarButton.DURING_EVENTS.shown(true, null));
	}

	private static Api.Me running()
	{
		Api.Me me = standings();
		me.events.add(new Api.MeEvent());
		return me;
	}

	private static Api.Me standings()
	{
		Api.Me me = new Api.Me();
		me.events = new ArrayList<>();
		me.bingo = new ArrayList<>();
		return me;
	}
}
