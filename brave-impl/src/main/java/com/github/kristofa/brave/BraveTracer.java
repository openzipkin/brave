package com.github.kristofa.brave;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.kristofa.brave.ClientTracer;
import com.github.kristofa.brave.EndPointSubmitter;
import com.github.kristofa.brave.ServerTracer;

public class BraveTracer {
    private static final String REQUEST_ANNOTATION = "request";
    private static final String FAILURE_ANNOTATION = "failure";
    
    private final static Logger LOGGER = LoggerFactory.getLogger(BraveTracer.class);

	ClientTracer clientTracer;
	ServerTracer serverTracer;
	EndPointSubmitter endPointSubmitter;
	boolean enabled = true;
	
	public BraveTracer(ClientTracer clientTracer,
			ServerTracer serverTracer, EndPointSubmitter endPointSubmitter) {
		super();
		this.clientTracer = clientTracer;
		this.serverTracer = serverTracer;
		this.endPointSubmitter = endPointSubmitter;
	}	
	
	public BraveTracer(ClientTracer clientTracer,
			ServerTracer serverTracer, EndPointSubmitter endPointSubmitter, boolean enabled) {
		this(clientTracer, serverTracer, endPointSubmitter);
		this.enabled = enabled;
	}
	
	public void submitFailure()
	{
		if (enabled)
		{
			clientTracer.submitAnnotation(FAILURE_ANNOTATION);	
		}
	}
	
	public void submitBinaryAnnotation(String name, int value)
	{
		if (enabled)
		{
			clientTracer.submitBinaryAnnotation(name, value);
		}
	}
	public void submitBinaryAnnotation(String name, String value)
	{
		if (enabled)
		{
			clientTracer.submitBinaryAnnotation(name, value);	
		}
	}
	public void submitAnnotation(String name, String value)
	{
		if (enabled)
		{
			clientTracer.submitAnnotation(value);
		}
	}
	public void stopServerTracer()
	{
		if (enabled)
		{
			serverTracer.setServerSend();
		}
	}
	public void startClientTracer(String clientContext)
	{
		if (enabled)
		{
			clientTracer.startNewSpan(clientContext);
	        clientTracer.submitBinaryAnnotation(REQUEST_ANNOTATION, clientContext);
	        clientTracer.setClientSent();
		}
	}
	public void stopClientTracer()
	{
		if (enabled)
		{
			clientTracer.setClientReceived();
		}
	}
	public void startServerTracer(String contextPath)
	{
		if (enabled)
		{
			submitEndpoint(contextPath);
	        LOGGER.debug("Received no span state.");
	        serverTracer.setStateUnknown(contextPath);
	        serverTracer.setServerReceived();
		}
	}
	public void submitEndpoint(String contextPath) {
		if (enabled)
		{
	        final String localAddr = "localhost";
	        final int localPort =0;
	        LOGGER.debug("Setting endpoint: addr: {}, port: {}, contextpath: {}", localAddr, localPort, contextPath);
	        if (!endPointSubmitter.endPointSubmitted())
	        {
	        	endPointSubmitter.submit(localAddr, localPort, contextPath);
	        }
		}
    }

}
