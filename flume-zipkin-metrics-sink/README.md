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

### Sink Configuration ###

We use the [Metrics](http://metrics.codahale.com) library to calculate statistical distribution of the metrics and we use the [Histogram](http://metrics.codahale.com/getting-started/#histograms) functionality of
the Metrics library. The way the Metrics library calculates statistical distribution (min, max, median, percentiles,...) can be configured.

The different configuration settings mostly have impact on accuracy over time (eg more accurate for recent measurements) or the number of measurements it should take into account.
We support most of the configuration options also through the sink.

The minimum configuration properties you need are `graphitehost` and `graphiteport` :

    # Define a sink that calculates statistical distribution of all annotations with duration and sends them to graphite.
    agent1.sinks.zipkin-metrics-sink1.channel = ch1
    agent1.sinks.zipkin-metrics-sink1.type = com.github.kristofa.flume.ZipkinMetricsSink
    agent1.sinks.zipkin-metrics-sink1.graphitehost = graphitehost
    agent1.sinks.zipkin-metrics-sink1.graphiteport = 2003

If you don't specific the reservoir we will use [UniformReservoir](http://metrics.codahale.com/manual/core/#uniform-reservoirs) with default configuration.

#### Metric prefix ####

You can add a default prefix to all metrics that are being submitted but defining the `metricprefix` property. Example:

    # Define a sink that calculates statistical distribution of all annotations with duration and sends them to graphite.
    agent1.sinks.zipkin-metrics-sink1.channel = ch1
    agent1.sinks.zipkin-metrics-sink1.type = com.github.kristofa.flume.ZipkinMetricsSink
    agent1.sinks.zipkin-metrics-sink1.graphitehost = graphitehost
    agent1.sinks.zipkin-metrics-sink1.graphiteport = 2003
    agent1.sinks.zipkin-metrics-sink1.metricprefix = prefix
    
So in this case a metric submitted by your application named `app.expensivecalculation` will end up being `prefix.app.expensivecalculation`.

If not specified there will be no prefix added.

#### Poll time ####

You can change the interval in minutes after which collected metrics are polled by the reporter and submitted to the back-end (graphite).
If you don't specify this option the default value is 1 minute. Example:

    # Define a sink that calculates statistical distribution of all annotations with duration and sends them to graphite.
    agent1.sinks.zipkin-metrics-sink1.channel = ch1
    agent1.sinks.zipkin-metrics-sink1.type = com.github.kristofa.flume.ZipkinMetricsSink
    agent1.sinks.zipkin-metrics-sink1.graphitehost = graphitehost
    agent1.sinks.zipkin-metrics-sink1.graphiteport = 2003
    agent1.sinks.zipkin-metrics-sink1.polltime = 2


#### Configuring Uniform Reservoir ####

The [UniformReservoir](http://metrics.codahale.com/manual/core/#uniform-reservoirs) has an optional configuration entry `nrofsamples`.
If not specified the default value will be used which is 1028 in current version of Metrics.

Example configuration:

    # Define a sink that calculates statistical distribution of all annotations with duration and sends them to graphite.
    agent1.sinks.zipkin-metrics-sink1.channel = ch1
    agent1.sinks.zipkin-metrics-sink1.type = com.github.kristofa.flume.ZipkinMetricsSink
    agent1.sinks.zipkin-metrics-sink1.graphitehost = graphitehost
    agent1.sinks.zipkin-metrics-sink1.graphiteport = 2003
    agent1.sinks.zipkin-metrics-sink1.reservoir = uniform
    agent1.sinks.zipkin-metrics-sink1.nrofsamples = 2056

#### Using Exponentially Decaying Reservoir ####

The [Exponentially Decaying Reservoir](http://metrics.codahale.com/manual/core/#exponentially-decaying-reservoirs) has an optional configuration entries
which should be used together: `nrofsamples` and `decayfactor`.  The nrofsamples indicates the number of samples to keep in reservoir. The decayfactor indicate how much the reservoir 
is biased towards newer values. The higher the more biased to newer values. If you don't specify `nrofsamples` and `decayfactor` default values will be used (nrofsamples = 1028, decayfactor = 0.015).

Example configuration:

    # Define a sink that calculates statistical distribution of all annotations with duration and sends them to graphite.
    agent1.sinks.zipkin-metrics-sink1.channel = ch1
    agent1.sinks.zipkin-metrics-sink1.type = com.github.kristofa.flume.ZipkinMetricsSink
    agent1.sinks.zipkin-metrics-sink1.graphitehost = graphitehost
    agent1.sinks.zipkin-metrics-sink1.graphiteport = 2003
    agent1.sinks.zipkin-metrics-sink1.reservoir = exponentiallydecaying
    agent1.sinks.zipkin-metrics-sink1.nrofsamples = 2056
    agent1.sinks.zipkin-metrics-sink1.decayfactor = 0.012

#### Using Sliding Time Window Reservoir ####

The [Sliding Time Window Reservoir](http://metrics.codahale.com/manual/core/#sliding-time-window-reservoirs) has a mandatory configuration option,
`windowseconds`. This defines the 'window in seconds' to take into account in the reservoir. It has no maximum on nr of measurements to keep track
of so in case lots of metrics are submitted this implementation can result in high memory usage. It is also the slowest reservoir to use.

Example configuration:

    # Define a sink that calculates statistical distribution of all annotations with duration and sends them to graphite.
    agent1.sinks.zipkin-metrics-sink1.channel = ch1
    agent1.sinks.zipkin-metrics-sink1.type = com.github.kristofa.flume.ZipkinMetricsSink
    agent1.sinks.zipkin-metrics-sink1.graphitehost = graphitehost
    agent1.sinks.zipkin-metrics-sink1.graphiteport = 2003
    agent1.sinks.zipkin-metrics-sink1.reservoir = slidingtimewindow
    agent1.sinks.zipkin-metrics-sink1.windowseconds = 120


#### Using Sliding Window Reservoir ####

The [Sliding Window Reservoir ](http://metrics.codahale.com/manual/core/#sliding-window-reservoirs) has a mandatory configuration option, `nrofsamples`.
It defines how many measurements to take into account.

Example configuration:

    # Define a sink that calculates statistical distribution of all annotations with duration and sends them to graphite.
    agent1.sinks.zipkin-metrics-sink1.channel = ch1
    agent1.sinks.zipkin-metrics-sink1.type = com.github.kristofa.flume.ZipkinMetricsSink
    agent1.sinks.zipkin-metrics-sink1.graphitehost = graphitehost
    agent1.sinks.zipkin-metrics-sink1.graphiteport = 2003
    agent1.sinks.zipkin-metrics-sink1.reservoir = slidingwindow
    agent1.sinks.zipkin-metrics-sink1.nrofsamples = 2056

### Example flume.conf file ###

This is an example configuration file (apache-flume-1.4.0-bin/conf/flume.conf) 
that I used during testing with 2 channels and both the `ZipkinMetricsSink` and the `ZipkinSpanCollectorSink` configured. 

    # Finally, now that we've defined all of our components, tell
    # agent1 which ones we want to activate.
    agent1.sources = scribe-source1
    agent1.channels = ch1 ch2
    agent1.sinks = zipkin-sink1 graphite-sink1


    # Define a memory channel called ch1 on agent1, for sending to zipkin collector
    agent1.channels.ch1.type = memory
    agent1.channels.ch1.capacity = 1000
    # Define a memory channel called ch2 on agent1, for sending to graphite sink
    agent1.channels.ch2.type = memory
    agent1.channels.ch2.capacity = 1000

    # Define an Scribe source.
    agent1.sources.scribe-source1.channels = ch1 ch2
    agent1.sources.scribe-source1.type = org.apache.flume.source.scribe.ScribeSource
    agent1.sources.scribe-source1.port = 1463
    agent1.sources.scribe-source1.selector.type = replicating

    # Define a sink that sends all events to zipkin span collector.
    agent1.sinks.zipkin-sink1.channel = ch1
    agent1.sinks.zipkin-sink1.type = com.github.kristofa.flume.ZipkinSpanCollectorSink
    agent1.sinks.zipkin-sink1.hostname = collectorhost
    agent1.sinks.zipkin-sink1.port = 9410
    agent1.sinks.zipkin-sink1.batchsize = 25

    # Define a sink that sends all annotations with durations to graphite using default
    # Uniform reservoir with default configuration.
    agent1.sinks.graphite-sink1.channel = ch2
    agent1.sinks.graphite-sink1.type = com.github.kristofa.flume.ZipkinMetricsSink
    agent1.sinks.graphite-sink1.graphitehost = graphitehost
    agent1.sinks.graphite-sink1.graphiteport = 2003
    agent1.sinks.graphite-sink1.batchsize = 25


### Starting flume ###

If you want to start flume with the above example config file you can execute from the 
apache-flume-1.4.0-bin directory:

    ./bin/flume-ng agent -n agent1 -c conf -f conf/flume.conf
    

