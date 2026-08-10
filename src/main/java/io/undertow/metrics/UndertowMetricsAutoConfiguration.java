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
import io.undertow.Undertow;
import io.undertow.UndertowOptions;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.export.simple.SimpleMetricsExportAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.boot.web.embedded.undertow.UndertowBuilderCustomizer;
import org.springframework.boot.web.embedded.undertow.UndertowDeploymentInfoCustomizer;
import org.springframework.boot.web.embedded.undertow.UndertowWebServer;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot auto-configuration that wires every Undertow-specific Micrometer
 * binder into a running application and ensures that Undertow's built-in
 * statistics collector is enabled.
 *
 * <p>The configuration is only activated when {@link Undertow} is on the
 * classpath ({@link ConditionalOnClass}). Once triggered, it exposes three
 * Spring beans &mdash; an {@link UndertowMetricsHandlerWrapper}, a
 * {@link UndertowDeploymentInfoCustomizer} that installs the wrapper as an
 * outer handler chain, and an {@link UndertowBuilderCustomizer} that
 * flips {@link UndertowOptions#ENABLE_STATISTICS} on the server.</p>
 *
 * <p>Once the application has started, the {@link ApplicationListener}
 * implementation in this class resolves the running {@link UndertowWebServer}
 * and {@link MeterRegistry} and registers a fresh instance of every
 * concrete binder against them:</p>
 * <ul>
 *     <li>{@link UndertowConnectorMetrics}</li>
 *     <li>{@link UndertowSessionMetrics}</li>
 *     <li>{@link UndertowXWorkerMetrics}</li>
 *     <li>{@link UndertowRequestMetrics}</li>
 * </ul>
 *
 * <p>The class is annotated with both {@link AutoConfigureAfter} (to depend
 * on the standard Micrometer auto-configurations) and
 * {@link AutoConfigureBefore} (so it runs before Spring's servlet web-server
 * factory auto-configuration) so that the handler-chain wrapper is installed
 * before any web server is built.</p>
 *
 * @author <a href="https://github.com/loong10k">Loong Wan</a>
 * @since 3.0.0
 * @see UndertowMetricsHandlerWrapper
 * @see UndertowConnectorMetrics
 * @see UndertowSessionMetrics
 * @see UndertowXWorkerMetrics
 * @see UndertowRequestMetrics
 */
@AutoConfigureAfter(value =  {MetricsAutoConfiguration.class, SimpleMetricsExportAutoConfiguration.class})
@AutoConfigureBefore(value = {ServletWebServerFactoryAutoConfiguration.class})
@ConditionalOnClass(Undertow.class)
// @ImportRuntimeHints(UndertowRuntimeHintsRegistrar.class)
public class UndertowMetricsAutoConfiguration implements ApplicationListener<ApplicationStartedEvent> {


	/**
	 * Expose the singleton {@link UndertowMetricsHandlerWrapper} bean that
	 * both the deployment customizer and {@link UndertowRequestMetrics}
	 * depend on.
	 *
	 * @return a freshly constructed wrapper; never {@code null}.
	 */
	@Bean
	public UndertowMetricsHandlerWrapper undertowMetricsHandlerWrapper() {
		return new UndertowMetricsHandlerWrapper();
	}

	/**
	 * Deployment-info customizer that installs the
	 * {@link UndertowMetricsHandlerWrapper} as the outermost handler-chain
	 * wrapper so that every request is observed by Undertow's
	 * {@link io.undertow.server.handlers.MetricsHandler}.
	 *
	 * @param undertowMetricsHandlerWrapper the singleton wrapper bean
	 *                                      supplied by
	 *                                      {@link #undertowMetricsHandlerWrapper()}.
	 * @return a customizer that decorates the deployment with the wrapper;
	 *         never {@code null}.
	 */
	@Bean
	UndertowDeploymentInfoCustomizer undertowDeploymentInfoCustomizer(UndertowMetricsHandlerWrapper undertowMetricsHandlerWrapper) {
		return deploymentInfo -> deploymentInfo.addOuterHandlerChainWrapper(undertowMetricsHandlerWrapper);
	}

	/**
	 * Builder customizer that flips {@link UndertowOptions#ENABLE_STATISTICS}
	 * on the underlying {@link io.undertow.Undertow.Builder} so that connector
	 * statistics are actually collected.
	 *
	 * @return a customizer that enables Undertow's built-in statistics; never
	 *         {@code null}.
	 */
	@Bean
	public UndertowBuilderCustomizer undertowBuilderCustomizerEnableStatistics() {
		return builder -> builder.setServerOption(UndertowOptions.ENABLE_STATISTICS, true);
	}

	/**
	 * Hook invoked by Spring once the application is fully started. Resolves
	 * the running {@link UndertowWebServer}, the {@link MeterRegistry} and
	 * the {@link UndertowMetricsHandlerWrapper} and registers a fresh
	 * instance of every concrete binder against them. The method is a no-op
	 * when either the web server or the unwrapped {@link Undertow} instance
	 * cannot be resolved (for example in a non-Undertow test slice).
	 *
	 * @param event the Spring application-started event; must not be
	 *              {@code null}.
	 */
	@Override
	public void onApplicationEvent(ApplicationStartedEvent event) {
		// Find UndertowWebServer
		UndertowWebServer undertowWebServer = UndertowMetrics.findUndertowWebServer(event.getApplicationContext());
		if (undertowWebServer == null) {
			return;
		}
		// Find Undertow
		Undertow undertow = UndertowMetrics.getUndertow(undertowWebServer);
		if (undertow == null) {
			return;
		}
		// Find MeterRegistry
		MeterRegistry registry = event.getApplicationContext().getBean(MeterRegistry.class);
		UndertowMetricsHandlerWrapper undertowMetricsHandlerWrapper = event.getApplicationContext().getBean(UndertowMetricsHandlerWrapper.class);
		// Bind Undertow Metrics
		new UndertowConnectorMetrics(undertowWebServer).bindTo(registry);
		new UndertowSessionMetrics(undertowWebServer).bindTo(registry);
		new UndertowXWorkerMetrics(undertowWebServer).bindTo(registry);
		new UndertowRequestMetrics(undertowMetricsHandlerWrapper).bindTo(registry);
	}

}