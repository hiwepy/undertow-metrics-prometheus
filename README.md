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
# HELP undertow_request_time_max_seconds The longest request duration in time
# TYPE undertow_request_time_max_seconds gauge
undertow_request_time_max_seconds{application="undertow-app",} NaN
# HELP undertow_request_count_seconds Number of requests
# TYPE undertow_request_count_seconds summary
undertow_request_count_seconds_count{application="undertow-app",} 0.0
undertow_request_count_seconds_sum{application="undertow-app",} 0.0
# HELP undertow_request_errors_total Total number of error requests 
# TYPE undertow_request_errors_total counter
undertow_request_errors_total{application="undertow-app",} 0.0
# HELP undertow_request_time_min_seconds The shortest request duration in time
# TYPE undertow_request_time_min_seconds gauge
undertow_request_time_min_seconds{application="undertow-app",} NaN


# HELP undertow_connectors_requests_active_connections  
# TYPE undertow_connectors_requests_active_connections gauge
undertow_connectors_requests_active_connections{application="undertow-app",protocol="http",} 0.0
# HELP undertow_connectors_bytes_sent_bytes  
# TYPE undertow_connectors_bytes_sent_bytes gauge
undertow_connectors_bytes_sent_bytes{application="undertow-app",protocol="http",} 725662.0
# HELP undertow_connectors_bytes_received_bytes  
# TYPE undertow_connectors_bytes_received_bytes gauge
undertow_connectors_bytes_received_bytes{application="undertow-app",protocol="http",} 28790.0

# HELP undertow_connectors_connections_active_connections  
# TYPE undertow_connectors_connections_active_connections gauge
undertow_connectors_connections_active_connections{application="undertow-app",protocol="http",} 5.0
# HELP undertow_connectors_processing_time_ms  
# TYPE undertow_connectors_processing_time_ms gauge
undertow_connectors_processing_time_ms{application="undertow-app",protocol="http",} 0.0
# HELP undertow_connectors_requests_error_count  
# TYPE undertow_connectors_requests_error_count gauge
undertow_connectors_requests_error_count{application="undertow-app",protocol="http",} 0.0
# HELP undertow_connectors_requests_count  
# TYPE undertow_connectors_requests_count gauge
undertow_connectors_requests_count{application="undertow-app",protocol="http",} 64.0
# HELP undertow_connectors_processing_time_max_ms  
# TYPE undertow_connectors_processing_time_max_ms gauge
undertow_connectors_processing_time_max_ms{application="undertow-app",protocol="http",} 0.0
# HELP undertow_connectors_requests_active_max_connections  
# TYPE undertow_connectors_requests_active_max_connections gauge
undertow_connectors_requests_active_max_connections{application="undertow-app",protocol="http",} 6.0
# HELP undertow_connectors_connections_active_max_connections  
# TYPE undertow_connectors_connections_active_max_connections gauge
undertow_connectors_connections_active_max_connections{application="undertow-app",protocol="http",} 8.0

# HELP undertow_sessions_rejected_sessions_total  
# TYPE undertow_sessions_rejected_sessions_total counter
undertow_sessions_rejected_sessions_total{application="undertow-app",} 0.0
# HELP undertow_sessions_alive_max_seconds  
# TYPE undertow_sessions_alive_max_seconds gauge
undertow_sessions_alive_max_seconds{application="undertow-app",} 0.0
# HELP undertow_sessions_active_max_sessions  
# TYPE undertow_sessions_active_max_sessions gauge
undertow_sessions_active_max_sessions{application="undertow-app",} -1.0
# HELP undertow_sessions_active_current_sessions  
# TYPE undertow_sessions_active_current_sessions gauge
undertow_sessions_active_current_sessions{application="undertow-app",} 0.0
# HELP undertow_sessions_created_sessions_total  
# TYPE undertow_sessions_created_sessions_total counter
undertow_sessions_created_sessions_total{application="undertow-app",} 0.0
# HELP undertow_sessions_expired_sessions_total  
# TYPE undertow_sessions_expired_sessions_total counter
undertow_sessions_expired_sessions_total{application="undertow-app",} 0.0

# HELP undertow_xwork_worker_pool_size XWork worker pool size
# TYPE undertow_xwork_worker_pool_size gauge
undertow_xwork_worker_pool_size{application="undertow-app",name="XNIO-2",} 6.0
# HELP undertow_xwork_worker_queue_size XWork worker queue size
# TYPE undertow_xwork_worker_queue_size gauge
undertow_xwork_worker_queue_size{application="undertow-app",name="XNIO-2",} 0.0
# HELP undertow_xwork_worker_pool_core_size XWork core worker pool size
# TYPE undertow_xwork_worker_pool_core_size gauge
undertow_xwork_worker_pool_core_size{application="undertow-app",name="XNIO-2",} 8.0
# HELP undertow_xwork_io_thread_count XWork io thread count
# TYPE undertow_xwork_io_thread_count gauge
undertow_xwork_io_thread_count{application="undertow-app",name="XNIO-2",} 2.0
# HELP undertow_xwork_worker_thread_busy_count XWork busy worker thread count
# TYPE undertow_xwork_worker_thread_busy_count gauge
undertow_xwork_worker_thread_busy_count{application="undertow-app",name="XNIO-2",} -1.0
# HELP undertow_xwork_worker_pool_max_size XWork max worker pool size
# TYPE undertow_xwork_worker_pool_max_size gauge
undertow_xwork_worker_pool_max_size{application="undertow-app",name="XNIO-2",} 8.0
```

## Jeebiz 技术社区

Jeebiz 技术社区 **微信公共号**、**小程序**，欢迎关注反馈意见和一起交流，关注公众号回复「Jeebiz」拉你入群。

|公共号|小程序|
|---|---|
| ![](https://raw.githubusercontent.com/hiwepy/static/main/images/qrcode_for_gh_1d965ea2dfd1_344.jpg)| ![](https://raw.githubusercontent.com/hiwepy/static/main/images/gh_09d7d00da63e_344.jpg)|
