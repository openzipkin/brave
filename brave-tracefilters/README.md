# brave-tracefilters #

This package contains additional TraceFilters. They are put in a separate project because
they might rely on external libraries (eg ZooKeeper) that you might not want to use.

Available TraceFilter implementations:

*   com.github.kristofa.brave.tracefilter.ZooKeeperSamplingTraceFilter: Trace Filter 
that accesses ZooKeeper to get sample rate. It will also get updated in case sample rate
is updated in ZooKeeper. This means it supports updating sample rate at runtime and 
switching on/off tracing at run time.

