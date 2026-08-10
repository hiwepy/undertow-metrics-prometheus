/*
 * Copyright (c) 2018-present, easy-4-java (https://github.com/easy-4-java).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.undertow.metrics;

import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.MetricsHandler;

/**
 * Spring-friendly {@link HandlerWrapper} that decorates a downstream
 * {@link HttpHandler} chain with Undertow's built-in
 * {@link MetricsHandler}.
 *
 * <p>The wrapper is installed as an <em>outer</em> handler-chain wrapper via
 * Spring Boot's {@code UndertowDeploymentInfoCustomizer}, which guarantees that
 * every request flowing through the server is observed by Undertow's
 * {@code MetricsHandler} before being handed to the application. Downstream
 * binders ({@link UndertowRequestMetrics}) then consume the metrics collected
 * by this handler through Micrometer.</p>
 *
 * <p>Because the wrapper is a singleton bean and only installs a single
 * {@code MetricsHandler} per application, the instance is also exposed via
 * {@link #getMetricsHandler()} so that {@link UndertowRequestMetrics} can
 * derive request counters and timings from it.</p>
 *
 * <p>Reference:
 * <a href="https://frandorado.github.io/spring/2020/03/31/spring-actuator-undertow.html">
 * spring-actuator-undertow</a>.</p>
 *
 * @author <a href="https://github.com/loong10k">Loong Wan</a>
 * @since 3.0.0
 * @see HandlerWrapper
 * @see MetricsHandler
 * @see UndertowRequestMetrics
 */
public class UndertowMetricsHandlerWrapper implements HandlerWrapper {

    /**
     * The {@link MetricsHandler} produced by the most recent call to
     * {@link #wrap(HttpHandler)}. Held so that downstream binders can read
     * counters and timings without re-wrapping the chain.
     */
    private MetricsHandler metricsHandler;

    /**
     * Wrap the supplied handler with a fresh {@link MetricsHandler}.
     *
     * <p>Each invocation replaces the previously installed handler with a new
     * {@code MetricsHandler} instance. Because the wrapper is registered as a
     * singleton Spring bean and only consulted once during deployment
     * initialisation, this method is normally called exactly once per
     * application lifecycle.</p>
     *
     * @param handler the downstream handler to wrap; must not be {@code null}.
     * @return the newly created {@link MetricsHandler}; never {@code null}.
     */
    @Override
    public HttpHandler wrap(HttpHandler handler) {
        metricsHandler = new MetricsHandler(handler);
        return metricsHandler;
    }

    /**
     * Return the {@link MetricsHandler} most recently produced by
     * {@link #wrap(HttpHandler)}, or {@code null} if {@code wrap} has not
     * been called yet.
     *
     * @return the live {@link MetricsHandler} instance, or {@code null} when
     *         the wrapper has not been initialised.
     */
    public MetricsHandler getMetricsHandler() {
        return metricsHandler;
    }
}