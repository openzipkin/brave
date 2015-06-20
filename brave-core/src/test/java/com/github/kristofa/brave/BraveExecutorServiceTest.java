package com.github.kristofa.brave;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.Before;
import org.junit.Test;

public class BraveExecutorServiceTest {

    private static final long TIMEOUT = 13;
    private static final TimeUnit TIME_UNIT = TimeUnit.SECONDS;

    private BraveExecutorService braveExecutorService;
    private ExecutorService wrappedExecutor;
    private ServerSpanThreadBinder mockThreadBinder;

    @Before
    public void setup() {
        wrappedExecutor = mock(ExecutorService.class);
        mockThreadBinder = mock(ServerSpanThreadBinder.class);
        braveExecutorService = new BraveExecutorService(wrappedExecutor, mockThreadBinder);
    }

    @Test
    public void testExecute() {
        final Runnable mockRunnable = mock(Runnable.class);
        braveExecutorService.execute(mockRunnable);
        final BraveRunnable expectedRunnable = BraveRunnable.create(mockRunnable, mockThreadBinder);
        verify(mockThreadBinder, times(2)).getCurrentServerSpan();
        verify(wrappedExecutor).execute(expectedRunnable);
        verifyNoMoreInteractions(wrappedExecutor, mockThreadBinder);
    }

    @Test
    public void testAwaitTermination() throws InterruptedException {

        braveExecutorService.awaitTermination(TIMEOUT, TIME_UNIT);
        verify(wrappedExecutor).awaitTermination(TIMEOUT, TIME_UNIT);
        verifyNoMoreInteractions(wrappedExecutor, mockThreadBinder);

    }

    @SuppressWarnings("unchecked")
    @Test
    public void testInvokeAllCollectionOfQextendsCallableOfT() throws InterruptedException {

        final Callable<String> mockCallable = mock(Callable.class);
        final Callable<String> mockCallable2 = mock(Callable.class);

        braveExecutorService.invokeAll(Arrays.asList(mockCallable, mockCallable2));

        final List<Callable<String>> expectedCollection = new ArrayList<Callable<String>>();
        expectedCollection.add(BraveCallable.create(mockCallable, mockThreadBinder));
        expectedCollection.add(BraveCallable.create(mockCallable2, mockThreadBinder));

        verify(mockThreadBinder, times(4)).getCurrentServerSpan();
        verify(wrappedExecutor).invokeAll(expectedCollection);
        verifyNoMoreInteractions(wrappedExecutor, mockThreadBinder);

    }

    @SuppressWarnings("unchecked")
    @Test
    public void testInvokeAllCollectionOfQextendsCallableOfTLongTimeUnit() throws InterruptedException {
        final Callable<String> mockCallable = mock(Callable.class);
        final Callable<String> mockCallable2 = mock(Callable.class);

        braveExecutorService.invokeAll(Arrays.asList(mockCallable, mockCallable2), TIMEOUT, TIME_UNIT);

        final List<Callable<String>> expectedCollection = new ArrayList<Callable<String>>();
        expectedCollection.add(BraveCallable.create(mockCallable, mockThreadBinder));
        expectedCollection.add(BraveCallable.create(mockCallable2, mockThreadBinder));

        verify(mockThreadBinder, times(4)).getCurrentServerSpan();
        verify(wrappedExecutor).invokeAll(expectedCollection, TIMEOUT, TIME_UNIT);
        verifyNoMoreInteractions(wrappedExecutor, mockThreadBinder);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testInvokeAnyCollectionOfQextendsCallableOfT() throws InterruptedException, ExecutionException {
        final Callable<String> mockCallable = mock(Callable.class);
        final Callable<String> mockCallable2 = mock(Callable.class);

        braveExecutorService.invokeAny(Arrays.asList(mockCallable, mockCallable2));

        final List<Callable<String>> expectedCollection = new ArrayList<Callable<String>>();
        expectedCollection.add(BraveCallable.create(mockCallable, mockThreadBinder));
        expectedCollection.add(BraveCallable.create(mockCallable2, mockThreadBinder));

        verify(mockThreadBinder, times(4)).getCurrentServerSpan();
        verify(wrappedExecutor).invokeAny(expectedCollection);
        verifyNoMoreInteractions(wrappedExecutor, mockThreadBinder);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testInvokeAnyCollectionOfQextendsCallableOfTLongTimeUnit() throws InterruptedException, ExecutionException,
        TimeoutException {
        final Callable<String> mockCallable = mock(Callable.class);
        final Callable<String> mockCallable2 = mock(Callable.class);

        braveExecutorService.invokeAny(Arrays.asList(mockCallable, mockCallable2), TIMEOUT, TIME_UNIT);

        final List<Callable<String>> expectedCollection = new ArrayList<Callable<String>>();
        expectedCollection.add(BraveCallable.create(mockCallable, mockThreadBinder));
        expectedCollection.add(BraveCallable.create(mockCallable2, mockThreadBinder));

        verify(mockThreadBinder, times(4)).getCurrentServerSpan();
        verify(wrappedExecutor).invokeAny(expectedCollection, TIMEOUT, TIME_UNIT);
        verifyNoMoreInteractions(wrappedExecutor, mockThreadBinder);
    }

    @Test
    public void testIsShutdown() {
        when(wrappedExecutor.isShutdown()).thenReturn(false);
        assertFalse(braveExecutorService.isShutdown());
        verify(wrappedExecutor).isShutdown();
        verifyNoMoreInteractions(wrappedExecutor, mockThreadBinder);
    }

    @Test
    public void testIsTerminated() {
        when(wrappedExecutor.isTerminated()).thenReturn(false);
        assertFalse(braveExecutorService.isTerminated());
        verify(wrappedExecutor).isTerminated();
        verifyNoMoreInteractions(wrappedExecutor, mockThreadBinder);
    }

    @Test
    public void testShutdown() {
        braveExecutorService.shutdown();
        verify(wrappedExecutor).shutdown();
        verifyNoMoreInteractions(wrappedExecutor, mockThreadBinder);
    }

    @Test
    public void testShutdownNow() {
        final List<Runnable> runnableList = new ArrayList<Runnable>();
        when(wrappedExecutor.shutdownNow()).thenReturn(runnableList);
        assertSame(runnableList, braveExecutorService.shutdownNow());
        verify(wrappedExecutor).shutdownNow();
        verifyNoMoreInteractions(wrappedExecutor, mockThreadBinder);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSubmitCallableOfT() {
        final Callable<String> callable = mock(Callable.class);
        final BraveCallable<String> expectedCallable = BraveCallable.create(callable, mockThreadBinder);
        final Future<String> future = mock(Future.class);
        when(wrappedExecutor.submit(expectedCallable)).thenReturn(future);
        assertSame(future, braveExecutorService.submit(callable));
        verify(mockThreadBinder, times(2)).getCurrentServerSpan();
        verify(wrappedExecutor).submit(expectedCallable);
        verifyNoMoreInteractions(wrappedExecutor, mockThreadBinder);
    }

    @Test
    public void testSubmitRunnable() {
        final Runnable runnable = mock(Runnable.class);
        final BraveRunnable expectedRunnable = BraveRunnable.create(runnable, mockThreadBinder);
        braveExecutorService.submit(runnable);
        verify(mockThreadBinder, times(2)).getCurrentServerSpan();
        verify(wrappedExecutor).submit(expectedRunnable);
        verifyNoMoreInteractions(wrappedExecutor, mockThreadBinder);

    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSubmitRunnableT() {
        final Runnable runnable = mock(Runnable.class);
        final BraveRunnable expectedRunnable = BraveRunnable.create(runnable, mockThreadBinder);
        final String returnValue = "return value";
        final Future<String> future = mock(Future.class);
        when(wrappedExecutor.submit(expectedRunnable, returnValue)).thenReturn(future);
        assertSame(future, braveExecutorService.submit(runnable, returnValue));
        verify(mockThreadBinder, times(2)).getCurrentServerSpan();
        verify(wrappedExecutor).submit(expectedRunnable, returnValue);
        verifyNoMoreInteractions(wrappedExecutor, mockThreadBinder);

    }

}
