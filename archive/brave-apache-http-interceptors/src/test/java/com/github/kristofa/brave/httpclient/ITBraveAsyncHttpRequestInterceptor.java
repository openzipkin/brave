package com.github.kristofa.brave.httpclient;

import com.github.kristofa.brave.http.ITHttpClient;
import com.github.kristofa.brave.http.SpanNameProvider;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

public class ITBraveAsyncHttpRequestInterceptor extends ITHttpClient<CloseableHttpAsyncClient> {

    @Override
    protected CloseableHttpAsyncClient newClient(int port) {
        return configureClient(BraveHttpRequestInterceptor.create(brave));
    }

    @Override
    protected CloseableHttpAsyncClient newClient(int port, SpanNameProvider spanNameProvider) {
        return configureClient(BraveHttpRequestInterceptor.builder(brave)
                .spanNameProvider(spanNameProvider).build());
    }

    private CloseableHttpAsyncClient configureClient(BraveHttpRequestInterceptor requestInterceptor) {
        CloseableHttpAsyncClient client = HttpAsyncClients.custom()
                .addInterceptorFirst(requestInterceptor)
                .addInterceptorFirst(BraveHttpResponseInterceptor.create(brave))
                .build();
        client.start();
        return client;
    }

    @Override
    protected void closeClient(CloseableHttpAsyncClient client) throws IOException {
        client.close();
    }

    @Override
    protected void get(CloseableHttpAsyncClient client, String pathIncludingQuery)
            throws IOException {
        getAsync(client, pathIncludingQuery);
    }

    @Override
    protected void getAsync(CloseableHttpAsyncClient client, String pathIncludingQuery) {
        try {
            client.execute(new HttpGet(server.url(pathIncludingQuery).uri()), null).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    @Test(expected = AssertionError.class) // doesn't yet close a span on exception
    public void addsErrorTagOnTransportException() throws Exception {
        super.addsErrorTagOnTransportException();
    }

    @Override
    @Test(expected = AssertionError.class) // base url is not logged in apache
    public void httpUrlTagIncludesQueryParams() throws Exception {
        super.httpUrlTagIncludesQueryParams();
    }

    @Override
    @Test(expected = AssertionError.class) // doesn't yet close a span on exception
    public void reportsSpanOnTransportException() throws Exception {
        super.reportsSpanOnTransportException();
    }
}
