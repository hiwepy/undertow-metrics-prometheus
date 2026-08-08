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
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.TimeGauge;
import io.undertow.Undertow;
import org.springframework.boot.web.embedded.undertow.UndertowWebServer;

import java.util.Collections;

/**
 * Convenience base class for the Undertow binders that operate on a Spring
 * Boot {@link UndertowWebServer} rather than on a standalone
 * {@link Undertow} instance.
 *
 * <p>The class captures the three pieces of state shared by all such binders
 * (the {@link UndertowWebServer}, the metric-name prefix and the user-supplied
 * tag set) and forwards the public Micrometer
 * {@link io.micrometer.core.instrument.binder.MeterBinder#bindTo(MeterRegistry)
 * bindTo} call to two package-private overloads:</p>
 * <ol>
 *     <li>{@link #bindTo(MeterRegistry, UndertowWebServer, String, Iterable)}
 *         for binders that want to observe Spring-Boot-specific data such as
 *         the {@code DeploymentManager} or the session manager.</li>
 *     <li>{@link #bindTo(MeterRegistry, Undertow, String, Iterable)} for
 *         binders that want to observe the raw {@link Undertow} instance
 *         (e.g. connector and XWorker metrics).</li>
 * </ol>
 *
 * <p>Both overloads are intentionally empty no-ops; concrete subclasses
 * override only the ones they need.</p>
 *
 * @author [@Loong Wan](https://github.com/loong10k)
 * @since 3.0.0
 * @see UndertowMeterBinder
 * @see UndertowConnectorMetrics
 * @see UndertowSessionMetrics
 * @see UndertowXWorkerMetrics
 */
public abstract class UndertowMetrics extends UndertowMeterBinder {


	/** The Spring-managed web server observed by this binder; never {@code null} after construction. */
	private UndertowWebServer undertowWebServer;
	/** The metric-name prefix prepended to every emitted metric. */
	private String namePrefix;
	/** Tags applied to every emitted metric. */
	private Iterable<Tag> tags;

	/**
	 * Create a new binder with the default metric-name prefix.
	 *
	 * @param undertowWebServer the Spring-managed Undertow web server to
	 *                          observe; must not be {@code null}.
	 */
	public UndertowMetrics(UndertowWebServer undertowWebServer) {
		this(undertowWebServer, UNDERTOW_METRIC_NAME_PREFIX);
	}

	/**
	 * Create a new binder with a custom metric-name prefix.
	 *
	 * @param undertowWebServer the Spring-managed Undertow web server to
	 *                          observe; must not be {@code null}.
	 * @param namePrefix        the metric-name prefix to prepend to every
	 *                          emitted metric.
	 */
	public UndertowMetrics(UndertowWebServer undertowWebServer, String namePrefix) {
		this(undertowWebServer, namePrefix, Collections.emptyList());
	}

	/**
	 * Create a new binder with a custom metric-name prefix and an additional
	 * tag set.
	 *
	 * @param undertowWebServer the Spring-managed Undertow web server to
	 *                          observe; must not be {@code null}.
	 * @param namePrefix        the metric-name prefix to prepend to every
	 *                          emitted metric.
	 * @param tags              additional tags applied to every emitted
	 *                          metric; may be empty.
	 */
	public UndertowMetrics(UndertowWebServer undertowWebServer, String namePrefix, Iterable<Tag> tags) {
		this.undertowWebServer = undertowWebServer;
		this.namePrefix = namePrefix;
		this.tags = tags;
	}

	/**
	 * Bind every metric exposed by this binder to the supplied Micrometer
	 * registry. The default implementation simply fans the call out to the
	 * two package-private overloads (one for the Spring-Boot-specific
	 * {@link UndertowWebServer}, one for the underlying {@link Undertow}).
	 *
	 * @param registry the Micrometer registry to register with; must not be
	 *                 {@code null}.
	 */
	@Override
	public void bindTo(MeterRegistry registry) {

		bindTo(registry, undertowWebServer, namePrefix, tags);
		bindTo(registry, getUndertow(undertowWebServer), namePrefix, tags);

	}

	/**
	 * Hook invoked for binders that want to observe the Spring-Boot-specific
	 * {@link UndertowWebServer}. The default implementation is a no-op.
	 *
	 * @param registry          the Micrometer registry to register with; must
	 *                          not be {@code null}.
	 * @param undertowWebServer the Spring-managed web server; must not be
	 *                          {@code null}.
	 * @param namePrefix        the metric-name prefix in effect for this
	 *                          bind operation.
	 * @param tags              tags to attach to every emitted metric.
	 */
	void bindTo(@NonNull MeterRegistry registry, UndertowWebServer undertowWebServer, String namePrefix, Iterable<Tag> tags) {};

	/**
	 * Hook invoked for binders that want to observe the underlying
	 * {@link Undertow} instance directly. The default implementation is a
	 * no-op.
	 *
	 * @param registry   the Micrometer registry to register with; must not
	 *                   be {@code null}.
	 * @param undertow   the unwrapped Undertow instance; must not be
	 *                   {@code null}.
	 * @param namePrefix the metric-name prefix in effect for this bind
	 *                   operation.
	 * @param tags       tags to attach to every emitted metric.
	 */
	void bindTo(@NonNull MeterRegistry registry, Undertow undertow, String namePrefix, Iterable<Tag> tags){};



}