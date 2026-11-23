package cc.ocage.plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class XpTrackerTest
{
	private static List<Api.XpSkill> skill(String name, int remaining)
	{
		Api.XpSkill skill = new Api.XpSkill();
		skill.skill = name;
		skill.remaining = remaining;
		return Collections.singletonList(skill);
	}

	@Test
	public void onlyListedSkillsAreReportedAndTheFirstIsTheStart()
	{
		XpTracker xp = new XpTracker();
		assertNull(xp.update("Mining", 1000, 0));
		assertFalse(xp.tracking());
		xp.setSkills(skill("Mining", 200_000));
		assertNull(xp.update("Woodcutting", 5000, 0));
		assertEquals(Integer.valueOf(1000), xp.update("Mining", 1000, 0));
	}

	@Test
	public void reportsEveryStepOrWhenTheDoorIsCovered()
	{
		XpTracker xp = new XpTracker();
		xp.setSkills(skill("Mining", 200_000));
		xp.update("Mining", 100_000, 0);
		assertNull(xp.update("Mining", 109_999, 1));
		assertEquals(Integer.valueOf(110_000), xp.update("Mining", 110_000, 2));
		// The door only needs 3k more: reported the moment that's gained.
		xp.setSkills(skill("Mining", 3_000));
		assertNull(xp.update("Mining", 112_000, 3));
		assertEquals(Integer.valueOf(113_000), xp.update("Mining", 113_000, 4));
	}

	@Test
	public void aSmallGainIsSentAfterAnHourOrAtLogout()
	{
		XpTracker xp = new XpTracker();
		xp.setSkills(skill("Runecraft", 60_000));
		xp.update("Runecraft", 50_000, 0);
		assertNull(xp.update("Runecraft", 50_500, 1000));
		assertEquals(Integer.valueOf(50_500), xp.update("Runecraft", 50_500, XpTracker.MAX_WAIT_MS));
		assertNull(xp.update("Runecraft", 50_500, 3 * XpTracker.MAX_WAIT_MS));  // nothing new since
		long soon = XpTracker.MAX_WAIT_MS + 10;  // under an hour since the last report
		assertNull(xp.update("Runecraft", 51_000, soon));
		assertEquals(Collections.singletonMap("Runecraft", 51_000), xp.unsent(soon + 1));
		assertTrue(xp.unsent(soon + 2).isEmpty());
	}

	@Test
	public void aSkillNoLongerListedIsForgottenAndStartsAgain()
	{
		XpTracker xp = new XpTracker();
		xp.setSkills(skill("Mining", 200_000));
		xp.update("Mining", 1000, 0);
		xp.setSkills(Collections.emptyList());
		assertFalse(xp.tracking());
		xp.setSkills(skill("Mining", 200_000));
		assertEquals(Integer.valueOf(1500), xp.update("Mining", 1500, 1));  // a new start, not a 500 gain
	}
}
