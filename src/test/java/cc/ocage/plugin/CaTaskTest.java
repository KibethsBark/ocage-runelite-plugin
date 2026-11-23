package cc.ocage.plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class CaTaskTest
{
	@Test
	public void readsTheCompletedCombatTask()
	{
		assertEquals("Mirror Image",
			OcagePlugin.caTask("Congratulations, you've completed a hard combat task: Mirror Image (5 points)."));
		assertEquals("Perfect Phantom Muspah",
			OcagePlugin.caTask("Congratulations, you've completed an elite combat task: Perfect Phantom Muspah (5 points)."));
		assertEquals("Noxious Foe",
			OcagePlugin.caTask("Congratulations, you've completed an easy combat task: Noxious Foe (1 point)."));
		assertNull(OcagePlugin.caTask("Your Duke Sucellus kill count is: 12."));
	}
}
