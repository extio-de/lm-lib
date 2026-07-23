package de.extio.lmlib.client;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.springframework.web.client.RestClient;

public final class ProxyAuthorizationSupport {

	public static final String MODE_HTTP_CLIENT = "http-client";

	public static final String MODE_HEADER = "header";

	public static final String PROXY_AUTH_HEADER = "Proxy-Authorization";

	private final boolean headerMode;

	private final String proxyUser;

	private final String proxyPassword;

	public ProxyAuthorizationSupport(final String authMode, final String proxyUser, final String proxyPassword) {
		this.headerMode = MODE_HEADER.equalsIgnoreCase(authMode);
		this.proxyUser = proxyUser;
		this.proxyPassword = proxyPassword;
	}

	public boolean isHeaderMode() {
		return this.headerMode;
	}

	public boolean isHttpClientMode() {
		return !this.headerMode;
	}

	@SuppressWarnings("unchecked")
	public <S extends RestClient.RequestHeadersSpec<S>> S apply(final S requestSpec) {
		if (!this.headerMode || this.proxyUser == null || this.proxyUser.isBlank()) {
			return requestSpec;
		}
		return (S) requestSpec.header(PROXY_AUTH_HEADER, "Basic " + encodeCredentials(this.proxyUser, this.proxyPassword));
	}

	static String encodeCredentials(final String user, final String password) {
		return Base64.getEncoder().encodeToString((user + ":" + (password != null ? password : "")).getBytes(StandardCharsets.UTF_8));
	}

}
