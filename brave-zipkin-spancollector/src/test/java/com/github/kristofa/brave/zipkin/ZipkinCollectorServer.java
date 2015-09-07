package com.github.kristofa.brave.zipkin;

import com.twitter.zipkin.gen.scribe;
import java.util.List;

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

class ZipkinCollectorServer {

    private final TServer server;
    private final ZipkinCollectorReceiver receiver;

    public ZipkinCollectorServer(final int port) throws TTransportException {
        this(port, -1);
    }

    public ZipkinCollectorServer(final int port, final int delayMs) throws TTransportException {
        receiver = new ZipkinCollectorReceiver(delayMs);

        final Processor<Iface> processor = new scribe.Processor<>(receiver);

        final TNonblockingServerTransport transport = new TNonblockingServerSocket(port);
        final THsHaServer.Args args = new THsHaServer.Args(transport);

        args.workerThreads(1);
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

    public void stop() {
        server.stop();
    }

}
