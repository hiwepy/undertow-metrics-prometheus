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
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.undertow.Undertow;
import io.undertow.server.ConnectorStatistics;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.embedded.undertow.UndertowWebServer;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link UndertowConnectorMetrics}.
 *
 * <p>The tests cover the three constructor overloads and the actual
 * {@code bindTo} contract: every listener declared on the underlying
 * {@link Undertow} instance produces the full set of gauges (count, error
 * count, active requests, max active requests, bytes sent, bytes received,
 * processing time, max processing time, active connections, max active
 * connections) tagged with the listener protocol and any user-supplied tags.</p>
 *
 * @since 3.0.0
 */
public class UndertowConnectorMetricsTest {

    /**
     * Default constructor must accept the web server without throwing and
     * use the default metric-name prefix.
     */
    @Test
    public void shouldInstantiateWithDefaultConstructor() {
        UndertowWebServer server = mock(UndertowWebServer.class);
        UndertowConnectorMetrics binder = new UndertowConnectorMetrics(server);

        assertNotNull(binder, "binder must be created");
    }

    /**
     * Two-argument constructor must accept a custom prefix.
     */
    @Test
    public void shouldInstantiateWithPrefix() {
        UndertowWebServer server = mock(UndertowWebServer.class);
        UndertowConnectorMetrics binder = new UndertowConnectorMetrics(server, "http");

        assertNotNull(binder, "binder must be created");
    }

    /**
     * Three-argument constructor must accept a prefix and a tag iterable.
     */
    @Test
    public void shouldInstantiateWithPrefixAndTags() {
        UndertowWebServer server = mock(UndertowWebServer.class);
        Iterable<Tag> tags = Tags.of("env", "test");
        UndertowConnectorMetrics binder = new UndertowConnectorMetrics(server, "http", tags);

        assertNotNull(binder, "binder must be created");
    }

    /**
     * {@code bindTo} must register the full set of connector gauges for
     * every listener declared by the underlying {@link Undertow} instance.
     * Each gauge must carry the listener's protocol as a tag and any
     * user-supplied tags.
     */
    @Test
    public void shouldRegisterAllConnectorGaugesForEveryListener() {
        ConnectorStatistics stats = mock(ConnectorStatistics.class);
        when(stats.getRequestCount()).thenReturn(10L);
        when(stats.getErrorCount()).thenReturn(1L);
        when(stats.getActiveRequests()).thenReturn(2L);
        when(stats.getMaxActiveRequests()).thenReturn(5L);
        when(stats.getBytesSent()).thenReturn(100L);
        when(stats.getBytesReceived()).thenReturn(200L);
        when(stats.getProcessingTime()).thenReturn(300_000_000L);
        when(stats.getMaxProcessingTime()).thenReturn(400_000_000L);
        when(stats.getActiveConnections()).thenReturn(3L);
        when(stats.getMaxActiveConnections()).thenReturn(8L);

        Undertow.ListenerInfo listener = mock(Undertow.ListenerInfo.class);
        when(listener.getProtcol()).thenReturn("HTTP/1.1");
        when(listener.getConnectorStatistics()).thenReturn(stats);

        Undertow undertow = mock(Undertow.class);
        when(undertow.getListenerInfo()).thenReturn(Collections.singletonList(listener));

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        UndertowConnectorMetrics binder = new UndertowConnectorMetrics(mock(UndertowWebServer.class), "http",
                Tags.of("application", "test"));

        binder.bindTo(registry, undertow, "http", Tags.of("application", "test"));

        // 10 gauges per listener (requests.count, requests.error.count, requests.active,
        // requests.active.max, bytes.sent, bytes.received, processing.time,
        // processing.time.max, connections.active, connections.active.max).
        long gauges = registry.getMeters().stream()
                .filter(m -> m instanceof Gauge)
                .count();
        assertEquals(10L, gauges);

        Gauge requestsCount = registry.find("http.connectors.requests.count")
                .tag("protocol", "HTTP/1.1")
                .tag("application", "test")
                .gauge();
        assertNotNull(requestsCount, "requests.count gauge must be registered");
        assertEquals(10.0d, requestsCount.value(), 0.0001d);

        Gauge activeConnections = registry.find("http.connectors.connections.active")
                .tag("protocol", "HTTP/1.1")
                .tag("application", "test")
                .gauge();
        assertNotNull(activeConnections, "connections.active gauge must be registered");
        assertEquals(3.0d, activeConnections.value(), 0.0001d);

        Gauge processingTime = registry.find("http.connectors.processing.time")
                .tag("protocol", "HTTP/1.1")
                .gauge();
        assertNotNull(processingTime, "processing.time gauge must be registered");
        // 300,000,000 ns -> 300 ms
        assertEquals(300.0d, processingTime.value(), 0.0001d);

        Gauge maxProcessingTime = registry.find("http.connectors.processing.time.max")
                .tag("protocol", "HTTP/1.1")
                .gauge();
        assertNotNull(maxProcessingTime, "processing.time.max gauge must be registered");
        // 400,000,000 ns -> 400 ms
        assertEquals(400.0d, maxProcessingTime.value(), 0.0001d);
    }

    /**
     * When the Undertow instance reports multiple listeners, every listener
     * must produce its own complete set of gauges.
     */
    @Test
    public void shouldIterateOverEveryListener() {
        ConnectorStatistics stats1 = mock(ConnectorStatistics.class);
        ConnectorStatistics stats2 = mock(ConnectorStatistics.class);
        when(stats1.getRequestCount()).thenReturn(1L);
        when(stats2.getRequestCount()).thenReturn(2L);

        Undertow.ListenerInfo listener1 = mock(Undertow.ListenerInfo.class);
        when(listener1.getProtcol()).thenReturn("HTTP/1.1");
        when(listener1.getConnectorStatistics()).thenReturn(stats1);

        Undertow.ListenerInfo listener2 = mock(Undertow.ListenerInfo.class);
        when(listener2.getProtcol()).thenReturn("HTTPS/2.0");
        when(listener2.getConnectorStatistics()).thenReturn(stats2);

        Undertow undertow = mock(Undertow.class);
        List<Undertow.ListenerInfo> listeners = Arrays.asList(listener1, listener2);
        when(undertow.getListenerInfo()).thenReturn(listeners);

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        new UndertowConnectorMetrics(mock(UndertowWebServer.class)).bindTo(registry, undertow, "undertow",
                Collections.emptyList());

        assertEquals(20L, registry.getMeters().stream().filter(m -> m instanceof Gauge).count());
        assertEquals(1.0d,
                registry.find("undertow.connectors.requests.count").tag("protocol", "HTTP/1.1").gauge().value(),
                0.0001d);
        assertEquals(2.0d,
                registry.find("undertow.connectors.requests.count").tag("protocol", "HTTPS/2.0").gauge().value(),
                0.0001d);
    }

    /**
     * When no listeners are reported, no gauges must be registered.
     */
    @Test
    public void shouldNotRegisterAnyGaugeWhenNoListeners() {
        Undertow undertow = mock(Undertow.class);
        when(undertow.getListenerInfo()).thenReturn(Collections.emptyList());

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        new UndertowConnectorMetrics(mock(UndertowWebServer.class))
                .bindTo(registry, undertow, "undertow", Collections.emptyList());

        assertEquals(0L, registry.getMeters().size());
    }
}