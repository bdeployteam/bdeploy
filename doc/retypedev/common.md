---
order: 10
icon: commit
---
# Common

This bundle contains things shared by all components (BHive, DCU, PCU, Minion, ...).

## SecurityHelper

The `io.bdeploy.common.security` package contains the `SecurityHelper` which can be used to generate and verify keystores and access tokens for remote APIs (over HTTP).

## Configuration

The `io.bdeploy.common.cfg` package contains the `Configuration` class which can be used to create command line tools and map their parameters to annotation proxies.

## ActivityReporter

The `ActivityReporter` implementations (`Stream` and `Null`) can be used to track activities/operations.

## Metrics

The `io.bdeploy.common.metrics` package contains an entry point to allow measurement of various metrics.

## Troubleshooting

1. The JUnit5 `@RegisterExtension` annotation allows to register **instances** of extensions (as opposed to `@ExtendWith`, which registers a class and manages it's lifecycle as appropriate). This means that the **instance** does not get changed. This on the other hand means that the instance **fields** of this extension will keep their state throughout test methods. This is not dramatic, just something you need to be aware of. This is the reason why `TestMinion` calls `resetRegistrations()` in it's `beforeEach` method. Not doing so led to duplicate registrations of singleton services, where the server picked up the services for another test later on...