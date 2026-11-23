package cc.ocage.plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Collections;
import org.junit.Test;

public class LootFilterTest
{
	private static Api.Report scales()
	{
		Api.Item item = new Api.Item(12934, 300, 150, "Zulrah's scales");
		return Api.Report.loot("l1", "2026-12-01T12:00:00Z", "Zulrah", "npc", 45_000, Collections.singletonList(item), false);
	}

	@Test
	public void anItemAnAdventDoorWantsIsKeptUnderTheValueFloor()
	{
		Api.Rules rules = new Api.Rules();
		rules.minValue = 100_000;
		assertNull(OcagePlugin.filterLoot(scales(), rules));
		rules.itemNames = Collections.singletonList("zulrah's scales");
		assertEquals(1, OcagePlugin.filterLoot(scales(), rules).items.size());
	}

	@Test
	public void lootFromACountedSourceIsSentEvenWithNothingKept()
	{
		Api.Item logs = new Api.Item(1511, 10, 30, "Logs");
		Api.Report crate = Api.Report.loot("l2", "2026-12-01T12:00:00Z", "Supply crate", "event", 300,
			Collections.singletonList(logs), false);
		Api.Rules rules = new Api.Rules();
		rules.minValue = 100_000;
		assertNull(OcagePlugin.filterLoot(crate, rules));
		rules.lootSources = Collections.singletonList("Supply crate");
		assertEquals(0, OcagePlugin.filterLoot(crate, rules).items.size());
	}
}
