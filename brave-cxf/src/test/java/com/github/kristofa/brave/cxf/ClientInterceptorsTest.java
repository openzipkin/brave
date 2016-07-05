package com.github.kristofa.brave.cxf;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.http.DefaultSpanNameProvider;
import org.apache.cxf.message.Exchange;
import org.apache.cxf.message.Message;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * @author Michał Podsiedzik
 */
public class ClientInterceptorsTest {
    private SpanCollectorForTesting collector;
    private Brave brave;
    private DefaultSpanNameProvider provider;

    @Mock
    private Message message;
    @Mock
    private Exchange exchange;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        this.collector = new SpanCollectorForTesting();
        this.provider = new DefaultSpanNameProvider();

        this.brave = new Brave.Builder().spanCollector(collector).build();
    }

    @Test
    public void test() {

        BraveClientInInterceptor inInterceptor = new BraveClientInInterceptor(brave.clientResponseInterceptor());
        BraveClientOutInterceptor outInterceptor = new BraveClientOutInterceptor(provider, brave.clientRequestInterceptor());

        when(exchange.get(Message.ENDPOINT_ADDRESS)).thenReturn("http://localhost:8000");
        when(message.getExchange()).thenReturn(exchange);
        when(message.get(Message.HTTP_REQUEST_METHOD)).thenReturn("post");
        when(message.get(Message.RESPONSE_CODE)).thenReturn(200);
        when(message.get(Message.PROTOCOL_HEADERS)).thenReturn(new TreeMap<String, List<String>>());

        // client sends
        outInterceptor.handleMessage(message);
        // client receives
        inInterceptor.handleMessage(message);

        assertThat(collector.getCollectedSpans()).hasSize(1);
    }
}
