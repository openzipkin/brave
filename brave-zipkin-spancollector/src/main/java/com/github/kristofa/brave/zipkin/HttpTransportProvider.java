package com.github.kristofa.brave.zipkin;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.http.HttpVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.thrift.transport.THttpClient;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * HTTP implementation. Relies on the {@link org.apache.thrift.transport.THttpClient thrift http transport} internally.
 *
 * @author botizac
 */
public class HttpTransportProvider implements ZipkinClientTransportProvider {

    /**
     * Zipkin server http(s) URL. Could point to a specific server or a load balancer.
     */
    private String url;

    /**
     * Http client socket connect timeout, in milliseconds.
     */
    private int connectionTimeout;

    /**
     * Http client socket read timeout, in milliseconds.
     */
    private int readTimeout;

    /**
     * Http client connection pool maximum size.
     */
    private int poolSize;

    /**
     * Is the Zipkin URL using https?
     */
    private boolean secure;

    /**
     * Constructor with default values.
     *
     * @param url Zipkin server http url, including the port.
     */
    public HttpTransportProvider(String url) {
        // timeouts both defaulted to 5 seconds.
        this(url, 5000, 5000, 100);
    }

    /**
     * Constructor with default pool size of 100.
     *
     * @param url               Zipkin server http url, including the port.
     * @param connectionTimeout Socket connect timeout, in milliseconds.
     * @param readTimeout       Socket read timeout, in milliseconds.
     */
    public HttpTransportProvider(String url, int connectionTimeout, int readTimeout) {
        // pool size defaulted to 100.
        this(url, connectionTimeout, readTimeout, 100);
    }

    /**
     * Most comprehensive constructor.
     *
     * @param url               Zipkin server http url, including the port
     * @param connectionTimeout Socket connect timeout in milliseconds.
     * @param readTimeout       Socket read timeout, in milliseconds
     * @param poolSize          Connection pool maximum size.
     */
    public HttpTransportProvider(String url, int connectionTimeout, int readTimeout, int poolSize) {
        Validate.isTrue(StringUtils.isNotBlank(url), "No url provided");

        try {
            final URL zipkinUrl = new URL(url);

            secure = "https".equals(zipkinUrl.getProtocol());
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Invalid zipkin server URL");
        }

        Validate.isTrue(connectionTimeout > 0, "Connection timeout must be > 0");
        Validate.isTrue(readTimeout > 0, "Read timeout must be > 0");
        Validate.isTrue(poolSize > 0, "Pool size must be > 0");

        this.url = url;
        this.connectionTimeout = connectionTimeout;
        this.readTimeout = readTimeout;
        this.poolSize = poolSize;
    }

    @Override
    public TTransport getTransport() {
        THttpClient client;
        try {
            client = new THttpClient(url, createClient());

            client.setConnectTimeout(connectionTimeout);
            client.setReadTimeout(readTimeout);
        } catch (TTransportException te) {
            throw new IllegalStateException("Could not create HTTP Transport instance", te);
        }

        return client;
    }

    /**
     * Initialize the underlying http client.
     *
     * @return The HTTP Client.
     */
    private HttpClient createClient() {
        ThreadSafeClientConnManager connManager = new ThreadSafeClientConnManager();

        connManager.setMaxTotal(poolSize);
        connManager.setDefaultMaxPerRoute(poolSize);

        final DefaultHttpClient client = new DefaultHttpClient(connManager);

        // TODO: if using SSL (secure == true), configure key and truststore


        // set custom properties as per Thrift recommendations
        client.getParams().setParameter("http.protocol.version", HttpVersion.HTTP_1_1);
        client.getParams().setParameter("http.protocol.content-charset", "UTF-8");
        client.getParams().setBooleanParameter("http.protocol.expect-continue", false);
        client.getParams().setBooleanParameter("http.connection.stalecheck", false);

        return client;
    }
}
