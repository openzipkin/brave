# brave-spark-framework-filter

The module contains a Spark framework filter implementing to deal with server
side integration: Getting existing span/trace state from request,
create and submit span with `sr`', `ss` annotations.


Here is a use Example:

Add a dependency:

```
<dependency>
    <groupId>io.zipkin.brave</groupId>
    <artifactId>brave-spark-framework-filter</artifactId>
    <version>4.0.6</version>
</dependency>
        
```

Use it in Spark:

```
    Sender sender = OkHttpSender.create("http://127.0.0.1:9411/api/v1/spans");
    
    Reporter reporter = new LoggingReporter();
    //Reporter reporter = AsyncReporter.builder(sender()).build();
    
    Brave brave = new Brave.Builder("service-name").reporter(reporter).build();
    
    Spark.before(BraveSparkRequestFilter.create(brave));
    
    Spark.get("/foo", (req, res) -> "bar");
    
    Spark.exception(Exception.class, BraveSparkExceptionHandler.create(brave, new ExceptionHandlerImpl()));
    
    Spark.after(BraveSparkResponseFilter.create(brave));

```
