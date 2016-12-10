# Hacking Brave
Brave is optimized for maintenance vs flexibility. It prefers small
features that have been asked for repeated times, that are insured with
tests, and have clear use cases. This limits the lines of code and count
of modules in Brave's repo.

Code design is opinionated including below:

* Classes and methods default to package, not public visibility.
* Changing certain implementation classes may be unsupported.
* 3rd-party dependencies, and gnarly apis like java.beans are avoided.

## How to request change
The best way to approach something not yet supported is to ask on
[gitter](https://gitter.im/openzipkin/zipkin).
Asking for the feature you need (like how to trace a lambda)
vs a specific implementation (like making a private type public) will
give you more options to accomplish your goal.

Advice usually comes in two parts: advice and workaround. Advice may be 
to change Brave's code, or to fork until the feature is more widely
requested.

## How change works
High quality pull requests that have clear scope and tests that reflect
the intent of the feature are often merged and released in days. If a
merged change isn't immediately released and it is of priority to you,
nag (make a comment) on your merged pull request until it is released.

## How to experiment
Changes to Brave's code are best addressed by the feature requestor in a
pull request *after* discussing in an issue or on gitter. By discussing
first, there's less chance of a mutually disappointing experience where
a pull request is rejected. Moreover, the feature may be already present!

Albeit rare, some features will be deferred or rejected for inclusion in
Brave's main repository. In these cases, the choices are typically to
either fork the repository, or make your own repository containing the
change.

### Forks are welcome!
Forking isn't bad. It is a natural place to experiment and vet a feature
before it ends up in Brave's main repository. Large features or those
which haven't satisfied diverse need are often deferred to forks or
separate repositories (see [Rule of Three](http://blog.codinghorror.com/rule-of-three/)).

### Large integrations -> separate repositories
If you look carefully, you'll notice Brave integrations are often less
than 1000 lines of code including tests. Some features are rejected for
inclusion solely due to the amount of maintenance. For example, adding
some features might imply tying up maintainers for several days or weeks
and resulting in a large percentage increase in the size of Brave.

Large integrations aren't bad, but to be sustainable, they need to be
isolated where the maintenance of that feature doesn't endanger the
maintainability of Brave itself. Brave has been going since 2013, without
the need of full-time attention. This is largely because maintenance is
low and approachable.

A good example of an external integration is [dropwizard-zipkin](https://github.com/smoketurner/dropwizard-zipkin).
Dropwizard Zipkin is better off in its own repo, not just because of feature
depth. It is also, better off because it is run by a core contributor to
Dropwizard. This reduces the framework-specific knowledge needed by core Brave
maintainers, and ensures the highest quality integration.
