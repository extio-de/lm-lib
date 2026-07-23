package de.extio.lmlib.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ProxyAuthorizationSupportTest {

	@Test
	void httpClientModeIsDefault() {
		final var support = new ProxyAuthorizationSupport(ProxyAuthorizationSupport.MODE_HTTP_CLIENT, "login", "password");
		assertTrue(support.isHttpClientMode());
		assertFalse(support.isHeaderMode());
	}

	@Test
	void headerModeIsEnabled() {
		final var support = new ProxyAuthorizationSupport(ProxyAuthorizationSupport.MODE_HEADER, "login", "password");
		assertTrue(support.isHeaderMode());
		assertFalse(support.isHttpClientMode());
	}

	@Test
	void encodesCredentials() {
		assertEquals("bG9naW46cGFzc3dvcmQ=", ProxyAuthorizationSupport.encodeCredentials("login", "password"));
	}

}
