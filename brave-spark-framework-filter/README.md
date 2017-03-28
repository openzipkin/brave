# brave-spark-framework-filter

The module contains a Spark framework filter implementing to deal with server
side integration: Getting existing span/trace state from request,
create and submit span with `sr`', `ss` annotations.


Here is a use Example:

```java

    Spark.before(BraveSparkFilter.create(brave).requestFilter());
    Spark.get("/foo", (req, res) -> "bar");
    //if you need add binaryAnnotation "error",use this.
    Spark.exception(Exception.class, BraveSparkExceptionHandler.create(brave, new ExceptionHandlerImpl()));
    Spark.afterAfter(BraveSparkFilter.create(brave).responseFilter());
    
```

