package cc.ocage.plugin;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;
import java.security.cert.CertificateException;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SSLException;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * The bot's /plugin/* routes. Every call is asynchronous (OkHttp's own
 * threads); callbacks run there too, never on the client thread, so
 * callers hop to the client thread or Swing thread themselves.
 */
@Slf4j
final class OcageClient
{
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
	private static final MediaType JPEG = MediaType.parse("image/jpeg");

	interface Result<T>
	{
		void ok(T value);

		void fail(Failure failure);
	}

	/** Why a call failed. */
	static final class Failure
	{
		/** The bot's error code for a banned player (see the bot's BingoError.code). */
		static final String BANNED = "plugin_banned";

		/** HTTP status, or 0 when the server couldn't be reached. */
		final int status;
		/** The bot's machine-readable error code, if it sent one. */
		final String code;
		/** What to show the player. */
		final String message;

		Failure(int status, String code, String message)
		{
			this.status = status;
			this.code = code;
			this.message = message;
		}

		boolean banned()
		{
			return status == 403 && BANNED.equals(code);
		}

		/** Worth retrying later: the server was unreachable, busy or broken. */
		boolean retryable()
		{
			return status == 0 || status == 429 || status >= 500;
		}
	}

	private final OkHttpClient http;
	private final Gson gson;
	private final String baseUrl;
	private final String userAgent;
	/**
	 * The last connection problem written to the log, so a player who's
	 * offline gets one warning per kind of problem, not one per request.
	 * Cleared once a request gets through.
	 */
	private final AtomicReference<String> loggedProblem = new AtomicReference<>();

	OcageClient(OkHttpClient http, Gson gson, String baseUrl, String version)
	{
		this.http = http;
		this.gson = gson;
		this.baseUrl = baseUrl;
		this.userAgent = "Ocage-RuneLite-Plugin/" + version;
	}

	String baseUrl()
	{
		return baseUrl;
	}

	void link(Api.LinkRequest body, Result<Api.LinkResponse> result)
	{
		send(request("/plugin/link", null, null).post(json(body)).build(), Api.LinkResponse.class, result);
	}

	void rules(String key, String accountHash, Result<Api.Rules> result)
	{
		send(request("/plugin/config", key, accountHash).get().build(), Api.Rules.class, result);
	}

	void report(String key, String accountHash, Api.ReportBatch batch, Result<Api.IngestResponse> result)
	{
		send(request("/plugin/events", key, accountHash).post(json(batch)).build(), Api.IngestResponse.class, result);
	}

	void me(String key, String accountHash, Result<Api.Me> result)
	{
		send(request("/plugin/me", key, accountHash).get().build(), Api.Me.class, result);
	}

	void screenshot(String key, String accountHash, String reportId, byte[] jpeg, Result<Api.ScreenshotResponse> result)
	{
		RequestBody body = RequestBody.create(JPEG, jpeg);
		send(request("/plugin/screenshots/" + reportId, key, accountHash).post(body).build(), Api.ScreenshotResponse.class, result);
	}

	void unlink(String key, String accountHash, Result<Void> result)
	{
		send(request("/plugin/token", key, accountHash).delete().build(), Void.class, result);
	}

	private Request.Builder request(String path, String key, String accountHash)
	{
		HttpUrl url = HttpUrl.parse(baseUrl + path);
		Request.Builder builder = new Request.Builder().url(url).header("User-Agent", userAgent);
		if (key != null)
		{
			builder.header("Authorization", "Bearer " + key).header("X-Account-Hash", accountHash);
		}
		return builder;
	}

	private RequestBody json(Object body)
	{
		return RequestBody.create(JSON, gson.toJson(body));
	}

	private <T> void send(Request request, Class<T> type, Result<T> result)
	{
		http.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				String problem = unreachable(e, request.url().host());
				// A warning, so it's in the player's client.log when they report a problem.
				if (!problem.equals(loggedProblem.getAndSet(problem)))
				{
					log.warn("Ocage API unreachable ({}): {} [{}]", request.url(), problem, chain(e));
				}
				result.fail(new Failure(0, null, problem));
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				loggedProblem.set(null);
				try (ResponseBody body = response.body())
				{
					String text = body == null ? "" : body.string();
					if (!response.isSuccessful())
					{
						result.fail(failure(text, response.code()));
						return;
					}
					result.ok(type == Void.class ? null : gson.fromJson(text, type));
				}
				catch (IOException | JsonParseException e)
				{
					log.warn("Bad response from Ocage API {}", request.url(), e);
					result.fail(new Failure(response.code(), null, "The Ocage server sent something unexpected."));
				}
			}
		});
	}

	/**
	 * What to tell the player when no answer came back at all, by cause:
	 * each one points somewhere different (their connection, a firewall or
	 * antivirus, the server). Looks through the exception's causes, since
	 * OkHttp often wraps the real one.
	 */
	static String unreachable(IOException e, String host)
	{
		for (Throwable t = e; t != null; t = t.getCause() == t ? null : t.getCause())
		{
			if (t instanceof UnknownHostException)
			{
				return "Couldn't find " + host + ". Check your internet connection.";
			}
			if (t instanceof SSLException || t instanceof CertificateException)
			{
				return "Couldn't verify the Ocage server's certificate. Antivirus or network filtering can cause this.";
			}
			if (t instanceof ConnectException || t instanceof NoRouteToHostException)
			{
				return "Couldn't connect to the Ocage server. It may be down, or a firewall is blocking RuneLite.";
			}
			if (t instanceof InterruptedIOException)
			{
				// SocketTimeoutException, and OkHttp's own call timeout.
				return "The Ocage server didn't answer in time. Try again in a minute.";
			}
		}
		return "Couldn't reach the Ocage server (" + e.getClass().getSimpleName() + ").";
	}

	/** "SSLHandshakeException: PKIX path building failed <- CertPathBuilderException: ..." for the log. */
	static String chain(Throwable e)
	{
		StringBuilder text = new StringBuilder();
		for (Throwable t = e; t != null; t = t.getCause() == t ? null : t.getCause())
		{
			if (text.length() > 0)
			{
				text.append(" <- ");
			}
			text.append(t.getClass().getSimpleName());
			if (t.getMessage() != null)
			{
				text.append(": ").append(t.getMessage());
			}
		}
		return text.toString();
	}

	private Failure failure(String text, int status)
	{
		try
		{
			Api.Error error = gson.fromJson(text, Api.Error.class);
			if (error != null && error.detail != null)
			{
				return new Failure(status, error.code, error.detail);
			}
		}
		catch (JsonParseException ignored)
		{
			// FastAPI validation errors (422) have a list, not a string, in "detail".
		}
		return new Failure(status, null, "The Ocage server said no (HTTP " + status + ").");
	}
}
