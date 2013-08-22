# flume-zipkin-collector-sink #

[Flume](http://flume.apache.org) Sink implementation that sends Flume Events that
contain Spans to the Zipkin Collector.

We expect that you use the org.apache.flume.source.scribe.ScribeSource which will 
receive spans from Brave Zipkin Span Collector or from the original Zipkin code.
The ZipkinCollectorSink can indeed also be used with Scala / Finagle Zipkin stack.

The agent should be configured like this:

    ScribeSource -> Channel of your choice -> ZipkinSpanCollectorSink

You can use multi hop flows by chaining multiple agents. Having the ZipkinSpanCollectorSink
send to the ScribeSource of another agent. This allows you to do Consolidation as explained
in the Flume documentation.

## Configuration ##

### Make flume-zipkin-collector available on flume classpath ###

TODO

### Example flume.conf file ###


    # Define a memory channel called ch1 on agent1
    agent1.channels.ch1.type = memory
     
    # Define an Scribe source.
    agent1.sources.scribe-source1.channels = ch1
    agent1.sources.scribe-source1.type = org.apache.flume.source.scribe.ScribeSource
    agent1.sources.scribe-source1.port = 1463
     
    # Define a sink that sends all events to zipkin span collector.
    agent1.sinks.zipkin-sink1.channel = ch1
    agent1.sinks.zipkin-sink1.type = com.github.kristofa.flume.ZipkinSpanCollectorSink
    agent1.sinks.zipkin-sink1.hostname = 10.0.1.8
    agent1.sinks.zipkin-sink1.port = 9410
     
    # Finally, now that we've defined all of our components, tell
    # agent1 which ones we want to activate.
    agent1.channels = ch1
    agent1.sources = scribe-source1
    agent1.sinks = zipkin-sink1

### Starting flume ###

TODO
