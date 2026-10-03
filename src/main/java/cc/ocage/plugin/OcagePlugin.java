package cc.ocage.plugin;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.inject.Provides;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.RuneScapeProfile;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.DrawManager;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Filepath;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;
import okhttp3.OkHttpClient;

/**
 * Reports kills and notable loot to the Ocage clan bot, which counts them
 * toward clan events (see the bot repo's docs/ARCHITECTURE.md → "RuneLite
 * plugin").
 *
 * <ul>
 * <li><b>Linking.</b> The player runs /plugin link in Discord and pastes the
 * code into the side panel; the bot returns a key, stored per game account
 * (RuneLite's RS profile config) and per server.</li>
 * <li><b>What's reported</b> is decided by the bot ({@link Api.Rules}, fetched
 * on login and every few minutes): collection-log items, items the clan
 * listed, and anything worth at least the value floor. Loot waits in the
 * queue until the rules are known, so nothing unlisted is ever sent. Kills
 * (boss and new KC, from the game's kill-count message) are only sent while
 * a boss event counts plugin reports; the rest are dropped unsent.</li>
 * <li><b>Delivery.</b> Reports queue and upload in batches every few seconds;
 * if the server can't be reached they wait and retry. The queue is saved to
 * {@code .runelite/plugin-data/ocage/} as it changes, so a restart doesn't lose what
 * hasn't been sent (the bot accepts reports up to 7 days old). Each report
 * carries its own id, so a resent batch never counts twice.</li>
 * <li><b>Bingo.</b> During a bingo the player is on a team for, {@link OcageOverlay}
 * shows the event, team and password on screen. A drop on the board's Item
 * List is screenshotted (overlay included) and the screenshot uploaded when
 * the bot asks for it: it becomes the proof of the drop's bingo submission,
 * which a moderator still approves.</li>
 * <li><b>Combat Achievements:</b> the highest tier
 * reached (CA points against the game's tier thresholds) is reported once
 * per login and whenever it rises; the bot keeps each tier's first time.</li>
 * </ul>
 * Loot comes from RuneLite's Loot Tracker plugin, which must be enabled.
 */
@Slf4j
@PluginDescriptor(
	name = "Ocage",
	description = "Reports your kills and notable drops to the Ocage clan bot for clan events",
	tags = {"clan", "ocage", "events", "drops", "boss", "bingo"},
	// The Plugin Hub name (plugins/ocage), which names the data directory,
	// .runelite/plugin-data/ocage; RuneLite moves .runelite/ocage there once.
	internalName = "ocage",
	legacyDataDirectory = "ocage"
)
public class OcagePlugin extends Plugin
{
	static final String VERSION = "1.0.0";

	/** "Your Vorkath kill count is: 123." and friends (chest, completion, success, harvest). */
	private static final Pattern KC_PATTERN = Pattern.compile(
		"Your (?<boss>.+?) (?:kill|chest|completion|success|harvest|opened) count is: ?(?<kc>[\\d,]+)\\b",
		Pattern.CASE_INSENSITIVE);
	/** "Your completed Chambers of Xeric count is: 12." / "Your subdued Wintertodt count is: 400." */
	private static final Pattern COMPLETED_PATTERN = Pattern.compile(
		"Your (?:completed|subdued) (?<boss>.+?) count is: ?(?<kc>[\\d,]+)\\b");
	/** "You have completed 12 hard Treasure Trails." (one clue: "1 hard Treasure Trail."). */
	static final Pattern CLUE_PATTERN = Pattern.compile(
		"You have completed (?<count>[\\d,]+) (?<tier>beginner|easy|medium|hard|elite|master) Treasure Trails?\\b",
		Pattern.CASE_INSENSITIVE);

	/** Needs the in-game setting "Collection log - New addition notification". */
	static final Pattern CLOG_PATTERN = Pattern.compile("New item added to your collection log: (?<item>.+)");
	/** A new pet (following, or in the backpack), or a duplicate ("you would have been followed"). */
	static final Pattern PET_PATTERN = Pattern.compile(
		"You have a funny feeling like you(?<dupe> would have been|'re being| are being) followed|You feel something weird sneaking into your backpack");
	/** Combat Achievements tiers, lowest first, and the game's points threshold for each. */
	static final String[] CA_TIERS = {"Easy", "Medium", "Hard", "Elite", "Master", "Grandmaster"};
	private static final int[] CA_THRESHOLDS = {
		VarbitID.CA_THRESHOLD_EASY, VarbitID.CA_THRESHOLD_MEDIUM, VarbitID.CA_THRESHOLD_HARD,
		VarbitID.CA_THRESHOLD_ELITE, VarbitID.CA_THRESHOLD_MASTER, VarbitID.CA_THRESHOLD_GRANDMASTER,
	};
	/** Game ticks after login before the CA tier is read, so every varbit has loaded (a GM must never read as "None"). */
	private static final int CA_SETTLE_TICKS = 5;
	/** A new pet's collection-log message comes with its pet message; this is how far apart they may be. */
	private static final long PET_NAME_WAIT_MS = 3_000;

	private static final int FLUSH_SECONDS = 5;
	private static final int BATCH_SIZE = 50;
	/** Past this many unsent reports (server down for a long time) the oldest are dropped. */
	private static final int MAX_QUEUE = 1000;
	/**
	 * Rules older than this aren't trusted to drop a kill: a boss event set to plugin tracking since they
	 * were fetched would lose it. The kill waits for fresh rules instead.
	 */
	private static final long KILL_RULES_MAX_AGE_MS = 30_000;
	/** How long to wait before asking again after the rules couldn't be fetched. */
	private static final long RULES_RETRY_MS = 60_000;
	/** Longest wait between upload retries while the server is unreachable or busy. */
	private static final long MAX_RETRY_MS = 300_000;
	/** Screenshots kept waiting to upload; past this the oldest are dropped (each is a few hundred KB). */
	private static final int MAX_SHOTS = 20;
	/** Wait after a failed screenshot upload (server unreachable or busy). */
	private static final long SHOT_RETRY_MS = 30_000;
	private static final float JPEG_QUALITY = 0.85f;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ConfigManager configManager;

	@Inject
	private OcageConfig config;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private OcageOverlay overlay;

	@Inject
	private DrawManager drawManager;

	@Inject
	private ScheduledExecutorService executor;

	@Inject
	private OkHttpClient okHttpClient;

	@Inject
	private Gson gson;

	private OcageClient api;
	private OcagePanel panel;
	private NavigationButton navButton;
	private ScheduledFuture<?> flushTask;
	/** .runelite/plugin-data/ocage (all file access goes through it); null if RuneLite couldn't provide it. */
	private Filepath dataDir;

	private final List<Pending> queue = new ArrayList<>();
	/** The queue changed since it was last saved. */
	private volatile boolean queueDirty;
	private volatile boolean flushing;
	private volatile Api.Rules rules;
	/** When the rules were last asked for (success or not) — so a failure isn't retried every tick. */
	private volatile long rulesRequestedAt;
	/** When the current rules arrived. */
	private volatile long rulesReceivedAt;
	/** When the side panel's standings were last asked for, and how often to ask (the bot says). */
	private volatile long meRequestedAt;
	private volatile long meIntervalMs = 300_000;
	/** The last standings shown, to tell the player what changed (null: none yet for this account). */
	private volatile Api.Me lastMe;
	/** Upload backoff after a transient failure: no upload before this time. */
	private volatile long nextFlushAt;
	private volatile long retryDelayMs = FLUSH_SECONDS * 1000L;

	/** Bingo screenshots by report id, in the order they were taken. */
	private final Map<String, Shot> shots = new LinkedHashMap<>();
	private volatile boolean uploadingShot;
	private volatile long nextShotAt;
	/** Frames still to be captured: the overlay is drawn in them even if the player hid it. */
	private volatile int capturing;

	/** A pet message waiting (client thread) for the collection-log message that names it. */
	private long petAt;
	private boolean petWaiting;
	/** The last collection-log item and when (client thread), in case it came just before the pet message. */
	private String lastClogItem;
	private long lastClogAt;

	/** Client thread: ticks since login (for CA_SETTLE_TICKS), whether CA points may have changed, and
	 * the tier last reported this session (null: not yet for this login). */
	private int ticksSinceLogin;
	private boolean caDirty;
	private String lastCaTier;

	/** The logged-in account, captured on the client thread for use elsewhere. */
	private volatile String accountHash;
	private volatile String rsn;

	/**
	 * A report plus the account it belongs to, fixed when it happened (the
	 * player may switch accounts). It's saved to disk while unsent, so it holds
	 * no key: the account's key is looked up when it's sent ({@link #keyFor}).
	 */
	private static final class Pending
	{
		final String accountHash;
		final String rsn;
		final Api.Report report;

		Pending(String accountHash, String rsn, Api.Report report)
		{
			this.accountHash = accountHash;
			this.rsn = rsn;
			this.report = report;
		}
	}

	/** A bingo drop's screenshot, for the account it was taken on. */
	private static final class Shot
	{
		final String key;
		final String accountHash;
		/** The JPEG, once captured and encoded (null until then). */
		volatile byte[] jpeg;
		/** The bot asked for it (the report was accepted as a bingo drop). */
		volatile boolean wanted;

		Shot(String key, String accountHash)
		{
			this.key = key;
			this.accountHash = accountHash;
		}
	}

	@Provides
	OcageConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(OcageConfig.class);
	}

	@Override
	protected void startUp()
	{
		api = new OcageClient(okHttpClient, gson, ApiUrl.resolve(), VERSION);
		panel = new OcagePanel(this, api.baseUrl(), ApiUrl.isProduction(api.baseUrl()));
		BufferedImage icon = ImageUtil.loadImageResource(getClass(), "icon.png");
		navButton = NavigationButton.builder().tooltip("Ocage").icon(icon).priority(8).panel(panel).build();
		clientToolbar.addNavigation(navButton);
		overlayManager.add(overlay);
		try
		{
			dataDir = getPluginDirectory();
		}
		catch (IOException | IllegalArgumentException e)
		{
			// Reports still send; they just aren't kept over a restart.
			log.warn("No Ocage data directory; unsent reports won't survive a restart", e);
			dataDir = null;
		}
		loadQueue();
		flushTask = executor.scheduleWithFixedDelay(this::tick, FLUSH_SECONDS, FLUSH_SECONDS, TimeUnit.SECONDS);
		clientThread.invokeLater(this::captureAccount);
	}

	@Override
	protected void shutDown()
	{
		flushTask.cancel(false);
		saveQueue();
		clientToolbar.removeNavigation(navButton);
		overlayManager.remove(overlay);
		synchronized (shots)
		{
			shots.clear();
		}
		accountHash = null;
		rsn = null;
		rules = null;
	}

	// -- Game events (client thread) -------------------------------------------

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			captureAccount();
		}
		else if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			ticksSinceLogin = 0;
			caDirty = false;
			lastCaTier = null;
			accountHash = null;
			rsn = null;
			SwingUtilities.invokeLater(() -> panel.showLoggedOut());
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (ticksSinceLogin < CA_SETTLE_TICKS)
		{
			ticksSinceLogin++;
			caDirty = true;  // read the tier once everything has loaded
			return;
		}
		if (caDirty)
		{
			caDirty = false;
			checkCaTier();
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (event.getVarbitId() == VarbitID.CA_POINTS)
		{
			caDirty = true;
		}
	}

	/**
	 * Client thread: reports the highest Combat Achievements tier reached, the first time this login and
	 * whenever it rises. Thresholds of 0 mean the varbits haven't loaded; nothing is sent then.
	 */
	private void checkCaTier()
	{
		if (client.getGameState() != GameState.LOGGED_IN || currentKey() == null)
		{
			return;
		}
		int points = client.getVarbitValue(VarbitID.CA_POINTS);
		String highest = "None";
		for (int i = 0; i < CA_TIERS.length; i++)
		{
			int threshold = client.getVarbitValue(CA_THRESHOLDS[i]);
			if (threshold <= 0)
			{
				return;
			}
			if (points >= threshold)
			{
				highest = CA_TIERS[i];
			}
		}
		if (highest.equals(lastCaTier))
		{
			return;
		}
		lastCaTier = highest;
		enqueue(Api.Report.ca(newId(), Instant.now().toString(), highest));
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM)
		{
			return;
		}
		String message = Text.removeTags(event.getMessage());
		if (clueCompleted(message) || petOrCollectionLog(message))
		{
			return;
		}
		Matcher matcher = KC_PATTERN.matcher(message);
		if (!matcher.find())
		{
			matcher = COMPLETED_PATTERN.matcher(message);
			if (!matcher.find())
			{
				return;
			}
		}
		int kc;
		try
		{
			kc = Integer.parseInt(matcher.group("kc").replace(",", ""));
		}
		catch (NumberFormatException e)
		{
			return;
		}
		enqueue(Api.Report.kill(newId(), Instant.now().toString(), matcher.group("boss"), kc));
	}

	/**
	 * A pet or a new collection-log item. A new pet is named by the
	 * collection-log message that comes with it (sent just before or after);
	 * a duplicate pet has none, so it's reported without a name.
	 */
	private boolean petOrCollectionLog(String message)
	{
		long now = System.currentTimeMillis();
		Matcher clog = CLOG_PATTERN.matcher(message);
		if (clog.find())
		{
			String item = clog.group("item").trim();
			enqueue(Api.Report.clog(newId(), Instant.now().toString(), item));
			if (petWaiting && now - petAt <= PET_NAME_WAIT_MS)
			{
				petWaiting = false;
				enqueue(Api.Report.pet(newId(), Instant.now().toString(), item));
			}
			else
			{
				lastClogItem = item;
				lastClogAt = now;
			}
			return true;
		}
		Matcher pet = PET_PATTERN.matcher(message);
		if (!pet.find())
		{
			return false;
		}
		boolean duplicate = " would have been".equals(pet.group("dupe"));
		if (duplicate)
		{
			enqueue(Api.Report.pet(newId(), Instant.now().toString(), null));
		}
		else if (lastClogItem != null && now - lastClogAt <= PET_NAME_WAIT_MS)
		{
			enqueue(Api.Report.pet(newId(), Instant.now().toString(), lastClogItem));
			lastClogItem = null;
		}
		else
		{
			petWaiting = true;
			petAt = now;
			clientThread.invokeLater(this::petTimeout);
		}
		return true;
	}

	/** Client thread: a new pet whose collection-log message never came (the setting is off). */
	private boolean petTimeout()
	{
		if (!petWaiting)
		{
			return true;
		}
		if (System.currentTimeMillis() - petAt < PET_NAME_WAIT_MS)
		{
			return false;  // invokeLater calls again next tick
		}
		petWaiting = false;
		enqueue(Api.Report.pet(newId(), Instant.now().toString(), null));
		return true;
	}

	/** "You have completed 12 hard Treasure Trails." - reported as a kill of "Clue Scroll (Hard)". */
	private boolean clueCompleted(String message)
	{
		Matcher matcher = CLUE_PATTERN.matcher(message);
		if (!matcher.find())
		{
			return false;
		}
		int count;
		try
		{
			count = Integer.parseInt(matcher.group("count").replace(",", ""));
		}
		catch (NumberFormatException e)
		{
			return true;
		}
		String tier = matcher.group("tier").toLowerCase();
		String boss = "Clue Scroll (" + Character.toUpperCase(tier.charAt(0)) + tier.substring(1) + ")";
		enqueue(Api.Report.kill(newId(), Instant.now().toString(), boss, count));
		return true;
	}

	@Subscribe
	public void onLootReceived(LootReceived event)
	{
		List<Api.Item> items = new ArrayList<>();
		long total = 0;
		for (ItemStack stack : event.getItems())
		{
			int id = itemManager.canonicalize(stack.getId());
			String name = itemManager.getItemComposition(id).getName();
			long price = itemManager.getItemPrice(id);
			total += price * stack.getQuantity();
			items.add(new Api.Item(id, stack.getQuantity(), price, name));
		}
		if (items.isEmpty())
		{
			return;
		}
		String lootType = event.getType() == null ? null : event.getType().name().toLowerCase();
		String id = newId();
		boolean screenshot = config.bingoScreenshots() && isBingoDrop(items, rules) && currentKey() != null;
		enqueue(Api.Report.loot(id, Instant.now().toString(), event.getName(), lootType, total, items, screenshot));
		if (screenshot)
		{
			takeScreenshot(id);
		}
	}

	// -- Bingo (overlay and screenshots) -----------------------------------------

	/** The active bingo events the player is on a team for (empty outside a bingo). */
	List<Api.Bingo> activeBingo()
	{
		Api.Rules current = rules;
		return current == null || current.bingo == null || accountHash == null
			? Collections.emptyList() : current.bingo;
	}

	boolean showBingoOverlay()
	{
		return config.bingoOverlay() || capturing > 0;
	}

	/** Lower-cased Item List names of every active bingo. */
	private static Set<String> bingoNames(Api.Rules rules)
	{
		Set<String> names = new HashSet<>();
		if (rules != null && rules.bingo != null)
		{
			for (Api.Bingo entry : rules.bingo)
			{
				if (entry.itemNames != null)
				{
					for (String name : entry.itemNames)
					{
						names.add(name.toLowerCase());
					}
				}
			}
		}
		return names;
	}

	static boolean isBingoDrop(List<Api.Item> items, Api.Rules rules)
	{
		Set<String> names = bingoNames(rules);
		for (Api.Item item : items)
		{
			if (item.name != null && names.contains(item.name.toLowerCase()))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Client thread: screenshots the game (overlay included) for report
	 * {@code reportId}. If the player hid the overlay it's drawn for one
	 * frame first, so the frame after that is captured.
	 */
	private void takeScreenshot(String reportId)
	{
		Shot shot = new Shot(currentKey(), accountHash);
		synchronized (shots)
		{
			shots.put(reportId, shot);
			while (shots.size() > MAX_SHOTS)
			{
				shots.remove(shots.keySet().iterator().next());
			}
		}
		// Hide what the player chose to keep private for the captured frame
		// only (what the Dink plugin does), then show it again.
		ChatPrivacy privacy = config.chatPrivacy();
		boolean chatHidden = hideWidget(privacy == ChatPrivacy.HIDE_ALL, InterfaceID.Chatbox.CHATAREA);
		boolean pmsHidden = hideWidget(privacy != ChatPrivacy.HIDE_NONE, InterfaceID.PmChat.CONTAINER);
		Runnable restore = () ->
		{
			showWidget(chatHidden, InterfaceID.Chatbox.CHATAREA);
			showWidget(pmsHidden, InterfaceID.PmChat.CONTAINER);
		};

		boolean overlayShown = config.bingoOverlay();
		capturing++;
		drawManager.requestNextFrameListener(first ->
		{
			if (overlayShown)
			{
				capture(shot, first, restore);
			}
			else
			{
				drawManager.requestNextFrameListener(second -> capture(shot, second, restore));
			}
		});
	}

	/** Client thread: hides a widget if asked and it's showing; true if it did. */
	private boolean hideWidget(boolean hide, int componentId)
	{
		if (!hide)
		{
			return false;
		}
		Widget widget = client.getWidget(componentId);
		if (widget == null || widget.isHidden())
		{
			return false;
		}
		widget.setHidden(true);
		return true;
	}

	private void showWidget(boolean wasHidden, int componentId)
	{
		if (!wasHidden)
		{
			return;
		}
		clientThread.invoke(() ->
		{
			Widget widget = client.getWidget(componentId);
			if (widget != null)
			{
				widget.setHidden(false);
			}
		});
	}

	private void capture(Shot shot, Image frame, Runnable restore)
	{
		restore.run();
		capturing--;
		BufferedImage copy = new BufferedImage(frame.getWidth(null), frame.getHeight(null), BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = copy.createGraphics();
		graphics.drawImage(frame, 0, 0, null);
		graphics.dispose();
		executor.execute(() ->
		{
			try
			{
				shot.jpeg = jpeg(copy);
			}
			catch (IOException e)
			{
				log.warn("Couldn't encode the Ocage screenshot", e);
			}
		});
	}

	static byte[] jpeg(BufferedImage image) throws IOException
	{
		ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (ImageOutputStream stream = ImageIO.createImageOutputStream(out))
		{
			writer.setOutput(stream);
			ImageWriteParam param = writer.getDefaultWriteParam();
			param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			param.setCompressionQuality(JPEG_QUALITY);
			writer.write(null, new IIOImage(image, null, null), param);
		}
		finally
		{
			writer.dispose();
		}
		return out.toByteArray();
	}

	/** Uploads the oldest screenshot the bot asked for, one at a time. */
	private void uploadShots()
	{
		if (uploadingShot || System.currentTimeMillis() < nextShotAt)
		{
			return;
		}
		String reportId = null;
		Shot shot = null;
		synchronized (shots)
		{
			for (Map.Entry<String, Shot> entry : shots.entrySet())
			{
				if (entry.getValue().wanted && entry.getValue().jpeg != null)
				{
					reportId = entry.getKey();
					shot = entry.getValue();
					break;
				}
			}
		}
		if (shot == null)
		{
			return;
		}
		uploadingShot = true;
		String id = reportId;
		Shot sending = shot;
		api.screenshot(shot.key, shot.accountHash, id, shot.jpeg, new OcageClient.Result<Api.ScreenshotResponse>()
		{
			@Override
			public void ok(Api.ScreenshotResponse value)
			{
				dropShot(id);
				uploadingShot = false;
			}

			@Override
			public void fail(OcageClient.Failure failure)
			{
				uploadingShot = false;
				if (failure.retryable())
				{
					nextShotAt = System.currentTimeMillis() + SHOT_RETRY_MS;
					return;
				}
				// The drop is still submitted, without a picture.
				log.warn("Ocage rejected a screenshot ({}): {}", failure.status, failure.message);
				dropShot(id);
				handleFailure(sending.key, sending.accountHash, failure);
			}
		});
	}

	private void dropShot(String reportId)
	{
		synchronized (shots)
		{
			shots.remove(reportId);
		}
	}

	private void captureAccount()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		long hash = client.getAccountHash();
		Player player = client.getLocalPlayer();
		if (hash == -1 || player == null || player.getName() == null)
		{
			// The local player isn't loaded on the very first logged-in tick.
			clientThread.invokeLater(this::captureAccount);
			return;
		}
		boolean changed = !String.valueOf(hash).equals(accountHash);
		if (changed)
		{
			lastMe = null;
			lastCaTier = null;
		}
		accountHash = String.valueOf(hash);
		rsn = player.getName();
		if (changed)
		{
			rules = null;
			executor.execute(this::refresh);
		}
	}

	// -- Linking (called from the panel) ----------------------------------------

	void link(String code)
	{
		String hash = accountHash;
		String name = rsn;
		if (hash == null || name == null)
		{
			SwingUtilities.invokeLater(() -> panel.showError("Log in to the game first, on the account you're linking."));
			return;
		}
		api.link(new Api.LinkRequest(code, hash, name), new OcageClient.Result<Api.LinkResponse>()
		{
			@Override
			public void ok(Api.LinkResponse value)
			{
				configManager.setRSProfileConfiguration(OcageConfig.GROUP, keyName(), value.token);
				SwingUtilities.invokeLater(() -> panel.showLinked(value.rsn));
				// Record the CA tiers they already have now, not at their next login.
				clientThread.invokeLater(() ->
				{
					lastCaTier = null;
					caDirty = true;
				});
				refresh();
			}

			@Override
			public void fail(OcageClient.Failure failure)
			{
				SwingUtilities.invokeLater(() -> panel.showError(failure.message));
			}
		});
	}

	void unlink()
	{
		String key = currentKey();
		String hash = accountHash;
		configManager.unsetRSProfileConfiguration(OcageConfig.GROUP, keyName());
		rules = null;
		SwingUtilities.invokeLater(() -> panel.showNotLinked());
		if (key != null)
		{
			api.unlink(key, hash, new OcageClient.Result<Void>()
			{
				@Override
				public void ok(Void value)
				{
				}

				@Override
				public void fail(OcageClient.Failure failure)
				{
					log.debug("Unlink on the server failed ({}): {}", failure.status, failure.message);
				}
			});
		}
	}

	/** Fetches the rules and the player's standings, and updates the panel. */
	void refresh()
	{
		String key = currentKey();
		String hash = accountHash;
		if (hash == null)
		{
			return;
		}
		if (key == null)
		{
			SwingUtilities.invokeLater(() -> panel.showNotLinked());
			return;
		}
		rulesRequestedAt = System.currentTimeMillis();
		api.rules(key, hash, new OcageClient.Result<Api.Rules>()
		{
			@Override
			public void ok(Api.Rules value)
			{
				rulesReceivedAt = System.currentTimeMillis();
				rules = value;
			}

			@Override
			public void fail(OcageClient.Failure failure)
			{
				handleFailure(key, hash, failure);
			}
		});
		refreshMe();
	}

	/** Fetches the player's standings (events, bingo leaderboards) for the side panel. */
	void refreshMe()
	{
		String key = currentKey();
		String hash = accountHash;
		if (key == null || hash == null)
		{
			return;
		}
		meRequestedAt = System.currentTimeMillis();
		api.me(key, hash, new OcageClient.Result<Api.Me>()
		{
			@Override
			public void ok(Api.Me value)
			{
				if (!hash.equals(accountHash))
				{
					return;  // the player switched accounts meanwhile
				}
				if (value.refreshSeconds != null)
				{
					meIntervalMs = Math.max(30, value.refreshSeconds) * 1000L;
				}
				Api.Me previous = lastMe;
				lastMe = value;
				if (previous != null && config.bingoUpdates())
				{
					List<String> changes = bingoChanges(previous, value);
					if (!changes.isEmpty())
					{
						clientThread.invokeLater(() -> changes.forEach(OcagePlugin.this::chat));
					}
				}
				SwingUtilities.invokeLater(() -> panel.showStandings(value));
			}

			@Override
			public void fail(OcageClient.Failure failure)
			{
				if (failure.status == 0)
				{
					SwingUtilities.invokeLater(() -> panel.showError(failure.message));
				}
			}
		});
	}

	/** The config key holding this server's key. Each server issues its own keys. */
	String keyName()
	{
		if (ApiUrl.isProduction(api.baseUrl()))
		{
			return "key";
		}
		return "key-" + api.baseUrl().replaceAll("[^A-Za-z0-9]", "");
	}

	private String currentKey()
	{
		if (accountHash == null)
		{
			return null;
		}
		String key = configManager.getRSProfileConfiguration(OcageConfig.GROUP, keyName());
		return key == null || key.isEmpty() ? null : key;
	}

	/**
	 * The key of the account with this hash, logged in or not (a queued report
	 * from an account the player has since switched away from), or null if
	 * that account isn't linked (any more).
	 */
	private String keyFor(String hash)
	{
		if (hash.equals(accountHash))
		{
			return currentKey();
		}
		for (RuneScapeProfile profile : configManager.getRSProfiles())
		{
			if (hash.equals(String.valueOf(profile.getAccountHash())))
			{
				String key = configManager.getConfiguration(OcageConfig.GROUP, profile.getKey(), keyName());
				if (key != null && !key.isEmpty())
				{
					return key;
				}
			}
		}
		return null;
	}

	/**
	 * What a failed call with {@code key} means for that key:
	 * <ul>
	 * <li><b>Banned</b> (403 "plugin_banned") or <b>dead</b> (401: revoked,
	 * relinked elsewhere): drop everything queued for the account and delete the key.
	 * With no key, the plugin makes no requests at all for that account until
	 * the player links again with a new /plugin link code — which Discord only
	 * gives out once a moderator has lifted the ban.</li>
	 * <li>Any other 403 (e.g. an RSN that isn't theirs): just show it.</li>
	 * </ul>
	 * A queued upload from another account never touches the logged-in
	 * account's key or panel.
	 */
	private void handleFailure(String key, String hash, OcageClient.Failure failure)
	{
		boolean current = key.equals(currentKey());
		if (failure.banned() || failure.status == 401)
		{
			synchronized (queue)
			{
				queue.removeIf(pending -> pending.accountHash.equals(hash));
			}
			queueDirty = true;
			synchronized (shots)
			{
				shots.values().removeIf(shot -> key.equals(shot.key));
			}
			if (current)
			{
				configManager.unsetRSProfileConfiguration(OcageConfig.GROUP, keyName());
				rules = null;
				String message = failure.banned()
					? "This account is banned from the Ocage plugin, so it has been unlinked and stopped sending. "
						+ "If you think that's wrong, ask a moderator; once you're unbanned, run /plugin link for a new code."
					: failure.message;
				SwingUtilities.invokeLater(() ->
				{
					panel.showNotLinked();
					panel.showError(message);
				});
			}
		}
		else if (failure.status == 403 && current)
		{
			SwingUtilities.invokeLater(() -> panel.showError(failure.message));
		}
	}

	// -- Queue and upload (background thread) ------------------------------------

	private void enqueue(Api.Report report)
	{
		if (currentKey() == null)
		{
			return;  // not linked on this account: nothing is recorded at all
		}
		synchronized (queue)
		{
			queue.add(new Pending(accountHash, rsn, report));
			while (queue.size() > MAX_QUEUE)
			{
				queue.remove(0);
			}
		}
		queueDirty = true;
	}

	/** The saved queue for this server (each server has its own keys); null if there's no data directory. */
	private Filepath queueFile()
	{
		return dataDir == null ? null : dataDir.joinSegment("queue-" + keyName() + ".json");
	}

	/** Startup: picks up whatever a previous session couldn't send. */
	private void loadQueue()
	{
		synchronized (queue)
		{
			queue.clear();
		}
		Filepath file = queueFile();
		if (file == null || !file.exists())
		{
			return;
		}
		try (Reader reader = file.openBufferedReader())
		{
			Pending[] saved = gson.fromJson(reader, Pending[].class);
			synchronized (queue)
			{
				for (Pending pending : saved == null ? new Pending[0] : saved)
				{
					if (pending != null && pending.accountHash != null && pending.report != null)
					{
						queue.add(pending);
					}
				}
			}
			// Rewrite it at once: a file saved by an older version also held the key.
			queueDirty = true;
			log.debug("Loaded {} unsent Ocage report(s)", queue.size());
		}
		catch (IOException | JsonParseException e)
		{
			log.warn("Couldn't read the saved Ocage queue; starting empty", e);
		}
	}

	/** Writes the queue if it changed (a temporary file, then a rename, so a crash can't leave half a file). */
	private void saveQueue()
	{
		if (!queueDirty)
		{
			return;
		}
		queueDirty = false;
		List<Pending> copy;
		synchronized (queue)
		{
			copy = new ArrayList<>(queue);
		}
		Filepath file = queueFile();
		if (file == null)
		{
			return;  // kept in memory only
		}
		try
		{
			if (copy.isEmpty())
			{
				file.deleteIfExists();
				return;
			}
			dataDir.createDirectories();
			Filepath temp = dataDir.joinSegment(file.getFileName() + ".tmp");
			try (Writer writer = temp.openBufferedWriter())
			{
				gson.toJson(copy, writer);
			}
			temp.moveTo(file, StandardCopyOption.REPLACE_EXISTING);
		}
		catch (IOException e)
		{
			queueDirty = true;  // try again next tick
			log.warn("Couldn't save the Ocage queue", e);
		}
	}

	private void tick()
	{
		Api.Rules current = rules;
		long sinceAsked = System.currentTimeMillis() - rulesRequestedAt;
		long due = current == null ? RULES_RETRY_MS : Math.max(60, current.refreshSeconds) * 1000L;
		if (currentKey() != null && sinceAsked >= due)
		{
			refresh();
		}
		else if (currentKey() != null && System.currentTimeMillis() - meRequestedAt >= meIntervalMs)
		{
			refreshMe();
		}
		if (System.currentTimeMillis() >= nextFlushAt)
		{
			flush();
		}
		uploadShots();
		saveQueue();
	}

	private void flush()
	{
		if (flushing)
		{
			return;
		}
		Api.Rules current = rules;
		List<Pending> batch = new ArrayList<>();
		synchronized (queue)
		{
			Iterator<Pending> it = queue.iterator();
			while (it.hasNext() && batch.size() < BATCH_SIZE)
			{
				Pending pending = it.next();
				if (!batch.isEmpty() && !batch.get(0).accountHash.equals(pending.accountHash))
				{
					continue;  // one account per upload
				}
				if ("loot".equals(pending.report.type))
				{
					if (current == null || !pending.accountHash.equals(accountHash))
					{
						continue;  // filtered with the rules of the account it belongs to
					}
					Api.Report filtered = filterLoot(pending.report, current);
					if (filtered == null)
					{
						it.remove();  // nothing reportable in this loot
						queueDirty = true;
						continue;
					}
					pending = new Pending(pending.accountHash, pending.rsn, filtered);
				}
				else if ("kill".equals(pending.report.type))
				{
					if (current == null || !pending.accountHash.equals(accountHash))
					{
						continue;  // decided with the rules of the account it belongs to
					}
					if (!wantsKills(current))
					{
						long now = System.currentTimeMillis();
						if (now - rulesReceivedAt > KILL_RULES_MAX_AGE_MS)
						{
							// An event may have started since: ask again, and keep the kill until we know.
							if (now - rulesRequestedAt > FLUSH_SECONDS * 1000L)
							{
								refresh();
							}
							continue;
						}
						it.remove();  // no plugin-tracked boss event: nothing would count it
						queueDirty = true;
						continue;
					}
				}
				batch.add(pending);
			}
		}
		if (batch.isEmpty())
		{
			return;
		}
		Pending first = batch.get(0);
		String key = keyFor(first.accountHash);
		if (key == null)
		{
			// Unlinked since: nothing more is sent for that account.
			synchronized (queue)
			{
				queue.removeIf(pending -> pending.accountHash.equals(first.accountHash));
			}
			queueDirty = true;
			return;
		}
		flushing = true;
		List<Api.Report> reports = new ArrayList<>();
		for (Pending pending : batch)
		{
			reports.add(pending.report);
		}
		api.report(key, first.accountHash, new Api.ReportBatch(first.rsn, reports), new OcageClient.Result<Api.IngestResponse>()
		{
			@Override
			public void ok(Api.IngestResponse value)
			{
				removeFromQueue(batch);
				retryDelayMs = FLUSH_SECONDS * 1000L;
				flushing = false;
				markWantedShots(reports, value.screenshots);
				if (value.notices != null && !value.notices.isEmpty() && config.countedMessages())
				{
					clientThread.invokeLater(() -> showNotices(value.notices));
				}
				if (value.counted != null && !value.counted.isEmpty())
				{
					if (config.countedMessages())
					{
						clientThread.invokeLater(() -> announce(value.counted));
					}
					refreshMe();  // the panel's standings just changed
				}
			}

			@Override
			public void fail(OcageClient.Failure failure)
			{
				flushing = false;
				if (failure.retryable())
				{
					// Keep them and back off (5s, 10s, 20s … up to 5 minutes).
					nextFlushAt = System.currentTimeMillis() + retryDelayMs;
					retryDelayMs = Math.min(retryDelayMs * 2, MAX_RETRY_MS);
					return;
				}
				log.warn("Ocage rejected {} report(s) ({}): {}", batch.size(), failure.status, failure.message);
				removeFromQueue(batch);
				markWantedShots(reports, null);
				handleFailure(key, first.accountHash, failure);
			}
		});
	}

	/** After an upload: keep the screenshots the bot asked for, drop the rest. */
	private void markWantedShots(List<Api.Report> sent, List<String> wanted)
	{
		synchronized (shots)
		{
			for (Api.Report report : sent)
			{
				Shot shot = shots.get(report.id);
				if (shot == null)
				{
					continue;
				}
				if (wanted != null && wanted.contains(report.id))
				{
					shot.wanted = true;
				}
				else
				{
					shots.remove(report.id);
				}
			}
		}
	}

	private void removeFromQueue(List<Pending> sent)
	{
		Set<String> ids = new HashSet<>();
		for (Pending pending : sent)
		{
			ids.add(pending.report.id);
		}
		synchronized (queue)
		{
			queue.removeIf(pending -> ids.contains(pending.report.id));
		}
		queueDirty = true;
	}

	/**
	 * Whether kills are worth sending: only while a boss event counts plugin
	 * reports (the rules list them). The bot matches the boss itself and
	 * ignores a kill that counts toward nothing, so this only saves traffic.
	 */
	static boolean wantsKills(Api.Rules rules)
	{
		return rules.events != null && !rules.events.isEmpty();
	}

	/**
	 * The loot report with only the items the rules ask for, or null if none
	 * and the loot's total value (a clue casket, a raid chest) is below the
	 * value floor too — such a report is sent with no items, for the log.
	 */
	static Api.Report filterLoot(Api.Report report, Api.Rules rules)
	{
		Set<Integer> allowed = new HashSet<>();
		if (rules.itemIds != null)
		{
			allowed.addAll(rules.itemIds);
		}
		if (rules.clogItemIds != null)
		{
			allowed.addAll(rules.clogItemIds);
		}
		Set<String> bingo = bingoNames(rules);
		List<Api.Item> kept = new ArrayList<>();
		for (Api.Item item : report.items)
		{
			if (allowed.contains(item.id) || item.price * item.quantity >= rules.minValue
				|| (item.name != null && bingo.contains(item.name.toLowerCase())))
			{
				kept.add(item);
			}
		}
		long total = report.totalValue == null ? 0 : report.totalValue;
		if (kept.isEmpty() && total < rules.minValue)
		{
			return null;
		}
		return report.withItems(kept);
	}

	/**
	 * What changed for the player's team between two fetches of the
	 * standings: "Alpha has 130 pts in Autumn Bingo (was 120) - now 1st."
	 * A leaderboard moves when moderators approve drops, which the plugin
	 * can't see, so it's noticed on the next fetch (every minute or so while
	 * a bingo is on).
	 */
	static List<String> bingoChanges(Api.Me before, Api.Me after)
	{
		List<String> changes = new ArrayList<>();
		if (before.bingo == null || after.bingo == null)
		{
			return changes;
		}
		for (Api.MeBingo now : after.bingo)
		{
			Api.MeBingo was = null;
			for (Api.MeBingo old : before.bingo)
			{
				if (old.event.equals(now.event) && old.team.equals(now.team))
				{
					was = old;
				}
			}
			if (was == null)
			{
				continue;
			}
			boolean pointsChanged = was.points != now.points;
			boolean rankChanged = now.rank != null && !now.rank.equals(was.rank);
			if (!pointsChanged && !rankChanged)
			{
				continue;
			}
			String text = now.team + " has " + points(now.points) + " in " + now.event;
			if (pointsChanged)
			{
				text += " (was " + points(was.points) + ")";
			}
			if (now.rank != null)
			{
				text += rankChanged ? " - now " + ordinal(now.rank) : " - " + ordinal(now.rank);
			}
			changes.add(text + ".");
		}
		return changes;
	}

	static String points(double value)
	{
		String number = value == Math.rint(value) ? String.valueOf((long) value) : String.format("%.1f", value);
		return number + (value == 1 ? " pt" : " pts");
	}

	static String ordinal(int n)
	{
		int mod100 = n % 100;
		String suffix = mod100 >= 11 && mod100 <= 13 ? "th"
			: n % 10 == 1 ? "st" : n % 10 == 2 ? "nd" : n % 10 == 3 ? "rd" : "th";
		return n + suffix;
	}

	/** Client thread: one "Ocage:" chat line. */
	private void chat(String message)
	{
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "<col=1f7a70>Ocage:</col> " + message, null);
	}

	/** Client thread: "Ocage: Skeletal visage counted for Vorkath Week." */
	private void announce(List<Api.Counted> counted)
	{
		for (Api.Counted c : counted)
		{
			String what = c.itemId != null
				? itemManager.getItemComposition(c.itemId).getName() + (c.quantity != null && c.quantity > 1 ? " x" + c.quantity : "")
				: "+" + c.kc + " " + c.boss + " KC";
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
				"<col=1f7a70>Ocage:</col> " + what + " counted for " + c.event + ".", null);
		}
	}

	/** Client thread: "Ocage: Congratulations on the Elite Combat Achievements tier!" */
	private void showNotices(List<String> notices)
	{
		for (String notice : notices)
		{
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "<col=1f7a70>Ocage:</col> " + Text.escapeJagex(notice), null);
		}
	}

	private static String newId()
	{
		return UUID.randomUUID().toString();
	}
}
