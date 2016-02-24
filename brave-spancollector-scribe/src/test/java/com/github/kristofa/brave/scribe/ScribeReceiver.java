package com.github.kristofa.brave.scribe;

import com.twitter.zipkin.gen.SpanCodec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.twitter.zipkin.gen.LogEntry;
import com.twitter.zipkin.gen.ResultCode;
import com.twitter.zipkin.gen.Span;
import com.twitter.zipkin.gen.scribe.Iface;

/**
 * ScribeReceiver served through ScribeServer. Keeps received spans in memory.
 * Used for testing.
 *
 * @see ScribeServer
 */
class ScribeReceiver implements Iface {

    private static final Logger LOGGER = Logger.getLogger(ScribeReceiver.class.getName());
    private final BlockingQueue<Span> spans = new LinkedBlockingQueue<>();
    private volatile int delayMs;

    public void clearReceivedSpans() {
        spans.clear();
    }

    @Override
    public ResultCode Log(final List<LogEntry> messages) {
        for (final LogEntry logEntry : messages) {
            final byte[] decodedSpan = Base64.getDecoder().decode(logEntry.getMessage());
            final Span span = SpanCodec.THRIFT.readSpan(decodedSpan);
            spans.add(span);
        }

        if (delayMs > 0) {
            try {
                Thread.sleep(delayMs);
            } catch (final InterruptedException e) {
                LOGGER.log(Level.SEVERE, "Interrupted.", e);
            }
        }
        return ResultCode.OK;
    }

    /**
     * Gets the received spans in the order as they were received.
     * 
     * @return Received spans.
     */
    public List<Span> getSpans() {
        List<Span> result = new ArrayList<>();
        spans.drainTo(result);
        return result;
    }

    public void setDelayMs(int delayMs) {
        if (delayMs < 0) {
            throw new IllegalArgumentException("Invalid delay of " + delayMs + " ms");
        }

        this.delayMs = delayMs;
    }
}
