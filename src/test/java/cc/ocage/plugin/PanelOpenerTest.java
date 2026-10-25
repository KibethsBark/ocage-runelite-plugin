package cc.ocage.plugin;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.ArrayList;
import java.util.Collections;
import org.junit.Test;

public class PanelOpenerTest
{
	@Test
	public void atLoginOpensOncePerLogin()
	{
		PanelOpener opener = new PanelOpener();
		assertTrue(opener.shouldOpen(PanelOpen.AT_LOGIN, standings()));
		assertFalse(opener.shouldOpen(PanelOpen.AT_LOGIN, standings()));  // the next refresh: closed stays closed
		opener.reset();  // logged in again
		assertTrue(opener.shouldOpen(PanelOpen.AT_LOGIN, standings()));
	}

	@Test
	public void duringEventsOpensOnceForEachRunningEventOnly()
	{
		PanelOpener opener = new PanelOpener();
		Api.Me none = standings();
		none.upcoming = new ArrayList<>(Collections.singletonList(new Api.MeUpcoming()));
		assertFalse(opener.shouldOpen(PanelOpen.DURING_EVENTS, none));  // upcoming events don't count
		Api.Me rush = standings();
		rush.events.add(boss("Nex Week"));
		assertTrue(opener.shouldOpen(PanelOpen.DURING_EVENTS, rush));
		assertFalse(opener.shouldOpen(PanelOpen.DURING_EVENTS, rush));
		Api.Me both = standings();
		both.events.add(boss("Nex Week"));
		both.bingo.add(bingo("Ocage Bingo 12"));
		assertTrue(opener.shouldOpen(PanelOpen.DURING_EVENTS, both));  // a second event starts
		assertFalse(opener.shouldOpen(PanelOpen.DURING_EVENTS, both));
	}

	@Test
	public void neverNeverOpens()
	{
		Api.Me rush = standings();
		rush.events.add(boss("Nex Week"));
		assertFalse(new PanelOpener().shouldOpen(PanelOpen.NEVER, rush));
	}

	private static Api.Me standings()
	{
		Api.Me me = new Api.Me();
		me.events = new ArrayList<>();
		me.bingo = new ArrayList<>();
		return me;
	}

	private static Api.MeEvent boss(String name)
	{
		Api.MeEvent event = new Api.MeEvent();
		event.name = name;
		return event;
	}

	private static Api.MeBingo bingo(String name)
	{
		Api.MeBingo bingo = new Api.MeBingo();
		bingo.event = name;
		return bingo;
	}
}
