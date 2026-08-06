[English](./README.md) | [简体中文](./README.zh-CN.md)

# undertow-metrics-prometheus

![Java](https://img.shields.io/badge/Java-8-blue)
![License](https://img.shields.io/badge/License-Apache%202.0-blue)

**Undertow Metrics For Prometheus** — a Spring Boot auto-configuration module (starter style) that exposes Undertow server metrics to Prometheus through Micrometer.

**Navigation**

- [1. Project Overview](#1-project-overview)
- [2. Features & Status](#2-features--status)
- [3. Requirements & Compatibility](#3-requirements--compatibility)
- [4. Architecture & Modules](#4-architecture--modules)
- [5. Installation](#5-installation)
- [6. Quick Start](#6-quick-start)
- [7. Configuration](#7-configuration)
- [8. Core Usage / API](#8-core-usage--api)
- [9. Testing & Build](#9-testing--build)
- [10. Versioning & Branches](#10-versioning--branches)
- [11. Contributing & License](#11-contributing--license)

## 1. Project Overview

`undertow-metrics-prometheus` is a Spring Boot 2.x auto-configuration module that binds Undertow runtime statistics (requests, connectors, HTTP sessions and the XNIO worker pool) to a Micrometer `MeterRegistry`, so they can be scraped by Prometheus through Spring Boot Actuator.

**What it is**

- A drop-in auto-configuration for Spring Boot 2.7.x applications that use Undertow as the embedded web server.
- A Micrometer binding layer: 26 metrics across 4 metric binders, all under the `undertow` name prefix.
- The implementation is derived from the [mica-metrics](https://gitee.com/596392912/mica/tree/master/mica-metrics) project.

**What it is not**

- Not a standalone metrics library — it requires Spring Boot + Actuator + Micrometer at runtime.
- Not a Tomcat/Jetty adapter — it only reads Undertow (XNIO) statistics.
- Not a Grafana dashboard pack — it only produces the Prometheus exposition format.

**Typical scenarios**

| Scenario | How this component helps |
|:---|:---|
| Expose Undertow request/error/time metrics to Prometheus | `undertow.request.*` metrics bound from the request handler wrapper |
| Monitor HTTP connector throughput and bytes | `undertow.connectors.*` metrics from Undertow listener statistics |
| Watch HTTP session lifecycle | `undertow.sessions.*` metrics from the session manager statistics |
| Observe XNIO worker pool pressure | `undertow.xwork.*` metrics from the `XnioWorkerMXBean` |
| Add custom labels (`application`, environment, ...) | Extra Micrometer tags via `management.metrics.tags.*` |

## 2. Features & Status

| Capability | Status | Description |
|:---|:---|:---|
| Undertow request metrics | Stable | `undertow.request.count` (summary), `undertow.request.errors`, `undertow.request.time.max`, `undertow.request.time.min` |
| Connector metrics | Stable | 10 metrics: requests count / errors / active / active max, bytes sent / received, processing time / max, connections active / max |
| HTTP session metrics | Stable | 6 metrics: active max / current, created, expired, rejected, alive max |
| XNIO worker metrics | Stable | 6 metrics: pool core / max / size, busy thread count, I/O thread count, queue size |
| Auto-configuration | Stable | Registered via `META-INF/spring.factories` (`EnableAutoConfiguration`) |
| Automatic statistics switch | Stable | Enables `UndertowOptions.ENABLE_STATISTICS` at startup, no manual server tuning needed |
| Customizable name prefix / tags | Stable | Every binder exposes constructors taking a custom `namePrefix` and `Iterable<Tag>` |
| Prometheus export | Stable | Works with any `MeterRegistry` bean; tested with `micrometer-registry-prometheus` + Actuator endpoint |
| Native-image runtime hints | Prepared | `UndertowRuntimeHintsRegistrar` exists but is **not yet wired** into the auto-configuration (the `@ImportRuntimeHints` reference is commented out) |

## 3. Requirements & Compatibility

| Requirement | Version |
|:---|:---|
| JDK | 8+ (baseline of the `feature/1.0.x` branch) |
| Maven | 3.0+ |
| Spring Boot | 2.7.x (the POM builds on `spring-boot-starter-parent` 2.7.18) |
| Embedded server | Undertow (`spring-boot-starter-undertow`) |
| Monitoring stack | `spring-boot-starter-actuator`, `micrometer-core`, `micrometer-registry-prometheus` |

**Version line matrix**

| Branch | JDK | Version pattern |
|:---|:---|:---|
| `feature/1.0.x` | 8 | `1.0.x.*` |
| `feature/2.0.x` | 17 | `2.0.x.*` |
| `feature/3.0.x` | 21 | `3.0.x.*` |

This document describes the `feature/1.0.x` line (current version: `1.0.x.20260630-SNAPSHOT`).

## 4. Architecture & Modules

```text
      Spring Boot 2.7.x Application (Undertow embedded server)
                                |
        +-----------------------v-----------------------+
        |      UndertowMetricsAutoConfiguration         |
        |  (registered via META-INF/spring.factories)   |
        |  * enables ENABLE_STATISTICS on the builder   |
        |  * wraps the handler chain (HandlerWrapper)   |
        |  * binds 4 metric binders at ApplicationStart |
        +-------+--------+-----------+---------+--------+
                |        |           |         |
       UndertowRequest  Connector   Session   XWorker
        Metrics         Metrics     Metrics   Metrics
                |        |           |         |
                +--------+-----+-----+---------+
                               |
                     bindTo(MeterRegistry)
                               |
                 Micrometer registry (Prometheus)
                               |
              /actuator/prometheus  ->  Prometheus scrape
```

**Module list**

| Module | Type | Responsibility |
|:---|:---|:---|
| `undertow-metrics-prometheus` | Single jar (auto-configuration module) | All binders, the handler wrapper and the auto-configuration entry point |

**Package layout** (`io.undertow.metrics`)

| Class | Role |
|:---|:---|
| `UndertowMetricsAutoConfiguration` | Auto-configuration entry; listener on `ApplicationStartedEvent`; registers the handler wrapper, the deployment-info customizer and the statistics builder customizer |
| `UndertowMeterBinder` | Abstract `MeterBinder` base with `bindTimer` / `bindGauge` / `bindTimeGauge` / `bindCounter` helpers and reflection-based access to the Undertow instance |
| `UndertowMetrics` | Abstract base for the four concrete binders; constant `UNDERTOW_METRIC_NAME_PREFIX = "undertow"` |
| `UndertowRequestMetrics` | Request count / errors / min-max time metrics |
| `UndertowConnectorMetrics` | Per-listener connector statistics |
| `UndertowSessionMetrics` | Session manager statistics |
| `UndertowXWorkerMetrics` | XNIO worker pool metrics via `XnioWorkerMXBean` |
| `UndertowMetricsHandlerWrapper` | `HandlerWrapper` that records per-request statistics |

## 5. Installation

> **Assumption**: artifacts are currently distributed through the project's private Maven repository (Aliyun) and GitHub Releases; the module is **not yet published to Maven Central**. If the coordinates below cannot be resolved, either add the private repository to your build or install locally with `./mvnw install`.

**Maven**

```xml
<dependency>
    <groupId>io.github.easy4j</groupId>
    <artifactId>undertow-metrics-prometheus</artifactId>
    <version>1.0.x.20260630-SNAPSHOT</version>
</dependency>
```

**Gradle**

```gradle
implementation 'io.github.easy4j:undertow-metrics-prometheus:1.0.x.20260630-SNAPSHOT'
```

The module brings `micrometer-core` / `micrometer-registry-prometheus` and Actuator transitively; `spring-boot-starter-web` and `spring-boot-starter-undertow` are declared `provided`, so your application must provide the web stack.

## 6. Quick Start

1. Add the dependency and make sure Undertow is the embedded server:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
    <exclusions>
        <exclusion>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-tomcat</artifactId>
        </exclusion>
    </exclusions>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-undertow</artifactId>
</dependency>
<dependency>
    <groupId>io.github.easy4j</groupId>
    <artifactId>undertow-metrics-prometheus</artifactId>
    <version>1.0.x.20260630-SNAPSHOT</version>
</dependency>
```

2. Expose the Prometheus endpoint (and optionally add an `application` tag):

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus
  metrics:
    tags:
      application: ${spring.application.name}
spring:
  application:
    name: demo
```

3. Start the application and scrape the endpoint:

```bash
curl http://localhost:8080/actuator/prometheus
```

**Expected output** (illustrative — values depend on runtime traffic):

```text
# HELP undertow_request_count_seconds Number of total requests
# TYPE undertow_request_count_seconds summary
undertow_request_count_seconds_count{application="demo",} 64.0
undertow_request_count_seconds_sum{application="demo",} 0.0
# HELP undertow_request_errors_total Total number of error requests
# TYPE undertow_request_errors_total counter
undertow_request_errors_total{application="demo",} 0.0
# HELP undertow_request_time_max_seconds Longest request duration
# TYPE undertow_request_time_max_seconds gauge
undertow_request_time_max_seconds{application="demo",} 0.0
# HELP undertow_xwork_worker_pool_size XWork worker pool size
# TYPE undertow_xwork_worker_pool_size gauge
undertow_xwork_worker_pool_size{application="demo",name="XNIO-2",} 6.0
```

## 7. Configuration

The module defines **no dedicated `undertow.metrics.*` properties** — it auto-configures itself once Undertow is on the classpath. The knobs you may want to touch are standard Spring Boot Actuator / Micrometer settings:

| Property | Purpose | Example |
|:---|:---|:---|
| `management.endpoints.web.exposure.include` | Expose the `prometheus` (and `metrics`) endpoints | `health,metrics,prometheus` |
| `management.metrics.tags.*` | Add global tags to every metric (e.g. `application`) | `management.metrics.tags.application: ${spring.application.name}` |
| `spring.application.name` | Application name used by the tag expression above | `demo` |
| `management.endpoint.health.probes.enabled` | Optional liveness/readiness probes | `true` |

To change the metric name prefix or add tags programmatically, use the binder constructors (see Section 8).

## 8. Core Usage / API

All binders live in `io.undertow.metrics` and implement `io.micrometer.core.instrument.binder.MeterBinder`. Metric names are composed as `{prefix}.{group}.{name}` with the default prefix `undertow` (`UNDERTOW_METRIC_NAME_PREFIX`).

| Binder | Metric names (under `undertow.`) | Micrometer type |
|:---|:---|:---|
| `UndertowRequestMetrics` | `request.count`, `request.errors` | FunctionTimer summary / FunctionCounter |
| | `request.time.max`, `request.time.min` | TimeGauge |
| `UndertowConnectorMetrics` | `connectors.requests.count`, `connectors.requests.error.count`, `connectors.requests.active`, `connectors.requests.active.max` | Gauge |
| | `connectors.bytes.sent`, `connectors.bytes.received` | Gauge |
| | `connectors.processing.time`, `connectors.processing.time.max` | Gauge |
| | `connectors.connections.active`, `connectors.connections.active.max` | Gauge |
| `UndertowSessionMetrics` | `sessions.active.max`, `sessions.active.current`, `sessions.created`, `sessions.expired`, `sessions.rejected`, `sessions.alive.max` | Gauge / Counter |
| `UndertowXWorkerMetrics` | `xwork.worker.pool.core.size`, `xwork.worker.pool.max.size`, `xwork.worker.pool.size`, `xwork.worker.thread.busy.count`, `xwork.io.thread.count`, `xwork.worker.queue.size` | Gauge |

**Programmatic binding** (useful when you manage your own `MeterRegistry`):

```java
MeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

// Obtain the UndertowWebServer from the Spring context
UndertowWebServer server = context.getBean(UndertowWebServer.class);

// Default prefix "undertow"
new UndertowConnectorMetrics(server).bindTo(registry);

// Custom prefix and tags
Iterable<Tag> tags = List.of(Tag.of("application", "demo"));
new UndertowRequestMetrics(new UndertowMetricsHandlerWrapper(), "myapp.undertow", tags)
        .bindTo(registry);
```

In a normal Spring Boot application you do not need any of this — `UndertowMetricsAutoConfiguration` binds all four metric groups automatically at application start.

## 9. Testing & Build

```bash
./mvnw clean verify     # full build incl. JaCoCo coverage report
./mvnw clean install    # install into the local repository
```

- **Tests**: unit tests are skipped by default in this module (Surefire `skip` / `skipTests`); the test sources contain a runnable Spring Boot demo application (`UndertowMetricsApplicationTests`) used for manual verification against a real Undertow server.
- **Coverage gate**: the POM configures JaCoCo with a 90% line-coverage minimum at the `verify` phase (`haltOnFailure=false`).

## 10. Versioning & Branches

| Branch | JDK | Version pattern | Notes |
|:---|:---|:---|:---|
| `feature/1.0.x` | 8 | `1.0.x.*` | Current line; Spring Boot 2.7.x baseline |
| `feature/2.0.x` | 17 | `2.0.x.*` | Next generation line |
| `feature/3.0.x` | 21 | `3.0.x.*` | Latest line |

- Snapshot versions follow the `1.0.x.yyyyMMdd-SNAPSHOT` scheme; releases are tagged `v{version}` and published through the project's private repository and GitHub Releases.
- The `feature/1.0.x` line is the actively maintained JDK 8 line; upgrade to `feature/2.0.x` (JDK 17) or `feature/3.0.x` (JDK 21) for newer JDK baselines.

## 11. Contributing & License

Contributions are welcome — please open an issue or a pull request on GitHub.

This project is licensed under the **Apache License, Version 2.0**. See the [LICENSE](./LICENSE) file for details.

> Attribution: the metrics implementation is derived from [mica-metrics](https://gitee.com/596392912/mica/tree/master/mica-metrics) (Dreamlu).
