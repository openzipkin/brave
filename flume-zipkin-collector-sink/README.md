# flume-zipkin-collector-sink #

Latest release available in Maven central: 2.0. You normally don't have to include this in
a project through Maven but can download the JAR from [here](http://search.maven.org/#browse%7C1822528105)
to add it to the Flume Agent class path.


[Flume](http://flume.apache.org) Sink implementation that sends Flume Events that
contain Spans to the Zipkin Collector.

flume-zipkin-collector-sink is only tested with [Flume 1.4.0](http://flume.apache.org/releases/content/1.4.0/FlumeUserGuide.html#).

We expect that you use the org.apache.flume.source.scribe.ScribeSource which will 
receive spans from Brave Zipkin Span Collector or from the original Zipkin code.
The ZipkinCollectorSink should indeed also be compatible with the Scala / Finagle Zipkin stack.

The agent should be configured like this:

    ScribeSource -> Channel of your choice -> ZipkinSpanCollectorSink

You can also use multi hop flows by chaining multiple agents. This can be done by having 
the ZipkinSpanCollectorSink send to the ScribeSource of another agent. This allows you to do [Consolidation as explained
in the Flume documentation](http://flume.apache.org/releases/content/1.4.0/FlumeUserGuide.html#consolidation). 
In this set up you will typically have a Flume agent on every
host that generates spans. This agent will collect the spans generated on localhost and submits
them to a central Flume agent which will submit them to the Zipkin collector.

## Configuration ##

### Make flume-zipkin-collector-sink available on flume classpath ###

The flume-zipkin-collector-sink only relies on flume dependencies so you can simply create
the jar file by executing

    mvn clean package

and copy the resulting jar file (./target/flume-zipkin-collector-sink-x.y-SNAPSHOT.jar)
to a location where Flume can access it.

Next you make flume-env.sh available by going into the apache-flume-1.4.0-bin/conf directory
and execute:

    cp flume-env.sh.template flume-env.sh

Finally you edit your just created flume-env.sh file and uncomment and complete the 
FLUME_CLASSPATH property:

    # Note that the Flume conf directory is always included in the classpath.
    FLUME_CLASSPATH="/directory/to/jar/flume-zipkin-collector-sink-2.0-SNAPSHOT.jar"
     
When you will start flume after doing this configuration change the flume-zipkin-collector-sink
should be available for flume to use.

### Example flume.conf file ###

This is an example configuration file (apache-flume-1.4.0-bin/conf/flume.conf) 
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
    agent1.sinks.zipkin-sink1.hostname = 10.0.1.8
    agent1.sinks.zipkin-sink1.port = 9410 # Zipkin collector port
    agent1.sinks.zipkin-sink1.batchsize = 100 # Optional, default value = 100
 
    # Finally, now that we've defined all of our components, tell
    # agent1 which ones we want to activate.
    agent1.channels = ch1
    agent1.sources = scribe-source1
    agent1.sinks = zipkin-sink1


### Starting flume ###

If you want to start flume with the above example config file you can execute from the 
apache-flume-1.4.0-bin directory:

    ./bin/flume-ng agent -n agent1 -c conf -f conf/flume.conf
    
Before starting the agent you should make sure you have the Zipkin collector running
(in example config it is expected to run at host 10.0.1.8).

Once flume is running you can submit spans using the brave-zipkin-spancollector (ZipkinSpanCollector)
or the original Twitter Zipkin stack to the host where the flume agent is running and port 1463.

