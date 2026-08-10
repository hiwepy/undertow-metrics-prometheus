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
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.TimeGauge;
import io.undertow.server.handlers.MetricsHandler;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * Micrometer binder that exposes the request-level statistics accumulated by
 * Undertow's {@link MetricsHandler}.
 *
 * <p>This binder differs from {@link UndertowConnectorMetrics} in that it
 * observes <em>application-level</em> request metrics (the data Undertow
 * collects when an actual request is handled) rather than raw connector-level
 * metrics. As a result, the metrics emitted here are wired up via a
 * {@link UndertowMetricsHandlerWrapper} bean that decorates the deployment
 * with a {@link MetricsHandler} during start-up.</p>
 *
 * <p>The emitted metrics are:</p>
 * <ul>
 *     <li>{@code <prefix>.request.count} &mdash; cumulative number of requests,
 *         exposed as a Micrometer {@link FunctionTimer} together with the
 *         minimum observed request time.</li>
 *     <li>{@code <prefix>.request.time.max} &mdash; the longest request
 *         duration, exposed as a {@link TimeGauge} in milliseconds.</li>
 *     <li>{@code <prefix>.request.time.min} &mdash; the shortest request
 *         duration, exposed as a {@link TimeGauge} in milliseconds.</li>
 *     <li>{@code <prefix>.request.errors} &mdash; cumulative number of error
 *         requests, exposed as a {@link FunctionCounter}.</li>
 * </ul>
 *
 * <p>Reference:
 * <a href="https://frandorado.github.io/spring/2020/03/31/spring-actuator-undertow.html">
 * spring-actuator-undertow</a>.</p>
 *
 * @author <a href="https://github.com/loong10k">Loong Wan</a>
 * @since 3.0.0
 * @see UndertowMetricsHandlerWrapper
 * @see MetricsHandler
 * @see UndertowMeterBinder
 */
public class UndertowRequestMetrics extends UndertowMeterBinder {

	/** Suffix for the total-request-count timer metric. */
	private static final String METRIC_NAME_REQUESTS 							= ".request.count";
	/** Suffix for the total-error-request-count counter metric. */
	private static final String METRIC_NAME_REQUEST_ERRORS						= ".request.errors";
	/** Suffix for the longest-request-duration time-gauge metric. */
	private static final String METRIC_NAME_REQUEST_TIME_MAX					= ".request.time.max";
	/** Suffix for the shortest-request-duration time-gauge metric. */
	private static final String METRIC_NAME_REQUEST_TIME_MIN					= ".request.time.min";

	/** Reserved tag-name constant kept for symmetry with sibling binders. */
	private static final String METRIC_TAG_PROTOCOL = "protocol";
	/** Wrapper that exposes the live {@link MetricsHandler} to be observed. */
	private UndertowMetricsHandlerWrapper undertowMetricsHandlerWrapper;
	/** Prefix prepended to every emitted metric name. */
	private String namePrefix;
	/** Tags applied to every emitted metric. */
	private Iterable<Tag> tags;

	/**
	 * Create a new request-metrics binder using the default metric-name
	 * prefix ({@link #UNDERTOW_METRIC_NAME_PREFIX}).
	 *
	 * @param undertowMetricsHandlerWrapper the Spring-managed wrapper whose
	 *        {@link UndertowMetricsHandlerWrapper#getMetricsHandler() metrics
	 *        handler} will be observed; must not be {@code null}.
	 */
	public UndertowRequestMetrics(UndertowMetricsHandlerWrapper undertowMetricsHandlerWrapper) {
		this(undertowMetricsHandlerWrapper, UNDERTOW_METRIC_NAME_PREFIX);
	}

	/**
	 * Create a new request-metrics binder with a custom metric-name prefix.
	 *
	 * @param undertowMetricsHandlerWrapper the Spring-managed wrapper whose
	 *        metrics handler will be observed; must not be {@code null}.
	 * @param namePrefix the metric-name prefix to prepend to every metric.
	 */
	public UndertowRequestMetrics(UndertowMetricsHandlerWrapper undertowMetricsHandlerWrapper, String namePrefix) {
		this(undertowMetricsHandlerWrapper, namePrefix, Collections.emptyList());
	}

	/**
	 * Create a new request-metrics binder with a custom metric-name prefix
	 * and an additional tag set.
	 *
	 * @param undertowMetricsHandlerWrapper the Spring-managed wrapper whose
	 *        metrics handler will be observed; must not be {@code null}.
	 * @param namePrefix the metric-name prefix to prepend to every metric.
	 * @param tags       additional tags applied to every metric; may be empty.
	 */
	public UndertowRequestMetrics(UndertowMetricsHandlerWrapper undertowMetricsHandlerWrapper, String namePrefix, Iterable<Tag> tags) {
		this.undertowMetricsHandlerWrapper = undertowMetricsHandlerWrapper;
		this.namePrefix = namePrefix;
		this.tags = tags;
	}

	/**
	 * Register the request counters, timers and time-gauges against the
	 * supplied Micrometer registry.
	 *
	 * <p>The implementation pulls the live {@link MetricsHandler} from the
	 * configured {@link UndertowMetricsHandlerWrapper} on every invocation
	 * so that tests can swap out the underlying handler without rebuilding
	 * the binder.</p>
	 *
	 * @param registry the Micrometer registry to register with; must not be
	 *                 {@code null}.
	 */
	@Override
	public void bindTo(MeterRegistry registry) {
		bindTimer(registry, namePrefix + METRIC_NAME_REQUESTS, "Number of total requests", undertowMetricsHandlerWrapper.getMetricsHandler(), m -> m.getMetrics().getTotalRequests(), m2 -> m2.getMetrics().getMinRequestTime(), tags);
		bindTimeGauge(registry, namePrefix + METRIC_NAME_REQUEST_TIME_MAX, "The longest request duration in time", undertowMetricsHandlerWrapper.getMetricsHandler(), m -> m.getMetrics().getMaxRequestTime(), tags);
		bindTimeGauge(registry, namePrefix + METRIC_NAME_REQUEST_TIME_MIN, "The shortest request duration in time", undertowMetricsHandlerWrapper.getMetricsHandler(), m -> m.getMetrics().getMinRequestTime(), tags);
		bindCounter(registry, namePrefix + METRIC_NAME_REQUEST_ERRORS, "Total number of error requests ", undertowMetricsHandlerWrapper.getMetricsHandler(), m -> m.getMetrics().getTotalErrors(), tags);

	}




}