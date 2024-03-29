package io.undertow.metrics;

import io.micrometer.common.lang.NonNull;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.BaseUnits;
import io.undertow.Undertow;
import io.undertow.server.ConnectorStatistics;
import io.undertow.server.handlers.MetricsHandler;
import org.springframework.boot.web.embedded.undertow.UndertowWebServer;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Undertow Connector Metrics
 */
public class UndertowConnectorMetrics extends UndertowMetrics {

	/**
	 * connectors
	 */
	private static final String METRIC_NAME_CONNECTORS_REQUESTS_COUNT 			= ".connectors.requests.count";
	private static final String METRIC_NAME_CONNECTORS_REQUESTS_ERROR_COUNT 	= ".connectors.requests.error.count";
	private static final String METRIC_NAME_CONNECTORS_REQUESTS_ACTIVE 			= ".connectors.requests.active";
	private static final String METRIC_NAME_CONNECTORS_REQUESTS_ACTIVE_MAX 		= ".connectors.requests.active.max";
	private static final String METRIC_NAME_CONNECTORS_BYTES_SENT 				= ".connectors.bytes.sent";
	private static final String METRIC_NAME_CONNECTORS_BYTES_RECEIVED 			= ".connectors.bytes.received";
	private static final String METRIC_NAME_CONNECTORS_PROCESSING_TIME 			= ".connectors.processing.time";
	private static final String METRIC_NAME_CONNECTORS_PROCESSING_TIME_MAX 		= ".connectors.processing.time.max";
	private static final String METRIC_NAME_CONNECTORS_CONNECTIONS_ACTIVE 		= ".connectors.connections.active";
	private static final String METRIC_NAME_CONNECTORS_CONNECTIONS_ACTIVE_MAX 	= ".connectors.connections.active.max";

	private static final String METRIC_TAG_PROTOCOL = "protocol";

	public UndertowConnectorMetrics(UndertowWebServer undertowWebServer) {
		super(undertowWebServer);
	}

	public UndertowConnectorMetrics(UndertowWebServer undertowWebServer, String namePrefix) {
		super(undertowWebServer, namePrefix);
	}

	public UndertowConnectorMetrics(UndertowWebServer undertowWebServer, String namePrefix, Iterable<Tag> tags) {
		super(undertowWebServer, namePrefix, tags);
	}

	/**
	 * Bind metrics to the given registry.
	 * @param registry
	 * @param undertow
	 * @param namePrefix
	 * @param tags
	 */
	@Override
	public void bindTo(@NonNull MeterRegistry registry, Undertow undertow, String namePrefix, Iterable<Tag> tags){
		// 连接信息指标
		List<Undertow.ListenerInfo> listenerInfoList = undertow.getListenerInfo();
		listenerInfoList.forEach(listenerInfo -> registerConnectorStatistics(registry, listenerInfo, namePrefix, tags));
	};


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
