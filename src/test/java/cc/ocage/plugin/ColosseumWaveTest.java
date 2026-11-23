package cc.ocage.plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class ColosseumWaveTest
{
	@Test
	public void readsTheClearedWaveFromChat()
	{
		assertEquals(Integer.valueOf(4), OcagePlugin.colosseumWave("Wave 4 completed! Wave duration: 1:23.4"));
		assertEquals(Integer.valueOf(12), OcagePlugin.colosseumWave("Wave 12 completed! Wave duration: 3:02.0"));
		assertNull(OcagePlugin.colosseumWave("Wave: 34"));
		assertNull(OcagePlugin.colosseumWave("Your Sol Heredit kill count is: 1."));
		assertNull(OcagePlugin.colosseumWave("Bob: Wave 8 completed! ez"));
	}
}
