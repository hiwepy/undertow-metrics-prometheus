package io.undertow.metrics;

import io.micrometer.common.lang.NonNull;
import io.micrometer.core.instrument.*;
import io.undertow.Undertow;
import org.springframework.boot.web.embedded.undertow.UndertowWebServer;

import java.util.Collections;

/**
 * Undertow Metrics
 */
public abstract class UndertowMetrics extends UndertowMeterBinder {


	private UndertowWebServer undertowWebServer;
	private String namePrefix;
	private Iterable<Tag> tags;

	public UndertowMetrics(UndertowWebServer undertowWebServer) {
		this(undertowWebServer, UNDERTOW_METRIC_NAME_PREFIX);
	}

	public UndertowMetrics(UndertowWebServer undertowWebServer, String namePrefix) {
		this(undertowWebServer, namePrefix, Collections.emptyList());
	}

	public UndertowMetrics(UndertowWebServer undertowWebServer, String namePrefix, Iterable<Tag> tags) {
		this.undertowWebServer = undertowWebServer;
		this.namePrefix = namePrefix;
		this.tags = tags;
	}

	@Override
	public void bindTo(MeterRegistry registry) {

		bindTo(registry, undertowWebServer, namePrefix, tags);
		bindTo(registry, getUndertow(undertowWebServer), namePrefix, tags);

	}

	void bindTo(@NonNull MeterRegistry registry, UndertowWebServer undertowWebServer, String namePrefix, Iterable<Tag> tags) {};

	void bindTo(@NonNull MeterRegistry registry, Undertow undertow, String namePrefix, Iterable<Tag> tags){};



}
