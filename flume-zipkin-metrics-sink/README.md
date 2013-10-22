# flume-zipkin-metrics-sink #

[Flume](http://flume.apache.org) Sink implementation that gets annotations with duration from spans. 
The annotations with duration are application specific and used to measure performance of parts of your code.

The sink calculates statistical distribution for the annotations and sends them to a back-end for storage and visualisation.  Both calculating statistical distribution and sending to back-end
is done using the [Metrics](http://metrics.codahale.com) library.  Currently as a back-end the sink supports [Graphite](http://graphite.wikidot.com) but it should be straightforward to support other back-ends
supported by Metrics.

flume-zipkin-metrics-sink is only tested with [Flume 1.4.0](http://flume.apache.org/releases/content/1.4.0/FlumeUserGuide.html#).

We expect that you use the `org.apache.flume.source.scribe.ScribeSource` which will 
receive spans from Brave Zipkin Span Collector or from the original Zipkin code.
This  sink can also be used with the Scala / Finagle Zipkin stack.

The agent should be configured like this:

    ScribeSource -> Channel of your choice -> ZipkinMetricsSink
    
You can also combine it with the ZipkinSpanCollectorSink (see flume-zipkin-collector-sink) to send spans to the Zipkin collector and annotations with
duration to Graphite.


    ScribeSource --> Channel of your choice -> ZipkinMetricsSink
                 l-> Channel of your choice -> ZipkinSpanCollectorSink



## Configuration ##

### Make flume-zipkin-metrics-sink available on flume classpath ###

The flume-zipkin-metrics-sink project has an assembly descriptor configured in its pom.xml which builds a flume distribution jar 
which contains the required dependencies and which should be put on the flume class path. You can create the distribution by executing:

    mvn clean package

and copy the resulting flume distribution jar file (./target/flume-zipkin-metrics-sink-x.y.z-SNAPSHOT-flume-dist.jar)
to a location where Flume can access it.

Next you make flume-env.sh available by going into the apache-flume-1.4.0-bin/conf directory
and execute:

    cp flume-env.sh.template flume-env.sh

Finally you edit your just created flume-env.sh file and uncomment and complete the 
FLUME_CLASSPATH property:

    # Note that the Flume conf directory is always included in the classpath.
    FLUME_CLASSPATH="/directory/to/jar/flume-zipkin-metrics-sink-2.1.0-SNAPSHOT-flume-dist.jar"
     
When you will start flume after doing this configuration change the flume-zipkin-metrics-sink
should be available for flume to use.

### Configuration ###

We use the [Metrics](http://metrics.codahale.com) library to calculate statistical distribution of the metrics and we use the [Histogram](http://metrics.codahale.com/getting-started/#histograms) functionality of
the Metrics library. The way the Metrics library calculates statistical distribution (min, max, median, percentiles,...) can be configured.

The different configuration settings mostly have impact on accuracy over time (eg more accurate for recent measurements) or the number of measurements it should take into account.
We support most of the configuration options also through the sink.

The minimum configuration properties you need are `graphitehost` and `graphiteport` :

    # Define a sink that calculates statistical distribution of all annotations with duration and sends them to graphite.
    agent1.sinks.zipkin-metrics-sink1.channel = ch1
    agent1.sinks.zipkin-metrics-sink1.type = com.github.kristofa.flume.ZipkinMetricsSink
    agent1.sinks.zipkin-metrics-sink1.graphitehost = localhost
    agent1.sinks.zipkin-metrics-sink1.graphiteport = 2003

If you don't specific the reservoir we will use [UniformReservoir](http://metrics.codahale.com/manual/core/#uniform-reservoirs) with default configuration.

#### Configuring Uniform Reservoir ####

TODO

#### Using Exponentially Decaying Reservoir ####

TODO

#### Using Sliding Time Window Reservoir ####

TODO

#### Using Sliding Window Reservoir ####

TODO

### Example flume.conf file ###

This is an example configuration file (apache-flume-1.4.0-bin/conf/flume.conf) 
that I used during testing. When using it in production you should probably use a channel
that uses persistence iso the memory channel. 

TODO


### Starting flume ###

If you want to start flume with the above example config file you can execute from the 
apache-flume-1.4.0-bin directory:

    ./bin/flume-ng agent -n agent1 -c conf -f conf/flume.conf
    
Before starting the agent you should make sure you have the Zipkin collector running
(in example config it is expected to run at host 10.0.1.8).

Once flume is running you can submit spans using the brave-zipkin-spancollector (ZipkinSpanCollector)
or the original Twitter Zipkin stack to the host where the flume agent is running and port 1463.

