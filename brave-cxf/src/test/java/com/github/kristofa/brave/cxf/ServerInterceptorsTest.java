package com.github.kristofa.brave.cxf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.TreeMap;

import org.apache.cxf.message.ExchangeImpl;
import org.apache.cxf.message.Message;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.http.DefaultSpanNameProvider;

/**
 * @author Michał Podsiedzik
 */
public class ServerInterceptorsTest {
    private Brave brave;
    private SpanCollectorForTesting collector;
    private DefaultSpanNameProvider provider;

    @Mock
    private Message message;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        this.collector = new SpanCollectorForTesting();
        this.provider = new DefaultSpanNameProvider();

        this.brave = new Brave.Builder().spanCollector(collector).build();
    }

    @Test
    public void test() {

        BraveServerInInterceptor inInterceptor = new BraveServerInInterceptor(brave, provider);
        BraveServerOutInterceptor outInterceptor = new BraveServerOutInterceptor(brave);

        ExchangeImpl exchange = new ExchangeImpl();

        when(message.get(Message.REQUEST_URL)).thenReturn("http://localhost:8000");
        when(message.get(Message.HTTP_REQUEST_METHOD)).thenReturn("post");
        when(message.get(Message.RESPONSE_CODE)).thenReturn(200);
        when(message.get(Message.PROTOCOL_HEADERS)).thenReturn(new TreeMap<String, List<String>>());
        when(message.getExchange()).thenReturn(exchange);

        // server receives
        inInterceptor.handleMessage(message);
        // server responds
        outInterceptor.handleMessage(message);

        assertThat(collector.getCollectedSpans()).hasSize(1);
    }
}