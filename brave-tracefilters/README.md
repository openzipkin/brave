# brave-tracefilters #

TraceFilter implementations:

*   com.github.kristofa.brave.tracefilter.ZooKeeperSamplingTraceFilter: Trace Filter 
that accesses ZooKeeper to get sample rate. It will also get updated in case sample rate
is updated in ZooKeeper. This means it supports updating sample rate at runtime.

