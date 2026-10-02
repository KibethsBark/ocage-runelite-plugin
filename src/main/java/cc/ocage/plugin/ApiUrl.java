package cc.ocage.plugin;

import java.io.InputStream;
import java.util.Properties;
import lombok.extern.slf4j.Slf4j;

/**
 * Which Ocage bot API this client talks to: the address baked in at build
 * time ({@code ./gradlew run -PocageApiUrl=...} or {@code shadowJar
 * -PocageApiUrl=...}, for the test server), else production,
 * {@link #PRODUCTION}. The Plugin Hub builds with its own build.gradle
 * ({@code build=standard}), which leaves the address unfilled, so every Hub
 * release uses production, and nothing on the player's PC (an
 * environment variable, a Java property) can point it anywhere else.
 * A key only works on the server that issued it, so each server keeps its own
 * key (see {@link OcagePlugin#keyName()}).
 */
@Slf4j
final class ApiUrl
{
	static final String PRODUCTION = "https://api.ocage.cc";

	private ApiUrl()
	{
	}

	static String resolve()
	{
		String url = buildDefault();
		if (url == null || url.trim().isEmpty())
		{
			url = PRODUCTION;
		}
		url = url.trim();
		while (url.endsWith("/"))
		{
			url = url.substring(0, url.length() - 1);
		}
		if (!url.startsWith("https://") && !url.startsWith("http://localhost") && !url.startsWith("http://127.0.0.1"))
		{
			// The key travels in every request: never send it in the clear.
			log.warn("Ignoring Ocage API URL {} — only https:// (or localhost) is allowed", url);
			url = PRODUCTION;
		}
		return url;
	}

	static boolean isProduction(String url)
	{
		return PRODUCTION.equals(url);
	}

	private static String buildDefault()
	{
		try (InputStream in = ApiUrl.class.getResourceAsStream("ocage-build.properties"))
		{
			if (in == null)
			{
				return null;
			}
			Properties properties = new Properties();
			properties.load(in);
			String value = properties.getProperty("apiUrl");
			// An unfiltered resource (e.g. run straight from an IDE) still says ${...}.
			return value == null || value.contains("${") ? null : value;
		}
		catch (Exception e)
		{
			log.debug("Couldn't read ocage-build.properties", e);
			return null;
		}
	}
}
