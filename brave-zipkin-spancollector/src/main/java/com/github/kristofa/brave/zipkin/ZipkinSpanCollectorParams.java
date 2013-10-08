package com.github.kristofa.brave.zipkin;

import org.apache.commons.lang.Validate;

/**
 * Optional parameters for {@link ZipkinSpanCollector}.
 * <p/>
 * If not specified we will use the default values. We support following parameters:
 * <ul>
 * <li>queue size: Size of the queue that is used as buffer between producers of spans and the thread(s) that submit the
 * spans to collector.</li>
 * <li>batch size: The maximum number of spans that is submitted at once to collector of spans.</li>
 * <li>number of threads: The number of parallel threads for submitting spans to collector.</li>
 * <li>socket time out: Time in milliseconds after which our socket connections will time out. When it times out an exception
 * will be thrown.</li>
 * </ul>
 * 
 * @author kristof
 */
public class ZipkinSpanCollectorParams {

    public int DEFAULT_QUEUE_SIZE = 200;
    public int DEFAULT_BATCH_SIZE = 10;
    public int DEFAULT_NR_OF_THREADS = 1;
    public int DEFAULT_SOCKET_TIMEOUT = 5000;

    private int queueSize;
    private int batchSize;
    private int nrOfThreads;
    private int socketTimeout;

    /**
     * Create a new instance with default values.
     */
    public ZipkinSpanCollectorParams() {
        queueSize = 200;
        batchSize = 10;
        nrOfThreads = 1;
        socketTimeout = 5000;
    }

    /**
     * Gets queue size.
     * 
     * @return queue size.
     */
    public int getQueueSize() {
        return queueSize;
    }

    /**
     * Sets the size of the queue that is used as buffer between producers of spans and the thread(s) that submit the spans
     * to collector.
     * 
     * @param queueSize Queue size.
     */
    public void setQueueSize(final int queueSize) {
        Validate.isTrue(queueSize > 0);
        this.queueSize = queueSize;
    }

    /**
     * Gets the maximum batch size.
     * 
     * @return Maximum queue size.
     */
    public int getBatchSize() {
        return batchSize;
    }

    /**
     * Sets the batch size. The batch size is the maximum number of spans that is submitted at once to the collector.
     * 
     * @param batchSize Maximum batch size.
     */
    public void setBatchSize(final int batchSize) {
        Validate.isTrue(batchSize > 0);
        this.batchSize = batchSize;
    }

    /**
     * Gets the number of threads.
     * 
     * @return Number of threads.
     */
    public int getNrOfThreads() {
        return nrOfThreads;
    }

    /**
     * Sets the number of parallel threads for submitting spans to collector.
     * 
     * @param nrOfThreads Number of parallel threads for submitting spans to collector.
     */
    public void setNrOfThreads(final int nrOfThreads) {
        Validate.isTrue(nrOfThreads > 0);
        this.nrOfThreads = nrOfThreads;
    }

    /**
     * Gets the socket time out.
     * 
     * @return Returns the socket time out in milliseconds.
     */
    public int getSocketTimeout() {
        return socketTimeout;
    }

    /**
     * Sets the socket time out in milliseconds.
     * 
     * @param socketTimeout Socket time out in milliseconds. Should be >= 100;
     */
    public void setSocketTimeout(final int socketTimeout) {
        Validate.isTrue(socketTimeout >= 100);
        this.socketTimeout = socketTimeout;
    }

}
