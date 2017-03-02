package brave;

import brave.propagation.TraceContext;

import java.util.concurrent.Callable;

public class TraceCallable<T> implements Callable<T> {

    private final Tracer tracer;
    private final Callable<T> delegate;
    private final TraceContext parent;

    public TraceCallable(Tracer tracer, Callable<T> delegate) {
        this.tracer = tracer;
        this.delegate = delegate;
        this.parent = tracer.currentContext();
    }

    @Override
    public T call() throws Exception {
        try(Span span = tracer.withContext(parent)) {
            return delegate.call();
        }
    }
}
