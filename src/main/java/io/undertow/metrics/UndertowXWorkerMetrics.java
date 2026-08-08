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
import io.undertow.Undertow;
import io.undertow.server.handlers.MetricsHandler;
import org.springframework.boot.web.embedded.undertow.UndertowWebServer;
import org.xnio.management.XnioWorkerMXBean;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Micrometer binder that exposes the underlying XNIO worker pool of an
 * Undertow server through the supplied {@link XnioWorkerMXBean}.
 *
 * <p>This binder reaches across the XNIO boundary to publish pool-size,
 * thread-busy-count, I/O thread count and worker queue size metrics. Every
 * emitted metric is automatically tagged with the worker's name (through
 * the {@code name} tag) in addition to any user-supplied tags, so that
 * dashboards can distinguish between pools when multiple workers are
 * configured.</p>
 *
 * <p>The metric-name suffixes follow the pattern
 * {@code .xwork.worker.pool.*} and {@code .xwork.io.thread.count} so that
 * Prometheus queries can target worker-related metrics directly.</p>
 *
 * @author [@Loong Wan](https://github.com/loong10k)
 * @since 3.0.0
 * @see UndertowMetrics
 * @see XnioWorkerMXBean
 */
public class UndertowXWorkerMetrics extends UndertowMetrics {

	/** Suffix for the core worker-pool-size gauge. */
	private static final String METRIC_NAME_X_WORK_WORKER_POOL_CORE_SIZE 		= ".xwork.worker.pool.core.size";
	/** Suffix for the max worker-pool-size gauge. */
	private static final String METRIC_NAME_X_WORK_WORKER_POOL_MAX_SIZE 		= ".xwork.worker.pool.max.size";
	/** Suffix for the current worker-pool-size gauge. */
	private static final String METRIC_NAME_X_WORK_WORKER_POOL_SIZE 			= ".xwork.worker.pool.size";
	/** Suffix for the busy-worker-thread-count gauge. */
	private static final String METRIC_NAME_X_WORK_WORKER_THREAD_BUSY_COUNT 	= ".xwork.worker.thread.busy.count";
	/** Suffix for the I/O thread-count gauge. */
	private static final String METRIC_NAME_X_WORK_IO_THREAD_COUNT 				= ".xwork.io.thread.count";
	/** Suffix for the worker queue-size gauge. */
	private static final String METRIC_NAME_X_WORK_WORKER_QUEUE_SIZE 			= ".xwork.worker.queue.size";

	/** Name of the tag that carries the worker's display name. */
	private static final String METRIC_CATEGORY = "name";

	/**
	 * Create a new XWorker binder that uses the default metric-name prefix.
	 *
	 * @param undertowWebServer the Spring-managed Undertow web server to
	 *                          observe; must not be {@code null}.
	 */
	public UndertowXWorkerMetrics(UndertowWebServer undertowWebServer) {
		super(undertowWebServer);
	}

	/**
	 * Create a new XWorker binder with a custom metric-name prefix.
	 *
	 * @param undertowWebServer the Spring-managed Undertow web server to
	 *                          observe; must not be {@code null}.
	 * @param namePrefix        the metric-name prefix to prepend to every
	 *                          emitted gauge.
	 */
	public UndertowXWorkerMetrics(UndertowWebServer undertowWebServer, String namePrefix) {
		super(undertowWebServer, namePrefix);
	}

	/**
	 * Create a new XWorker binder with a custom metric-name prefix and an
	 * additional tag set.
	 *
	 * @param undertowWebServer the Spring-managed Undertow web server to
	 *                          observe; must not be {@code null}.
	 * @param namePrefix        the metric-name prefix to prepend to every
	 *                          emitted gauge.
	 * @param tags              additional tags applied to every emitted
	 *                          gauge; may be {@code null} or empty.
	 */
	public UndertowXWorkerMetrics(UndertowWebServer undertowWebServer, String namePrefix, Iterable<Tag> tags) {
		super(undertowWebServer, namePrefix, tags);
	}

	/**
	 * Register the XWorker pool gauges against the supplied registry by
	 * pulling the {@link XnioWorkerMXBean} out of the live {@link Undertow}
	 * instance.
	 *
	 * @param registry   the Micrometer registry to register with; must not be
	 *                   {@code null}.
	 * @param undertow   the unwrapped Undertow server; must not be {@code null}.
	 * @param namePrefix the metric-name prefix to prepend to every gauge.
	 * @param tags       additional tags applied to every gauge; may be empty.
	 */
	@Override
	public void bindTo(@NonNull MeterRegistry registry, Undertow undertow, String namePrefix, Iterable<Tag> tags){
		XnioWorkerMXBean workerMXBean = undertow.getWorker().getMXBean();
		registerXWorker(registry, workerMXBean, namePrefix, tags);

	};

	/**
	 * Register the worker pool gauges for a single {@link XnioWorkerMXBean}.
	 *
	 * <p>The supplied tag iterable is copied into a mutable {@link ArrayList}
	 * and the {@code name} tag (containing {@link XnioWorkerMXBean#getName()})
	 * is appended. Six gauges are then registered: core pool size, max pool
	 * size, current pool size, busy thread count, I/O thread count and queue
	 * size.</p>
	 *
	 * @param registry     the Micrometer registry to register with; must not
	 *                     be {@code null}.
	 * @param workerMXBean the XNIO worker MBean to observe; must not be
	 *                     {@code null}.
	 * @param namePrefix   the metric-name prefix to prepend to every gauge.
	 * @param tags         additional tags applied to every gauge; may be
	 *                     {@code null} or empty.
	 */
	private void registerXWorker(MeterRegistry registry, XnioWorkerMXBean workerMXBean, String namePrefix, Iterable<Tag> tags) {

		List<Tag> tagsList =  new ArrayList<>();
		if(Objects.nonNull(tags)){
			tags.forEach(tag -> tagsList.add(tag));
		}
		tagsList.add(Tag.of(METRIC_CATEGORY, workerMXBean.getName()));

		// Number of worker threads. The default is 8 times the number of I/O threads.
		bindGauge(registry, namePrefix + METRIC_NAME_X_WORK_WORKER_POOL_CORE_SIZE, "XWork core worker pool size", workerMXBean, XnioWorkerMXBean::getCoreWorkerPoolSize, tagsList);
		bindGauge(registry, namePrefix + METRIC_NAME_X_WORK_WORKER_POOL_MAX_SIZE, "XWork max worker pool size", workerMXBean, XnioWorkerMXBean::getMaxWorkerPoolSize, tagsList);
		bindGauge(registry, namePrefix + METRIC_NAME_X_WORK_WORKER_POOL_SIZE, "XWork worker pool size", workerMXBean, XnioWorkerMXBean::getWorkerPoolSize, tagsList);
		bindGauge(registry, namePrefix + METRIC_NAME_X_WORK_WORKER_THREAD_BUSY_COUNT, "XWork busy worker thread count", workerMXBean, XnioWorkerMXBean::getBusyWorkerThreadCount, tagsList);
		//  Number of I/O threads to create for the worker. The default is the number of available processors.
		bindGauge(registry, namePrefix + METRIC_NAME_X_WORK_IO_THREAD_COUNT, "XWork I/O thread count", workerMXBean, XnioWorkerMXBean::getIoThreadCount, tagsList);
		bindGauge(registry, namePrefix + METRIC_NAME_X_WORK_WORKER_QUEUE_SIZE, "XWork worker queue size", workerMXBean, XnioWorkerMXBean::getWorkerQueueSize, tagsList);

	}

}