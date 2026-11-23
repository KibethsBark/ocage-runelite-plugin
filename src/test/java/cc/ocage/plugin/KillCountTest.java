package cc.ocage.plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.regex.Matcher;
import org.junit.Test;

public class KillCountTest
{
	@Test
	public void readsEachKindOfKillCountMessage()
	{
		assertKill("Your Vorkath kill count is: 812.", "Vorkath", "812");
		assertKill("Your completed Chambers of Xeric count is: 12.", "Chambers of Xeric", "12");
		assertKill("Your completion count for TzHaar-Ket-Rak's Third Challenge is: 4.",
			"TzHaar-Ket-Rak's Third Challenge", "4");
		assertKill("Your completion count for TzHaar-Ket-Rak's Sixth Challenge is: 1,024.",
			"TzHaar-Ket-Rak's Sixth Challenge", "1,024");
		assertNull(OcagePlugin.killCount("Wave 4 completed! Wave duration: 1:23.40"));
	}

	private static void assertKill(String message, String boss, String kc)
	{
		Matcher matcher = OcagePlugin.killCount(message);
		assertEquals(boss, matcher.group("boss"));
		assertEquals(kc, matcher.group("kc"));
	}
}
