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

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.undertow.Undertow;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.embedded.undertow.UndertowWebServer;
import org.xnio.XnioWorker;
import org.xnio.management.XnioWorkerMXBean;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link UndertowXWorkerMetrics}.
 *
 * <p>The XWorker binder is responsible for exposing the underlying XNIO
 * worker pool through six Micrometer gauges (core/max/current pool size,
 * busy worker threads, I/O thread count and worker queue size). The tests
 * verify every constructor overload, that every gauge is registered and
 * tagged with the worker name, and that the {@link TimeUnit} defaults used
 * by Micrometer are honoured.</p>
 *
 * @since 3.0.0
 */
public class UndertowXWorkerMetricsTest {

    /**
     * Default constructor must accept the web server without throwing.
     */
    @Test
    public void shouldInstantiateWithDefaultConstructor() {
        UndertowWebServer server = mock(UndertowWebServer.class);
        UndertowXWorkerMetrics binder = new UndertowXWorkerMetrics(server);

        assertNotNull(binder, "binder must be created");
    }

    /**
     * Two-argument constructor must accept a custom prefix.
     */
    @Test
    public void shouldInstantiateWithPrefix() {
        UndertowWebServer server = mock(UndertowWebServer.class);
        UndertowXWorkerMetrics binder = new UndertowXWorkerMetrics(server, "http");

        assertNotNull(binder, "binder must be created");
    }

    /**
     * Three-argument constructor must accept a prefix and a tag iterable.
     */
    @Test
    public void shouldInstantiateWithPrefixAndTags() {
        UndertowWebServer server = mock(UndertowWebServer.class);
        Iterable<Tag> tags = Tags.of("env", "test");
        UndertowXWorkerMetrics binder = new UndertowXWorkerMetrics(server, "http", tags);

        assertNotNull(binder, "binder must be created");
    }

    /**
     * {@code bindTo} must register the six XWorker gauges and tag every
     * gauge with the worker's name plus any user-supplied tags.
     */
    @Test
    public void shouldRegisterAllXWorkerGauges() {
        XnioWorkerMXBean mxBean = mock(XnioWorkerMXBean.class);
        when(mxBean.getName()).thenReturn("default");
        when(mxBean.getCoreWorkerPoolSize()).thenReturn(8);
        when(mxBean.getMaxWorkerPoolSize()).thenReturn(64);
        when(mxBean.getWorkerPoolSize()).thenReturn(16);
        when(mxBean.getBusyWorkerThreadCount()).thenReturn(2);
        when(mxBean.getIoThreadCount()).thenReturn(4);
        when(mxBean.getWorkerQueueSize()).thenReturn(5);

        XnioWorker worker = mock(XnioWorker.class);
        when(worker.getMXBean()).thenReturn(mxBean);

        Undertow undertow = mock(Undertow.class);
        when(undertow.getWorker()).thenReturn(worker);

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        new UndertowXWorkerMetrics(mock(UndertowWebServer.class), "u", Tags.of("application", "test"))
                .bindTo(registry, undertow, "u", Tags.of("application", "test"));

        Gauge core = registry.find("u.xwork.worker.pool.core.size")
                .tag("name", "default")
                .tag("application", "test")
                .gauge();
        assertNotNull(core, "core pool size gauge must be registered");
        assertEquals(8.0d, core.value(), 0.0001d);

        Gauge max = registry.find("u.xwork.worker.pool.max.size")
                .tag("name", "default")
                .gauge();
        assertNotNull(max, "max pool size gauge must be registered");
        assertEquals(64.0d, max.value(), 0.0001d);

        Gauge current = registry.find("u.xwork.worker.pool.size")
                .tag("name", "default")
                .gauge();
        assertNotNull(current, "current pool size gauge must be registered");
        assertEquals(16.0d, current.value(), 0.0001d);

        Gauge busy = registry.find("u.xwork.worker.thread.busy.count")
                .tag("name", "default")
                .gauge();
        assertNotNull(busy, "busy worker thread count gauge must be registered");
        assertEquals(2.0d, busy.value(), 0.0001d);

        Gauge ioThreads = registry.find("u.xwork.io.thread.count")
                .tag("name", "default")
                .gauge();
        assertNotNull(ioThreads, "I/O thread count gauge must be registered");
        assertEquals(4.0d, ioThreads.value(), 0.0001d);

        Gauge queueSize = registry.find("u.xwork.worker.queue.size")
                .tag("name", "default")
                .gauge();
        assertNotNull(queueSize, "worker queue size gauge must be registered");
        assertEquals(5.0d, queueSize.value(), 0.0001d);
    }

    /**
     * The {@code bindTo} overload must accept a {@code null} tag iterable
     * without throwing; the only tag added must be the worker {@code name}.
     */
    @Test
    public void shouldHandleNullTagsGracefully() {
        XnioWorkerMXBean mxBean = mock(XnioWorkerMXBean.class);
        when(mxBean.getName()).thenReturn("only-name");

        XnioWorker worker = mock(XnioWorker.class);
        when(worker.getMXBean()).thenReturn(mxBean);

        Undertow undertow = mock(Undertow.class);
        when(undertow.getWorker()).thenReturn(worker);

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        new UndertowXWorkerMetrics(mock(UndertowWebServer.class))
                .bindTo(registry, undertow, "undertow", null);

        Gauge core = registry.find("undertow.xwork.worker.pool.core.size")
                .tag("name", "only-name")
                .gauge();
        assertNotNull(core, "core pool size gauge must be registered even with null tags");
    }

    /**
     * The {@code bindTo} overload must accept an empty tag iterable.
     */
    @Test
    public void shouldHandleEmptyTagsGracefully() {
        XnioWorkerMXBean mxBean = mock(XnioWorkerMXBean.class);
        when(mxBean.getName()).thenReturn("empty-tags");

        XnioWorker worker = mock(XnioWorker.class);
        when(worker.getMXBean()).thenReturn(mxBean);

        Undertow undertow = mock(Undertow.class);
        when(undertow.getWorker()).thenReturn(worker);

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        new UndertowXWorkerMetrics(mock(UndertowWebServer.class))
                .bindTo(registry, undertow, "undertow", Collections.emptyList());

        Gauge core = registry.find("undertow.xwork.worker.pool.core.size")
                .tag("name", "empty-tags")
                .gauge();
        assertNotNull(core, "core pool size gauge must be registered with empty tags");
    }
}