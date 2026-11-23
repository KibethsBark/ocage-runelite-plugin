package cc.ocage.plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public class KillTimesTest
{
	private static Api.Report yama(String id)
	{
		return Api.Report.kill(id, "2026-12-01T12:00:00Z", "Yama", 12, 2);
	}

	@Test
	public void readsTheFightDuration()
	{
		assertEquals(Long.valueOf(149_400), OcagePlugin.fightDurationMs("Fight duration: 2:29.4"));
		assertEquals(Long.valueOf(149_000), OcagePlugin.fightDurationMs("Fight duration: 2:29"));
		assertEquals(Long.valueOf(149_400), OcagePlugin.fightDurationMs("Fight duration: 2:29.40. Personal best: 2:10.00"));
		assertEquals(Long.valueOf(612_000), OcagePlugin.fightDurationMs("Fight duration: 10:12"));
		assertEquals(Long.valueOf(3_723_000), OcagePlugin.fightDurationMs("Fight duration: 1:02:03"));
		assertNull(OcagePlugin.fightDurationMs("Your Yama kill count is: 12."));
	}

	@Test
	public void aDurationAfterTheKillIsAttached()
	{
		KillTimes times = new KillTimes();
		assertTrue(times.kill(yama("a"), 100).isEmpty());
		List<Api.Report> sent = times.duration(149_400, 101);
		assertEquals(1, sent.size());
		assertEquals(Long.valueOf(149_400), sent.get(0).durationMs);
		assertEquals(Integer.valueOf(2), sent.get(0).teamSize);
		assertTrue(times.tick(110).isEmpty());
	}

	@Test
	public void aDurationBeforeTheKillIsAttached()
	{
		KillTimes times = new KillTimes();
		assertTrue(times.duration(149_400, 100).isEmpty());
		List<Api.Report> sent = times.kill(yama("a"), 100);
		assertEquals(Long.valueOf(149_400), sent.get(0).durationMs);
	}

	@Test
	public void aKillWithNoDurationIsSentAfterTheWindow()
	{
		KillTimes times = new KillTimes();
		times.kill(yama("a"), 100);
		assertTrue(times.tick(100 + KillTimes.WINDOW_TICKS).isEmpty());
		List<Api.Report> sent = times.tick(101 + KillTimes.WINDOW_TICKS);
		assertEquals(1, sent.size());
		assertNull(sent.get(0).durationMs);
		// A duration long after belongs to no kill.
		assertTrue(times.duration(149_400, 200).isEmpty());
		assertNull(times.kill(yama("b"), 210).isEmpty() ? null : "sent early");
		assertNull(times.flush().get(0).durationMs);
	}

	@Test
	public void aSecondKillSendsTheFirst()
	{
		KillTimes times = new KillTimes();
		times.kill(yama("a"), 100);
		List<Api.Report> sent = times.kill(yama("b"), 101);
		assertEquals("a", sent.get(0).id);
		assertEquals("b", times.flush().get(0).id);
	}
}
