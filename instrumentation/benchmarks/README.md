# brave-instrumentation-benchmarks

This module includes [JMH](http://openjdk.java.net/projects/code-tools/jmh/)
benchmarks for Brave instrumentation. You can use these to measure overhead
of using Brave.

### Running the benchmark
From the project directory, run this to build the benchmarks:

```bash
$ ./mvnw install -pl instrumentation/benchmarks -am -Dmaven.test.skip.exec=true`
```

and the following to run them:

```bash
$ java -jar instrumentation/benchmarks/target/benchmarks.jar
```
