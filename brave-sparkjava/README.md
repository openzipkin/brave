# brave-spark-framework-filter

The module contains a Spark framework filter implementing to deal with server
side integration: Getting existing span/trace state from request,
create and submit span with `sr`', `ss` annotations.


Here is a use Example:

```java

    // setup brave tracing...
    BraveTracingSparkJava tracing = BraveTracingSparkJava.create(brave);
    Spark.before(tracing.before());
    Spark.exception(Exception.class, tracing.exception(new ExceptionHandlerImpl()));
    Spark.afterAfter(tracing.afterAfter());
    
    // any routes you add are now traced, such as the below
     Spark.get("/foo", (req, res) -> "bar");
    
```

