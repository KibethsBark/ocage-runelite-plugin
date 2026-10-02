package cc.ocage.plugin;

/**
 * What bingo screenshots hide of the chat (the same choices as the Dink
 * plugin). Labels are short on purpose: RuneLite's settings page puts the
 * dropdown beside the setting's name, sized to the longest label, so a long
 * one squeezes the name to "..".
 */
public enum ChatPrivacy
{
	HIDE_NONE("Show all"),
	HIDE_SPLIT_PM("Hide PMs"),
	HIDE_ALL("Hide all");

	private final String label;

	ChatPrivacy(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
