/*
 * Copyright (c) 2019-2029, Dreamlu 卢春梦 (596392912@qq.com & www.dreamlu.net).
 * <p>
 * Licensed under the GNU LESSER GENERAL PUBLIC LICENSE 3.0;
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.gnu.org/licenses/lgpl.html
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.metrics;

import io.micrometer.common.lang.NonNull;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.undertow.Undertow;
import io.undertow.server.handlers.MetricsHandler;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.boot.web.embedded.undertow.UndertowWebServer;
import org.springframework.boot.web.reactive.context.ReactiveWebServerApplicationContext;
import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;

/**
 * Undertow Metrics
 *
 * @author L.cm
 */
public abstract class UndertowMetrics implements MeterBinder, ApplicationListener<ApplicationStartedEvent> {

	/**
	 * Prefix used for all Undertow metric names.
	 */
	public static final String UNDERTOW_METRIC_NAME_PREFIX = "undertow";
	/**
	 * Request
	 */
	private static final String METRIC_NAME_REQUESTS 							= UNDERTOW_METRIC_NAME_PREFIX + ".request.count";
	private static final String METRIC_NAME_REQUEST_ERRORS						= UNDERTOW_METRIC_NAME_PREFIX + ".request.errors";
	private static final String METRIC_NAME_REQUEST_TIME_MAX					= UNDERTOW_METRIC_NAME_PREFIX + ".request.time.max";
	private static final String METRIC_NAME_REQUEST_TIME_MIN					= UNDERTOW_METRIC_NAME_PREFIX + ".request.time.min";

	private static final Field UNDERTOW_FIELD;
	private static final String METRIC_TAG_PROTOCOL = "protocol";

	private UndertowWebServer undertowWebServer;
	private MetricsHandler metricsHandler;
	private Undertow undertow;
	private String namePrefix;
	private Iterable<Tag> tags;

	public UndertowMetrics(MetricsHandler metricsHandler) {
		this(metricsHandler, UNDERTOW_METRIC_NAME_PREFIX);
	}

	public UndertowMetrics(MetricsHandler metricsHandler, String namePrefix) {
		this(metricsHandler, namePrefix, Collections.emptyList());
	}

	public UndertowMetrics(MetricsHandler metricsHandler, String namePrefix, Iterable<Tag> tags) {
		this.metricsHandler = metricsHandler;
		this.namePrefix = namePrefix;
		this.tags = Collections.emptyList();
	}

	@Override
	public void onApplicationEvent(ApplicationStartedEvent event) {
		// Find UndertowWebServer
		UndertowWebServer undertowWebServer = findUndertowWebServer(event.getApplicationContext());
		if (undertowWebServer == null) {
			return;
		}
		this.undertowWebServer = undertowWebServer;
		// Find Undertow
		Undertow undertow = getUndertow(undertowWebServer);
		if (undertow == null) {
			return;
		}
		this.undertow = undertow;
		// Find MeterRegistry
		MeterRegistry registry = event.getApplicationContext().getBean(MeterRegistry.class);
		this.bindTo(registry);
	}

	@Override
	public void bindTo(MeterRegistry registry) {

		bindTimer(registry, METRIC_NAME_REQUESTS, "Number of requests", metricsHandler, m -> m.getMetrics().getTotalRequests(), m2 -> m2.getMetrics().getMinRequestTime(), tags);
		bindTimeGauge(registry, METRIC_NAME_REQUEST_TIME_MAX, "The longest request duration in time", metricsHandler, m -> m.getMetrics().getMaxRequestTime(), tags);
		bindTimeGauge(registry, METRIC_NAME_REQUEST_TIME_MIN, "The shortest request duration in time", metricsHandler, m -> m.getMetrics().getMinRequestTime(), tags);
		bindCounter(registry, METRIC_NAME_REQUEST_ERRORS, "Total number of error requests ", metricsHandler, m -> m.getMetrics().getTotalErrors(), tags);

		bindTo(registry, undertowWebServer, undertow, metricsHandler,  namePrefix, tags);
	}

	/**
	 * Bind metrics to the given registry.
	 * @param registry
	 * @param undertow
	 * @param tags
	 */
	abstract void bindTo(@NonNull MeterRegistry registry, UndertowWebServer undertowWebServer, Undertow undertow, MetricsHandler metricsHandler, String namePrefix, Iterable<Tag> tags);


	protected  <T> void bindTimer(MeterRegistry registry, String name, String desc, T metricsHandler, ToLongFunction<T> countFunc, ToDoubleFunction<T> consumer, Iterable<Tag> tags) {
		FunctionTimer.builder(name, metricsHandler, countFunc, consumer, TimeUnit.MILLISECONDS)
				.description(desc)
				.tags(tags)
				//.tag(METRIC_CATEGORY, workerMXBean.getName())
				.register(registry);
	}

	protected <T> void bindGauge(MeterRegistry registry, String name, String desc, T metricResult,
								 ToDoubleFunction<T> consumer, Iterable<Tag> tags) {
		Gauge.builder(name, metricResult, consumer)
				.description(desc)
				.tags(tags)
				.register(registry);
	}

	protected <T> void bindTimeGauge(MeterRegistry registry, String name, String desc, T metricResult,
							   ToDoubleFunction<T> consumer, Iterable<Tag> tags) {
		TimeGauge.builder(name, metricResult, TimeUnit.MILLISECONDS, consumer)
				.description(desc)
				.tags(tags)
				//.tag(METRIC_CATEGORY, workerMXBean.getName())
				.register(registry);
	}

	protected <T> void bindCounter(MeterRegistry registry, String name, String desc, T metricsHandler, ToDoubleFunction<T> consumer, Iterable<Tag> tags) {
		FunctionCounter.builder(name, metricsHandler, consumer)
				.description(desc)
				.tags(tags)
				//.tag(METRIC_CATEGORY, workerMXBean.getName())
				.register(registry);
	}

	static {
		UNDERTOW_FIELD = ReflectionUtils.findField(UndertowWebServer.class, "undertow");
		Objects.requireNonNull(UNDERTOW_FIELD, "UndertowWebServer class field undertow not exist.");
		ReflectionUtils.makeAccessible(UNDERTOW_FIELD);
	}

	private static Undertow getUndertow(UndertowWebServer undertowWebServer) {
		return (Undertow) ReflectionUtils.getField(UNDERTOW_FIELD, undertowWebServer);
	}

	private static UndertowWebServer findUndertowWebServer(ConfigurableApplicationContext applicationContext) {
		WebServer webServer;
		if (applicationContext instanceof ReactiveWebServerApplicationContext) {
			ReactiveWebServerApplicationContext context = (ReactiveWebServerApplicationContext) applicationContext;
			webServer = context.getWebServer();
		} else if (applicationContext instanceof ServletWebServerApplicationContext) {
			ServletWebServerApplicationContext context = (ServletWebServerApplicationContext) applicationContext;
			webServer = context.getWebServer();
		} else {
			return null;
		}
		if (webServer instanceof UndertowWebServer) {
			UndertowWebServer server = (UndertowWebServer) webServer;
			return server;
		}
		return null;
	}

}
