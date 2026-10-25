package cc.ocage.plugin;

import com.google.gson.annotations.SerializedName;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/**
 * Request and response bodies of the bot's /plugin/* routes. The bot's
 * openapi.json ("Plugin" tag) is the source of truth for these shapes.
 */
final class Api
{
	private Api()
	{
	}

	static final class LinkRequest
	{
		final String code;
		@SerializedName("account_hash")
		final String accountHash;
		final String rsn;

		LinkRequest(String code, String accountHash, String rsn)
		{
			this.code = code;
			this.accountHash = accountHash;
			this.rsn = rsn;
		}
	}

	static final class LinkResponse
	{
		String token;
		String rsn;
		@SerializedName("display_name")
		String displayName;
	}

	/** GET /plugin/config: what to report. */
	static final class Rules
	{
		@SerializedName("min_value")
		long minValue;
		@SerializedName("item_ids")
		List<Integer> itemIds;
		@SerializedName("clog_item_ids")
		List<Integer> clogItemIds;
		List<RulesEvent> events;
		/** Active bingo events the player is on a team for (older bots don't send it). */
		List<Bingo> bingo;
		@SerializedName("refresh_seconds")
		int refreshSeconds;
		/** When the next plugin-tracked boss event starts, ISO 8601 UTC (null: none; older bots don't send it). */
		@SerializedName("next_event_start")
		String nextEventStart;

		/**
		 * Whether a plugin-tracked boss event has started by {@code occurredAt}
		 * (ISO 8601), which these rules can't yet list: a kill from then on waits
		 * for fresher rules instead of being dropped. An unreadable time counts
		 * as started, so the kill waits and the bot decides.
		 */
		boolean eventStartedBy(String occurredAt)
		{
			if (nextEventStart == null)
			{
				return false;
			}
			try
			{
				return !Instant.parse(occurredAt).isBefore(Instant.parse(nextEventStart));
			}
			catch (DateTimeParseException | NullPointerException e)
			{
				return true;
			}
		}
	}

	static final class RulesEvent
	{
		int id;
		String name;
		List<String> bosses;
	}

	static final class Bingo
	{
		String event;
		String team;
		/** The event password (null if the event has none). */
		String password;
		@SerializedName("end_date")
		String endDate;
		/** The board's Item List: these are reported, with a screenshot. */
		@SerializedName("item_names")
		List<String> itemNames;
	}

	static final class Item
	{
		final int id;
		final int quantity;
		final long price;
		/** For the bot's /plugin_admin log only; the bot matches items by id. */
		final String name;

		Item(int id, int quantity, long price, String name)
		{
			this.id = id;
			this.quantity = quantity;
			this.price = price;
			this.name = name;
		}
	}

	/** One report (kill, loot, pet, clog, ca). {@code id} makes a resent report a harmless duplicate. */
	static final class Report
	{
		final String id;
		final String type;
		@SerializedName("occurred_at")
		final String occurredAt;
		final String source;
		final Integer kc;
		final List<Item> items;
		/** Loot only: RuneLite's loot type, lower-case ("npc", "event" for clues and chests, ...). */
		@SerializedName("loot_type")
		final String lootType;
		/** Loot only: the whole loot's GE value, items the rules filter out included. */
		@SerializedName("total_value")
		final Long totalValue;
		/** Loot only: a screenshot of it was taken, to upload if the bot asks (a bingo drop). */
		final Boolean screenshot;
		/** CA only: the tier before, when the plugin saw the tier rise in game (not a sync). */
		final String previous;
		/** Pet only: one the player already has ("you would have been followed"). */
		final Boolean duplicate;

		private Report(String id, String type, String occurredAt, String source, Integer kc, List<Item> items,
			String lootType, Long totalValue, Boolean screenshot, String previous, Boolean duplicate)
		{
			this.id = id;
			this.type = type;
			this.occurredAt = occurredAt;
			this.source = source;
			this.kc = kc;
			this.items = items;
			this.lootType = lootType;
			this.totalValue = totalValue;
			this.screenshot = screenshot;
			this.previous = previous;
			this.duplicate = duplicate;
		}

		static Report kill(String id, String occurredAt, String boss, int kc)
		{
			return new Report(id, "kill", occurredAt, boss, kc, null, null, null, null, null, null);
		}

		/** A pet; {@code name} is null when the plugin couldn't tell which (see OcagePlugin.petName). */
		static Report pet(String id, String occurredAt, String name, boolean duplicate)
		{
			return new Report(id, "pet", occurredAt, name, null, null, null, null, null, null, duplicate ? true : null);
		}

		/** A new collection-log item (the game's "New item added to your collection log" message). */
		static Report clog(String id, String occurredAt, String name)
		{
			return new Report(id, "clog", occurredAt, name, null, null, null, null, null, null, null);
		}

		static Report loot(String id, String occurredAt, String source, String lootType, long totalValue, List<Item> items,
			boolean screenshot)
		{
			return new Report(id, "loot", occurredAt, source, null, items, lootType, totalValue, screenshot ? true : null, null, null);
		}

		/** The highest Combat Achievements tier the account has reached ("None" if none yet). */
		/** {@code previous}: the tier before, if the plugin saw it rise in game; null for a sync. */
		static Report ca(String id, String occurredAt, String tier, String previous)
		{
			return new Report(id, "ca", occurredAt, tier, null, null, null, null, null, previous, null);
		}

		/** This loot report with only {@code kept} items (the rest of its fields unchanged). */
		Report withItems(List<Item> kept)
		{
			return new Report(id, type, occurredAt, source, kc, kept, lootType, totalValue, screenshot, previous, duplicate);
		}
	}

	static final class ReportBatch
	{
		final String rsn;
		final List<Report> events;

		ReportBatch(String rsn, List<Report> events)
		{
			this.rsn = rsn;
			this.events = events;
		}
	}

	static final class IngestResponse
	{
		int accepted;
		int duplicates;
		int ignored;
		List<Counted> counted;
		/** Report ids whose screenshot the bot wants (older bots don't send it). */
		List<String> screenshots;
		/** Chat lines for the player, e.g. a new CA tier (older bots don't send it). */
		List<String> notices;
	}

	static final class ScreenshotResponse
	{
		/** True: the bot will post it and submit the drop. False: it wasn't waiting for one (nothing to do). */
		boolean accepted;
	}

	static final class Counted
	{
		String event;
		String boss;
		Integer kc;
		@SerializedName("item_id")
		Integer itemId;
		Integer quantity;
	}

	/** GET /plugin/me: the side panel's standings. */
	static final class Me
	{
		String rsn;
		@SerializedName("display_name")
		String displayName;
		List<MeEvent> events;
		/** Active bingos the player is on a team for (older bots don't send it). */
		List<MeBingo> bingo;
		/** Events that haven't started yet, soonest first (older bots don't send it). */
		List<MeUpcoming> upcoming;
		/** When to ask again: shorter while a bingo is on (older bots don't send it). */
		@SerializedName("refresh_seconds")
		Integer refreshSeconds;
	}

	static final class MeBingo
	{
		String event;
		String team;
		/** The team's rank; tied teams share one. */
		Integer rank;
		double points;
		@SerializedName("tiles_verified")
		int tilesVerified;
		@SerializedName("end_date")
		String endDate;
		List<MeBingoTeam> leaderboard;
	}

	static final class MeBingoTeam
	{
		int rank;
		String team;
		double points;
		@SerializedName("tiles_verified")
		int tilesVerified;
	}

	static final class MeEvent
	{
		String name;
		/** Every boss the event tracks, in order (older bots don't send it). */
		List<String> bosses;
		@SerializedName("end_date")
		String endDate;
		int kc;
		@SerializedName("kc_by_boss")
		Map<String, Integer> kcByBoss;
		/** Overall KC rank; tied players share one. */
		@SerializedName("kc_rank")
		Integer kcRank;
		/** The top players by KC gained, best first (older bots don't send it). */
		List<MeKcRow> leaderboard;
		@SerializedName("unique_drops")
		int uniqueDrops;
		/** The unique items gained, by name (older bots don't send it). */
		List<MeDrop> drops;
		@SerializedName("drops_rank")
		Integer dropsRank;
	}

	static final class MeKcRow
	{
		/** Tied players share a rank. */
		int rank;
		String rsn;
		int kc;
	}

	static final class MeUpcoming
	{
		String name;
		/** "bingo" or "boss". */
		@SerializedName("event_type")
		String eventType;
		/** UTC, "YYYY-MM-DD HH:MM:SS"; null while it has no date. */
		@SerializedName("start_date")
		String startDate;
		/** "upcoming", or a bingo's sign-up stage: signup_open, signup_closed, drafting, drafted. */
		String status;
		/** A boss event's bosses. */
		List<String> bosses;
		/** A boss event's KC source: "plugin" or "temple". */
		@SerializedName("tracking_source")
		String trackingSource;
		/** Whether the player is signed up; null unless the bingo is at a sign-up stage. */
		@SerializedName("signed_up")
		Boolean signedUp;
		/** The player's team once teams are set. */
		String team;
		/** The event's page on the website, if the bot knows it. */
		String url;
	}

	static final class MeDrop
	{
		@SerializedName("item_id")
		int itemId;
		String name;
	}

	static final class Error
	{
		String detail;
		/** Set on the few errors the plugin must act on, e.g. "plugin_banned". */
		String code;
	}
}
