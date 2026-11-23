package cc.ocage.plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class TzhaarWaveTest
{
	@Test
	public void theNextWaveSaysWhichWaveWasCleared()
	{
		assertEquals(Integer.valueOf(34), OcagePlugin.clearedTzhaarWave("Wave: 35"));
		assertEquals(Integer.valueOf(1), OcagePlugin.clearedTzhaarWave("Wave: 2"));
		assertNull(OcagePlugin.clearedTzhaarWave("Wave: 1"));
		assertNull(OcagePlugin.clearedTzhaarWave("Wave completed in: 1:23.45"));
		assertNull(OcagePlugin.clearedTzhaarWave("Wave 4 completed! Wave duration: 1:23.4"));
	}

	@Test
	public void onlyInsideTheInfernoOrTheFightCaves()
	{
		assertEquals("Inferno", OcagePlugin.tzhaarWaves(new int[]{9042, OcagePlugin.INFERNO_REGION}));
		assertEquals("Fight Caves", OcagePlugin.tzhaarWaves(new int[]{OcagePlugin.FIGHT_CAVES_REGION}));
		assertNull(OcagePlugin.tzhaarWaves(new int[]{12850}));
		assertNull(OcagePlugin.tzhaarWaves(null));
	}
}
