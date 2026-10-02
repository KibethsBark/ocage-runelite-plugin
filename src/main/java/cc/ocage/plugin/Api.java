package cc.ocage.plugin;

import com.google.gson.annotations.SerializedName;
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

		private Report(String id, String type, String occurredAt, String source, Integer kc, List<Item> items,
			String lootType, Long totalValue, Boolean screenshot)
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
		}

		static Report kill(String id, String occurredAt, String boss, int kc)
		{
			return new Report(id, "kill", occurredAt, boss, kc, null, null, null, null);
		}

		/** A pet; {@code name} is null when the game didn't say which (a duplicate pet). */
		static Report pet(String id, String occurredAt, String name)
		{
			return new Report(id, "pet", occurredAt, name, null, null, null, null, null);
		}

		/** A new collection-log item (the game's "New item added to your collection log" message). */
		static Report clog(String id, String occurredAt, String name)
		{
			return new Report(id, "clog", occurredAt, name, null, null, null, null, null);
		}

		static Report loot(String id, String occurredAt, String source, String lootType, long totalValue, List<Item> items,
			boolean screenshot)
		{
			return new Report(id, "loot", occurredAt, source, null, items, lootType, totalValue, screenshot ? true : null);
		}

		/** The highest Combat Achievements tier the account has reached ("None" if none yet). */
		static Report ca(String id, String occurredAt, String tier)
		{
			return new Report(id, "ca", occurredAt, tier, null, null, null, null, null);
		}

		/** This loot report with only {@code kept} items (the rest of its fields unchanged). */
		Report withItems(List<Item> kept)
		{
			return new Report(id, type, occurredAt, source, kc, kept, lootType, totalValue, screenshot);
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
		@SerializedName("kc_rank")
		Integer kcRank;
		@SerializedName("unique_drops")
		int uniqueDrops;
		/** The unique items gained, by name (older bots don't send it). */
		List<MeDrop> drops;
		@SerializedName("drops_rank")
		Integer dropsRank;
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
