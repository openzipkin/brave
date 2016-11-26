package com.github.kristofa.brave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.twitter.zipkin.gen.Annotation;
import com.twitter.zipkin.gen.BinaryAnnotation;
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
        assertEquals(ServerSpan.EMPTY, state.getCurrentServerSpan());
        state.setCurrentServerSpan(mockServerSpan);
        assertSame(mockServerSpan, state.getCurrentServerSpan());
        assertNull("Should not have been modified.", state.getCurrentClientSpan());
    }

    @Test
    public void testGetAndSetCurrentClientSpan() {
        assertNull(state.getCurrentClientSpan());
        state.setCurrentClientSpan(mockSpan);
        assertSame(mockSpan, state.getCurrentClientSpan());
        assertEquals("Should not have been modified.", ServerSpan.EMPTY,
                state.getCurrentServerSpan());
    }

    @Test
    public void testGetAndSetCurrentLocalSpan() {
        assertNull(state.getCurrentClientSpan());
        assertNull(state.getCurrentLocalSpan());
        state.setCurrentLocalSpan(mockSpan);
        assertSame(mockSpan, state.getCurrentLocalSpan());
        assertEquals("Should not have been modified.", ServerSpan.EMPTY, state.getCurrentServerSpan());
        assertNull(state.getCurrentClientSpan());
    }

    @Test
    public void testGetAndSetCurrentLocalSpanInheritence() {
        assertNull(state.getCurrentClientSpan());
        assertNull(state.getCurrentLocalSpan());
        state.setCurrentLocalSpan(mockSpan);
        assertSame(mockSpan, state.getCurrentLocalSpan());
        assertEquals("Should not have been modified.", ServerSpan.EMPTY,
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

    @Test
    public void parentSpanShouldNotLeakAcrossThreads() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicBoolean passed = new AtomicBoolean(false);
        ThreadFactory threadFactory = new ThreadFactoryBuilder().setNameFormat("brave-%d").build();

        final ServerSpan parent = ServerSpan.create(1L, 2L, 3L, "testing");

        state.setCurrentServerSpan(parent);
        threadFactory.newThread(() -> {
            ServerSpan current = state.getCurrentServerSpan();
            passed.set(current.equals(parent) && current != parent
                    && current.getSpan() != parent.getSpan());
            latch.countDown();

        }).start();

        latch.await(5, TimeUnit.SECONDS);
        assertTrue(passed.get());

    }

    @Test
    public void localSpanShouldNotLeakAcrossThreads() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicBoolean passed = new AtomicBoolean(false);
        ThreadFactory threadFactory = new ThreadFactoryBuilder().setNameFormat("brave-%d").build();

        com.twitter.zipkin.gen.Span parent = new com.twitter.zipkin.gen.Span();
        parent.setId(1L);
        parent.setName("testing");
        parent.setTrace_id(2L);
        parent.setParent_id(3L);
        parent.setTimestamp(1000L);
        parent.setDebug(true);
        parent.setDuration(100000L);
        parent.addToAnnotations(Annotation.create(100L, "", Endpoint.create("service", 12345)));
        parent.addToBinary_annotations(BinaryAnnotation.create("key", "value", Endpoint.create("other", 12345)));
        state.setCurrentLocalSpan(parent);
        threadFactory.newThread(() -> {
            Span current = state.getCurrentLocalSpan();
            passed.set(current.equals(parent) && current != parent);
            latch.countDown();

        }).start();

        latch.await(5, TimeUnit.SECONDS);
        assertTrue(passed.get());

    }
    
    @Test
    public void clientSpanShouldNotLeakAcrossThreads() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicBoolean passed = new AtomicBoolean(false);
        ThreadFactory threadFactory = new ThreadFactoryBuilder().setNameFormat("brave-%d").build();

        com.twitter.zipkin.gen.Span parent = new com.twitter.zipkin.gen.Span();
        parent.setId(1L);
        parent.setName("testing");
        parent.setTrace_id(2L);
        parent.setParent_id(3L);
        parent.setTimestamp(1000L);
        parent.setDebug(true);
        parent.setDuration(100000L);
        parent.addToAnnotations(Annotation.create(100L, "", Endpoint.create("service", 12345)));
        parent.addToBinary_annotations(BinaryAnnotation.create("key", "value", Endpoint.create("other", 12345)));
        state.setCurrentClientSpan(parent);
        threadFactory.newThread(() -> {
            Span current = state.getCurrentClientSpan();
            passed.set(current.equals(parent) && current != parent);
            latch.countDown();

        }).start();

        latch.await(5, TimeUnit.SECONDS);
        assertTrue(passed.get());

    }
        
    private static Span currentParentSpan(ServerClientAndLocalSpanState state) {
        Span parentSpan = state.getCurrentLocalSpan();
        return (parentSpan == null) ? state.getCurrentServerSpan().getSpan() : parentSpan;
    }
}
