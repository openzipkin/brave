package com.github.kristofa.brave;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.kristofa.brave.ClientTracer;
import com.github.kristofa.brave.EndPointSubmitter;
import com.github.kristofa.brave.ServerTracer;

public class BraveTracer {
    private static final String REQUEST_ANNOTATION = "request";
    private static final String FAILURE_ANNOTATION = "failure";
    private final static Logger LOGGER = LoggerFactory.getLogger(BraveTracerTest.class);

	ClientTracer clientTracer;
	ServerTracer serverTracer;
	EndPointSubmitter endPointSubmitter;
	
	public BraveTracer(ClientTracer clientTracer,
			ServerTracer serverTracer, EndPointSubmitter endPointSubmitter) {
		super();
		this.clientTracer = clientTracer;
		this.serverTracer = serverTracer;
		this.endPointSubmitter = endPointSubmitter;
	}	
	public void submitFailure()
	{
        clientTracer.submitAnnotation(FAILURE_ANNOTATION);	
	}
	public void submitBinaryAnnotation(String name, int value)
	{
		clientTracer.submitBinaryAnnotation(name, value);
	}
	public void submitBinaryAnnotation(String name, String value)
	{
		clientTracer.submitBinaryAnnotation(name, value);		
	}
	public void submitAnnotation(String name, String value)
	{
		clientTracer.submitAnnotation(value);
	}
	public void stopServerTracer()
	{
		serverTracer.setServerSend();
	}
	public void startClientTracer(String clientContext)
	{
		clientTracer.startNewSpan(clientContext);
        clientTracer.submitBinaryAnnotation(REQUEST_ANNOTATION, clientContext);
        clientTracer.setClientSent();
	}
	public void stopClientTracer()
	{
		clientTracer.setClientReceived();
	}
	public void startServerTracer(String contextPath)
	{
		submitEndpoint(contextPath);
        LOGGER.debug("Received no span state.");
        serverTracer.setStateUnknown(contextPath);
        serverTracer.setServerReceived();
	}
	public void submitEndpoint(String contextPath) {
        final String localAddr = "localhost";
        final int localPort =0;
        LOGGER.debug("Setting endpoint: addr: {}, port: {}, contextpath: {}", localAddr, localPort, contextPath);
        endPointSubmitter.submit(localAddr, localPort, contextPath);
    }

}
