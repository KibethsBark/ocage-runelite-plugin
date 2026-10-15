package cc.ocage.plugin;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.ComponentConstants;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * During a bingo: the event, the player's team, the event password and the
 * time, on the game screen. Every screenshot of the game then shows them, so
 * a screenshot is its own proof. Shown while the player has it turned on,
 * and always in the frame the plugin screenshots.
 *
 * <p>The box is as wide as its longest line, never narrower. RuneLite's
 * panels are a fixed 129 px by default and wrap a line that doesn't fit word
 * by word, which split a three-word event name and the time over three lines
 * each. Alt-dragging the edge (RuneLite's own resizing) can make it wider,
 * not narrower than the text. The Size setting scales the whole box.</p>
 */
class OcageOverlay extends OverlayPanel
{
	private static final String TITLE_TEXT = "Ocage Bingo";
	private static final Color TITLE = new Color(0x1f, 0xc7, 0xb4);
	/** Space kept between a line's label and its value. */
	private static final int GAP = 10;

	private final OcagePlugin plugin;
	private final OcageConfig config;
	/** The font {@link #widestDigit} was measured in. */
	private Font digitFont;
	private char widestDigit = '0';

	@Inject
	OcageOverlay(OcagePlugin plugin, OcageConfig config)
	{
		super(plugin);
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
	}

	/**
	 * Draws the panel itself rather than through OverlayPanel.render, which
	 * would set the panel to the Alt-dragged width even when that's narrower
	 * than the text. Keeps OverlayPanel's background colour handling.
	 */
	@Override
	public Dimension render(Graphics2D graphics)
	{
		List<Api.Bingo> bingo = plugin.activeBingo();
		if (bingo.isEmpty() || !plugin.showBingoOverlay())
		{
			return null;
		}
		// Screenshots are proof, so they always carry the team and the time.
		boolean compact = config.overlayStyle() == OverlayStyle.COMPACT && !plugin.capturingScreenshot();
		String time = compact ? null : ZonedDateTime.now(ZoneOffset.UTC).format(config.overlayTime().format());
		List<String[]> lines = lines(bingo, compact, time);

		double scale = config.overlaySize().scale();
		graphics.scale(scale, scale);
		FontMetrics metrics = graphics.getFontMetrics();
		int width = textWidth(lines, compact, metrics, widestDigit(metrics)) + 2 * ComponentConstants.STANDARD_BORDER;
		Dimension dragged = getPreferredSize();
		if (dragged != null)
		{
			width = Math.max(width, (int) Math.ceil(dragged.width / scale));
		}

		Dimension size = drawPanel(graphics, compact, lines, width);
		return size == null ? null
			: new Dimension((int) Math.ceil(size.width * scale), (int) Math.ceil(size.height * scale));
	}

	/**
	 * The overlay's lines as {label, value}. Full: each bingo's event, team
	 * and password (if it has one), then the time. Compact: one {event,
	 * password} line per bingo and no time ({@code time} is null).
	 */
	static List<String[]> lines(List<Api.Bingo> bingo, boolean compact, String time)
	{
		List<String[]> lines = new ArrayList<>();
		for (Api.Bingo entry : bingo)
		{
			String password = nonNull(entry.password);
			if (compact)
			{
				lines.add(new String[]{nonNull(entry.event), password});
				continue;
			}
			lines.add(new String[]{"Event", nonNull(entry.event)});
			lines.add(new String[]{"Team", nonNull(entry.team)});
			if (!password.isEmpty())
			{
				lines.add(new String[]{"Password", password});
			}
		}
		if (time != null)
		{
			lines.add(new String[]{"Time", time});
		}
		return lines;
	}

	/**
	 * The width the text needs: the longest line's label, a gap and its
	 * value, so RuneLite's LineComponent never wraps it. Full also fits the
	 * title and keeps RuneLite's standard panel width, like its other
	 * overlays. The time (Full's last line) is measured as if every digit
	 * were {@code widestDigit}, so a ticking clock doesn't make the box
	 * twitch wider and narrower.
	 */
	static int textWidth(List<String[]> lines, boolean compact, FontMetrics metrics, char widestDigit)
	{
		int width = compact ? 0 : Math.max(metrics.stringWidth(TITLE_TEXT),
			ComponentConstants.STANDARD_WIDTH - 2 * ComponentConstants.STANDARD_BORDER);
		for (int i = 0; i < lines.size(); i++)
		{
			String[] line = lines.get(i);
			boolean time = !compact && i == lines.size() - 1;
			String right = time ? withDigit(line[1], widestDigit) : line[1];
			int gap = line[0].isEmpty() || right.isEmpty() ? 0 : GAP;
			width = Math.max(width, metrics.stringWidth(line[0]) + gap + metrics.stringWidth(right));
		}
		return width;
	}

	/** Draws the title and lines at {@code width}, as OverlayPanel.render would, then resets the panel. */
	private Dimension drawPanel(Graphics2D graphics, boolean compact, List<String[]> lines, int width)
	{
		if (!compact)
		{
			panelComponent.getChildren().add(TitleComponent.builder().text(TITLE_TEXT).color(TITLE).build());
		}
		for (String[] line : lines)
		{
			panelComponent.getChildren().add(LineComponent.builder().left(line[0]).right(line[1]).build());
		}
		Dimension oldSize = panelComponent.getPreferredSize();
		Color oldBackground = panelComponent.getBackgroundColor();
		if (getPreferredColor() != null && ComponentConstants.STANDARD_BACKGROUND_COLOR.equals(oldBackground))
		{
			panelComponent.setBackgroundColor(getPreferredColor());
		}
		panelComponent.setPreferredSize(new Dimension(width, 0));
		try
		{
			return panelComponent.render(graphics);
		}
		finally
		{
			panelComponent.getChildren().clear();
			panelComponent.setPreferredSize(oldSize);
			panelComponent.setBackgroundColor(oldBackground);
		}
	}

	/** The widest digit in the font, worked out once per font rather than every frame. */
	private char widestDigit(FontMetrics metrics)
	{
		if (!metrics.getFont().equals(digitFont))
		{
			char widest = '0';
			for (char digit = '1'; digit <= '9'; digit++)
			{
				if (metrics.charWidth(digit) > metrics.charWidth(widest))
				{
					widest = digit;
				}
			}
			digitFont = metrics.getFont();
			widestDigit = widest;
		}
		return widestDigit;
	}

	/** {@code text} with every digit replaced by {@code digit}. */
	static String withDigit(String text, char digit)
	{
		char[] chars = text.toCharArray();
		for (int i = 0; i < chars.length; i++)
		{
			if (chars[i] >= '0' && chars[i] <= '9')
			{
				chars[i] = digit;
			}
		}
		return new String(chars);
	}

	private static String nonNull(String value)
	{
		return value == null ? "" : value;
	}
}
