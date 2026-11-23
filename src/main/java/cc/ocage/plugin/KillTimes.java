package cc.ocage.plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pairs a kill with the game's "Fight duration: 2:29.4" line (1.0.5, for
 * speed tasks). The duration line names no boss and may come just before
 * or just after the kill-count message, so a kill waits up to
 * {@link #WINDOW_TICKS} game ticks for its duration (and a duration for its
 * kill) before it's sent as it is. Each method returns the kills to send
 * now. Synchronized: the client thread uses it, and shutdown flushes it.
 */
final class KillTimes
{
	static final int WINDOW_TICKS = 2;

	private Api.Report kill;
	private int killTick;
	private Long durationMs;
	private int durationTick;

	/** A kill-count message. */
	synchronized List<Api.Report> kill(Api.Report report, int tick)
	{
		List<Api.Report> send = new ArrayList<>(flushKill());
		if (durationMs != null && tick - durationTick <= WINDOW_TICKS)
		{
			send.add(report.withDuration(durationMs));
			durationMs = null;
			return send;
		}
		durationMs = null;
		kill = report;
		killTick = tick;
		return send;
	}

	/** A "Fight duration" line. */
	synchronized List<Api.Report> duration(long ms, int tick)
	{
		if (kill != null && tick - killTick <= WINDOW_TICKS)
		{
			Api.Report timed = kill.withDuration(ms);
			kill = null;
			return Collections.singletonList(timed);
		}
		durationMs = ms;
		durationTick = tick;
		return Collections.emptyList();
	}

	/** Each game tick: a kill whose duration hasn't come is sent without one. */
	synchronized List<Api.Report> tick(int tick)
	{
		if (durationMs != null && tick - durationTick > WINDOW_TICKS)
		{
			durationMs = null;
		}
		return kill != null && tick - killTick > WINDOW_TICKS ? flushKill() : Collections.emptyList();
	}

	/** Logout or shutdown: whatever kill is waiting, sent as it is. */
	synchronized List<Api.Report> flush()
	{
		durationMs = null;
		return flushKill();
	}

	private List<Api.Report> flushKill()
	{
		if (kill == null)
		{
			return Collections.emptyList();
		}
		Api.Report waiting = kill;
		kill = null;
		return Collections.singletonList(waiting);
	}
}
