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
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.undertow.Undertow;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.embedded.undertow.UndertowWebServer;
import org.springframework.boot.web.reactive.context.ReactiveWebServerApplicationContext;
import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link UndertowMeterBinder} and its concrete helper
 * {@link TestableBinder} used to exercise the package-private
 * {@code bindTimer}, {@code bindGauge}, {@code bindTimeGauge} and
 * {@code bindCounter} overloads.
 *
 * <p>The tests also cover {@link UndertowMeterBinder#findUndertowWebServer}
 * for every application-context type and
 * {@link UndertowMeterBinder#getUndertow} for the unwrap path.</p>
 *
 * @since 3.0.0
 */
public class UndertowMeterBinderTest {

    /**
     * Default-prefix constant must equal {@code "undertow"}.
     */
    @Test
    public void shouldExposeUndertowMetricNamePrefix() {
        assertEquals(UndertowMeterBinder.UNDERTOW_METRIC_NAME_PREFIX, "undertow");
    }

    /**
     * The static initialiser must successfully resolve the {@code undertow}
     * field of {@link UndertowWebServer} so that
     * {@link UndertowMeterBinder#getUndertow} can unwrap it later.
     *
     * <p>Because {@code getUndertow} uses reflection to read the private
     * {@code undertow} field (bypassing the mock getter), we inject the
     * value via reflection.</p>
     */
    @Test
    public void shouldUnwrapUndertowFromUndertowWebServer() throws Exception {
        Undertow undertow = mock(Undertow.class);
        UndertowWebServer server = mock(UndertowWebServer.class);

        // Inject the undertow instance into the mock's private field via reflection
        Field undertowField = UndertowWebServer.class.getDeclaredField("undertow");
        undertowField.setAccessible(true);
        undertowField.set(server, undertow);

        Undertow unwrapped = UndertowMeterBinder.getUndertow(server);
        assertSame(undertow, unwrapped, "getUndertow() must return the same instance Spring holds");
    }

    /**
     * {@link UndertowMeterBinder#findUndertowWebServer} must handle
     * reactive, servlet and "other" contexts without throwing.
     */
    @Test
    public void shouldReturnNullForUnknownContextType() {
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        UndertowWebServer server = UndertowMeterBinder.findUndertowWebServer(context);
        assertNull(server, "non-Undertow context must yield null");
    }

    /**
     * When a reactive context's underlying web server is an
     * {@link UndertowWebServer}, the helper must return it.
     */
    @Test
    public void shouldFindUndertowWebServerFromReactiveContext() {
        UndertowWebServer server = mock(UndertowWebServer.class);
        ReactiveWebServerApplicationContext context = mock(ReactiveWebServerApplicationContext.class);
        when(context.getWebServer()).thenReturn(server);

        UndertowWebServer found = UndertowMeterBinder.findUndertowWebServer(context);
        assertSame(server, found, "reactive context must surface its UndertowWebServer");
    }

    /**
     * When a servlet context's underlying web server is an
     * {@link UndertowWebServer}, the helper must return it.
     */
    @Test
    public void shouldFindUndertowWebServerFromServletContext() {
        UndertowWebServer server = mock(UndertowWebServer.class);
        ServletWebServerApplicationContext context = mock(ServletWebServerApplicationContext.class);
        when(context.getWebServer()).thenReturn(server);

        UndertowWebServer found = UndertowMeterBinder.findUndertowWebServer(context);
        assertSame(server, found, "servlet context must surface its UndertowWebServer");
    }

    /**
     * When the servlet context's underlying web server is <em>not</em> an
     * {@link UndertowWebServer}, the helper must return {@code null}.
     */
    @Test
    public void shouldReturnNullForServletContextWithNonUndertowServer() {
        WebServer tomcat = mock(WebServer.class);
        ServletWebServerApplicationContext context = mock(ServletWebServerApplicationContext.class);
        when(context.getWebServer()).thenReturn(tomcat);

        UndertowWebServer found = UndertowMeterBinder.findUndertowWebServer(context);
        assertNull(found, "non-Undertow web server must produce null");
    }

    /**
     * {@code bindTimer} must register a {@link FunctionTimer} that observes
     * the supplied source object.
     */
    @Test
    public void shouldBindTimer() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        // Use a single source object whose methods return count and total time.
        TimerSource source = new TimerSource(3L, 60.0d);

        new TestableBinder().bindTimer(
                registry,
                "test.timer",
                "Test timer",
                source,
                TimerSource::getCount,
                TimerSource::getTotalTime,
                Collections.emptyList()
        );

        FunctionTimer timer = registry.find("test.timer").functionTimer();
        assertNotNull(timer, "function timer must be registered");
        assertEquals(3.0d, timer.count(), 0.0001d);
        assertEquals(60.0d, timer.totalTime(TimeUnit.MILLISECONDS), 0.0001d);
    }

    /**
     * {@code bindGauge} must register a {@link Gauge} that observes the
     * supplied source object.
     */
    @Test
    public void shouldBindGauge() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AtomicLong counter = new AtomicLong(42);

        new TestableBinder().bindGauge(
                registry,
                "test.gauge",
                "Test gauge",
                counter,
                AtomicLong::doubleValue,
                Collections.emptyList()
        );

        Gauge gauge = registry.find("test.gauge").gauge();
        assertNotNull(gauge, "gauge must be registered");
        assertEquals(42.0d, gauge.value(), 0.0001d);

        counter.set(99);
        assertEquals(99.0d, gauge.value(), 0.0001d, "gauge must observe the source live");
    }

    /**
     * {@code bindTimeGauge} must register a {@link TimeGauge} expressed in
     * milliseconds.
     */
    @Test
    public void shouldBindTimeGauge() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AtomicLong elapsed = new AtomicLong(1234);

        new TestableBinder().bindTimeGauge(
                registry,
                "test.timegauge",
                "Test time gauge",
                elapsed,
                AtomicLong::doubleValue,
                Collections.emptyList()
        );

        TimeGauge gauge = registry.find("test.timegauge").timeGauge();
        assertNotNull(gauge, "time gauge must be registered");
        assertEquals(1234.0d, gauge.value(TimeUnit.MILLISECONDS), 0.0001d);
        assertNotNull(gauge.baseTimeUnit(), "base time unit must not be null");
    }

    /**
     * {@code bindCounter} must register a {@link FunctionCounter} that
     * observes the supplied source object.
     */
    @Test
    public void shouldBindCounter() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AtomicLong counter = new AtomicLong(7);

        new TestableBinder().bindCounter(
                registry,
                "test.counter",
                "Test counter",
                counter,
                AtomicLong::doubleValue,
                Collections.emptyList()
        );

        FunctionCounter registered = registry.find("test.counter").functionCounter();
        assertNotNull(registered, "function counter must be registered");
        assertEquals(7.0d, registered.count(), 0.0001d);

        counter.set(8);
        assertEquals(8.0d, registered.count(), 0.0001d, "counter must observe the source live");
    }

    /**
     * The four {@code bindXxx} overloads must honour the supplied
     * {@link Tag} iterable and propagate it to the registered meter.
     */
    @Test
    public void shouldApplyTagsWhenBinding() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        Iterable<Tag> tags = Collections.singletonList(Tag.of("env", "test"));

        new TestableBinder().bindGauge(
                registry,
                "test.gauge.tags",
                "Tagged gauge",
                new AtomicLong(1),
                AtomicLong::doubleValue,
                tags
        );

        assertNotNull(registry.find("test.gauge.tags").tag("env", "test").gauge(), "tagged gauge must be registered");
    }

    /**
     * Concrete {@link UndertowMeterBinder} used to expose the
     * package-private {@code bindXxx} helpers to the test.
     */
    static class TestableBinder extends UndertowMeterBinder {
        @Override
        public void bindTo(MeterRegistry registry) {
            // no-op for testing purposes
        }
    }

    /**
     * Simple POJO used as a single source object for {@code bindTimer}
     * tests so that both the count function and the total-time function
     * operate on the same instance.
     */
    static class TimerSource {
        private final long count;
        private final double totalTime;

        TimerSource(long count, double totalTime) {
            this.count = count;
            this.totalTime = totalTime;
        }

        long getCount() {
            return count;
        }

        double getTotalTime() {
            return totalTime;
        }
    }
}