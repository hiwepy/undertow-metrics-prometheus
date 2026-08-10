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

import io.micrometer.common.lang.NonNull;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.BaseUnits;
import io.undertow.Undertow;
import io.undertow.server.ConnectorStatistics;
import org.springframework.boot.web.embedded.undertow.UndertowWebServer;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * {@link io.micrometer.core.instrument.binder.MeterBinder} implementation that exposes
 * per-listener Undertow <em>connector</em> metrics through Micrometer.
 *
 * <p>For every {@link Undertow.ListenerInfo listener} configured on the bound
 * {@link UndertowWebServer}, this binder registers a series of Micrometer
 * {@link Gauge gauges} derived from the listener's {@link ConnectorStatistics}:
 * request counters, error counters, active-request gauges, byte counters,
 * processing-time gauges and active-connection gauges. Each metric is tagged
 * with the protocol reported by the listener (for example {@code HTTP/1.1} or
 * {@code HTTPS/2.0}) and any user-supplied tags.</p>
 *
 * <p>The metric-name suffix constants are intentionally segmented by
 * {@code .connectors.*} so that scrapers can distinguish connector-level
 * metrics from session, worker or request-level metrics emitted by sibling
 * binders.</p>
 *
 * @author <a href="https://github.com/loong10k">Loong Wan</a>
 * @since 3.0.0
 * @see UndertowMetrics
 * @see UndertowSessionMetrics
 * @see UndertowXWorkerMetrics
 * @see UndertowRequestMetrics
 * @see ConnectorStatistics
 */
public class UndertowConnectorMetrics extends UndertowMetrics {

	/** Suffix for the cumulative request count gauge emitted per listener. */
	private static final String METRIC_NAME_CONNECTORS_REQUESTS_COUNT 			= ".connectors.requests.count";
	/** Suffix for the cumulative error-request count gauge emitted per listener. */
	private static final String METRIC_NAME_CONNECTORS_REQUESTS_ERROR_COUNT 	= ".connectors.requests.error.count";
	/** Suffix for the currently-in-flight request gauge emitted per listener (unit: connections). */
	private static final String METRIC_NAME_CONNECTORS_REQUESTS_ACTIVE 			= ".connectors.requests.active";
	/** Suffix for the historical high-water-mark of in-flight requests (unit: connections). */
	private static final String METRIC_NAME_CONNECTORS_REQUESTS_ACTIVE_MAX 		= ".connectors.requests.active.max";
	/** Suffix for the total bytes-sent counter emitted per listener (unit: bytes). */
	private static final String METRIC_NAME_CONNECTORS_BYTES_SENT 				= ".connectors.bytes.sent";
	/** Suffix for the total bytes-received counter emitted per listener (unit: bytes). */
	private static final String METRIC_NAME_CONNECTORS_BYTES_RECEIVED 			= ".connectors.bytes.received";
	/** Suffix for the average request processing time (unit: milliseconds). */
	private static final String METRIC_NAME_CONNECTORS_PROCESSING_TIME 			= ".connectors.processing.time";
	/** Suffix for the maximum observed request processing time (unit: milliseconds). */
	private static final String METRIC_NAME_CONNECTORS_PROCESSING_TIME_MAX 		= ".connectors.processing.time.max";
	/** Suffix for the gauge of currently-open connections (unit: connections). */
	private static final String METRIC_NAME_CONNECTORS_CONNECTIONS_ACTIVE 		= ".connectors.connections.active";
	/** Suffix for the gauge of the historical high-water-mark of open connections (unit: connections). */
	private static final String METRIC_NAME_CONNECTORS_CONNECTIONS_ACTIVE_MAX 	= ".connectors.connections.active.max";

	/** Name of the listener-protocol tag attached to every emitted metric. */
	private static final String METRIC_TAG_PROTOCOL = "protocol";

	/**
	 * Create a new binder that scrapes metrics from the given Undertow web
	 * server using the default metric-name prefix.
	 *
	 * @param undertowWebServer the Spring-Boot-managed Undertow server to
	 *                          inspect; must not be {@code null}.
	 */
	public UndertowConnectorMetrics(UndertowWebServer undertowWebServer) {
		super(undertowWebServer);
	}

	/**
	 * Create a new binder with a custom metric-name prefix.
	 *
	 * @param undertowWebServer the Spring-Boot-managed Undertow server to
	 *                          inspect; must not be {@code null}.
	 * @param namePrefix        the metric-name prefix to prepend to every
	 *                          emitted gauge (for example {@code "http.undertow"}).
	 */
	public UndertowConnectorMetrics(UndertowWebServer undertowWebServer, String namePrefix) {
		super(undertowWebServer, namePrefix);
	}

	/**
	 * Create a new binder with a custom metric-name prefix and a set of
	 * additional tags to attach to every emitted gauge.
	 *
	 * @param undertowWebServer the Spring-Boot-managed Undertow server to
	 *                          inspect; must not be {@code null}.
	 * @param namePrefix        the metric-name prefix to prepend to every
	 *                          emitted gauge (for example {@code "http.undertow"}).
	 * @param tags              additional tags applied to every gauge; may be
	 *                          {@code null} or empty in which case only the
	 *                          built-in {@code protocol} tag is attached.
	 */
	public UndertowConnectorMetrics(UndertowWebServer undertowWebServer, String namePrefix, Iterable<Tag> tags) {
		super(undertowWebServer, namePrefix, tags);
	}

	/**
	 * Iterate over every listener exposed by the supplied {@link Undertow}
	 * instance and register the full set of connector gauges for it.
	 *
	 * <p>This is invoked by {@link UndertowMetrics#bindTo(MeterRegistry)} after
	 * the bound {@link UndertowWebServer} has been unwrapped to its underlying
	 * {@link Undertow} instance.</p>
	 *
	 * @param registry   the Micrometer registry to register the gauges with;
	 *                   must not be {@code null}.
	 * @param undertow   the unwrapped Undertow server whose listeners are
	 *                   inspected; must not be {@code null}.
	 * @param namePrefix the metric-name prefix to prepend to every gauge.
	 * @param tags       additional tags applied to every gauge; may be empty.
	 */
	@Override
	public void bindTo(@NonNull MeterRegistry registry, Undertow undertow, String namePrefix, Iterable<Tag> tags){
		List<Undertow.ListenerInfo> listenerInfoList = undertow.getListenerInfo();
		listenerInfoList.forEach(listenerInfo -> registerConnectorStatistics(registry, listenerInfo, namePrefix, tags));
	};


	/**
	 * Register the connector statistics gauges for a single Undertow listener.
	 *
	 * <p>Ten gauges are registered: total requests, error count, active requests,
	 * peak active requests, bytes sent, bytes received, processing time (avg and
	 * max), active connections and peak active connections. Each gauge carries
	 * the listener's protocol as a {@code protocol} tag in addition to the
	 * user-supplied {@code tags}.</p>
	 *
	 * @param registry     the Micrometer registry to register the gauges with;
	 *                     must not be {@code null}.
	 * @param listenerInfo the listener whose statistics are exposed; must not
	 *                     be {@code null}.
	 * @param namePrefix   the metric-name prefix to prepend to every gauge.
	 * @param tags         additional tags applied to every gauge; may be empty.
	 */
	private void registerConnectorStatistics(MeterRegistry registry, Undertow.ListenerInfo listenerInfo, String namePrefix, Iterable<Tag> tags) {
		String protocol = listenerInfo.getProtcol();
		ConnectorStatistics statistics = listenerInfo.getConnectorStatistics();
		Gauge.builder(namePrefix + METRIC_NAME_CONNECTORS_REQUESTS_COUNT, statistics, ConnectorStatistics::getRequestCount)
				.tags(tags)
				.tag(METRIC_TAG_PROTOCOL, protocol)
				.register(registry);
		Gauge.builder(namePrefix + METRIC_NAME_CONNECTORS_REQUESTS_ERROR_COUNT, statistics, ConnectorStatistics::getErrorCount)
				.tags(tags)
				.tag(METRIC_TAG_PROTOCOL, protocol)
				.register(registry);
		Gauge.builder(namePrefix + METRIC_NAME_CONNECTORS_REQUESTS_ACTIVE, statistics, ConnectorStatistics::getActiveRequests)
				.tags(tags)
				.tag(METRIC_TAG_PROTOCOL, protocol)
				.baseUnit(BaseUnits.CONNECTIONS)
				.register(registry);
		Gauge.builder(namePrefix + METRIC_NAME_CONNECTORS_REQUESTS_ACTIVE_MAX, statistics, ConnectorStatistics::getMaxActiveRequests)
				.tags(tags)
				.tag(METRIC_TAG_PROTOCOL, protocol)
				.baseUnit(BaseUnits.CONNECTIONS)
				.register(registry);

		Gauge.builder(namePrefix + METRIC_NAME_CONNECTORS_BYTES_SENT, statistics, ConnectorStatistics::getBytesSent)
				.tags(tags)
				.tag(METRIC_TAG_PROTOCOL, protocol)
				.baseUnit(BaseUnits.BYTES)
				.register(registry);
		Gauge.builder(namePrefix + METRIC_NAME_CONNECTORS_BYTES_RECEIVED, statistics, ConnectorStatistics::getBytesReceived)
				.tags(tags)
				.tag(METRIC_TAG_PROTOCOL, protocol)
				.baseUnit(BaseUnits.BYTES)
				.register(registry);

		Gauge.builder(namePrefix + METRIC_NAME_CONNECTORS_PROCESSING_TIME, statistics, (s) -> TimeUnit.NANOSECONDS.toMillis(s.getProcessingTime()))
				.tags(tags)
				.tag(METRIC_TAG_PROTOCOL, protocol)
				.baseUnit(BaseUnits.MILLISECONDS)
				.register(registry);
		Gauge.builder(namePrefix + METRIC_NAME_CONNECTORS_PROCESSING_TIME_MAX, statistics, (s) -> TimeUnit.NANOSECONDS.toMillis(s.getMaxProcessingTime()))
				.tags(tags)
				.tag(METRIC_TAG_PROTOCOL, protocol)
				.baseUnit(BaseUnits.MILLISECONDS)
				.register(registry);

		Gauge.builder(namePrefix + METRIC_NAME_CONNECTORS_CONNECTIONS_ACTIVE, statistics, ConnectorStatistics::getActiveConnections)
				.tags(tags)
				.tag(METRIC_TAG_PROTOCOL, protocol)
				.baseUnit(BaseUnits.CONNECTIONS)
				.register(registry);
		Gauge.builder(namePrefix + METRIC_NAME_CONNECTORS_CONNECTIONS_ACTIVE_MAX, statistics, ConnectorStatistics::getMaxActiveConnections)
				.tags(tags)
				.tag(METRIC_TAG_PROTOCOL, protocol)
				.baseUnit(BaseUnits.CONNECTIONS)
				.register(registry);

	}



}