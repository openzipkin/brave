# flume-zipkin-collector-sink #

Latest release available in Maven central: 2.2.1. You normally don't have to include this in
a project through Maven but can download the flume distribution jar from [here](http://search.maven.org/#search%7Cga%7C1%7Cflume-zipkin-collector-sink)
to add it to the Flume Agent class path.


[Flume](http://flume.apache.org) Sink implementation that sends Flume Events that
contain Spans to the Zipkin Collector.

flume-zipkin-collector-sink is tested with Flume 1.4.0 and [1.5.0](http://flume.apache.org/FlumeUserGuide.html).

We expect that you use the `org.apache.flume.source.scribe.ScribeSource` which will 
receive spans from Brave Zipkin Span Collector or from the original Zipkin code.

The agent should be configured like this:

    ScribeSource -> Channel of your choice -> ZipkinSpanCollectorSink

You can also use multi hop flows by chaining multiple agents. This can be done by having 
the ZipkinSpanCollectorSink send to the ScribeSource of another agent. This allows you to do [Consolidation as explained
in the Flume documentation](http://flume.apache.org/FlumeUserGuide.html#consolidation). 
In this set up you will typically have a Flume agent on every
host that generates spans. This agent will collect the spans generated on localhost and submits
them to a central Flume agent which will submit them to the Zipkin collector.

## Configuration ##

### Make flume-zipkin-collector-sink available on flume classpath ###

The flume-zipkin-metrics-sink project has an assembly descriptor configured in its pom.xml which builds a flume distribution jar 
which contains the required dependencies and which should be put on the flume class path. You can create the distribution by executing:

    mvn clean package

and copy the resulting flume distribution jar file (./target/flume-zipkin-collector-sink-x.y.z-SNAPSHOT-flume-dist.jar)
to a location where Flume can access it.  Or instead of building one yourself you can also copy a released one, see above.

Next you make flume-env.sh available by going into the apache-flume-1.5.0-bin/conf directory
and execute:

    cp flume-env.sh.template flume-env.sh

Finally you edit your just created flume-env.sh file and uncomment and complete the 
FLUME_CLASSPATH property:

    # Note that the Flume conf directory is always included in the classpath.
    FLUME_CLASSPATH="/directory/to/jar/flume-zipkin-collector-sink-2.2.0-SNAPSHOT-flume-dist.jar"
     
When you will start flume after doing this configuration change the flume-zipkin-collector-sink
should be available for flume to use.

### Example flume.conf file ###

This is an example configuration file (apache-flume-1.5.0-bin/conf/flume.conf) 
that I used during testing. When using it in production you should probably use a channel
that uses persistence iso the memory channel. 

    # Define a memory channel called ch1 on agent1
    agent1.channels.ch1.type = memory
 
    # Define an Scribe source.
    agent1.sources.scribe-source1.channels = ch1
    agent1.sources.scribe-source1.type = org.apache.flume.source.scribe.ScribeSource
    agent1.sources.scribe-source1.port = 1463
 
    # Define a sink that sends all events to zipkin span collector.
    agent1.sinks.zipkin-sink1.channel = ch1
    agent1.sinks.zipkin-sink1.type = com.github.kristofa.flume.ZipkinSpanCollectorSink
    # Zipkin collector service host and port
    agent1.sinks.zipkin-sink1.hostname = collectorservicehost
    agent1.sinks.zipkin-sink1.port = 9410 
    agent1.sinks.zipkin-sink1.batchsize = 25
    # Connection and socket time out in ms. Default = 2000.
    agent1.sinks.zipkin-sink1.timeout = 5000
 
    # Finally, now that we've defined all of our components, tell
    # agent1 which ones we want to activate.
    agent1.channels = ch1
    agent1.sources = scribe-source1
    agent1.sinks = zipkin-sink1

For details on how to set up a load balancing configuration, [see here](http://kdevlog.blogspot.be/2014/06/add-load-balancing-to-zipkin-flume.html).

### Starting flume ###

If you want to start flume with the above example config file you can execute from the 
apache-flume-1.5.0-bin directory:

    ./bin/flume-ng agent -n agent1 -c conf -f conf/flume.conf
    
Before starting the agent you should make sure you have the Zipkin collector running
(in example config it is expected to run at host `collectorservicehost`).

Once flume is running you can submit spans using the brave-zipkin-spancollector (ZipkinSpanCollector)
or the original Twitter Zipkin stack to the host where the flume agent is running and port 1463.

