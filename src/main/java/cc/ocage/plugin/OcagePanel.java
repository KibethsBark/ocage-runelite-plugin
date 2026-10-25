package cc.ocage.plugin;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.LinkBrowser;

/**
 * The Ocage sidebar: the clan banner, linking with a /plugin link code, then
 * your standing in active clan events, then the events still to come. Every
 * method runs on the Swing thread (the plugin calls them through
 * SwingUtilities.invokeLater).
 *
 * <p>Layout: one left-aligned column. Text is shown in read-only text areas
 * that wrap at word boundaries to whatever width the sidebar gives them —
 * HTML labels don't wrap reliably (they measure text differently from how
 * it's drawn, and RuneLite's font differs again), which cut lines off at the
 * right edge. Every row is left-aligned: a vertical BoxLayout otherwise
 * centres panels and left-aligns labels, pushing text off to one side.</p>
 */
@Slf4j
class OcagePanel extends PluginPanel
{
	/** The bot's time zone. A named zone, so times show "UTC" rather than "Z". */
	static final ZoneId UTC = ZoneId.of("UTC");
	/** How the bot writes dates (UTC). */
	private static final DateTimeFormatter BOT_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
	/** "Sat 7 Nov, 19:00 CET": always with the zone, so nobody takes a local time for UTC. */
	private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm z", Locale.ENGLISH);
	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);
	/** The clan's own website: the only place besides the bot's own host a panel link may open. */
	private static final String CLAN_DOMAIN = "ocage.cc";

	private final OcagePlugin plugin;
	/** The bot's host, for {@link #linkHost}. */
	private final String apiUrl;
	private final JTextArea server;
	private final JTextArea status = text("", null);
	private final JTextArea error = text("", ColorScheme.PROGRESS_ERROR_COLOR);
	private final JTextField codeField = new JTextField();
	private final JButton linkButton = new JButton("Link");
	private final JButton unlinkButton = new JButton("Unlink");
	private final JButton refreshButton = new JButton("Refresh");
	/** Refresh, plus Unlink when linked (GridLayout keeps a cell for a hidden button, so they're re-added). */
	private final JPanel buttons = new JPanel(new GridLayout(1, 0, 4, 0));
	private final JPanel linkForm = new JPanel(new BorderLayout(0, 4));
	private final JPanel standings = column();
	/** Whether each section is open, kept across refreshes (all open at first). */
	private boolean bingoOpen = true;
	private boolean bossOpen = true;
	private boolean upcomingOpen = true;
	/** The standings on show, as the plugin fingerprinted them (null: none, or redraw next time). */
	private String shownStandings;
	/** The width the text was last wrapped at. */
	private int laidOutWidth = -1;

	OcagePanel(OcagePlugin plugin, String serverUrl, boolean production)
	{
		this.plugin = plugin;
		this.apiUrl = serverUrl;
		setLayout(new BorderLayout(0, 8));
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		JPanel top = column();
		top.add(row(banner()));
		// Never let a tester mistake the test server for the real one.
		server = text("TEST server: " + serverUrl, ColorScheme.PROGRESS_INPROGRESS_COLOR);
		server.setVisible(!production);
		top.add(row(server));
		top.add(row(status));
		top.add(row(error));

		linkForm.add(text("In Discord, run /plugin link and paste the code:", null), BorderLayout.NORTH);
		linkForm.add(codeField, BorderLayout.CENTER);
		linkForm.add(linkButton, BorderLayout.SOUTH);
		linkForm.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
		linkButton.addActionListener(e ->
		{
			String code = codeField.getText().trim();
			if (!code.isEmpty())
			{
				setError(null);
				linkButton.setEnabled(false);
				plugin.link(code);
			}
		});
		top.add(row(linkForm));

		buttons.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
		refreshButton.setToolTipText("Check your link and events again now");
		refreshButton.addActionListener(e -> plugin.refresh());
		unlinkButton.addActionListener(e -> plugin.unlink());
		top.add(row(buttons));

		add(top, BorderLayout.NORTH);
		add(standings, BorderLayout.CENTER);
		addComponentListener(new ComponentAdapter()
		{
			@Override
			public void componentResized(ComponentEvent e)
			{
				if (getWidth() != laidOutWidth)
				{
					laidOutWidth = getWidth();
					relayout();
				}
			}
		});
		showLoggedOut();
	}

	/**
	 * A wrapping text area only knows its height once the first layout pass
	 * has given it a width, and Swing caches that first answer: so lay out,
	 * then forget the cached sizes and lay out again.
	 */
	private void relayout()
	{
		revalidate();
		SwingUtilities.invokeLater(() ->
		{
			invalidateTree(this);
			revalidate();
			repaint();
		});
	}

	private static void invalidateTree(Component component)
	{
		component.invalidate();
		if (component instanceof Container)
		{
			for (Component child : ((Container) component).getComponents())
			{
				invalidateTree(child);
			}
		}
	}

	// -- Building blocks -----------------------------------------------------------

	/** The clan's animated banner (the website's), centred; the name if it can't be loaded. */
	private static JLabel banner()
	{
		JLabel label = new JLabel();
		byte[] gif = null;
		// getResourceAsStream, not getResource: a Hub plugin runs from its jar (Plugin Hub README).
		try (InputStream in = OcagePanel.class.getResourceAsStream("banner.gif"))
		{
			gif = in == null ? null : in.readAllBytes();
		}
		catch (IOException e)
		{
			log.debug("Couldn't load the Ocage banner", e);
		}
		if (gif != null)
		{
			// ImageIcon from the bytes keeps a GIF animated.
			label.setIcon(new ImageIcon(gif));
		}
		else
		{
			label.setText("Ocage");
			label.setFont(label.getFont().deriveFont(16f));
		}
		label.setHorizontalAlignment(SwingConstants.CENTER);
		label.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
		return label;
	}

	/** Read-only text that wraps at word boundaries to the width it's given. */
	private static JTextArea text(String value, Color color)
	{
		JTextArea area = new JTextArea(value);
		area.setLineWrap(true);
		area.setWrapStyleWord(true);
		area.setEditable(false);
		area.setFocusable(false);
		area.setOpaque(false);
		area.setBorder(null);
		// A tiny preferred width: the layout stretches it to the sidebar's
		// width, and it wraps there, instead of widening the sidebar.
		area.setColumns(1);
		area.setFont(UIManager.getFont("Label.font"));
		area.setForeground(color != null ? color : UIManager.getColor("Label.foreground"));
		return area;
	}

	private static JTextArea bold(String value)
	{
		JTextArea area = text(value, null);
		area.setFont(area.getFont().deriveFont(Font.BOLD));
		return area;
	}

	/** Indented, e.g. a boss or a drop under its heading. */
	private static JComponent indented(JComponent component)
	{
		component.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 0));
		return component;
	}

	private static JPanel column()
	{
		JPanel column = new JPanel();
		column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
		column.setOpaque(false);
		return column;
	}

	/**
	 * One full-width, left-aligned row of a column: a vertical BoxLayout
	 * lines children up by their alignmentX, so every row gets the same.
	 */
	private static JComponent row(JComponent component)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setOpaque(false);
		row.add(component, BorderLayout.CENTER);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		return row;
	}

	// -- States --------------------------------------------------------------------

	void showLoggedOut()
	{
		shownStandings = null;
		status.setText("Log in to the game to link or see your events.");
		setLinkedControls(false);
		linkForm.setVisible(false);
		buttons.setVisible(false);
		standings.removeAll();
		relayout();
	}

	void showNotLinked()
	{
		shownStandings = null;
		status.setText("This account isn't linked yet. Linked before? Click Refresh first.");
		setLinkedControls(false);
		standings.removeAll();
		relayout();
	}

	void showLinked(String rsn)
	{
		status.setText("Linked as " + rsn);
		codeField.setText("");
		setError(null);
		setLinkedControls(true);
		relayout();
	}

	void showError(String message)
	{
		shownStandings = null;  // the next standings clear the error, even unchanged ones
		setError(message);
		linkButton.setEnabled(true);
		relayout();
	}

	private void setError(String message)
	{
		error.setText(message == null ? "" : message);
		error.setVisible(message != null && !message.isEmpty());
	}

	/** Redraws the standings (after a settings change, e.g. local times). */
	void showStandings(Api.Me me)
	{
		showStandings(me, null);
	}

	/**
	 * Shows the standings, unless they're the ones already on show
	 * ({@code fingerprint}, their JSON): the panel refreshes every minute
	 * during an event, and rebuilding identical cards is wasted work that can
	 * also move the sidebar. Null always redraws.
	 */
	void showStandings(Api.Me me, String fingerprint)
	{
		if (fingerprint != null && fingerprint.equals(shownStandings))
		{
			return;
		}
		shownStandings = fingerprint;
		showLinked(me.displayName.equals(me.rsn) ? me.rsn : me.rsn + " (" + me.displayName + ")");
		standings.removeAll();
		List<JComponent> bingoCards = new ArrayList<>();
		if (me.bingo != null)
		{
			for (Api.MeBingo bingo : me.bingo)
			{
				bingoCards.add(bingoCard(bingo));
			}
		}
		List<JComponent> bossCards = new ArrayList<>();
		if (me.events != null)
		{
			for (Api.MeEvent event : me.events)
			{
				bossCards.add(eventCard(event, me.rsn));
			}
		}
		List<JComponent> upcomingCards = new ArrayList<>();
		if (me.upcoming != null)
		{
			for (Api.MeUpcoming event : me.upcoming)
			{
				upcomingCards.add(upcomingCard(event));
			}
		}
		if (bingoCards.isEmpty() && bossCards.isEmpty())
		{
			standings.add(row(text("No active events right now.", null)));
		}
		if (!bingoCards.isEmpty())
		{
			standings.add(row(section("Bingo", bingoCards, bingoOpen, open -> bingoOpen = open)));
		}
		if (!bossCards.isEmpty())
		{
			standings.add(row(section("Boss events", bossCards, bossOpen, open -> bossOpen = open)));
		}
		if (!upcomingCards.isEmpty())
		{
			standings.add(row(section("Upcoming", upcomingCards, upcomingOpen, open -> upcomingOpen = open)));
		}
		relayout();
	}

	/**
	 * A collapsible section: a header (an arrow and "Bingo (2)") that opens and closes
	 * the cards under it. Whether it's open is remembered across refreshes.
	 */
	private JComponent section(String title, List<JComponent> cards, boolean open, Consumer<Boolean> remember)
	{
		JPanel body = column();
		for (JComponent card : cards)
		{
			body.add(row(card));
		}
		body.setVisible(open);

		JLabel header = new JLabel();
		header.setFont(header.getFont().deriveFont(Font.BOLD));
		header.setOpaque(true);
		header.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		header.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
		header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		String label = title + " (" + cards.size() + ")";
		header.setText(label);
		header.setIcon(new Arrow(open));
		header.setIconTextGap(6);
		header.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				boolean nowOpen = !body.isVisible();
				body.setVisible(nowOpen);
				header.setIcon(new Arrow(nowOpen));
				remember.accept(nowOpen);
				relayout();
			}
		});

		JPanel section = column();
		JComponent headerRow = row(header);
		headerRow.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
		section.add(headerRow);
		section.add(row(body));
		section.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
		return section;
	}

	/** A small open (pointing down) or closed (pointing right) triangle — drawn, not a font glyph. */
	private static final class Arrow implements Icon
	{
		private static final int SIZE = 8;
		private final boolean open;

		Arrow(boolean open)
		{
			this.open = open;
		}

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(c.getForeground());
			if (open)
			{
				g2.fillPolygon(new int[]{x, x + SIZE, x + SIZE / 2}, new int[]{y + 2, y + 2, y + SIZE - 1}, 3);
			}
			else
			{
				g2.fillPolygon(new int[]{x + 1, x + SIZE - 2, x + 1}, new int[]{y, y + SIZE / 2, y + SIZE}, 3);
			}
			g2.dispose();
		}

		@Override
		public int getIconWidth()
		{
			return SIZE;
		}

		@Override
		public int getIconHeight()
		{
			return SIZE;
		}
	}

	/** A bingo: the player's team, its rank and points, and the leaderboard. */
	private JPanel bingoCard(Api.MeBingo bingo)
	{
		JPanel lines = column();
		lines.add(row(bold(bingo.event)));
		String standing = bingo.rank == null ? "" : ", " + OcagePlugin.ordinal(bingo.rank);
		lines.add(row(text("Your team: " + bingo.team + standing + " - " + OcagePlugin.points(bingo.points), null)));
		if (bingo.leaderboard != null && !bingo.leaderboard.isEmpty())
		{
			lines.add(row(text("Leaderboard", null)));
			for (Api.MeBingoTeam team : bingo.leaderboard)
			{
				boolean mine = team.team.equals(bingo.team);
				String line = team.rank + ". " + team.team + " - " + OcagePlugin.points(team.points)
					+ " (" + team.tilesVerified + (team.tilesVerified == 1 ? " tile)" : " tiles)");
				lines.add(row(indented(mine ? bold(line) : text(line, null))));
			}
		}
		if (bingo.endDate != null)
		{
			lines.add(row(text("Ends " + ends(bingo.endDate, plugin.panelZone()), null)));
		}
		return card(lines);
	}

	/**
	 * A boss event: the player's KC (overall and per boss), the event's top
	 * players by KC with the player's own line added if they're further
	 * down, then their unique drops.
	 */
	private JPanel eventCard(Api.MeEvent event, String rsn)
	{
		JPanel lines = column();
		lines.add(row(bold(event.name)));
		lines.add(row(text("KC gained: " + event.kc + rank(event.kcRank), null)));
		// Every tracked boss, even one, even at 0 — so it's clear what counts.
		// An older bot sends no boss list: fall back to the bosses with KC.
		Map<String, Integer> kcByBoss = event.kcByBoss != null ? event.kcByBoss : new LinkedHashMap<>();
		List<String> bosses = event.bosses != null ? event.bosses : new ArrayList<>(kcByBoss.keySet());
		for (String boss : bosses)
		{
			lines.add(row(indented(text(boss + ": " + kcByBoss.getOrDefault(boss, 0), null))));
		}
		List<BoardLine> board = kcLeaderboard(event, rsn);
		if (!board.isEmpty())
		{
			lines.add(row(text("Leaderboard", null)));
			for (BoardLine line : board)
			{
				lines.add(row(indented(line.mine ? bold(line.text) : text(line.text, null))));
			}
		}
		lines.add(row(text("Unique drops: " + event.uniqueDrops + rank(event.dropsRank), null)));
		if (event.drops != null)
		{
			for (Api.MeDrop drop : event.drops)
			{
				lines.add(row(indented(text(drop.name, null))));
			}
		}
		if (event.endDate != null)
		{
			lines.add(row(text("Ends " + ends(event.endDate, plugin.panelZone()), null)));
		}
		return card(lines);
	}

	/** One line of a leaderboard, and whether it's the player's own (shown in bold). */
	static final class BoardLine
	{
		final String text;
		final boolean mine;

		BoardLine(String text, boolean mine)
		{
			this.text = text;
			this.mine = mine;
		}
	}

	/**
	 * "1. Woox - 100" for each of the event's top players, the player's own
	 * line in bold. If they're further down, their line is added after the
	 * top players ("12. Witted - 15"). No KC yet: no line of their own.
	 * Empty when the bot sent no leaderboard (an older bot) or nobody has KC.
	 */
	static List<BoardLine> kcLeaderboard(Api.MeEvent event, String rsn)
	{
		List<BoardLine> board = new ArrayList<>();
		if (event.leaderboard == null || event.leaderboard.isEmpty())
		{
			return board;
		}
		boolean shown = false;
		for (Api.MeKcRow row : event.leaderboard)
		{
			boolean mine = row.rsn != null && row.rsn.equalsIgnoreCase(rsn);
			shown |= mine;
			board.add(new BoardLine(row.rank + ". " + row.rsn + " - " + row.kc, mine));
		}
		if (!shown && event.kcRank != null && event.kc > 0)
		{
			board.add(new BoardLine(event.kcRank + ". " + rsn + " - " + event.kc, true));
		}
		return board;
	}

	/**
	 * An event that hasn't started: when it starts; for a bingo, the sign-up
	 * stage, whether the player is signed up and their team once drafted; for
	 * a boss event, its bosses and what counts the KC. Then a website link.
	 */
	private JPanel upcomingCard(Api.MeUpcoming event)
	{
		boolean bingo = !"boss".equals(event.eventType);
		JPanel lines = column();
		lines.add(row(bold(event.name)));
		lines.add(row(text((bingo ? "Bingo" : "Boss event") + " - " + starts(event.startDate, plugin.panelZone()), null)));
		if (bingo)
		{
			String stage = signupStage(event);
			if (stage != null)
			{
				Color color = event.signedUp == null ? ColorScheme.LIGHT_GRAY_COLOR
					: event.signedUp ? ColorScheme.PROGRESS_COMPLETE_COLOR : ColorScheme.PROGRESS_INPROGRESS_COLOR;
				lines.add(row(text(stage, color)));
			}
			if (event.team != null)
			{
				lines.add(row(text("Your team: " + event.team, null)));
			}
		}
		else
		{
			if (event.bosses != null && !event.bosses.isEmpty())
			{
				lines.add(row(indented(text(String.join(", ", event.bosses), null))));
			}
			String source = "plugin".equals(event.trackingSource) ? "Counted by this plugin: keep it on"
				: "temple".equals(event.trackingSource) ? "Counted from TempleOSRS" : null;
			if (source != null)
			{
				lines.add(row(text(source, ColorScheme.LIGHT_GRAY_COLOR)));
			}
		}
		JComponent link = link(event);
		if (link != null)
		{
			lines.add(row(link));
		}
		return card(lines);
	}

	/** "starts Fri 7 Nov, 19:00 CET", in {@code zone}; the bot's own text if it can't be read. */
	static String starts(String startDate, ZoneId zone)
	{
		if (startDate == null || startDate.isEmpty())
		{
			return "date to be announced";
		}
		return "starts " + when(startDate, zone);
	}

	/**
	 * "Sat 7 Nov, 19:00 CET", in {@code zone}. A date-only end date is shown as the UTC date
	 * ("Sat 7 Nov UTC"): the bot doesn't treat its end time the same everywhere, so no time is implied.
	 */
	static String ends(String endDate, ZoneId zone)
	{
		if (endDate.length() == 10)
		{
			try
			{
				return LocalDate.parse(endDate).format(DAY) + " UTC";
			}
			catch (DateTimeParseException e)
			{
				return endDate + " UTC";
			}
		}
		return when(endDate, zone);
	}

	/** A bot date ("yyyy-MM-dd HH:mm:ss", or a date for midnight) in {@code zone}; as sent, plus UTC, if unreadable. */
	private static String when(String botDate, ZoneId zone)
	{
		try
		{
			LocalDateTime utc = botDate.length() == 10
				? LocalDate.parse(botDate).atStartOfDay()
				: LocalDateTime.parse(botDate, BOT_DATE);
			return utc.atZone(UTC).withZoneSameInstant(zone).format(WHEN);
		}
		catch (DateTimeParseException e)
		{
			return botDate + " UTC";
		}
	}

	/** A bingo's sign-up stage, and whether the player is signed up; null before sign-ups open. */
	static String signupStage(Api.MeUpcoming event)
	{
		String you = event.signedUp == null ? "" : event.signedUp ? ": you're signed up" : ": you haven't signed up";
		if (event.status == null)
		{
			return null;
		}
		switch (event.status)
		{
			case "signup_open":
				return "Sign-ups open" + you;
			case "signup_closed":
				return "Sign-ups closed" + you;
			case "drafting":
				return "Teams being drafted" + you;
			case "drafted":
				return "Teams picked" + you;
			default:
				return null;
		}
	}

	/** "Sign up on ocage.cc" while sign-ups are open and the player isn't in, else "View on ocage.cc". */
	private JComponent link(Api.MeUpcoming event)
	{
		String host = linkHost(event.url, apiUrl);
		if (host == null)
		{
			return null;
		}
		boolean signUp = "signup_open".equals(event.status) && Boolean.FALSE.equals(event.signedUp);
		JLabel label = new JLabel((signUp ? "Sign up on " : "View on ") + host);
		label.setForeground(ColorScheme.GRAND_EXCHANGE_LIMIT);
		label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		label.setToolTipText(event.url);
		label.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				LinkBrowser.browse(event.url);
			}
		});
		return label;
	}

	/**
	 * The host of a link the bot sent, if the panel may open it: https on the
	 * clan's own domain ({@value #CLAN_DOMAIN} or a subdomain) or the bot's
	 * own host (a test server's site); else null, and no link is shown. The
	 * bot is ours, but the panel shouldn't open just any address it's sent.
	 */
	static String linkHost(String url, String apiUrl)
	{
		if (url == null || !url.startsWith("https://"))
		{
			return null;
		}
		try
		{
			String host = URI.create(url).getHost();
			if (host == null)
			{
				return null;
			}
			host = host.toLowerCase(Locale.ROOT);
			String apiHost = apiUrl == null ? null : URI.create(apiUrl).getHost();
			boolean clan = host.equals(CLAN_DOMAIN) || host.endsWith("." + CLAN_DOMAIN);
			return clan || host.equalsIgnoreCase(apiHost) ? host : null;
		}
		catch (IllegalArgumentException e)
		{
			return null;
		}
	}

	/** A dark card around one event's lines, with a gap below it. */
	private static JPanel card(JPanel lines)
	{
		JPanel card = new JPanel(new BorderLayout());
		card.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.add(lines, BorderLayout.CENTER);
		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setOpaque(false);
		wrapper.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
		wrapper.add(card);
		return wrapper;
	}

	private void setLinkedControls(boolean linked)
	{
		linkForm.setVisible(!linked);
		linkButton.setEnabled(true);
		// Refresh shows while not linked too: right after login the panel can say "not linked"
		// before RuneLite has loaded the account's settings, and Refresh reads the key again.
		buttons.removeAll();
		buttons.add(refreshButton);
		if (linked)
		{
			buttons.add(unlinkButton);
		}
		buttons.setVisible(true);
	}

	private static String rank(Integer rank)
	{
		return rank == null ? "" : " (#" + rank + ")";
	}
}
