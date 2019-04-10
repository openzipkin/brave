# brave-instrumentation-benchmarks

This module includes [JMH](http://openjdk.java.net/projects/code-tools/jmh/)
benchmarks for Brave instrumentation. You can use these to measure overhead
of using Brave.

### Running the benchmark
From the project directory, run `./mvnw install -pl instrumentation/benchmarks -am -DskipTests` to build the benchmarks, and the following to run them:

```bash
$ java -jar instrumentation/benchmarks/target/benchmarks.jar
```
