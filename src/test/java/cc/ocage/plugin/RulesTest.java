package cc.ocage.plugin;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class RulesTest
{
	@Test
	public void aKillFromTheNextEventsStartWaitsForFresherRules()
	{
		Api.Rules rules = new Api.Rules();
		assertFalse(rules.eventStartedBy("2026-10-25T12:00:00Z"));  // no event coming: drop it
		rules.nextEventStart = "2026-10-25T18:00:00Z";
		assertFalse(rules.eventStartedBy("2026-10-25T17:59:59.500Z"));
		assertTrue(rules.eventStartedBy("2026-10-25T18:00:00Z"));
		assertTrue(rules.eventStartedBy("2026-10-25T18:00:01.123Z"));
		assertTrue(rules.eventStartedBy("not a time"));  // unreadable: keep it, the bot decides
	}
}
