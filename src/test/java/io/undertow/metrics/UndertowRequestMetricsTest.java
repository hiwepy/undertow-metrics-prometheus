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
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.TimeGauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.MetricsHandler;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link UndertowRequestMetrics}.
 *
 * <p>The request binder observes the live {@link MetricsHandler} supplied
 * by an {@link UndertowMetricsHandlerWrapper} and exposes a request
 * timer, two time-gauges (min/max) and an error counter. The tests cover
 * every constructor overload and the full {@code bindTo} registration path.</p>
 *
 * @since 3.0.0
 */
public class UndertowRequestMetricsTest {

    /**
     * Default constructor must accept the wrapper and pull the live
     * {@link MetricsHandler} from it at bind time.
     */
    @Test
    public void shouldInstantiateWithDefaultConstructor() {
        UndertowMetricsHandlerWrapper wrapper = new UndertowMetricsHandlerWrapper();
        wrapper.wrap(mock(HttpHandler.class));
        UndertowRequestMetrics binder = new UndertowRequestMetrics(wrapper);

        assertNotNull(binder, "binder must be created");
    }

    /**
     * Two-argument constructor must accept a custom prefix.
     */
    @Test
    public void shouldInstantiateWithPrefix() {
        UndertowMetricsHandlerWrapper wrapper = new UndertowMetricsHandlerWrapper();
        UndertowRequestMetrics binder = new UndertowRequestMetrics(wrapper, "http");

        assertNotNull(binder, "binder must be created");
    }

    /**
     * Three-argument constructor must accept a prefix and a tag iterable.
     */
    @Test
    public void shouldInstantiateWithPrefixAndTags() {
        UndertowMetricsHandlerWrapper wrapper = new UndertowMetricsHandlerWrapper();
        Iterable<Tag> tags = Tags.of("env", "test");
        UndertowRequestMetrics binder = new UndertowRequestMetrics(wrapper, "http", tags);

        assertNotNull(binder, "binder must be created");
    }

    /**
     * {@code bindTo} must register the request timer, two time-gauges and
     * the error counter, all derived from the live
     * {@link MetricsHandler.MetricResult} exposed by the wrapper.
     */
    @Test
    public void shouldRegisterRequestTimerAndGauges() {
        MetricsHandler.MetricResult result = mock(MetricsHandler.MetricResult.class);
        when(result.getTotalRequests()).thenReturn(11L);
        when(result.getMinRequestTime()).thenReturn(5);
        when(result.getMaxRequestTime()).thenReturn(50);
        when(result.getTotalErrors()).thenReturn(1L);

        MetricsHandler handler = mock(MetricsHandler.class);
        when(handler.getMetrics()).thenReturn(result);

        UndertowMetricsHandlerWrapper wrapper = mock(UndertowMetricsHandlerWrapper.class);
        when(wrapper.getMetricsHandler()).thenReturn(handler);

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        new UndertowRequestMetrics(wrapper, "http", Tags.of("application", "test"))
                .bindTo(registry);

        FunctionTimer timer = registry.find("http.request.count").tag("application", "test").functionTimer();
        assertNotNull(timer, "request.count function timer must be registered");
        assertEquals(11L, timer.count());
        // function timer records the "min" of the timer source as total time.
        assertEquals(5.0d, timer.totalTime(TimeUnit.MILLISECONDS), 0.0001d);

        TimeGauge minGauge = registry.find("http.request.time.min").tag("application", "test").timeGauge();
        assertNotNull(minGauge, "request.time.min time gauge must be registered");
        assertEquals(5.0d, minGauge.value(TimeUnit.MILLISECONDS), 0.0001d);

        TimeGauge maxGauge = registry.find("http.request.time.max").tag("application", "test").timeGauge();
        assertNotNull(maxGauge, "request.time.max time gauge must be registered");
        assertEquals(50.0d, maxGauge.value(TimeUnit.MILLISECONDS), 0.0001d);

        FunctionCounter errors = registry.find("http.request.errors").tag("application", "test").functionCounter();
        assertNotNull(errors, "request.errors function counter must be registered");
        assertEquals(1.0d, errors.count(), 0.0001d);
    }

    /**
     * When no tags are supplied, the binder must still register its metrics.
     */
    @Test
    public void shouldRegisterWithEmptyTags() {
        MetricsHandler.MetricResult result = mock(MetricsHandler.MetricResult.class);
        when(result.getTotalRequests()).thenReturn(1L);

        MetricsHandler handler = mock(MetricsHandler.class);
        when(handler.getMetrics()).thenReturn(result);

        UndertowMetricsHandlerWrapper wrapper = mock(UndertowMetricsHandlerWrapper.class);
        when(wrapper.getMetricsHandler()).thenReturn(handler);

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        new UndertowRequestMetrics(wrapper, "u", Collections.emptyList()).bindTo(registry);

        assertNotNull(registry.find("u.request.count").functionTimer());
        assertNotNull(registry.find("u.request.time.max").timeGauge());
        assertNotNull(registry.find("u.request.time.min").timeGauge());
        assertNotNull(registry.find("u.request.errors").functionCounter());
    }
}