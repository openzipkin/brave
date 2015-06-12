package com.github.kristofa.brave;

import com.twitter.zipkin.gen.Span;
import org.apache.commons.lang3.Validate;

/**
 * Created by hzhao on 8/11/14.
 */
public class ClientSpanThreadBinderImpl implements ClientSpanThreadBinder
{
    private final ClientSpanState clientSpanState;

    /**
     * Creates a new instance.
     *
     * @param clientSpanState client span state, should not be <code>null</code>
     */
    public ClientSpanThreadBinderImpl(final ClientSpanState clientSpanState) {
        Validate.notNull(clientSpanState);
        this.clientSpanState = clientSpanState;
    }

    @Override
    public Span getCurrentClientSpan()
    {
        return clientSpanState.getCurrentClientSpan();
    }

    @Override
    public void setCurrentSpan(Span span)
    {
        clientSpanState.setCurrentClientSpan(span);
    }
}
