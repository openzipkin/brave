package com.github.kristofa.brave.spark;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.LoggingReporter;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import spark.Spark;
import zipkin.reporter.Reporter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import static org.junit.Assert.assertEquals;

/**
 * Created by 00013708 on 2017/3/13.
 */
public class ITBraveSparkExample {


    @Before
    public void setup() {
        Reporter reporter = new LoggingReporter();
        Brave brave = new Brave.Builder("brave-spark-test").reporter(reporter).build();

        Spark.port(8888);
        Spark.threadPool(1000, 1000,
                60000);

        Spark.before(BraveSparkRequestFilter.create(brave));

        Spark.get("/ping", (req, res) -> "pong");

        Spark.after(BraveSparkResponseFilter.create(brave));


    }

    @After
    public void tearDown() throws Exception {
        Thread.sleep(1000);
        Spark.stop();
    }

    @Test
    public void test() throws IOException {
        //这个地方要发一个请求，怎么搞？
        CloseableHttpClient httpClient = HttpClients.custom()
                .build();

        HttpGet httpGet = new HttpGet("http://localhost:8888/ping");
        CloseableHttpResponse response = httpClient.execute(httpGet);

        int statusCode = response.getStatusLine().getStatusCode();
        BufferedReader rd = new BufferedReader(
                new InputStreamReader(response.getEntity().getContent()));

        StringBuffer result = new StringBuffer();
        String line = "";
        while ((line = rd.readLine()) != null) {
            result.append(line);
        }

        assertEquals(200, statusCode);
        assertEquals("pong", result.toString());

    }
}
