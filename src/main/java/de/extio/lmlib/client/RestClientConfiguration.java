package de.extio.lmlib.client;

import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.BackOffExecution;
import org.springframework.util.backoff.ExponentialBackOff;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfiguration {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(RestClientConfiguration.class);

	private static final int TIMEOUT = 300000;
	
	private static final int CONNECT_TIMEOUT = 10000;
	
	@Bean
	@Qualifier("lmLibRestClientBuilder")
	RestClient.Builder lmLibRestClientBuilder(
			@Value("${lmlib.client.proxy.enabled:false}") final boolean proxyEnabled,
			@Value("${lmlib.client.proxy.host:}") final String proxyHost,
			@Value("${lmlib.client.proxy.port:0}") final int proxyPort,
			@Value("${lmlib.client.proxy.user:}") final String proxyUser,
			@Value("${lmlib.client.proxy.password:}") final String proxyPassword,
			@Value("${lmlib.client.tls.verification.disabled:false}") final boolean tlsVerificationDisabled,
			@Value("${lmlib.client.retry.max-attempts:5}") final int maxAttempts,
			@Value("${lmlib.client.retry.backoff-interval-min:125}") final long backoffIntervalMin,
			@Value("${lmlib.client.retry.backoff-interval-max:2500}") final long backoffIntervalMax) {
		
		final HttpClient.Builder httpClientBuilder = HttpClient.newBuilder()
				.connectTimeout(Duration.ofMillis(CONNECT_TIMEOUT));
		
		if (proxyEnabled && proxyHost != null && !proxyHost.isBlank()) {
			httpClientBuilder.proxy(ProxySelector.of(new InetSocketAddress(proxyHost, proxyPort)));
			
			if (proxyUser != null && !proxyUser.isBlank()) {
				httpClientBuilder.authenticator(new Authenticator() {
					
					@Override
					protected PasswordAuthentication getPasswordAuthentication() {
						return new PasswordAuthentication(proxyUser, proxyPassword.toCharArray());
					}
				});
			}
		}
		
		if (tlsVerificationDisabled) {
			try {
				final SSLContext sslContext = SSLContext.getInstance("TLS");
				sslContext.init(null, new TrustManager[] { new X509TrustManager() {
					
					@Override
					public void checkClientTrusted(final X509Certificate[] chain, final String authType) {
					}
					
					@Override
					public void checkServerTrusted(final X509Certificate[] chain, final String authType) {
					}
					
					@Override
					public X509Certificate[] getAcceptedIssuers() {
						return new X509Certificate[0];
					}
				} }, new SecureRandom());
				httpClientBuilder.sslContext(sslContext);
			}
			catch (NoSuchAlgorithmException | KeyManagementException e) {
				throw new RuntimeException("Failed to disable TLS verification", e);
			}
		}
		
		final JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClientBuilder.build());
		requestFactory.setReadTimeout(Duration.ofMillis(TIMEOUT));
		
		return RestClient.builder()
				.requestFactory(requestFactory)
				.defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
				.requestInterceptor(createRetryInterceptor(maxAttempts, backoffIntervalMin, backoffIntervalMax));
	}
	
	private ClientHttpRequestInterceptor createRetryInterceptor(final int maxAttempts, final long backoffIntervalMin, final long backoffIntervalMax) {
		return (request, body, execution) -> {
			final BackOff backOff = new ExponentialBackOff();
			((ExponentialBackOff) backOff).setInitialInterval(backoffIntervalMin);
			((ExponentialBackOff) backOff).setMaxInterval(backoffIntervalMax);
			((ExponentialBackOff) backOff).setMaxElapsedTime(maxAttempts * backoffIntervalMax);
			((ExponentialBackOff) backOff).setMultiplier(2.0);
			((ExponentialBackOff) backOff).setJitter(backoffIntervalMin / 2);
			final BackOffExecution backOffExecution = backOff.start();
			
			while (true) {
				try {
					return execution.execute(request, body);
				}
				catch (final java.io.IOException e) {
					final long waitTime = backOffExecution.nextBackOff();
					if (waitTime == BackOffExecution.STOP) {
						throw e;
					}
					LOGGER.warn("Request failed, retrying in {} ms: {} {}", waitTime, e.getClass().getSimpleName(), e.getMessage() != null ? e.getMessage() : "");
					try {
						Thread.sleep(waitTime);
					}
					catch (final InterruptedException ie) {
						Thread.currentThread().interrupt();
						throw e;
					}
				}
			}
		};
	}
	
}
