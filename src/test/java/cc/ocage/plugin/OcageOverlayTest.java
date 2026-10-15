package cc.ocage.plugin;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class OcageOverlayTest
{
	@Test
	public void fullShowsEachBingoThenTheTime()
	{
		List<String[]> lines = OcageOverlay.lines(
			Arrays.asList(bingo("Plugin Test Bingo", "Testers", "test123"), bingo("Second", "Alpha", null)),
			false, "14 Oct 18:42 UTC");
		assertEquals(6, lines.size());
		assertArrayEquals(new String[]{"Event", "Plugin Test Bingo"}, lines.get(0));
		assertArrayEquals(new String[]{"Password", "test123"}, lines.get(2));
		// No password: no Password line.
		assertArrayEquals(new String[]{"Team", "Alpha"}, lines.get(4));
		assertArrayEquals(new String[]{"Time", "14 Oct 18:42 UTC"}, lines.get(5));
	}

	@Test
	public void compactIsOneLinePerBingo()
	{
		List<String[]> lines = OcageOverlay.lines(
			Arrays.asList(bingo("Plugin Test Bingo", "Testers", "test123"), bingo("Second", "Alpha", null)),
			true, null);
		assertEquals(2, lines.size());
		assertArrayEquals(new String[]{"Plugin Test Bingo", "test123"}, lines.get(0));
		assertArrayEquals(new String[]{"Second", ""}, lines.get(1));
	}

	@Test
	public void theBoxIsWideEnoughThatNoLineWraps()
	{
		FontMetrics metrics = metrics();
		List<String[]> lines = OcageOverlay.lines(
			Collections.singletonList(bingo("A Very Long Bingo Event Name", "Testers", "test123")),
			false, "Tue 14 Oct 2026 18:42:05 UTC");
		int width = OcageOverlay.textWidth(lines, false, metrics, '0');
		for (String[] line : lines)
		{
			// RuneLite's LineComponent wraps when label + value is wider than it is.
			assertTrue(line[0], metrics.stringWidth(line[0]) + metrics.stringWidth(line[1]) < width);
		}
	}

	@Test
	public void theTimeIsMeasuredWithItsWidestDigitsSoTheBoxDoesntTwitch()
	{
		FontMetrics metrics = metrics();
		List<String[]> narrow = OcageOverlay.lines(Collections.singletonList(bingo("E", "T", "")), false, "11 Nov 11:11 UTC");
		List<String[]> wide = OcageOverlay.lines(Collections.singletonList(bingo("E", "T", "")), false, "00 Nov 00:00 UTC");
		assertEquals(OcageOverlay.textWidth(narrow, false, metrics, '0'), OcageOverlay.textWidth(wide, false, metrics, '0'));
		assertEquals("88 Oct 88:88", OcageOverlay.withDigit("14 Oct 18:42", '8'));
	}

	private static FontMetrics metrics()
	{
		Graphics2D graphics = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
		graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
		return graphics.getFontMetrics();
	}

	private static Api.Bingo bingo(String event, String team, String password)
	{
		Api.Bingo bingo = new Api.Bingo();
		bingo.event = event;
		bingo.team = team;
		bingo.password = password;
		return bingo;
	}
}
