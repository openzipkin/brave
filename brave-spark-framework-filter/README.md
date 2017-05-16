# brave-spark-framework-filter

The module contains a Spark framework filter implementing to deal with server
side integration: Getting existing span/trace state from request,
create and submit span with `sr`', `ss` annotations.


Here is a use Example:

First,add dependency

```
<dependency>
    <groupId>io.zipkin.brave</groupId>
    <artifactId>brave-spark-framework-filter</artifactId>
    <version>4.0.6</version>
</dependency>
        
```
Second,configure Brave
```
@Configuration
public class BraveConfiguration {

    /*@Bean 
    Sender sender() {
        return OkHttpSender.create("http://127.0.0.1:9411/api/v1/spans");
    }*/

    @Bean
    public Reporter reporter() {
        return new MyLogReporter();// I just report to log files,then use flume to gather.
        //return AsyncReporter.builder(sender()).build();
    }

    @Bean
    public Brave brave(Reporter reporter) {
        Brave brave = new Brave.Builder("esb-producer").reporter(reporter).build();
        return brave;
    }
}

```

Third,use it in Spark

```
    ApplicationContext context = new ClassPathXmlApplicationContext("applicationContext.xml");
    
    Brave brave = context.getBean(Brave.class);
    
    Spark.before(BraveSparkRequestFilter.create(brave));
     
    Spark.after(BraveSparkResponseFilter.create(brave));

```
