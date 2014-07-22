# brave-tracefilters #

Latest release available in Maven central:

    <dependency>
        <groupId>com.github.kristofa</groupId>
        <artifactId>brave-tracefilters</artifactId>
        <version>2.2.1</version>
    </dependency>


This package contains additional TraceFilters. They are put in a separate project because
they might rely on external libraries (eg ZooKeeper) that you might not want to use.

## ZookeeperSamplingTraceFilter ##

`com.github.kristofa.brave.tracefilter.ZooKeeperSamplingTraceFilter` is a Trace Filter 
that accesses ZooKeeper to get sample rate. It will also get updated in case sample rate
is updated in ZooKeeper. This means it supports updating sample rate and switching tracing on/off at runtime.

Below is an example of using `zkCLi` to create a znode (`/brave/samplerate`) which starts with sample rate 
value of 20 and is than updated to value 25. If you set the sample rate to <= 0 tracing will be disabled.


    [zk: localhost:2181(CONNECTED) 15] create /brave null
    Created /brave
    [zk: localhost:2181(CONNECTED) 16] create /brave/samplerate 20
    Created /brave/samplerate
    [zk: localhost:2181(CONNECTED) 17] get /brave/samplerate
    20
    cZxid = 0x7f
    ctime = Sun Oct 27 17:42:10 CET 2013
    mZxid = 0x7f
    mtime = Sun Oct 27 17:42:10 CET 2013
    pZxid = 0x7f
    cversion = 0
    dataVersion = 0
    aclVersion = 0
    ephemeralOwner = 0x0
    dataLength = 2
    numChildren = 0
    [zk: localhost:2181(CONNECTED) 18] set /brave/samplerate 25
    cZxid = 0x7f
    ctime = Sun Oct 27 17:42:10 CET 2013
    mZxid = 0x80
    mtime = Sun Oct 27 17:42:47 CET 2013
    pZxid = 0x7f
    cversion = 0
    dataVersion = 1
    aclVersion = 0
    ephemeralOwner = 0x0
    dataLength = 2
    numChildren = 0
    [zk: localhost:2181(CONNECTED) 19] get /brave/samplerate
    25
    cZxid = 0x7f
    ctime = Sun Oct 27 17:42:10 CET 2013
    mZxid = 0x80
    mtime = Sun Oct 27 17:42:47 CET 2013
    pZxid = 0x7f
    cversion = 0
    dataVersion = 1
    aclVersion = 0
    ephemeralOwner = 0x0
    dataLength = 2
    numChildren = 0
    [zk: localhost:2181(CONNECTED) 20]
