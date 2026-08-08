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

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.TimeGauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.undertow.Undertow;
import org.springframework.boot.web.embedded.undertow.UndertowWebServer;
import org.springframework.boot.web.reactive.context.ReactiveWebServerApplicationContext;
import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;

/**
 * Abstract base class for every Micrometer {@link MeterBinder} that exposes
 * Undertow runtime metrics.
 *
 * <p>This class centralises the small amount of shared state and helper code
 * required by every concrete binder ({@link UndertowConnectorMetrics},
 * {@link UndertowSessionMetrics}, {@link UndertowXWorkerMetrics},
 * {@link UndertowRequestMetrics}) &mdash; most importantly:</p>
 * <ul>
 *     <li>The default {@link #UNDERTOW_METRIC_NAME_PREFIX} prefix used when no
 *         custom prefix is supplied.</li>
 *     <li>Reflection-based unwrapping of the underlying {@link Undertow}
 *         instance from Spring Boot's {@link UndertowWebServer} (because the
 *         field is package-private and not exposed by the public API).</li>
 *     <li>Convenience overloads for binding timers, gauges, time-gauges and
 *         function-counters so that the concrete binders can stay focused on
 *         the metrics that matter to their subsystem.</li>
 *     <li>A small helper for resolving the {@link UndertowWebServer} from the
 *         active {@link ConfigurableApplicationContext}.</li>
 * </ul>
 *
 * <p>The reflective access to {@code UndertowWebServer.undertow} is performed
 * exactly once in a static initialiser; subsequent reads reuse the cached,
 * accessible {@link Field}.</p>
 *
 * @author [@Loong Wan](https://github.com/loong10k)
 * @since 3.0.0
 * @see MeterBinder
 * @see UndertowMetrics
 */
public abstract class UndertowMeterBinder implements MeterBinder {

    private static final Field UNDERTOW_FIELD;
    /**
     * Default metric-name prefix used when a binder is constructed without an
     * explicit prefix. Concrete binders prepend this value to their internal
     * {@code METRIC_NAME_*} constants to form the fully-qualified metric
     * name (for example {@code undertow.request.count}).
     */
    public static final String UNDERTOW_METRIC_NAME_PREFIX = "undertow";

    /**
     * Register a Micrometer {@link FunctionTimer} with the supplied
     * characteristics. Used by concrete binders to expose cumulative counts
     * alongside a derived rate (for example, requests per second).
     *
     * @param registry     the Micrometer registry to register the timer with;
     *                     must not be {@code null}.
     * @param name         fully-qualified metric name; must not be {@code null}.
     * @param desc         human-readable description; may be {@code null}.
     * @param metricsHandler the upstream object supplying the count and total
     *                     time; must not be {@code null}.
     * @param countFunc    function returning the cumulative count value.
     * @param consumer     function returning the cumulative duration value
     *                     (in the unit declared below).
     * @param tags         additional tags applied to the timer; may be empty.
     * @param <T>          type of the upstream object.
     */
    protected  <T> void bindTimer(MeterRegistry registry, String name, String desc, T metricsHandler, ToLongFunction<T> countFunc, ToDoubleFunction<T> consumer, Iterable<Tag> tags) {
        FunctionTimer.builder(name, metricsHandler, countFunc, consumer, TimeUnit.MILLISECONDS)
                .description(desc)
                .tags(tags)
                .register(registry);
    }

    /**
     * Register a Micrometer {@link Gauge} backed by the supplied
     * {@link ToDoubleFunction}.
     *
     * @param registry     the Micrometer registry to register the gauge with;
     *                     must not be {@code null}.
     * @param name         fully-qualified metric name; must not be {@code null}.
     * @param desc         human-readable description; may be {@code null}.
     * @param metricResult the upstream object observed by the gauge.
     * @param consumer     function returning the current gauge value.
     * @param tags         additional tags applied to the gauge; may be empty.
     * @param <T>          type of the upstream object.
     */
    protected <T> void bindGauge(MeterRegistry registry, String name, String desc, T metricResult,
                                 ToDoubleFunction<T> consumer, Iterable<Tag> tags) {
        Gauge.builder(name, metricResult, consumer)
                .description(desc)
                .tags(tags)
                .register(registry);
    }

    /**
     * Register a Micrometer {@link TimeGauge} whose values are expressed in
     * milliseconds.
     *
     * @param registry     the Micrometer registry to register the gauge with;
     *                     must not be {@code null}.
     * @param name         fully-qualified metric name; must not be {@code null}.
     * @param desc         human-readable description; may be {@code null}.
     * @param metricResult the upstream object observed by the gauge.
     * @param consumer     function returning the current gauge value in
     *                     milliseconds.
     * @param tags         additional tags applied to the gauge; may be empty.
     * @param <T>          type of the upstream object.
     */
    protected <T> void bindTimeGauge(MeterRegistry registry, String name, String desc, T metricResult,
                                     ToDoubleFunction<T> consumer, Iterable<Tag> tags) {
        TimeGauge.builder(name, metricResult, TimeUnit.MILLISECONDS, consumer)
                .description(desc)
                .tags(tags)
                .register(registry);
    }

    /**
     * Register a Micrometer {@link FunctionCounter} whose value is derived
     * from a {@link ToDoubleFunction} applied to the supplied source object.
     *
     * @param registry     the Micrometer registry to register the counter
     *                     with; must not be {@code null}.
     * @param name         fully-qualified metric name; must not be {@code null}.
     * @param desc         human-readable description; may be {@code null}.
     * @param metricsHandler the upstream object observed by the counter.
     * @param consumer     function returning the current counter value.
     * @param tags         additional tags applied to the counter; may be empty.
     * @param <T>          type of the upstream object.
     */
    protected <T> void bindCounter(MeterRegistry registry, String name, String desc, T metricsHandler, ToDoubleFunction<T> consumer, Iterable<Tag> tags) {
        FunctionCounter.builder(name, metricsHandler, consumer)
                .description(desc)
                .tags(tags)
                .register(registry);
    }

    static {
        UNDERTOW_FIELD = ReflectionUtils.findField(UndertowWebServer.class, "undertow");
        Objects.requireNonNull(UNDERTOW_FIELD, "UndertowWebServer class field undertow not exist.");
        ReflectionUtils.makeAccessible(UNDERTOW_FIELD);
    }

    /**
     * Resolve the active {@link UndertowWebServer} from the supplied Spring
     * application context.
     *
     * <p>The method inspects both the servlet and reactive variants of
     * {@code WebServerApplicationContext}; any other context type returns
     * {@code null}. The returned {@link WebServer} is further narrowed to
     * {@link UndertowWebServer} so the caller does not need to cast.</p>
     *
     * @param applicationContext the active Spring application context; must
     *                           not be {@code null}.
     * @return the resolved {@link UndertowWebServer}, or {@code null} if no
     *         web server is registered with the context or if the registered
     *         web server is not an Undertow instance.
     */
    public static UndertowWebServer findUndertowWebServer(ConfigurableApplicationContext applicationContext) {
        WebServer webServer;
        if (applicationContext instanceof ReactiveWebServerApplicationContext) {
            ReactiveWebServerApplicationContext context = (ReactiveWebServerApplicationContext) applicationContext;
            webServer = context.getWebServer();
        } else if (applicationContext instanceof ServletWebServerApplicationContext) {
            ServletWebServerApplicationContext context = (ServletWebServerApplicationContext) applicationContext;
            webServer = context.getWebServer();
        } else {
            return null;
        }
        if (webServer instanceof UndertowWebServer) {
            UndertowWebServer server = (UndertowWebServer) webServer;
            return server;
        }
        return null;
    }

    /**
     * Unwrap the underlying {@link Undertow} instance from the supplied
     * Spring Boot {@link UndertowWebServer} using reflection.
     *
     * <p>Spring Boot does not expose the {@code undertow} field through its
     * public API; this helper centralises the reflective lookup so that the
     * concrete binders can keep their own code clean.</p>
     *
     * @param undertowWebServer the Spring-managed web server; must not be
     *                          {@code null}.
     * @return the wrapped {@link Undertow} instance; may be {@code null} if
     *         Spring has not initialised the field yet.
     */
    public static Undertow getUndertow(UndertowWebServer undertowWebServer) {
        return (Undertow) ReflectionUtils.getField(UNDERTOW_FIELD, undertowWebServer);
    }

}