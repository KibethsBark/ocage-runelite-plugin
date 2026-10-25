package cc.ocage.plugin;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Works out which pet a pet message was about. The game's own message
 * ("You have a funny feeling like you're being followed.") never names the
 * pet, so the name comes from the messages around it, best first:
 * <ol>
 * <li>the clan's broadcast of it, which names duplicates too (needs the
 * clan's pet broadcasts, and the player in the clan chat);</li>
 * <li>"Untradeable drop: Heron", with the game setting "Untradeable loot
 * notifications" (pets are untradeable), duplicates too;</li>
 * <li>the collection-log message, for a new pet only, with the game setting
 * "Collection log - New addition notification".</li>
 * </ol>
 * The last two are only used when exactly one item came in the wait: the
 * same kill can bring another untradeable or another new collection-log
 * item, and guessing would name the pet after it. The same order as the
 * Dink plugin's pet notifier.
 *
 * <p>Client thread only.</p>
 */
final class PetNames
{
	/** How far apart a pet's message and the messages naming it may be. */
	static final long WAIT_MS = 3_000;

	/** A new pet (following, or in the backpack), or a duplicate ("you would have been followed"). */
	static final Pattern PET_PATTERN = Pattern.compile(
		"You have a funny feeling like you(?<dupe> would have been|'re being| are being) followed"
			+ "|You feel something weird sneaking into your backpack");
	/** Needs the in-game setting "Untradeable loot notifications". */
	static final Pattern UNTRADEABLE_PATTERN = Pattern.compile("^Untradeable drop: (?<item>.+?)\\.?$");
	/**
	 * The clan broadcast, which may start with the clan's name and end with a
	 * milestone: "[Ocage] Kibet h has a funny feeling like he would have been
	 * followed: Heron at 50 killcount."
	 */
	static final Pattern BROADCAST_PATTERN = Pattern.compile(
		"^(?:\\[[^\\]]*\\] )?(?<player>.+?) (?:has a funny feeling like .+ followed"
			+ "|feels something weird sneaking into .+ backpack|feels like .+ acquired something special)"
			+ ": (?<pet>.+?)(?: at .+)?\\.?$");

	enum Kind
	{
		BROADCAST, UNTRADEABLE, CLOG
	}

	private static final class Clue
	{
		final Kind kind;
		final String name;
		final long at;

		Clue(Kind kind, String name, long at)
		{
			this.kind = kind;
			this.name = name;
			this.at = at;
		}
	}

	private final List<Clue> clues = new ArrayList<>();

	/** Something seen at {@code now} that may name a pet; anything older than the wait is forgotten. */
	void add(Kind kind, String name, long now)
	{
		clues.removeIf(clue -> now - clue.at > WAIT_MS);
		clues.add(new Clue(kind, name, now));
	}

	/**
	 * The pet whose message came at {@code petAt}: the broadcast's name, else
	 * the one untradeable drop, else (a new pet only: a duplicate has no
	 * collection-log message) the one new collection-log item. Null when
	 * nothing names it for sure.
	 */
	String nameFor(long petAt, boolean duplicate)
	{
		String broadcast = null;
		for (Clue clue : clues)
		{
			if (clue.kind == Kind.BROADCAST && clue.at >= petAt - WAIT_MS)
			{
				broadcast = clue.name;
			}
		}
		if (broadcast != null)
		{
			return broadcast;
		}
		String untradeable = only(Kind.UNTRADEABLE, petAt);
		if (untradeable != null)
		{
			return untradeable;
		}
		return duplicate ? null : only(Kind.CLOG, petAt);
	}

	/** Whether the player's own broadcast has come since {@code petAt}: nothing better will. */
	boolean hasBroadcastSince(long petAt)
	{
		for (Clue clue : clues)
		{
			if (clue.kind == Kind.BROADCAST && clue.at >= petAt)
			{
				return true;
			}
		}
		return false;
	}

	void clear()
	{
		clues.clear();
	}

	private String only(Kind kind, long petAt)
	{
		Set<String> names = new LinkedHashSet<>();
		for (Clue clue : clues)
		{
			if (clue.kind == kind && clue.at >= petAt - WAIT_MS)
			{
				names.add(clue.name);
			}
		}
		return names.size() == 1 ? names.iterator().next() : null;
	}

	/** The pet a clan broadcast names, if the broadcast is about {@code rsn}; else null. */
	static String broadcastPet(String message, String rsn)
	{
		Matcher matcher = BROADCAST_PATTERN.matcher(message);
		if (rsn == null || !matcher.find() || !sameName(matcher.group("player"), rsn))
		{
			return null;
		}
		return matcher.group("pet").trim();
	}

	/** The item an "Untradeable drop" message names, else null. */
	static String untradeableItem(String message)
	{
		Matcher matcher = UNTRADEABLE_PATTERN.matcher(message);
		return matcher.find() ? matcher.group("item").trim() : null;
	}

	/** RSNs as the game shows them: any case, and a space may be a non-breaking space or an underscore. */
	static boolean sameName(String a, String b)
	{
		return normalize(a).equalsIgnoreCase(normalize(b));
	}

	private static String normalize(String name)
	{
		return name.replace(' ', ' ').replace('_', ' ').trim();
	}
}
