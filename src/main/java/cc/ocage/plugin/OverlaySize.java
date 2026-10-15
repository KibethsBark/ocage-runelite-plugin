package cc.ocage.plugin;

/** How big the bingo overlay is drawn: the whole box, text and all. */
public enum OverlaySize
{
	SIZE_75("75%", 0.75),
	SIZE_100("100%", 1.0),
	SIZE_125("125%", 1.25),
	SIZE_150("150%", 1.5),
	SIZE_200("200%", 2.0);

	private final String label;
	private final double scale;

	OverlaySize(String label, double scale)
	{
		this.label = label;
		this.scale = scale;
	}

	double scale()
	{
		return scale;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
