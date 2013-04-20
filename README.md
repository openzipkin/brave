# brave #


Java implementation of [Dapper](http://research.google.com/pubs/pub36356.html) and inspired by [zipkin](https://github.com/twitter/zipkin/).

dapper (dutch) = brave (english)... so that's where the name comes from.

## introduction ##

I advise you to read the [Dapper](http://research.google.com/pubs/pub36356.html) paper, but in
short:

What we want to achieve is understand system behavior and performance of complex distributed systems.
We want to do this with minimal impact on existing code by introducing some small common libraries that
are reusable and don't interfere with the existing business logic or architecture.

## about traces and spans ##

![Distributed tracing overview](https://raw.github.com/wiki/kristofa/brave/distributed_tracing.png)





