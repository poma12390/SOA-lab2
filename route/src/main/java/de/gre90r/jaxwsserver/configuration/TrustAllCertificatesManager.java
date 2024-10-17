package de.gre90r.jaxwsserver.configuration;

import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;

public class TrustAllCertificatesManager implements X509TrustManager {

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) {
        // Не выполняем проверку
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) {
        // Не выполняем проверку
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return new X509Certificate[0];
    }
}
