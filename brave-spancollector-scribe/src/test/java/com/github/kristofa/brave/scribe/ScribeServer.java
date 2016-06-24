package com.github.kristofa.brave.scribe;

import com.twitter.zipkin.gen.scribe;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.server.THsHaServer;
import org.apache.thrift.server.TServer;
import org.apache.thrift.transport.TFramedTransport;
import org.apache.thrift.transport.TNonblockingServerSocket;
import org.apache.thrift.transport.TNonblockingServerTransport;
import org.apache.thrift.transport.TTransportException;

import com.twitter.zipkin.gen.Span;
import com.twitter.zipkin.gen.scribe.Iface;
import com.twitter.zipkin.gen.scribe.Processor;

/**
 * Scribe server used for testing.
 */
class ScribeServer {

    private final TServer server;
    private final ScribeReceiver receiver;

    public ScribeServer(final int port) throws TTransportException {
        receiver = new ScribeReceiver();

        final Processor<Iface> processor = new scribe.Processor<>(receiver);

        final TNonblockingServerTransport transport = new TNonblockingServerSocket(port);
        final THsHaServer.Args args = new THsHaServer.Args(transport);

        args.minWorkerThreads(1);
        args.maxWorkerThreads(1);
        args.processor(processor);
        args.protocolFactory(new TBinaryProtocol.Factory());
        args.transportFactory(new TFramedTransport.Factory());

        server = new THsHaServer(args);

    }

    public void start() {

        final Runnable serveThread = new Runnable() {

            @Override
            public void run() {
                server.serve();
            }
        };

        new Thread(serveThread).start();
    }

    public void clearReceivedSpans() {
        receiver.clearReceivedSpans();
    }

    public List<Span> getReceivedSpans() {
        return receiver.getSpans();
    }

    public void introduceDelay(int duration, TimeUnit unit) {
        receiver.setDelayMs((int) TimeUnit.MILLISECONDS.convert(duration, unit));
    }

    public void clearDelay() {
        receiver.setDelayMs(0);
    }

    public void stop() {
        server.stop();
    }

}
