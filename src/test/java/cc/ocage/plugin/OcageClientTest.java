package cc.ocage.plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.cert.CertificateException;
import javax.net.ssl.SSLHandshakeException;
import org.junit.Test;

/** What the panel says when no answer came back, by cause (OcageClient.unreachable). */
public class OcageClientTest
{
	private static final String HOST = "api.ocage.cc";

	@Test
	public void eachCauseGetsItsOwnMessage()
	{
		assertEquals("Couldn't find api.ocage.cc. Check your internet connection.",
			OcageClient.unreachable(new UnknownHostException(HOST), HOST));
		assertTrue(OcageClient.unreachable(new SSLHandshakeException("PKIX path building failed"), HOST)
			.startsWith("Couldn't verify the Ocage server's certificate"));
		assertTrue(OcageClient.unreachable(new ConnectException("Connection refused"), HOST)
			.startsWith("Couldn't connect to the Ocage server"));
		assertTrue(OcageClient.unreachable(new SocketTimeoutException("timeout"), HOST)
			.startsWith("The Ocage server didn't answer in time"));
	}

	@Test
	public void aWrappedCauseIsFound()
	{
		IOException wrapped = new IOException("unexpected end of stream", new CertificateException("expired"));
		assertTrue(OcageClient.unreachable(wrapped, HOST).startsWith("Couldn't verify the Ocage server's certificate"));
	}

	@Test
	public void anythingElseNamesTheException()
	{
		assertEquals("Couldn't reach the Ocage server (IOException).",
			OcageClient.unreachable(new IOException("stream was reset"), HOST));
	}

	@Test
	public void theLogLineShowsEveryCause()
	{
		IOException e = new IOException("outer", new SSLHandshakeException("PKIX path building failed"));
		assertEquals("IOException: outer <- SSLHandshakeException: PKIX path building failed", OcageClient.chain(e));
	}
}
