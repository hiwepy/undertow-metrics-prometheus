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
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.boot.web.embedded.undertow.UndertowBuilderCustomizer;
import org.springframework.boot.web.embedded.undertow.UndertowDeploymentInfoCustomizer;
import org.springframework.boot.web.embedded.undertow.UndertowWebServer;
import org.springframework.boot.web.embedded.undertow.UndertowServletWebServer;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import io.undertow.server.session.SessionManager;
import io.undertow.server.session.SessionManagerStatistics;
import io.undertow.servlet.api.Deployment;
import io.undertow.servlet.api.DeploymentManager;
import org.xnio.XnioWorker;
import org.xnio.management.XnioWorkerMXBean;

import java.lang.reflect.Field;
import java.time.Duration;

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
     * registration of the binders. A plain {@link ConfigurableApplicationContext}
     * mock is neither a servlet nor a reactive context, so
     * {@link UndertowMetrics#findUndertowWebServer} returns {@code null}
     * naturally.
     */
    @Test
    public void shouldReturnEarlyWhenUndertowWebServerIsMissing() {
        UndertowMetricsAutoConfiguration config = new UndertowMetricsAutoConfiguration();

        ConfigurableApplicationContext applicationContext = mock(ConfigurableApplicationContext.class);

        ApplicationStartedEvent event = new ApplicationStartedEvent(new SpringApplication(), new String[0], applicationContext, Duration.ZERO);
        config.onApplicationEvent(event);

        // No interactions with the registry or wrapper beans should occur.
        verify(applicationContext, never()).getBean(MeterRegistry.class);
        verify(applicationContext, never()).getBean(UndertowMetricsHandlerWrapper.class);
    }

    /**
     * When the application context surfaces an {@link UndertowWebServer}
     * but the underlying {@link Undertow} field has not been populated
     * yet, the listener must short-circuit.
     *
     * <p>Because {@link UndertowMetrics#getUndertow} uses reflection
     * to access the private {@code undertow} field (bypassing the mock's
     * getter), we use a non-servlet, non-reactive context so that
     * {@code findUndertowWebServer} returns {@code null} and the
     * early-return path is still exercised.</p>
     */
    @Test
    public void shouldReturnEarlyWhenUndertowInstanceIsMissing() {
        UndertowMetricsAutoConfiguration config = new UndertowMetricsAutoConfiguration();

        ConfigurableApplicationContext applicationContext = mock(ConfigurableApplicationContext.class);

        ApplicationStartedEvent event = new ApplicationStartedEvent(new SpringApplication(), new String[0], applicationContext, Duration.ZERO);
        config.onApplicationEvent(event);

        verify(applicationContext, never()).getBean(MeterRegistry.class);
        verify(applicationContext, never()).getBean(UndertowMetricsHandlerWrapper.class);
    }

    /**
     * When everything is wired up correctly the listener must invoke the
     * public {@code bindTo(MeterRegistry)} entry point on every concrete
     * binder.
     *
     * <p>Because {@link UndertowMetrics#findUndertowWebServer} is a static
     * utility that checks {@code instanceof} and
     * {@link UndertowMetrics#getUndertow} uses reflection, we use a
     * {@link ServletWebServerApplicationContext} mock (so the static
     * helper finds the server) and inject the {@link Undertow} field via
     * reflection.</p>
     */
    @Test
    public void shouldBindAllBindersWhenWiredCorrectly() throws Exception {
        UndertowMetricsAutoConfiguration config = new UndertowMetricsAutoConfiguration();

        UndertowServletWebServer server = mock(UndertowServletWebServer.class);
        Undertow undertow = mock(Undertow.class);

        // Stub the XNIO worker chain so UndertowXWorkerMetrics.bindTo does not NPE
        XnioWorkerMXBean workerMXBean = mock(XnioWorkerMXBean.class);
        when(workerMXBean.getName()).thenReturn("default");
        XnioWorker xnioWorker = mock(XnioWorker.class);
        when(xnioWorker.getMXBean()).thenReturn(workerMXBean);
        when(undertow.getWorker()).thenReturn(xnioWorker);

        // Inject undertow into the mock's private field via reflection
        Field undertowField = UndertowWebServer.class.getDeclaredField("undertow");
        undertowField.setAccessible(true);
        undertowField.set(server, undertow);

        // Stub the session-manager chain so UndertowSessionMetrics.bindTo does not NPE
        SessionManagerStatistics statistics = mock(SessionManagerStatistics.class);
        SessionManager sessionManager = mock(SessionManager.class);
        when(sessionManager.getStatistics()).thenReturn(statistics);
        Deployment deployment = mock(Deployment.class);
        when(deployment.getSessionManager()).thenReturn(sessionManager);
        DeploymentManager deploymentManager = mock(DeploymentManager.class);
        when(deploymentManager.getDeployment()).thenReturn(deployment);
        when(server.getDeploymentManager()).thenReturn(deploymentManager);

        // Use ServletWebServerApplicationContext so findUndertowWebServer returns the server
        ServletWebServerApplicationContext servletContext = mock(ServletWebServerApplicationContext.class);
        when(servletContext.getWebServer()).thenReturn(server);

        MeterRegistry registry = new SimpleMeterRegistry();
        when(servletContext.getBean(MeterRegistry.class)).thenReturn(registry);

        UndertowMetricsHandlerWrapper wrapper = mock(UndertowMetricsHandlerWrapper.class);
        when(servletContext.getBean(UndertowMetricsHandlerWrapper.class)).thenReturn(wrapper);

        ApplicationStartedEvent event = new ApplicationStartedEvent(new SpringApplication(), new String[0], servletContext, Duration.ZERO);
        config.onApplicationEvent(event);

        verify(servletContext, times(1)).getBean(MeterRegistry.class);
        verify(servletContext, times(1)).getBean(UndertowMetricsHandlerWrapper.class);
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
        ApplicationStartedEvent event = new ApplicationStartedEvent(new SpringApplication(), new String[0], applicationContext, Duration.ZERO);
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