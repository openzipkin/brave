package com.github.kristofa.brave.cxf;

import static com.github.kristofa.brave.cxf.BraveCxfConstants.BRAVE_SERVER_SPAN;

import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.interceptor.StaxInInterceptor;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.http.HttpServerRequestAdapter;
import com.github.kristofa.brave.http.SpanNameProvider;

/**
 * @author Michał Podsiedzik
 */
public class BraveServerInInterceptor extends AbstractPhaseInterceptor<Message> {
	private final SpanNameProvider spanNameProvider;
	private final Brave brave;

	public BraveServerInInterceptor(final Brave brave, final SpanNameProvider spanNameProvider) {
		super(Phase.RECEIVE);
		addBefore(StaxInInterceptor.class.getName());

		this.brave = brave;
		this.spanNameProvider = spanNameProvider;
	}

	@Override
	public void handleMessage(final Message message) throws Fault {
		try {
			brave.serverRequestInterceptor().handle(new HttpServerRequestAdapter(new ServerRequest(message), spanNameProvider));
			message.getExchange().put(BRAVE_SERVER_SPAN, brave.serverSpanThreadBinder().getCurrentServerSpan());
		} finally {
			brave.serverSpanThreadBinder().setCurrentSpan(null);
		}
	}
}