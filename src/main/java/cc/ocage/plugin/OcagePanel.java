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
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

/**
 * The Ocage sidebar: the clan banner, linking with a /plugin link code, then
 * your standing in active clan events. Every method runs on the Swing thread
 * (the plugin calls them through SwingUtilities.invokeLater).
 *
 * <p>Layout: one left-aligned column. Text is shown in read-only text areas
 * that wrap at word boundaries to whatever width the sidebar gives them —
 * HTML labels don't wrap reliably (they measure text differently from how
 * it's drawn, and RuneLite's font differs again), which cut lines off at the
 * right edge. Every row is left-aligned: a vertical BoxLayout otherwise
 * centres panels and left-aligns labels, pushing text off to one side.</p>
 */
class OcagePanel extends PluginPanel
{
	private final OcagePlugin plugin;
	private final JTextArea server;
	private final JTextArea status = text("", null);
	private final JTextArea error = text("", ColorScheme.PROGRESS_ERROR_COLOR);
	private final JTextField codeField = new JTextField();
	private final JButton linkButton = new JButton("Link");
	private final JButton unlinkButton = new JButton("Unlink");
	private final JButton refreshButton = new JButton("Refresh");
	private final JPanel linkForm = new JPanel(new BorderLayout(0, 4));
	private final JPanel standings = column();
	/** Whether each section is open, kept across refreshes (all open at first). */
	private boolean bingoOpen = true;
	private boolean bossOpen = true;
	/** The width the text was last wrapped at. */
	private int laidOutWidth = -1;

	OcagePanel(OcagePlugin plugin, String serverUrl, boolean production)
	{
		this.plugin = plugin;
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

		JPanel buttons = new JPanel(new GridLayout(1, 2, 4, 0));
		buttons.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
		buttons.add(refreshButton);
		buttons.add(unlinkButton);
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
		URL gif = OcagePanel.class.getResource("banner.gif");
		if (gif != null)
		{
			// ImageIcon from a URL keeps a GIF animated.
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
		status.setText("Log in to the game to link or see your events.");
		setLinkedControls(false);
		linkForm.setVisible(false);
		standings.removeAll();
		relayout();
	}

	void showNotLinked()
	{
		status.setText("This account isn't linked yet.");
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
		setError(message);
		linkButton.setEnabled(true);
		relayout();
	}

	private void setError(String message)
	{
		error.setText(message == null ? "" : message);
		error.setVisible(message != null && !message.isEmpty());
	}

	void showStandings(Api.Me me)
	{
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
				bossCards.add(eventCard(event));
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
			lines.add(row(text("Ends " + bingo.endDate + " UTC", null)));
		}
		return card(lines);
	}

	private JPanel eventCard(Api.MeEvent event)
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
			lines.add(row(text("Ends " + event.endDate + " UTC", null)));
		}
		return card(lines);
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
		unlinkButton.setVisible(linked);
		refreshButton.setVisible(linked);
	}

	private static String rank(Integer rank)
	{
		return rank == null ? "" : " (#" + rank + ")";
	}
}
