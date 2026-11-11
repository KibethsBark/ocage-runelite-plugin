package cc.ocage.plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import net.runelite.client.ui.ColorScheme;
import org.junit.Test;

public class OcagePanelTest
{
	private static final String API = "https://api.ocage.cc";
	private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

	@Test
	public void tasksAreReadFromTheBotsStandings()
	{
		Api.Me me = new Gson().fromJson("{\"rsn\": \"Alice\", \"display_name\": \"Alice\", \"tasks\": ["
			+ "{\"source\": \"Advent · Door 8 · Medium\", \"title\": \"Kill Zulrah 20 times\", \"status\": \"open\","
			+ " \"detail\": \"12 / 20 Zulrah KC\", \"progress\": 12, \"target\": 20, \"url\": \"https://ocage.cc/events/3\"}]}",
			Api.Me.class);
		assertEquals(1, me.tasks.size());
		Api.MeTask task = me.tasks.get(0);
		assertEquals("Kill Zulrah 20 times", task.title);
		assertEquals(Integer.valueOf(12), task.progress);
		assertEquals(Integer.valueOf(20), task.target);
		// An older bot sends no tasks at all: the section just doesn't show.
		assertNull(new Gson().fromJson("{\"rsn\": \"Alice\", \"display_name\": \"Alice\"}", Api.Me.class).tasks);
	}

	@Test
	public void aTasksDetailIsColouredByItsStatus()
	{
		assertEquals(ColorScheme.PROGRESS_COMPLETE_COLOR, OcagePanel.taskColor("done"));
		assertEquals(ColorScheme.PROGRESS_INPROGRESS_COLOR, OcagePanel.taskColor("pending"));
		assertNull(OcagePanel.taskColor("open"));
	}

	@Test
	public void startsReadsTheBotsUtcDates()
	{
		assertEquals("starts Sat 7 Nov, 18:00 UTC", OcagePanel.starts("2026-11-07 18:00:00", OcagePanel.UTC));
		assertEquals("starts Sat 7 Nov, 00:00 UTC", OcagePanel.starts("2026-11-07", OcagePanel.UTC));
		assertEquals("date to be announced", OcagePanel.starts(null, OcagePanel.UTC));
		// Anything else is shown as the bot sent it rather than dropped.
		assertEquals("starts soon UTC", OcagePanel.starts("soon", PARIS));
	}

	@Test
	public void timesCanBeShownInTheLocalZone()
	{
		assertEquals("starts Sat 7 Nov, 19:00 CET", OcagePanel.starts("2026-11-07 18:00:00", PARIS));
		// Summer time, and a time that crosses midnight.
		assertEquals("starts Sat 1 Aug, 01:30 CEST", OcagePanel.starts("2026-07-31 23:30:00", PARIS));
		assertEquals("Sat 7 Nov, 18:00 UTC", OcagePanel.ends("2026-11-07 18:00:00", OcagePanel.UTC));
		assertEquals("Sat 7 Nov, 19:00 CET", OcagePanel.ends("2026-11-07 18:00:00", PARIS));
		// A date-only end date stays the UTC date: no time is implied.
		assertEquals("Sat 7 Nov UTC", OcagePanel.ends("2026-11-07", PARIS));
	}

	@Test
	public void signupStageSaysWhetherYouAreIn()
	{
		assertEquals("Sign-ups open: you haven't signed up", OcagePanel.signupStage(upcoming("signup_open", false)));
		assertEquals("Sign-ups closed: you're signed up", OcagePanel.signupStage(upcoming("signup_closed", true)));
		assertEquals("Teams being drafted: you're signed up", OcagePanel.signupStage(upcoming("drafting", true)));
		assertEquals("Teams picked: you haven't signed up", OcagePanel.signupStage(upcoming("drafted", false)));
		// A bingo without sign-ups, or a status this build doesn't know: no line.
		assertNull(OcagePanel.signupStage(upcoming("upcoming", null)));
		assertNull(OcagePanel.signupStage(upcoming("something_new", null)));
		assertNull(OcagePanel.signupStage(upcoming(null, null)));
	}

	@Test
	public void linksOnlyOpenTheClansSiteOrTheBotsHost()
	{
		assertEquals("ocage.cc", OcagePanel.linkHost("https://ocage.cc/events/12", API));
		assertEquals("test.ocage.cc", OcagePanel.linkHost("https://test.ocage.cc/events/12", API));
		assertEquals("bot.example.net", OcagePanel.linkHost("https://bot.example.net/events/1", "https://bot.example.net"));
		assertNull(OcagePanel.linkHost("https://evil.example/events/12", API));
		assertNull(OcagePanel.linkHost("https://notocage.cc/events/12", API));
		assertNull(OcagePanel.linkHost("http://ocage.cc/events/12", API));
		assertNull(OcagePanel.linkHost("javascript:alert(1)", API));
		assertNull(OcagePanel.linkHost("https://", API));
		assertNull(OcagePanel.linkHost(null, API));
	}

	@Test
	public void theBossLeaderboardAddsYourLineWhenYoureOutsideTheTop()
	{
		Api.MeEvent event = boss(15, 12, row(1, "Woox", 100), row(2, "Kibet h", 98), row(3, "12", 92),
			row(4, "B0aty", 85), row(5, "Jewna", 77));
		List<OcagePanel.BoardLine> board = OcagePanel.kcLeaderboard(event, "Witted");
		assertEquals(6, board.size());
		assertEquals("1. Woox - 100", board.get(0).text);
		assertEquals("12. Witted - 15", board.get(5).text);
		assertTrue(board.get(5).mine);
		assertFalse(board.get(0).mine);
	}

	@Test
	public void theBossLeaderboardBoldsYouInPlaceWhenYoureInTheTop()
	{
		Api.MeEvent event = boss(98, 2, row(1, "Woox", 100), row(2, "Kibet h", 98));
		List<OcagePanel.BoardLine> board = OcagePanel.kcLeaderboard(event, "kibet H");
		assertEquals(2, board.size());
		assertTrue(board.get(1).mine);
	}

	@Test
	public void noKcOrNoLeaderboardAddsNothingOfYourOwn()
	{
		assertEquals(1, OcagePanel.kcLeaderboard(boss(0, null, row(1, "Woox", 100)), "Witted").size());
		assertTrue(OcagePanel.kcLeaderboard(boss(0, null), "Witted").isEmpty());
		Api.MeEvent olderBot = boss(5, 1);
		olderBot.leaderboard = null;
		assertTrue(OcagePanel.kcLeaderboard(olderBot, "Witted").isEmpty());
	}

	private static Api.MeEvent boss(int kc, Integer rank, Api.MeKcRow... rows)
	{
		Api.MeEvent event = new Api.MeEvent();
		event.kc = kc;
		event.kcRank = rank;
		event.leaderboard = Arrays.asList(rows);
		return event;
	}

	private static Api.MeKcRow row(int rank, String rsn, int kc)
	{
		Api.MeKcRow row = new Api.MeKcRow();
		row.rank = rank;
		row.rsn = rsn;
		row.kc = kc;
		return row;
	}

	private static Api.MeUpcoming upcoming(String status, Boolean signedUp)
	{
		Api.MeUpcoming event = new Api.MeUpcoming();
		event.status = status;
		event.signedUp = signedUp;
		return event;
	}
}
