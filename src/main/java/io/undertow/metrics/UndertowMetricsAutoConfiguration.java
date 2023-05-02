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
 * Undertow Metrics 配置
 */
@AutoConfigureAfter(value =  {MetricsAutoConfiguration.class, SimpleMetricsExportAutoConfiguration.class})
@AutoConfigureBefore(value = {ServletWebServerFactoryAutoConfiguration.class})
@ConditionalOnClass(Undertow.class)
// @ImportRuntimeHints(UndertowRuntimeHintsRegistrar.class)
public class UndertowMetricsAutoConfiguration implements ApplicationListener<ApplicationStartedEvent> {


	@Bean
	public UndertowMetricsHandlerWrapper undertowMetricsHandlerWrapper() {
		return new UndertowMetricsHandlerWrapper();
	}

	@Bean
	UndertowDeploymentInfoCustomizer undertowDeploymentInfoCustomizer(UndertowMetricsHandlerWrapper undertowMetricsHandlerWrapper) {
		return deploymentInfo -> deploymentInfo.addOuterHandlerChainWrapper(undertowMetricsHandlerWrapper);
	}

	@Bean
	public UndertowBuilderCustomizer undertowBuilderCustomizerEnableStatistics() {
		return builder -> builder.setServerOption(UndertowOptions.ENABLE_STATISTICS, true);
	}

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
