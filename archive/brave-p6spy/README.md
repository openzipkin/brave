# brave-p6spy #

The brave-p6spy module allows for tracing of all JDBC activity via the [p6spy](https://github.com/p6spy/p6spy) framework which is a proxy for calls to your JDBC driver.

## Using ##

1. Configure

To configure tracing, you first need to configure p6spy. p6spy requires a `spy.properties` to available in your applications classpath (`src/main/resources`). brave-jdbc has a custom listener that you define in the modulelist of the properties file. The example below is a minimalistic configuration that sets up a Derby JDBC driver for tracing: 

```
modulelist=com.github.kristofa.brave.p6spy.P6BraveFactory
url=jdbc:p6spy:derby:memory:p6spy;create=true
user=whatever
password=
host=198.68.0.1
port=3306
serviceName=customerDb
```

Additional information on how to configure p6spy for other application servers and databases is available on their [site](https://github.com/p6spy/p6spy).

2. Inject `ClientTracer` into `BraveP6SpyListener`. e.g.

```java
Brave brave = new Brave.Builder("myService").build();
BraveP6SpyListener.setClientTracer(brave.clientTracer());
```
