package brave.http;

import brave.SpanCustomizer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class HttpClientParserTest {

    @Mock
    HttpClientAdapter<Object, Object> adapter;

    @Mock
    SpanCustomizer customizer;

    private Object request = new Object();

    private HttpParser parser = new HttpClientParser();

    @Test public void request_addUrl() {
        when(adapter.url(request)).thenReturn("http://foo:bar/tea");

        parser.request(adapter, request, customizer);

        verify(customizer).tag("http.url", "http://foo:bar/tea");
    }

    @Test
    public void request_doesntCrashOnNullUrl() {
        parser.request(adapter, request, customizer);

        verify(customizer, never()).tag("http.url", null);
    }
}
