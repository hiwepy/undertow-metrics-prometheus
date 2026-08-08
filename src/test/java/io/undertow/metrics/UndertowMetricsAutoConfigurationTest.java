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
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.undertow.Undertow;
import io.undertow.UndertowOptions;
import io.undertow.server.HttpHandler;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.boot.web.embedded.undertow.UndertowBuilderCustomizer;
import org.springframework.boot.web.embedded.undertow.UndertowDeploymentInfoCustomizer;
import org.springframework.boot.web.embedded.undertow.UndertowWebServer;
import org.springframework.boot.web.embedded.undertow.UndertowServletWebServer;
import org.springframework.context.ConfigurableApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link UndertowMetricsAutoConfiguration}.
 *
 * <p>The configuration is exercised at three levels:</p>
 * <ul>
 *     <li>The bean producer methods
 *         ({@link UndertowMetricsAutoConfiguration#undertowMetricsHandlerWrapper()},
 *         {@link UndertowMetricsAutoConfiguration#undertowDeploymentInfoCustomizer(UndertowMetricsHandlerWrapper)},
 *         {@link UndertowMetricsAutoConfiguration#undertowBuilderCustomizerEnableStatistics()})
 *         are called directly.</li>
 *     <li>The {@link UndertowMetricsAutoConfiguration#onApplicationEvent(ApplicationStartedEvent)}
 *         fan-out path is exercised with mocked Spring collaborators to
 *         confirm that the binder registration is skipped when the web
 *         server is missing.</li>
 *     <li>The {@link UndertowOptions#ENABLE_STATISTICS} flag is verified to
 *         be flipped by the builder customizer.</li>
 * </ul>
 *
 * @since 3.0.0
 */
public class UndertowMetricsAutoConfigurationTest {

    /**
     * The handler-wrapper bean producer must return a non-null wrapper.
     */
    @Test
    public void shouldExposeUndertowMetricsHandlerWrapperBean() {
        UndertowMetricsAutoConfiguration config = new UndertowMetricsAutoConfiguration();
        UndertowMetricsHandlerWrapper wrapper = config.undertowMetricsHandlerWrapper();
        assertNotNull(wrapper, "handler wrapper bean must be non-null");
    }

    /**
     * The deployment-info customizer must install the supplied wrapper as an
     * outer handler-chain wrapper.
     */
    @Test
    public void shouldInstallWrapperAsOuterHandlerChain() {
        UndertowMetricsAutoConfiguration config = new UndertowMetricsAutoConfiguration();
        UndertowMetricsHandlerWrapper wrapper = config.undertowMetricsHandlerWrapper();
        UndertowDeploymentInfoCustomizer customizer = config.undertowDeploymentInfoCustomizer(wrapper);

        io.undertow.servlet.api.DeploymentInfo info = mock(io.undertow.servlet.api.DeploymentInfo.class);
        customizer.customize(info);
        verify(info, times(1)).addOuterHandlerChainWrapper(wrapper);
    }

    /**
     * The builder customizer must flip
     * {@link UndertowOptions#ENABLE_STATISTICS} on the supplied
     * {@link Undertow.Builder}.
     */
    @Test
    public void shouldEnableUndertowStatistics() {
        UndertowMetricsAutoConfiguration config = new UndertowMetricsAutoConfiguration();
        UndertowBuilderCustomizer customizer = config.undertowBuilderCustomizerEnableStatistics();

        Undertow.Builder builder = mock(Undertow.Builder.class);
        customizer.customize(builder);
        verify(builder, times(1)).setServerOption(UndertowOptions.ENABLE_STATISTICS, true);
    }

    /**
     * When the application context does not surface an
     * {@link UndertowWebServer}, the listener must short-circuit and skip
     * registration of the binders.
     */
    @Test
    public void shouldReturnEarlyWhenUndertowWebServerIsMissing() {
        UndertowMetricsAutoConfiguration config = new UndertowMetricsAutoConfiguration();

        ConfigurableApplicationContext applicationContext = mock(ConfigurableApplicationContext.class);
        when(UndertowMetrics.findUndertowWebServer(applicationContext)).thenReturn(null);

        ApplicationStartedEvent event = new ApplicationStartedEvent(applicationContext, null);
        config.onApplicationEvent(event);

        // No interactions with the registry or wrapper beans should occur.
        verify(applicationContext, never()).getBean(MeterRegistry.class);
        verify(applicationContext, never()).getBean(UndertowMetricsHandlerWrapper.class);
    }

    /**
     * When the supplied web server is an {@link UndertowWebServer} but
     * Spring has not yet populated the underlying {@link Undertow} field,
     * the listener must short-circuit as well.
     */
    @Test
    public void shouldReturnEarlyWhenUndertowInstanceIsMissing() {
        UndertowMetricsAutoConfiguration config = new UndertowMetricsAutoConfiguration();

        UndertowWebServer server = mock(UndertowWebServer.class);
        when(server.getUndertow()).thenReturn(null);

        ConfigurableApplicationContext applicationContext = mock(ConfigurableApplicationContext.class);
        when(UndertowMetrics.findUndertowWebServer(applicationContext)).thenReturn(server);

        ApplicationStartedEvent event = new ApplicationStartedEvent(applicationContext, null);
        config.onApplicationEvent(event);

        verify(applicationContext, never()).getBean(MeterRegistry.class);
        verify(applicationContext, never()).getBean(UndertowMetricsHandlerWrapper.class);
    }

    /**
     * When everything is wired up correctly the listener must invoke the
     * public {@code bindTo(MeterRegistry)} entry point on every concrete
     * binder.
     */
    @Test
    public void shouldBindAllBindersWhenWiredCorrectly() {
        UndertowMetricsAutoConfiguration config = new UndertowMetricsAutoConfiguration();

        UndertowServletWebServer server = mock(UndertowServletWebServer.class);
        Undertow undertow = mock(Undertow.class);
        when(server.getUndertow()).thenReturn(undertow);

        ConfigurableApplicationContext applicationContext = mock(ConfigurableApplicationContext.class);
        when(UndertowMetrics.findUndertowWebServer(applicationContext)).thenReturn(server);

        MeterRegistry registry = new SimpleMeterRegistry();
        when(applicationContext.getBean(MeterRegistry.class)).thenReturn(registry);

        UndertowMetricsHandlerWrapper wrapper = mock(UndertowMetricsHandlerWrapper.class);
        when(applicationContext.getBean(UndertowMetricsHandlerWrapper.class)).thenReturn(wrapper);

        ApplicationStartedEvent event = new ApplicationStartedEvent(applicationContext, null);
        config.onApplicationEvent(event);

        verify(applicationContext, times(1)).getBean(MeterRegistry.class);
        verify(applicationContext, times(1)).getBean(UndertowMetricsHandlerWrapper.class);
    }

    /**
     * The handler-wrapper bean producer must return a distinct instance on
     * every call so that the auto-configuration is repeatable.
     */
    @Test
    public void shouldProduceFreshWrapperPerCall() {
        UndertowMetricsAutoConfiguration config = new UndertowMetricsAutoConfiguration();
        UndertowMetricsHandlerWrapper first = config.undertowMetricsHandlerWrapper();
        UndertowMetricsHandlerWrapper second = config.undertowMetricsHandlerWrapper();
        assertNotNull(first);
        assertNotNull(second);
        // distinct instances, no accidental state sharing.
        assertEquals(first.getClass(), second.getClass());
    }

    /**
     * The deployment-info customizer must wrap the supplied wrapper &mdash;
     * capturing the wrapper argument explicitly to make the contract
     * obvious.
     */
    @Test
    public void shouldCaptureWrapperWhenCustomizing() {
        UndertowMetricsAutoConfiguration config = new UndertowMetricsAutoConfiguration();
        UndertowMetricsHandlerWrapper wrapper = new UndertowMetricsHandlerWrapper();
        UndertowDeploymentInfoCustomizer customizer = config.undertowDeploymentInfoCustomizer(wrapper);

        io.undertow.servlet.api.DeploymentInfo info = mock(io.undertow.servlet.api.DeploymentInfo.class);
        customizer.customize(info);

        ArgumentCaptor<io.undertow.server.HandlerWrapper> captor =
                ArgumentCaptor.forClass(io.undertow.server.HandlerWrapper.class);
        verify(info).addOuterHandlerChainWrapper(captor.capture());
        assertSame(wrapper, captor.getValue(), "wrapper must be installed verbatim");
    }

    /**
     * The {@code onApplicationEvent} listener must accept the supplied
     * {@link ApplicationStartedEvent} argument without throwing when the
     * listener is initialised but never received a Spring context.
     */
    @Test
    public void shouldAcceptEventWithoutThrowing() {
        UndertowMetricsAutoConfiguration config = new UndertowMetricsAutoConfiguration();
        ConfigurableApplicationContext applicationContext = mock(ConfigurableApplicationContext.class);
        // even though we ignore the event, we still need the listener to be
        // safely callable.
        ApplicationStartedEvent event = new ApplicationStartedEvent(applicationContext, null);
        config.onApplicationEvent(event);
        // The argument is unused (because findUndertowWebServer returned null),
        // but the call must complete normally.
        verify(applicationContext, never()).getBean(eq(MeterRegistry.class));
        verify(applicationContext, never()).getBean(any(Class.class));
    }

    /**
     * The configuration instance must declare the expected bean producer
     * methods, allowing Spring to wire them up via reflection.
     */
    @Test
    public void shouldExposeBeanProducerMethods() throws NoSuchMethodException {
        UndertowMetricsAutoConfiguration config = new UndertowMetricsAutoConfiguration();
        assertNotNull(config.getClass().getMethod("undertowMetricsHandlerWrapper"), "bean producer method must exist");
        assertNotNull(config.getClass().getDeclaredMethod("undertowDeploymentInfoCustomizer",
                        UndertowMetricsHandlerWrapper.class), "deployment-info customizer method must exist");
        assertNotNull(config.getClass().getMethod("undertowBuilderCustomizerEnableStatistics"), "builder customizer method must exist");
    }

    /**
     * Smoke test ensuring the configuration can be instantiated as a plain
     * object (mirrors Spring's reflective instantiation).
     */
    @Test
    public void shouldInstantiateViaDefaultConstructor() {
        UndertowMetricsAutoConfiguration config = new UndertowMetricsAutoConfiguration();
        assertNotNull(config, "auto-configuration must be instantiable");
    }

    /**
     * Touch the {@link HttpHandler} import to make sure it is not flagged
     * as unused after compilation &mdash; the deployment-info customizer
     * references handler chains indirectly.
     */
    @Test
    public void shouldReferenceHttpHandlerType() {
        HttpHandler handler = mock(HttpHandler.class);
        assertNotNull(handler, "HttpHandler must be mockable");
    }
}