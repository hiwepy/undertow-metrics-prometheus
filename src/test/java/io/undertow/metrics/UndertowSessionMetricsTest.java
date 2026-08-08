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
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.TimeGauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.undertow.server.session.SessionManager;
import io.undertow.server.session.SessionManagerStatistics;
import io.undertow.servlet.api.Deployment;
import io.undertow.servlet.api.DeploymentManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.embedded.undertow.UndertowServletWebServer;
import org.springframework.boot.web.embedded.undertow.UndertowWebServer;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link UndertowSessionMetrics}.
 *
 * <p>The session binder is the only family member that requires an
 * {@link UndertowServletWebServer} &mdash; non-servlet variants must not
 * produce any metrics. The tests cover all three constructor overloads, the
 * "non-servlet" short-circuit and the full session-metrics registration path
 * when a servlet web server is supplied.</p>
 *
 * @since 3.0.0
 */
public class UndertowSessionMetricsTest {

    /**
     * Default constructor must accept the web server without throwing.
     */
    @Test
    public void shouldInstantiateWithDefaultConstructor() {
        UndertowWebServer server = mock(UndertowWebServer.class);
        UndertowSessionMetrics binder = new UndertowSessionMetrics(server);

        assertNotNull(binder, "binder must be created");
    }

    /**
     * Two-argument constructor must accept a custom prefix.
     */
    @Test
    public void shouldInstantiateWithPrefix() {
        UndertowWebServer server = mock(UndertowWebServer.class);
        UndertowSessionMetrics binder = new UndertowSessionMetrics(server, "http");

        assertNotNull(binder, "binder must be created");
    }

    /**
     * Three-argument constructor must accept a prefix and a tag iterable.
     */
    @Test
    public void shouldInstantiateWithPrefixAndTags() {
        UndertowWebServer server = mock(UndertowWebServer.class);
        Iterable<Tag> tags = Tags.of("env", "test");
        UndertowSessionMetrics binder = new UndertowSessionMetrics(server, "http", tags);

        assertNotNull(binder, "binder must be created");
    }

    /**
     * When the supplied web server is not an {@link UndertowServletWebServer}
     * the binder must register no metrics at all.
     */
    @Test
    public void shouldNotRegisterAnythingForNonServletServer() {
        UndertowWebServer server = mock(UndertowWebServer.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        new UndertowSessionMetrics(server).bindTo(registry, server, "u", Collections.emptyList());

        assertEquals(0L, registry.getMeters().size());
    }

    /**
     * When the supplied web server is an {@link UndertowServletWebServer},
     * the binder must register the full set of session metrics.
     */
    @Test
    public void shouldRegisterAllSessionMetricsForServletServer() {
        SessionManagerStatistics statistics = mock(SessionManagerStatistics.class);
        when(statistics.getMaxActiveSessions()).thenReturn(10L);
        when(statistics.getActiveSessionCount()).thenReturn(5L);
        when(statistics.getCreatedSessionCount()).thenReturn(7L);
        when(statistics.getExpiredSessionCount()).thenReturn(2L);
        when(statistics.getRejectedSessions()).thenReturn(1L);
        when(statistics.getHighestSessionCount()).thenReturn(11L);

        SessionManager sessionManager = mock(SessionManager.class);
        when(sessionManager.getStatistics()).thenReturn(statistics);

        Deployment deployment = mock(Deployment.class);
        when(deployment.getSessionManager()).thenReturn(sessionManager);

        DeploymentManager manager = mock(DeploymentManager.class);
        when(manager.getDeployment()).thenReturn(deployment);

        UndertowServletWebServer server = mock(UndertowServletWebServer.class);
        when(server.getDeploymentManager()).thenReturn(manager);

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        new UndertowSessionMetrics(server, "u", Tags.of("application", "test"))
                .bindTo(registry, server, "u", Tags.of("application", "test"));

        Gauge maxActive = registry.find("u.sessions.active.max").tag("application", "test").gauge();
        assertNotNull(maxActive, "max active sessions gauge must be registered");
        assertEquals(10.0d, maxActive.value(), 0.0001d);

        Gauge activeCurrent = registry.find("u.sessions.active.current").tag("application", "test").gauge();
        assertNotNull(activeCurrent, "active sessions gauge must be registered");
        assertEquals(5.0d, activeCurrent.value(), 0.0001d);

        FunctionCounter created = registry.find("u.sessions.created").functionCounter();
        assertNotNull(created, "created sessions counter must be registered");
        assertEquals(7.0d, created.count(), 0.0001d);

        FunctionCounter expired = registry.find("u.sessions.expired").functionCounter();
        assertNotNull(expired, "expired sessions counter must be registered");
        assertEquals(2.0d, expired.count(), 0.0001d);

        FunctionCounter rejected = registry.find("u.sessions.rejected").functionCounter();
        assertNotNull(rejected, "rejected sessions counter must be registered");
        assertEquals(1.0d, rejected.count(), 0.0001d);

        TimeGauge aliveMax = registry.find("u.sessions.alive.max").timeGauge();
        assertNotNull(aliveMax, "alive.max time gauge must be registered");
        assertEquals(11.0d, aliveMax.value(TimeUnit.SECONDS), 0.0001d);
        assertEquals(TimeUnit.SECONDS, aliveMax.baseTimeUnit());
    }

    /**
     * The session binder's {@code bindTo(UndertowWebServer, ...)} overload
     * must be called once via the fan-out from the public
     * {@link io.micrometer.core.instrument.binder.MeterBinder#bindTo(MeterRegistry)}.
     */
    @Test
    public void shouldFanOutToWebServerOverload() {
        SessionManagerStatistics statistics = mock(SessionManagerStatistics.class);
        when(statistics.getMaxActiveSessions()).thenReturn(0L);
        when(statistics.getActiveSessionCount()).thenReturn(0L);
        when(statistics.getCreatedSessionCount()).thenReturn(0L);
        when(statistics.getExpiredSessionCount()).thenReturn(0L);
        when(statistics.getRejectedSessions()).thenReturn(0L);
        when(statistics.getHighestSessionCount()).thenReturn(0L);

        SessionManager sessionManager = mock(SessionManager.class);
        when(sessionManager.getStatistics()).thenReturn(statistics);

        Deployment deployment = mock(Deployment.class);
        when(deployment.getSessionManager()).thenReturn(sessionManager);

        DeploymentManager manager = mock(DeploymentManager.class);
        when(manager.getDeployment()).thenReturn(deployment);

        UndertowServletWebServer server = mock(UndertowServletWebServer.class);
        when(server.getDeploymentManager()).thenReturn(manager);

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        new UndertowSessionMetrics(server).bindTo(registry);

        // 6 meters registered (2 gauges + 3 counters + 1 time gauge).
        assertEquals(6L, registry.getMeters().size());
    }
}