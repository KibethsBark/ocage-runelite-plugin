package cc.ocage.plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class PetNamesTest
{
	private static final long PET = 10_000;

	@Test
	public void thePetMessagesAreRecognised()
	{
		assertEquals("'re being", dupe("You have a funny feeling like you're being followed."));
		assertEquals(" would have been", dupe("You have a funny feeling like you would have been followed..."));
		assertTrue(PetNames.PET_PATTERN.matcher("You feel something weird sneaking into your backpack.").find());
	}

	@Test
	public void theClanBroadcastNamesTheirOwnPetEvenADuplicate()
	{
		assertEquals("Heron", PetNames.broadcastPet(
			"[Ocage] Kibet h has a funny feeling like he would have been followed: Heron at 50 killcount.", "Kibet h"));
		assertEquals("Vorki", PetNames.broadcastPet("Witted has a funny feeling like she's being followed: Vorki.", "witted"));
		assertEquals("Tumeken's guardian", PetNames.broadcastPet(
			"Jewna feels like she's acquired something special: Tumeken's guardian", "Jewna"));
		assertEquals("Baby chinchompa", PetNames.broadcastPet(
			"B0aty feels something weird sneaking into his backpack: Baby chinchompa.", "B0aty"));
		// A space in a name may come as a non-breaking space or an underscore.
		assertEquals("Heron", PetNames.broadcastPet("Kibet h has a funny feeling like he would have been followed: Heron.", "Kibet_h"));
		// Someone else's pet isn't the player's.
		assertNull(PetNames.broadcastPet("Woox has a funny feeling like he's being followed: Vorki.", "Witted"));
		assertNull(PetNames.broadcastPet("Witted received a drop: Abyssal whip.", "Witted"));
	}

	@Test
	public void theUntradeableDropMessageNamesAnItem()
	{
		assertEquals("Heron", PetNames.untradeableItem("Untradeable drop: Heron"));
		assertNull(PetNames.untradeableItem("Valuable drop: 34 x Battlestaff (267,104 coins)"));
	}

	@Test
	public void theBroadcastWinsThenAOneOffUntradeableThenAOneOffNewLogItem()
	{
		PetNames names = new PetNames();
		names.add(PetNames.Kind.CLOG, "Vorki", PET);
		assertEquals("Vorki", names.nameFor(PET, false));
		names.add(PetNames.Kind.UNTRADEABLE, "Vorki", PET + 100);
		names.add(PetNames.Kind.BROADCAST, "Vorki", PET + 600);
		assertTrue(names.hasBroadcastSince(PET));
		assertEquals("Vorki", names.nameFor(PET, false));
	}

	@Test
	public void aDuplicateIsNamedByTheBroadcastOrAnUntradeableNeverAnotherLogItem()
	{
		PetNames names = new PetNames();
		names.add(PetNames.Kind.CLOG, "Vorkath's head", PET);  // a new log item from the same kill
		assertNull(names.nameFor(PET, true));
		names.add(PetNames.Kind.UNTRADEABLE, "Vorki", PET + 100);
		assertEquals("Vorki", names.nameFor(PET, true));
	}

	@Test
	public void twoCandidatesOfTheSameKindAreNotGuessedBetween()
	{
		PetNames names = new PetNames();
		names.add(PetNames.Kind.CLOG, "Vorkath's head", PET - 100);
		names.add(PetNames.Kind.CLOG, "Vorki", PET);
		assertNull(names.nameFor(PET, false));
		assertFalse(names.hasBroadcastSince(PET));
	}

	@Test
	public void oldMessagesAreForgotten()
	{
		PetNames names = new PetNames();
		names.add(PetNames.Kind.CLOG, "Abyssal whip", PET - PetNames.WAIT_MS - 1);
		names.add(PetNames.Kind.CLOG, "Vorki", PET);
		assertEquals("Vorki", names.nameFor(PET, false));
	}

	private static String dupe(String message)
	{
		java.util.regex.Matcher matcher = PetNames.PET_PATTERN.matcher(message);
		assertTrue(message, matcher.find());
		return matcher.group("dupe");
	}
}
