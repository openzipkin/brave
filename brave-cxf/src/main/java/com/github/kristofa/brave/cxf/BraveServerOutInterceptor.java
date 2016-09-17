package com.github.kristofa.brave.cxf;

import static com.github.kristofa.brave.cxf.BraveCxfConstants.BRAVE_SERVER_SPAN;

import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.interceptor.LoggingOutInterceptor;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ServerSpan;
import com.github.kristofa.brave.http.HttpServerResponseAdapter;

/**
 * @author Michał Podsiedzik
 */
public class BraveServerOutInterceptor extends AbstractPhaseInterceptor<Message> {

	private final Brave brave;

	public BraveServerOutInterceptor(final Brave brave) {
		super(Phase.PRE_STREAM);
		addBefore(LoggingOutInterceptor.class.getName());

		this.brave = brave;
	}

	@Override
	public void handleMessage(final Message message) throws Fault {
		final ServerSpan serverSpan = (ServerSpan) message.getExchange().get(BRAVE_SERVER_SPAN);

		if (serverSpan != null) {
			brave.serverSpanThreadBinder().setCurrentSpan(serverSpan);
			brave.serverResponseInterceptor().handle(new HttpServerResponseAdapter(new ServerResponse(message)));
		}
	}
}