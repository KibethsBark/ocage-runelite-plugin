package cc.ocage.plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * When to report a skill's total XP (an advent door counting XP, 1.0.5).
 * Only skills the rules list ({@code xp_skills}) are tracked, and only
 * while they're listed: the bot stops listing a skill once no open door
 * counts it. The first report for a skill is the door's start; after
 * that one is due every {@link #STEP} XP, as soon as the gain covers what
 * the nearest door still needs (so it finishes at once), and otherwise
 * after {@link #MAX_WAIT_MS} or at logout, so a small gain isn't lost.
 * Not thread safe: used on the client thread only.
 */
final class XpTracker
{
	static final int STEP = 10_000;
	static final long MAX_WAIT_MS = 60 * 60 * 1000L;

	/** Listed skill → the least XP an open door still needs. */
	private final Map<String, Integer> remaining = new HashMap<>();
	private final Map<String, Integer> sentXp = new HashMap<>();
	private final Map<String, Long> sentAt = new HashMap<>();
	private final Map<String, Integer> latestXp = new HashMap<>();

	/** The rules' xp_skills: forgets every skill no longer listed. */
	void setSkills(List<Api.XpSkill> skills)
	{
		Map<String, Integer> listed = new HashMap<>();
		if (skills != null)
		{
			for (Api.XpSkill skill : skills)
			{
				if (skill != null && skill.skill != null)
				{
					listed.put(skill.skill, Math.max(1, skill.remaining));
				}
			}
		}
		remaining.clear();
		remaining.putAll(listed);
		sentXp.keySet().retainAll(listed.keySet());
		sentAt.keySet().retainAll(listed.keySet());
		latestXp.keySet().retainAll(listed.keySet());
	}

	boolean tracking()
	{
		return !remaining.isEmpty();
	}

	List<String> skills()
	{
		return new ArrayList<>(remaining.keySet());
	}

	/** A new account or a logout: the next account starts from its own XP. */
	void reset()
	{
		remaining.clear();
		sentXp.clear();
		sentAt.clear();
		latestXp.clear();
	}

	/**
	 * The skill's XP now. Returns the total to report now (and counts it as
	 * sent), or null: not listed, or not due yet.
	 */
	Integer update(String skill, int xp, long now)
	{
		Integer needed = remaining.get(skill);
		if (needed == null || xp <= 0)
		{
			return null;
		}
		latestXp.put(skill, xp);
		Integer sent = sentXp.get(skill);
		boolean due = sent == null
			|| xp - sent >= Math.min(STEP, needed)
			|| (xp > sent && now - sentAt.get(skill) >= MAX_WAIT_MS);
		return due ? sent(skill, xp, now) : null;
	}

	/** At logout: every listed skill with XP gained since it was last reported. */
	Map<String, Integer> unsent(long now)
	{
		Map<String, Integer> due = new HashMap<>();
		for (Map.Entry<String, Integer> entry : latestXp.entrySet())
		{
			Integer sent = sentXp.get(entry.getKey());
			if (sent != null && entry.getValue() > sent)
			{
				due.put(entry.getKey(), sent(entry.getKey(), entry.getValue(), now));
			}
		}
		return due;
	}

	private int sent(String skill, int xp, long now)
	{
		sentXp.put(skill, xp);
		sentAt.put(skill, now);
		return xp;
	}
}
