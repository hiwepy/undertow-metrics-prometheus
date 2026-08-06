[English](./README.md) | [简体中文](./README.zh-CN.md)

# undertow-metrics-prometheus

![Java](https://img.shields.io/badge/Java-8-blue)
![License](https://img.shields.io/badge/License-Apache%202.0-blue)

**Undertow Metrics For Prometheus** —— 一个 Spring Boot 自动配置模块（Starter 风格），通过 Micrometer 将 Undertow 服务器指标暴露给 Prometheus。

**导航**

- [1. 项目概述](#1-项目概述)
- [2. 能力与状态](#2-能力与状态)
- [3. 环境要求与兼容性](#3-环境要求与兼容性)
- [4. 架构与模块](#4-架构与模块)
- [5. 安装](#5-安装)
- [6. 快速开始](#6-快速开始)
- [7. 配置](#7-配置)
- [8. 核心用法 / API](#8-核心用法--api)
- [9. 测试与构建](#9-测试与构建)
- [10. 版本线与分支](#10-版本线与分支)
- [11. 贡献与许可](#11-贡献与许可)

## 1. 项目概述

`undertow-metrics-prometheus` 是一个 Spring Boot 2.x 自动配置模块，将 Undertow 运行时统计（请求、连接器、HTTP 会话与 XNIO 工作线程池）绑定到 Micrometer 的 `MeterRegistry`，从而可通过 Spring Boot Actuator 被 Prometheus 抓取。

**它是什么**

- 面向使用 Undertow 作为内嵌 Web 服务器的 Spring Boot 2.7.x 应用的即插即用自动配置。
- 一个 Micrometer 绑定层：4 个指标绑定器共 26 个指标，统一使用 `undertow` 名称前缀。
- 实现源自 [mica-metrics](https://gitee.com/596392912/mica/tree/master/mica-metrics) 项目。

**它不是什么**

- 不是独立的指标库 —— 运行时依赖 Spring Boot + Actuator + Micrometer。
- 不是 Tomcat / Jetty 适配器 —— 只读取 Undertow（XNIO）统计信息。
- 不是 Grafana 仪表盘包 —— 只产出 Prometheus 文本格式。

**典型场景**

| 场景 | 组件的作用 |
|:---|:---|
| 将 Undertow 请求 / 错误 / 耗时指标暴露给 Prometheus | 通过请求处理器包装器绑定 `undertow.request.*` 指标 |
| 监控 HTTP 连接器吞吐与流量 | 来自 Undertow Listener 统计的 `undertow.connectors.*` 指标 |
| 观察 HTTP 会话生命周期 | 来自会话管理器统计的 `undertow.sessions.*` 指标 |
| 观察 XNIO 工作线程池压力 | 来自 `XnioWorkerMXBean` 的 `undertow.xwork.*` 指标 |
| 增加自定义标签（application、环境等） | 通过 `management.metrics.tags.*` 添加额外 Micrometer 标签 |

## 2. 能力与状态

| 能力 | 状态 | 说明 |
|:---|:---|:---|
| Undertow 请求指标 | 稳定 | `undertow.request.count`（summary）、`undertow.request.errors`、`undertow.request.time.max`、`undertow.request.time.min` |
| 连接器指标 | 稳定 | 10 个指标：请求数 / 错误数 / 活跃数 / 活跃峰值、发送 / 接收字节、处理时间 / 峰值、活跃连接 / 峰值 |
| HTTP 会话指标 | 稳定 | 6 个指标：活跃峰值 / 当前活跃、创建、过期、拒绝、存活峰值 |
| XNIO 工作线程指标 | 稳定 | 6 个指标：线程池核心 / 最大 / 当前大小、繁忙线程数、IO 线程数、队列大小 |
| 自动配置 | 稳定 | 通过 `META-INF/spring.factories`（`EnableAutoConfiguration`）注册 |
| 自动开启统计 | 稳定 | 启动时自动设置 `UndertowOptions.ENABLE_STATISTICS`，无需手工调优服务器 |
| 自定义名称前缀 / 标签 | 稳定 | 每个绑定器都提供接收自定义 `namePrefix` 与 `Iterable<Tag>` 的构造器 |
| Prometheus 导出 | 稳定 | 兼容任意 `MeterRegistry` Bean；已与 `micrometer-registry-prometheus` + Actuator 端点配合验证 |
| 原生镜像运行时提示 | 已预备 | `UndertowRuntimeHintsRegistrar` 已存在但**尚未接入**自动配置（`@ImportRuntimeHints` 引用处于注释状态） |

## 3. 环境要求与兼容性

| 要求 | 版本 |
|:---|:---|
| JDK | 8+（`feature/1.0.x` 分支基线） |
| Maven | 3.0+ |
| Spring Boot | 2.7.x（POM 基于 `spring-boot-starter-parent` 2.7.18 构建） |
| 内嵌服务器 | Undertow（`spring-boot-starter-undertow`） |
| 监控组件 | `spring-boot-starter-actuator`、`micrometer-core`、`micrometer-registry-prometheus` |

**版本线矩阵**

| 分支 | JDK | 版本模式 |
|:---|:---|:---|
| `feature/1.0.x` | 8 | `1.0.x.*` |
| `feature/2.0.x` | 17 | `2.0.x.*` |
| `feature/3.0.x` | 21 | `3.0.x.*` |

本文档描述 `feature/1.0.x` 版本线（当前版本：`1.0.x.20260630-SNAPSHOT`）。

## 4. 架构与模块

```text
      Spring Boot 2.7.x 应用（Undertow 内嵌服务器）
                                |
        +-----------------------v-----------------------+
        |      UndertowMetricsAutoConfiguration         |
        |  （通过 META-INF/spring.factories 注册）        |
        |  * 启动时开启 ENABLE_STATISTICS               |
        |  * 包装处理器链（HandlerWrapper）              |
        |  * 在 ApplicationStart 时绑定 4 个绑定器      |
        +-------+--------+-----------+---------+--------+
                |        |           |         |
       UndertowRequest  Connector   Session   XWorker
        Metrics         Metrics     Metrics   Metrics
                |        |           |         |
                +--------+-----+-----+---------+
                               |
                     bindTo(MeterRegistry)
                               |
                  Micrometer 注册表（Prometheus）
                               |
              /actuator/prometheus  ->  Prometheus 抓取
```

**模块清单**

| 模块 | 类型 | 职责 |
|:---|:---|:---|
| `undertow-metrics-prometheus` | 单 jar（自动配置模块） | 全部绑定器、处理器包装器与自动配置入口 |

**包结构**（`io.undertow.metrics`）

| 类 | 职责 |
|:---|:---|
| `UndertowMetricsAutoConfiguration` | 自动配置入口；监听 `ApplicationStartedEvent`；注册处理器包装器、DeploymentInfo 定制器与统计开关定制器 |
| `UndertowMeterBinder` | 抽象 `MeterBinder` 基类，提供 `bindTimer` / `bindGauge` / `bindTimeGauge` / `bindCounter` 辅助方法，并通过反射访问 Undertow 实例 |
| `UndertowMetrics` | 四个具体绑定器的抽象基类；常量 `UNDERTOW_METRIC_NAME_PREFIX = "undertow"` |
| `UndertowRequestMetrics` | 请求数 / 错误数 / 最短最长耗时指标 |
| `UndertowConnectorMetrics` | 按 Listener 的连接器统计 |
| `UndertowSessionMetrics` | 会话管理器统计 |
| `UndertowXWorkerMetrics` | 通过 `XnioWorkerMXBean` 获取的 XNIO 工作线程池指标 |
| `UndertowMetricsHandlerWrapper` | 记录每次请求统计的 `HandlerWrapper` |

## 5. 安装

> **假设**：制品目前通过项目私有 Maven 仓库（阿里云）与 GitHub Releases 分发；该模块**尚未发布到 Maven Central**。若下列坐标无法解析，请在构建中配置私有仓库，或使用 `./mvnw install` 本地安装。

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

模块会传递引入 `micrometer-core` / `micrometer-registry-prometheus` 与 Actuator；`spring-boot-starter-web` 与 `spring-boot-starter-undertow` 声明为 `provided`，Web 技术栈由应用自行提供。

## 6. 快速开始

1. 添加依赖并确保 Undertow 为内嵌服务器：

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

2. 开放 Prometheus 端点（可选地添加 `application` 标签）：

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

3. 启动应用并抓取端点：

```bash
curl http://localhost:8080/actuator/prometheus
```

**预期输出**（示意，数值随运行时流量变化）：

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

## 7. 配置

本模块**不定义专属的 `undertow.metrics.*` 配置项** —— 只要 Undertow 在 classpath 上即自动完成配置。可能需要调整的均为 Spring Boot Actuator / Micrometer 标准配置：

| 配置项 | 用途 | 示例 |
|:---|:---|:---|
| `management.endpoints.web.exposure.include` | 开放 `prometheus`（及 `metrics`）端点 | `health,metrics,prometheus` |
| `management.metrics.tags.*` | 为所有指标添加全局标签（如 `application`） | `management.metrics.tags.application: ${spring.application.name}` |
| `spring.application.name` | 上述标签表达式使用的应用名 | `demo` |
| `management.endpoint.health.probes.enabled` | 可选：启用存活 / 就绪探针 | `true` |

如需程序化修改指标名称前缀或添加标签，请使用绑定器构造器（见第 8 节）。

## 8. 核心用法 / API

所有绑定器位于 `io.undertow.metrics` 包，实现 `io.micrometer.core.instrument.binder.MeterBinder`。指标名格式为 `{前缀}.{分组}.{名称}`，默认前缀为 `undertow`（`UNDERTOW_METRIC_NAME_PREFIX`）。

| 绑定器 | 指标名（`undertow.` 之下） | Micrometer 类型 |
|:---|:---|:---|
| `UndertowRequestMetrics` | `request.count`、`request.errors` | FunctionTimer summary / FunctionCounter |
| | `request.time.max`、`request.time.min` | TimeGauge |
| `UndertowConnectorMetrics` | `connectors.requests.count`、`connectors.requests.error.count`、`connectors.requests.active`、`connectors.requests.active.max` | Gauge |
| | `connectors.bytes.sent`、`connectors.bytes.received` | Gauge |
| | `connectors.processing.time`、`connectors.processing.time.max` | Gauge |
| | `connectors.connections.active`、`connectors.connections.active.max` | Gauge |
| `UndertowSessionMetrics` | `sessions.active.max`、`sessions.active.current`、`sessions.created`、`sessions.expired`、`sessions.rejected`、`sessions.alive.max` | Gauge / Counter |
| `UndertowXWorkerMetrics` | `xwork.worker.pool.core.size`、`xwork.worker.pool.max.size`、`xwork.worker.pool.size`、`xwork.worker.thread.busy.count`、`xwork.io.thread.count`、`xwork.worker.queue.size` | Gauge |

**程序化绑定**（适用于自行管理 `MeterRegistry` 的场景）：

```java
MeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

// 从 Spring 容器获取 UndertowWebServer
UndertowWebServer server = context.getBean(UndertowWebServer.class);

// 默认前缀 "undertow"
new UndertowConnectorMetrics(server).bindTo(registry);

// 自定义前缀与标签
Iterable<Tag> tags = List.of(Tag.of("application", "demo"));
new UndertowRequestMetrics(new UndertowMetricsHandlerWrapper(), "myapp.undertow", tags)
        .bindTo(registry);
```

在普通 Spring Boot 应用中无需任何手工代码 —— `UndertowMetricsAutoConfiguration` 会在应用启动时自动绑定全部 4 组指标。

## 9. 测试与构建

```bash
./mvnw clean verify     # 完整构建，含 JaCoCo 覆盖率报告
./mvnw clean install    # 安装到本地仓库
```

- **测试**：本模块默认跳过单元测试（Surefire `skip` / `skipTests`）；测试源码中提供可运行的 Spring Boot 演示应用（`UndertowMetricsApplicationTests`），用于对真实 Undertow 服务器进行手工验证。
- **覆盖率门禁**：POM 配置了 JaCoCo，在 `verify` 阶段校验行覆盖率不低于 90%（`haltOnFailure=false`）。

## 10. 版本线与分支

| 分支 | JDK | 版本模式 | 说明 |
|:---|:---|:---|:---|
| `feature/1.0.x` | 8 | `1.0.x.*` | 当前版本线；Spring Boot 2.7.x 基线 |
| `feature/2.0.x` | 17 | `2.0.x.*` | 下一代版本线 |
| `feature/3.0.x` | 21 | `3.0.x.*` | 最新版本线 |

- 快照版本遵循 `1.0.x.yyyyMMdd-SNAPSHOT` 命名；发布版本以 `v{version}` 打标签，并通过项目私有仓库与 GitHub Releases 分发。
- `feature/1.0.x` 是持续维护的 JDK 8 版本线；需要更新的 JDK 基线请升级到 `feature/2.0.x`（JDK 17）或 `feature/3.0.x`（JDK 21）。

## 11. 贡献与许可

欢迎贡献 —— 请在 GitHub 上提交 Issue 或 Pull Request。

本项目基于 **Apache License, Version 2.0** 许可发布。详见 [LICENSE](./LICENSE) 文件。

> 声明：指标实现源自 [mica-metrics](https://gitee.com/596392912/mica/tree/master/mica-metrics)（Dreamlu）。
