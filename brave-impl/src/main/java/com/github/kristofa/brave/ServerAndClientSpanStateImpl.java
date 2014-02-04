package com.github.kristofa.brave;

import java.util.LinkedList;

import com.twitter.zipkin.gen.Endpoint;
import com.twitter.zipkin.gen.Span;

/**
 * {@link ServerAndClientSpanState} implementation.
 * 
 * @author kristof
 */
class ServerAndClientSpanStateImpl implements ServerAndClientSpanState {

    private final static ThreadLocal<ServerSpan> currentServerSpan = new ThreadLocal<ServerSpan>() {

        @Override
        protected ServerSpanImpl initialValue() {
            return new ServerSpanImpl(null);
        }
    };
    private final static ThreadLocal<LinkedList<Span>> currentClientSpan = new ThreadLocal<LinkedList<Span>>();

    private Endpoint endPoint;

    public ServerAndClientSpanStateImpl()
    {
    	currentClientSpan.set(new LinkedList<Span>());
    }
    /**
     * {@inheritDoc}
     */
    @Override
    public ServerSpan getCurrentServerSpan() {
        return currentServerSpan.get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setCurrentServerSpan(final ServerSpan span) {
        if (span == null) {
            currentServerSpan.remove();
        } else {
            currentServerSpan.set(span);
        }

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Endpoint getEndPoint() {
        return endPoint;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setEndPoint(final Endpoint endPoint) {
        this.endPoint = endPoint;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Span getCurrentClientSpan() {
    	LinkedList<Span> spans = currentClientSpan.get();
        return spans.size() == 0? null: spans.getLast();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setCurrentClientSpan(final Span span) {
    	LinkedList<Span> spans = currentClientSpan.get();
    	
    	if (spans == null)
    	{
    		spans = new LinkedList<Span>();
    		currentClientSpan.set(spans);
    	}
    	if (span != null)
    	{
    		spans.addLast(span);
    	}
    	else
    	{
    		if (spans.size() != 0)
    		{
    			spans.removeLast();
    		}
    	}
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void incrementServerSpanThreadDuration(final long durationMs) {
        currentServerSpan.get().incThreadDuration(durationMs);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getServerSpanThreadDuration() {
        return currentServerSpan.get().getThreadDuration();
    }

    @Override
    public Boolean sample() {
        return currentServerSpan.get().getSample();
    }

}
