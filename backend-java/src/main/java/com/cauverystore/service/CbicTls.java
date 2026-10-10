package com.cauverystore.service;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;

/**
 * An HTTP client that can complete a TLS handshake with CBIC's tax information portal.
 *
 * <h2>The fault this works around</h2>
 *
 * taxinformation.cbic.gov.in sends only its own certificate. It leaves out the intermediate that
 * issued it - "Sectigo Public Server Authentication CA OV R36" - which a server is supposed to
 * send. Browsers and curl paper over that by fetching or remembering the missing certificate.
 * Java does not, so every request from here failed with "PKIX path building failed", and the
 * daily rate check was blind while reporting only a truststore warning.
 *
 * <h2>Why this is not a relaxation of anything</h2>
 *
 * The missing intermediate is committed under resources/certs and handed to the ordinary
 * validator alongside whatever CBIC sent. The chain is still verified, link by link, up to the
 * Sectigo root the JDK already trusts, and the host name is still checked. Nothing is trusted
 * here that was not trusted before; the validator is simply given the certificate CBIC forgot.
 * If CBIC moves to a different issuer the handshake fails again, loudly, which is correct.
 *
 * Used for CBIC only. Every other outbound call keeps the JVM's default behaviour.
 */
final class CbicTls {

    private static final String INTERMEDIATE = "/certs/sectigo-public-server-authentication-ca-ov-r36.pem";

    private CbicTls() {}

    /** A RestTemplate for CBIC. Falls back to the default one if the helper cannot be built. */
    static RestTemplate restTemplate() {
        try {
            X509Certificate intermediate;
            try (InputStream in = CbicTls.class.getResourceAsStream(INTERMEDIATE)) {
                if (in == null) return new RestTemplate();
                intermediate = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(in);
            }
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init((KeyStore) null);
            X509ExtendedTrustManager standard = null;
            for (TrustManager tm : tmf.getTrustManagers()) {
                if (tm instanceof X509ExtendedTrustManager x) { standard = x; break; }
            }
            if (standard == null) return new RestTemplate();

            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, new TrustManager[] {new CompletingTrustManager(standard, intermediate)}, null);

            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory() {
                @Override
                protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
                    if (connection instanceof HttpsURLConnection https) {
                        https.setSSLSocketFactory(context.getSocketFactory());
                    }
                    super.prepareConnection(connection, httpMethod);
                }
            };
            factory.setConnectTimeout(15_000);
            factory.setReadTimeout(60_000);
            return new RestTemplate(factory);
        } catch (Exception e) {
            return new RestTemplate();
        }
    }

    /** Validates as usual, after adding the intermediate to a chain that arrived without it. */
    private static final class CompletingTrustManager extends X509ExtendedTrustManager {
        private final X509ExtendedTrustManager standard;
        private final X509Certificate intermediate;

        CompletingTrustManager(X509ExtendedTrustManager standard, X509Certificate intermediate) {
            this.standard = standard;
            this.intermediate = intermediate;
        }

        private X509Certificate[] complete(X509Certificate[] chain) {
            if (chain == null || chain.length != 1) return chain;
            // Only when the lone certificate really was issued by this intermediate.
            if (!chain[0].getIssuerX500Principal().equals(intermediate.getSubjectX500Principal())) return chain;
            X509Certificate[] full = Arrays.copyOf(chain, 2);
            full[1] = intermediate;
            return full;
        }

        @Override public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
            standard.checkServerTrusted(complete(chain), authType, socket);
        }
        @Override public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
            standard.checkServerTrusted(complete(chain), authType, engine);
        }
        @Override public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            standard.checkServerTrusted(complete(chain), authType);
        }
        @Override public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
            standard.checkClientTrusted(chain, authType, socket);
        }
        @Override public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
            standard.checkClientTrusted(chain, authType, engine);
        }
        @Override public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            standard.checkClientTrusted(chain, authType);
        }
        @Override public X509Certificate[] getAcceptedIssuers() {
            return standard.getAcceptedIssuers();
        }
    }
}
