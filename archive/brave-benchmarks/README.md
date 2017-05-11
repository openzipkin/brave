Brave Benchmarks
================

This module includes [JMH](http://openjdk.java.net/projects/code-tools/jmh/) benchmarks for Brave.

=== Running the benchmark
From the parent directory, run `./mvnw install` to build the benchmarks, and the following to run them:

```bash
# Run with a single worker thread
$ java -jar brave-benchmarks/target/benchmarks.jar
# Add contention by running with 4 threads
$ java -jar brave-benchmarks/target/benchmarks.jar -t4
```
