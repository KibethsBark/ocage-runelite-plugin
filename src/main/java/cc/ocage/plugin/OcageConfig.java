package cc.ocage.plugin;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(OcageConfig.GROUP)
public interface OcageConfig extends Config
{
	String GROUP = "ocage";

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
		name = "Show bingo overlay",
		description = "During a bingo, show the event, your team and the event password on screen. "
			+ "Screenshots of bingo drops always include it.",
		position = 2
	)
	default boolean bingoOverlay()
	{
		return true;
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
