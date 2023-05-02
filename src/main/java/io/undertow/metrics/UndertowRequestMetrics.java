package io.undertow.metrics;

import io.micrometer.core.instrument.*;
import io.undertow.server.handlers.MetricsHandler;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;

/**
 * Undertow Request Metrics
 * https://frandorado.github.io/spring/2020/03/31/spring-actuator-undertow.html
 */
public class UndertowRequestMetrics extends UndertowMeterBinder {

	/**
	 * Request
	 */
	private static final String METRIC_NAME_REQUESTS 							= ".request.count";
	private static final String METRIC_NAME_REQUEST_ERRORS						= ".request.errors";
	private static final String METRIC_NAME_REQUEST_TIME_MAX					= ".request.time.max";
	private static final String METRIC_NAME_REQUEST_TIME_MIN					= ".request.time.min";

	private static final String METRIC_TAG_PROTOCOL = "protocol";
	private UndertowMetricsHandlerWrapper undertowMetricsHandlerWrapper;
	private String namePrefix;
	private Iterable<Tag> tags;

	public UndertowRequestMetrics(UndertowMetricsHandlerWrapper undertowMetricsHandlerWrapper) {
		this(undertowMetricsHandlerWrapper, UNDERTOW_METRIC_NAME_PREFIX);
	}

	public UndertowRequestMetrics(UndertowMetricsHandlerWrapper undertowMetricsHandlerWrapper, String namePrefix) {
		this(undertowMetricsHandlerWrapper, namePrefix, Collections.emptyList());
	}

	public UndertowRequestMetrics(UndertowMetricsHandlerWrapper undertowMetricsHandlerWrapper, String namePrefix, Iterable<Tag> tags) {
		this.undertowMetricsHandlerWrapper = undertowMetricsHandlerWrapper;
		this.namePrefix = namePrefix;
		this.tags = tags;
	}

	@Override
	public void bindTo(MeterRegistry registry) {
		bindTimer(registry, namePrefix + METRIC_NAME_REQUESTS, "Number of total requests", undertowMetricsHandlerWrapper.getMetricsHandler(), m -> m.getMetrics().getTotalRequests(), m2 -> m2.getMetrics().getMinRequestTime(), tags);
		bindTimeGauge(registry, namePrefix + METRIC_NAME_REQUEST_TIME_MAX, "The longest request duration in time", undertowMetricsHandlerWrapper.getMetricsHandler(), m -> m.getMetrics().getMaxRequestTime(), tags);
		bindTimeGauge(registry, namePrefix + METRIC_NAME_REQUEST_TIME_MIN, "The shortest request duration in time", undertowMetricsHandlerWrapper.getMetricsHandler(), m -> m.getMetrics().getMinRequestTime(), tags);
		bindCounter(registry, namePrefix + METRIC_NAME_REQUEST_ERRORS, "Total number of error requests ", undertowMetricsHandlerWrapper.getMetricsHandler(), m -> m.getMetrics().getTotalErrors(), tags);

	}




}
