package cc.ocage.plugin;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(OcageConfig.GROUP)
public interface OcageConfig extends Config
{
	String GROUP = "ocage";

	@ConfigSection(
		name = "Bingo overlay",
		description = "The box on the game screen during a bingo: event, team, password and time",
		position = 10
	)
	String overlaySection = "overlay";

	@ConfigItem(
		keyName = "countedMessages",
		name = "Show counted drops",
		description = "Show a chat message when a kill or drop counts toward an Ocage event, "
			+ "or you reach a new Combat Achievements tier",
		position = 1
	)
	default boolean countedMessages()
	{
		return true;
	}

	@ConfigItem(
		keyName = "bingoUpdates",
		name = "Bingo standing updates",
		description = "Show a chat message when your team's bingo points or rank change",
		position = 4
	)
	default boolean bingoUpdates()
	{
		return true;
	}

	@ConfigItem(
		keyName = "bingoOverlay",
		name = "Show on screen",
		description = "Show the bingo overlay while you play. Off or on, bingo drop screenshots "
			+ "always include it (with the chat hidden as 'Screenshot chat' says), "
			+ "but only while 'Screenshot bingo drops' is on.",
		position = 11,
		section = overlaySection
	)
	default boolean bingoOverlay()
	{
		return true;
	}

	@ConfigItem(
		keyName = "overlayStyle",
		name = "Style",
		description = "Full: the event, your team, the password and the time. Compact: the event and password only. "
			+ "Bingo drop screenshots always use Full.",
		position = 12,
		section = overlaySection
	)
	default OverlayStyle overlayStyle()
	{
		return OverlayStyle.FULL;
	}

	@ConfigItem(
		keyName = "overlayTime",
		name = "Time",
		description = "Short: 14 Oct 18:42 UTC. Long: Tue 14 Oct 2026 18:42:05 UTC. "
			+ "The Compact style shows no time.",
		position = 13,
		section = overlaySection
	)
	default OverlayTime overlayTime()
	{
		return OverlayTime.SHORT;
	}

	@ConfigItem(
		keyName = "overlaySize",
		name = "Size",
		description = "How big the overlay is drawn. Hold Alt and drag its edge to make it wider.",
		position = 14,
		section = overlaySection
	)
	default OverlaySize overlaySize()
	{
		return OverlaySize.SIZE_100;
	}

	@ConfigItem(
		keyName = "bingoScreenshots",
		name = "Screenshot bingo drops",
		description = "Upload a screenshot of a drop that counts for a bingo, as its proof. "
			+ "It shows your game screen; see 'Screenshot chat' for what's hidden. "
			+ "Off (the default): the drop is still submitted, without a picture.",
		// Plugin Hub rule: a setting that sends data to a third-party server is opt-in, with this warning.
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers",
		position = 3
	)
	default boolean bingoScreenshots()
	{
		return false;
	}

	@ConfigItem(
		keyName = "chatPrivacy",
		name = "Screenshot chat",
		description = "What bingo screenshots hide. Hide PMs: private messages shown above the chat box "
			+ "(the game's 'Split friends private chat' setting). Hide all: the chat box too. "
			+ "The chat may flicker for a moment while the screenshot is taken.",
		position = 5
	)
	default ChatPrivacy chatPrivacy()
	{
		return ChatPrivacy.HIDE_SPLIT_PM;
	}
}
