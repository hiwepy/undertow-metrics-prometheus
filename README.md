# undertow-metrics-prometheus

Undertow Metrics For Prometheus

### 组件简介

> Undertow + Actuator 的 Spring Boot Starter 实现；主要代码来自 ：
> https://gitee.com/596392912/mica/tree/master/mica-metrics

### 使用说明

##### 1、Spring Boot 项目添加 Maven 依赖

``` xml
<dependency>
	<groupId>com.github.hiwepy</groupId>
	<artifactId>undertow-metrics-prometheus</artifactId>
	<version>${project.version}</version>
</dependency>
```


##### 2、使用示例

访问本地配置：

http://localhost:8080/actuator/prometheus


```
# HELP undertow_requests_seconds Number of requests
# TYPE undertow_requests_seconds summary
undertow_requests_seconds_count{application="druid-app",} 0.0
undertow_requests_seconds_sum{application="druid-app",} 0.0
# HELP undertow_request_time_max_seconds The longest request duration in time
# TYPE undertow_request_time_max_seconds gauge
undertow_request_time_max_seconds{application="druid-app",} 0.0
# HELP undertow_request_errors_total Total number of error requests
# TYPE undertow_request_errors_total counter
undertow_request_errors_total{application="druid-app",} 0.0
# HELP undertow_request_time_min_seconds The shortest request duration in time
# TYPE undertow_request_time_min_seconds gauge
undertow_request_time_min_seconds{application="druid-app",} -0.001
```

## Jeebiz 技术社区

Jeebiz 技术社区 **微信公共号**、**小程序**，欢迎关注反馈意见和一起交流，关注公众号回复「Jeebiz」拉你入群。

|公共号|小程序|
|---|---|
| ![](https://raw.githubusercontent.com/hiwepy/static/main/images/qrcode_for_gh_1d965ea2dfd1_344.jpg)| ![](https://raw.githubusercontent.com/hiwepy/static/main/images/gh_09d7d00da63e_344.jpg)|
