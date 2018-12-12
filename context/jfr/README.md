# brave-context-jfr
This adds trace and span IDs to JDK Flight Recorder "Scope" events so
that you can correlate with Zipkin UI or logs accordingly.

Applications that use this must be running JRE 11 and enable flight
recording (ex `-XX:StartFlightRecording`).

To view the flight recordings, you need [JDK Mission Control 7](http://jdk.java.net/jmc/).

With the above in place, you can configure `brave.Tracing` with
`JfrScopeDecorator` like so:

```java
tracing = Tracing.newBuilder()
    .currentTraceContext(ThreadLocalCurrentTraceContext.newBuilder()
       .addScopeDecorator(JfrScopeDecorator.create())
       .build()
    )
    ...
    .build();
```

Once a flight is recorded, look for "Zipkin/Scope" in the Event browser:

<img width="1020" alt="flight recording" src="https://user-images.githubusercontent.com/64215/49850602-1b45d900-fe19-11e8-83fd-14b498128f09.png">

You can then copy/paste the trace ID into the zipkin UI, or use log
correlation to further debug a problem.
