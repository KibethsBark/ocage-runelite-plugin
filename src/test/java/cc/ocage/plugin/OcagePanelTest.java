package cc.ocage.plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class OcagePanelTest
{
	private static final String API = "https://api.ocage.cc";

	@Test
	public void startsReadsTheBotsUtcDates()
	{
		assertEquals("starts Sat 7 Nov, 18:00 UTC", OcagePanel.starts("2026-11-07 18:00:00"));
		assertEquals("starts Sat 7 Nov, 00:00 UTC", OcagePanel.starts("2026-11-07"));
		assertEquals("date to be announced", OcagePanel.starts(null));
		// Anything else is shown as the bot sent it rather than dropped.
		assertEquals("starts soon UTC", OcagePanel.starts("soon"));
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

	private static Api.MeUpcoming upcoming(String status, Boolean signedUp)
	{
		Api.MeUpcoming event = new Api.MeUpcoming();
		event.status = status;
		event.signedUp = signedUp;
		return event;
	}
}
