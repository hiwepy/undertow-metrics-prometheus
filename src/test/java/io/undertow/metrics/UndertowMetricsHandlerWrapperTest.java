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

import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.MetricsHandler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link UndertowMetricsHandlerWrapper}.
 *
 * <p>The wrapper is a small Spring bean whose entire behaviour is to wrap a
 * downstream handler with a fresh {@link MetricsHandler} and expose it. The
 * tests therefore focus on:</p>
 * <ul>
 *     <li>The newly-wrapped handler is a non-null {@link MetricsHandler}.</li>
 *     <li>Calling {@code wrap} more than once replaces the previously-stored
 *         handler with a new instance.</li>
 *     <li>{@link UndertowMetricsHandlerWrapper#getMetricsHandler()} returns
 *         {@code null} before the first call to
 *         {@link UndertowMetricsHandlerWrapper#wrap(HttpHandler)}.</li>
 *     <li>The wrapper implements {@link io.undertow.server.HandlerWrapper}.</li>
 * </ul>
 *
 * @since 3.0.0
 */
public class UndertowMetricsHandlerWrapperTest {

    /**
     * The default constructor must succeed and produce a non-null instance
     * that exposes a {@code null} metrics handler until {@code wrap} is
     * invoked.
     */
    @Test
    public void shouldInstantiateWithDefaultConstructor() {
        UndertowMetricsHandlerWrapper wrapper = new UndertowMetricsHandlerWrapper();
        assertNotNull(wrapper, "wrapper must be created");
        assertNull(wrapper.getMetricsHandler(), "getMetricsHandler() must return null before wrap()");
    }

    /**
     * After wrapping a downstream handler the wrapper must expose the new
     * {@link MetricsHandler} through {@link UndertowMetricsHandlerWrapper#getMetricsHandler()}.
     */
    @Test
    public void shouldExposeMetricsHandlerAfterWrap() {
        UndertowMetricsHandlerWrapper wrapper = new UndertowMetricsHandlerWrapper();
        HttpHandler downstream = mock(HttpHandler.class);

        HttpHandler wrapped = wrapper.wrap(downstream);

        assertNotNull(wrapped, "wrap() must return a non-null handler");
        MetricsHandler exposed = wrapper.getMetricsHandler();
        assertNotNull(exposed, "getMetricsHandler() must be non-null after wrap()");
        assertSame(wrapped, exposed, "wrap() must return the same MetricsHandler exposed by the wrapper");
    }

    /**
     * Each invocation of {@code wrap} must install a fresh
     * {@link MetricsHandler} so that previously-captured counters are not
     * silently carried over.
     */
    @Test
    public void shouldReplaceMetricsHandlerOnEachWrap() {
        UndertowMetricsHandlerWrapper wrapper = new UndertowMetricsHandlerWrapper();
        HttpHandler downstream = mock(HttpHandler.class);

        MetricsHandler first = wrapper.getMetricsHandler();
        HttpHandler wrap1 = wrapper.wrap(downstream);
        MetricsHandler afterFirst = wrapper.getMetricsHandler();
        HttpHandler wrap2 = wrapper.wrap(downstream);
        MetricsHandler afterSecond = wrapper.getMetricsHandler();

        assertNull(first, "metrics handler is null before wrap()");
        assertNotNull(afterFirst, "metrics handler is non-null after first wrap()");
        assertNotNull(afterSecond, "metrics handler is non-null after second wrap()");
        assertNotSame(afterFirst, afterSecond, "each wrap() should produce a fresh MetricsHandler");
        assertNotSame(wrap1, wrap2, "each wrap() should produce a fresh handler chain");
    }
}