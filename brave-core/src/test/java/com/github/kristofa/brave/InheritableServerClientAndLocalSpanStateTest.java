package com.github.kristofa.brave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.twitter.zipkin.gen.Endpoint;
import com.twitter.zipkin.gen.Span;

public class InheritableServerClientAndLocalSpanStateTest {

    private static final short PORT = 80;
    private static final String SERVICE_NAME = InheritableServerClientAndLocalSpanStateTest.class.getSimpleName();
    private static final int MAX_EXPECTED_DEPTH = 1;

    private InheritableServerClientAndLocalSpanState state;
    private ServerSpan mockServerSpan;
    private Span mockSpan;

    @Before
    public void setup() {
        Endpoint endpoint = Endpoint.builder()
            .serviceName(SERVICE_NAME)
            .ipv4(192 << 24 | 168 << 16 | 1)
            .port(PORT).build();
        state = new InheritableServerClientAndLocalSpanState(endpoint);
        mockServerSpan = mock(ServerSpan.class);
        mockSpan = mock(Span.class);
    }

    @After
    public void tearDown() {
        state.setCurrentClientSpan(null);
        state.setCurrentServerSpan(null);
        Span localSpan;
        int depth = 0;
        while ((localSpan = state.getCurrentLocalSpan()) != null) {
            depth++;
            state.setCurrentLocalSpan(null);
            assertThat(state.getCurrentLocalSpan()).isNotEqualTo(localSpan);
            assertThat(depth).isLessThanOrEqualTo(MAX_EXPECTED_DEPTH).as("Depth should not exceed %d, was %d", depth);
        }
        assertThat(state.getCurrentLocalSpan()).isNull();
    }

    @Test
    public void testGetAndSetCurrentServerSpan() {
        assertEquals(ServerSpan.create(null), state.getCurrentServerSpan());
        state.setCurrentServerSpan(mockServerSpan);
        assertSame(mockServerSpan, state.getCurrentServerSpan());
        assertNull("Should not have been modified.", state.getCurrentClientSpan());
    }

    @Test
    public void testGetAndSetCurrentClientSpan() {
        assertNull(state.getCurrentClientSpan());
        state.setCurrentClientSpan(mockSpan);
        assertSame(mockSpan, state.getCurrentClientSpan());
        assertEquals("Should not have been modified.", ServerSpan.create(null),
                state.getCurrentServerSpan());
    }

    @Test
    public void testGetAndSetCurrentLocalSpan() {
        assertNull(state.getCurrentClientSpan());
        assertNull(state.getCurrentLocalSpan());
        state.setCurrentLocalSpan(mockSpan);
        assertSame(mockSpan, state.getCurrentLocalSpan());
        assertEquals("Should not have been modified.", ServerSpan.create(null),
                state.getCurrentServerSpan());
        assertNull(state.getCurrentClientSpan());
    }

    @Test
    public void testGetAndSetCurrentLocalSpanInheritence() {
        assertNull(state.getCurrentClientSpan());
        assertNull(state.getCurrentLocalSpan());
        state.setCurrentLocalSpan(mockSpan);
        assertSame(mockSpan, state.getCurrentLocalSpan());
        assertEquals("Should not have been modified.", ServerSpan.create(null),
                state.getCurrentServerSpan());
        assertNull(state.getCurrentClientSpan());
    }

    @Test
    public void testGetParentSpan_localSpan_exists() throws Exception {
        Span currentServerSpan = mock(Span.class);
        when(mockServerSpan.getSpan()).thenReturn(currentServerSpan);

        Span x = currentParentSpan(state);
        assertThat(x).isNull();

        state.setCurrentServerSpan(mockServerSpan);
        assertThat(currentParentSpan(state)).isSameAs(state.getCurrentServerSpan().getSpan());
        assertThat(currentParentSpan(state)).isSameAs(mockServerSpan.getSpan());
        assertThat(currentParentSpan(state)).isNotEqualTo(mockSpan);

        state.setCurrentLocalSpan(mockSpan);
        assertThat(currentParentSpan(state)).isSameAs(state.getCurrentLocalSpan());
        assertThat(currentParentSpan(state)).isSameAs(mockSpan);
        assertThat(currentParentSpan(state)).isNotEqualTo(currentServerSpan);

        state.setCurrentLocalSpan(null);
        assertThat(currentParentSpan(state)).isSameAs(state.getCurrentServerSpan().getSpan());
        assertThat(currentParentSpan(state)).isSameAs(mockServerSpan.getSpan());
        assertThat(currentParentSpan(state)).isNotEqualTo(mockSpan);

        state.setCurrentServerSpan(null);
        assertThat(currentParentSpan(state)).isNull();
    }

    @Test
    public void testToString() throws Exception {
        assertThat(state.toString()).startsWith("InheritableServerClientAndLocalSpanState");
    }

    private static Span currentParentSpan(ServerClientAndLocalSpanState state) {
        Span parentSpan = state.getCurrentLocalSpan();
        return (parentSpan == null) ? state.getCurrentServerSpan().getSpan() : parentSpan;
    }
}
