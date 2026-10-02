package cc.ocage.plugin;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * During a bingo: the event, the player's team, the event password and the
 * time, on the game screen. Every screenshot of the game then shows them, so
 * a screenshot is its own proof. Shown while the player has it turned on,
 * and always in the frame the plugin screenshots.
 */
class OcageOverlay extends OverlayPanel
{
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'");
	private static final Color TITLE = new Color(0x1f, 0xc7, 0xb4);

	private final OcagePlugin plugin;

	@Inject
	OcageOverlay(OcagePlugin plugin)
	{
		super(plugin);
		this.plugin = plugin;
		setPosition(OverlayPosition.TOP_LEFT);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		List<Api.Bingo> bingo = plugin.activeBingo();
		if (bingo.isEmpty() || !plugin.showBingoOverlay())
		{
			return null;
		}
		panelComponent.getChildren().add(TitleComponent.builder().text("Ocage Bingo").color(TITLE).build());
		for (Api.Bingo entry : bingo)
		{
			line("Event", entry.event);
			line("Team", entry.team);
			if (entry.password != null && !entry.password.isEmpty())
			{
				line("Password", entry.password);
			}
		}
		line("Time", ZonedDateTime.now(ZoneOffset.UTC).format(TIME));
		return super.render(graphics);
	}

	private void line(String left, String right)
	{
		panelComponent.getChildren().add(LineComponent.builder().left(left).right(right == null ? "" : right).build());
	}
}
