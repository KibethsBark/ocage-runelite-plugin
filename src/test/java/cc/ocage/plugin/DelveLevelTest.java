package cc.ocage.plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class DelveLevelTest
{
	@Test
	public void readsTheDelveLevelFromChat()
	{
		assertEquals(Integer.valueOf(3), OcagePlugin.delveLevel("Delve level: 3"));
		assertEquals(Integer.valueOf(12), OcagePlugin.delveLevel("Delve level: 12 duration: 1:23.40"));
		assertEquals(Integer.valueOf(12), OcagePlugin.delveLevel("Delve Level: 8+ (12) duration: 1:23"));
		// The game's own line past level 8 (checked in game).
		assertEquals(Integer.valueOf(12), OcagePlugin.delveLevel("Delve level 8+(12) duration: 1:23.4"));
		assertNull(OcagePlugin.delveLevel("Your Vorkath kill count is: 12."));
	}
}
