package io.undertow.metrics;

import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.MetricsHandler;

/**
 * https://frandorado.github.io/spring/2020/03/31/spring-actuator-undertow.html
 */
public class UndertowMetricsHandlerWrapper implements HandlerWrapper {

    private MetricsHandler metricsHandler;

    @Override
    public HttpHandler wrap(HttpHandler handler) {
        metricsHandler = new MetricsHandler(handler);
        return metricsHandler;
    }

    public MetricsHandler getMetricsHandler() {
        return metricsHandler;
    }
}