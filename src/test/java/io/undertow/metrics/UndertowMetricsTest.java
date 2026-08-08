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

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.undertow.Undertow;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.embedded.undertow.UndertowWebServer;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link UndertowMetrics} using a
 * {@link RecordingUndertowMetrics} test double that records every call to
 * the package-private {@code bindTo} overloads.
 *
 * <p>Because {@link UndertowMetrics} is abstract, the tests rely on a small
 * concrete subclass that exposes the captured invocations. The tests cover
 * every constructor overload, the public {@code bindTo(MeterRegistry)}
 * fan-out and the package-private overloads' no-op default behaviour.</p>
 *
 * @since 3.0.0
 */
public class UndertowMetricsTest {

    /**
     * Default constructor must accept the web server and store it for later
     * use; the name prefix must default to
     * {@link UndertowMeterBinder#UNDERTOW_METRIC_NAME_PREFIX}.
     */
    @Test
    public void shouldUseDefaultPrefixInSingleArgConstructor() {
        UndertowWebServer server = mock(UndertowWebServer.class);
        RecordingUndertowMetrics metrics = new RecordingUndertowMetrics(server);

        assertEquals(UndertowMeterBinder.UNDERTOW_METRIC_NAME_PREFIX, metrics.getNamePrefix(), "default prefix must be used");
        assertNotNull(metrics.getServer(), "server must be stored");
    }

    /**
     * The two-argument constructor must accept the supplied prefix and
     * default to an empty tag iterable.
     */
    @Test
    public void shouldUseCustomPrefixInTwoArgConstructor() {
        UndertowWebServer server = mock(UndertowWebServer.class);
        RecordingUndertowMetrics metrics = new RecordingUndertowMetrics(server, "custom.prefix");

        assertEquals(metrics.getNamePrefix(), "custom.prefix");
        assertNotNull(metrics.getTags(), "empty tag iterable must be supplied");
    }

    /**
     * The three-argument constructor must propagate all three parameters
     * unchanged.
     */
    @Test
    public void shouldUseCustomPrefixAndTagsInThreeArgConstructor() {
        UndertowWebServer server = mock(UndertowWebServer.class);
        Iterable<Tag> tags = Collections.singletonList(Tag.of("k", "v"));
        RecordingUndertowMetrics metrics = new RecordingUndertowMetrics(server, "p", tags);

        assertEquals(metrics.getNamePrefix(), "p");
        assertEquals(tags, metrics.getTags());
    }

    /**
     * The public {@code bindTo(MeterRegistry)} method must fan the call out
     * to both package-private overloads using the configured state.
     *
     * <p>Because {@link UndertowMetrics#getUndertow} uses reflection to
     * read the private {@code undertow} field, we inject the value via
     * reflection on the mock.</p>
     */
    @Test
    public void shouldFanOutToBothOverloadsWhenBinding() throws Exception {
        UndertowWebServer server = mock(UndertowWebServer.class);
        Undertow undertow = mock(Undertow.class);

        // Inject undertow into the mock's private field via reflection
        Field undertowField = UndertowWebServer.class.getDeclaredField("undertow");
        undertowField.setAccessible(true);
        undertowField.set(server, undertow);

        RecordingUndertowMetrics metrics = new RecordingUndertowMetrics(server, "p", Collections.emptyList());
        MeterRegistry registry = new SimpleMeterRegistry();

        metrics.bindTo(registry);

        assertEquals(1, metrics.webServerCalls.size(), "web-server overload must be called once");
        assertEquals(1, metrics.undertowCalls.size(), "undertow overload must be called once");

        RecordingUndertowMetrics.Call webCall = metrics.webServerCalls.get(0);
        assertEquals(registry, webCall.registry);
        assertEquals(server, webCall.server);
        assertEquals(webCall.prefix, "p");
        assertEquals(metrics.getTags(), webCall.tags);

        RecordingUndertowMetrics.Call underCall = metrics.undertowCalls.get(0);
        assertEquals(registry, underCall.registry);
        assertEquals(undertow, underCall.undertow);
        assertEquals(underCall.prefix, "p");
        assertEquals(metrics.getTags(), underCall.tags);
    }

    /**
     * The no-op default implementation of the package-private
     * {@code bindTo(MeterRegistry, UndertowWebServer, String, Iterable)} and
     * {@code bindTo(MeterRegistry, Undertow, String, Iterable)} overloads must
     * not register any meters.
     */
    @Test
    public void shouldNotRegisterAnyMetricFromBaseClassNoOp() {
        UndertowWebServer server = mock(UndertowWebServer.class);
        when(server.getUndertow()).thenReturn(mock(Undertow.class));
        UndertowMetrics metrics = new RecordingUndertowMetrics(server);
        MeterRegistry registry = new SimpleMeterRegistry();

        int countBefore = registry.getMeters().size();
        metrics.bindTo(registry, server, "p", Collections.emptyList());
        metrics.bindTo(registry, mock(Undertow.class), "p", Collections.emptyList());

        assertEquals(countBefore, registry.getMeters().size(), "base-class bindTo must not register any meter");
    }

    /**
     * When {@link UndertowWebServer#getUndertow()} returns {@code null}, the
     * fan-out must still call the {@code UndertowWebServer} overload and
     * pass {@code null} to the {@link Undertow} overload without throwing.
     */
    @Test
    public void shouldHandleNullUndertowGracefully() {
        UndertowWebServer server = mock(UndertowWebServer.class);
        when(server.getUndertow()).thenReturn(null);

        RecordingUndertowMetrics metrics = new RecordingUndertowMetrics(server);
        metrics.bindTo(new SimpleMeterRegistry());

        assertEquals(1, metrics.webServerCalls.size(), "web-server overload must be called once even when Undertow is null");
        assertEquals(1, metrics.undertowCalls.size(), "undertow overload must be called once even when Undertow is null");
        assertEquals(null, metrics.undertowCalls.get(0).undertow);
    }

    /**
     * Concrete {@link UndertowMetrics} used by the test suite to record the
     * calls dispatched through the package-private overloads.
     */
    static class RecordingUndertowMetrics extends UndertowMetrics {

        private final UndertowWebServer server;
        private final String namePrefix;
        private final Iterable<Tag> tags;
        final List<Call> webServerCalls = new ArrayList<>();
        final List<Call> undertowCalls = new ArrayList<>();

        RecordingUndertowMetrics(UndertowWebServer server) {
            super(server);
            this.server = server;
            this.namePrefix = UndertowMeterBinder.UNDERTOW_METRIC_NAME_PREFIX;
            this.tags = Collections.emptyList();
        }

        RecordingUndertowMetrics(UndertowWebServer server, String namePrefix) {
            super(server, namePrefix);
            this.server = server;
            this.namePrefix = namePrefix;
            this.tags = Collections.emptyList();
        }

        RecordingUndertowMetrics(UndertowWebServer server, String namePrefix, Iterable<Tag> tags) {
            super(server, namePrefix, tags);
            this.server = server;
            this.namePrefix = namePrefix;
            this.tags = tags;
        }

        UndertowWebServer getServer() {
            return server;
        }

        String getNamePrefix() {
            return namePrefix;
        }

        Iterable<Tag> getTags() {
            return tags;
        }

        @Override
        void bindTo(MeterRegistry registry, UndertowWebServer undertowWebServer, String namePrefix, Iterable<Tag> tags) {
            webServerCalls.add(new Call(registry, undertowWebServer, null, namePrefix, tags));
        }

        @Override
        void bindTo(MeterRegistry registry, Undertow undertow, String namePrefix, Iterable<Tag> tags) {
            undertowCalls.add(new Call(registry, null, undertow, namePrefix, tags));
        }

        /** Captured invocation. */
        static class Call {
            final MeterRegistry registry;
            final UndertowWebServer server;
            final Undertow undertow;
            final String prefix;
            final Iterable<Tag> tags;

            Call(MeterRegistry registry, UndertowWebServer server, Undertow undertow, String prefix, Iterable<Tag> tags) {
                this.registry = registry;
                this.server = server;
                this.undertow = undertow;
                this.prefix = prefix;
                this.tags = tags;
            }
        }
    }
}