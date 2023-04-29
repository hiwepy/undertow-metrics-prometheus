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
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.TimeGauge;
import io.undertow.Undertow;
import io.undertow.server.handlers.MetricsHandler;
import org.springframework.boot.web.embedded.undertow.UndertowWebServer;
import org.xnio.management.XnioWorkerMXBean;

import java.util.concurrent.TimeUnit;

/**
 * Undertow XWorker Metrics
 */
public class UndertowXWorkerMetrics extends UndertowMetrics {

	/**
	 * XWorker
	 */
	private static final String METRIC_NAME_X_WORK_WORKER_POOL_CORE_SIZE 		= ".xwork.worker.pool.core.size";
	private static final String METRIC_NAME_X_WORK_WORKER_POOL_MAX_SIZE 		= ".xwork.worker.pool.max.size";
	private static final String METRIC_NAME_X_WORK_WORKER_POOL_SIZE 			= ".xwork.worker.pool.size";
	private static final String METRIC_NAME_X_WORK_WORKER_THREAD_BUSY_COUNT 	= ".xwork.worker.thread.busy.count";
	private static final String METRIC_NAME_X_WORK_IO_THREAD_COUNT 				= ".xwork.io.thread.count";
	private static final String METRIC_NAME_X_WORK_WORKER_QUEUE_SIZE 			= ".xwork.worker.queue.size";

	private static final String METRIC_CATEGORY = "name";

	public UndertowXWorkerMetrics(MetricsHandler metricsHandler) {
		super(metricsHandler);
	}

	public UndertowXWorkerMetrics(MetricsHandler metricsHandler, String namePrefix) {
		super(metricsHandler, namePrefix);
	}

	public UndertowXWorkerMetrics(MetricsHandler metricsHandler, String namePrefix, Iterable<Tag> tags) {
		super(metricsHandler, namePrefix, tags);
	}

	/**
	 * Bind metrics to the given registry.
	 * @param registry
	 * @param undertow
	 * @param tags
	 */
	@Override
	public void bindTo(@NonNull MeterRegistry registry, UndertowWebServer undertowWebServer, Undertow undertow, MetricsHandler metricsHandler, String namePrefix, Iterable<Tag> tags){

		// Find XnioWorkerMXBean
		XnioWorkerMXBean workerMXBean = undertow.getWorker().getMXBean();

		// xWorker 指标
		registerXWorker(registry, workerMXBean, namePrefix, tags);


	};

	private void registerXWorker(MeterRegistry registry, XnioWorkerMXBean workerMXBean, String namePrefix, Iterable<Tag> tags) {

		bindGauge(registry, namePrefix + METRIC_NAME_X_WORK_WORKER_POOL_CORE_SIZE, "XWork core worker pool size", workerMXBean, XnioWorkerMXBean::getCoreWorkerPoolSize, tags);
		bindGauge(registry, namePrefix + METRIC_NAME_X_WORK_WORKER_POOL_MAX_SIZE, "XWork max worker pool size", workerMXBean, XnioWorkerMXBean::getMaxWorkerPoolSize, tags);
		bindGauge(registry, namePrefix + METRIC_NAME_X_WORK_WORKER_POOL_SIZE, "XWork worker pool size", workerMXBean, XnioWorkerMXBean::getWorkerPoolSize, tags);
		bindGauge(registry, namePrefix + METRIC_NAME_X_WORK_WORKER_THREAD_BUSY_COUNT, "XWork busy worker thread count", workerMXBean, XnioWorkerMXBean::getBusyWorkerThreadCount, tags);
		bindGauge(registry, namePrefix + METRIC_NAME_X_WORK_IO_THREAD_COUNT, "XWork I/O thread count", workerMXBean, XnioWorkerMXBean::getIoThreadCount, tags);
		bindGauge(registry, namePrefix + METRIC_NAME_X_WORK_WORKER_QUEUE_SIZE, "XWork worker queue size", workerMXBean, XnioWorkerMXBean::getWorkerQueueSize, tags);




		Gauge.builder(namePrefix + METRIC_NAME_X_WORK_WORKER_POOL_CORE_SIZE, workerMXBean, XnioWorkerMXBean::getCoreWorkerPoolSize)
				.description("XWork core worker pool size")
				.tags(tags)
				.tag(METRIC_CATEGORY, workerMXBean.getName())
				.register(registry);

		// Number of worker threads. The default is 8 times the number of I/O threads.
		TimeGauge.builder(namePrefix + METRIC_NAME_X_WORK_WORKER_POOL_CORE_SIZE, workerMXBean, TimeUnit.MILLISECONDS, XnioWorkerMXBean::getCoreWorkerPoolSize)
				.description("XWork core worker pool size")
				.tags(tags)
				.tag(METRIC_CATEGORY, workerMXBean.getName())
				.register(registry);

		Gauge.builder(namePrefix + METRIC_NAME_X_WORK_WORKER_POOL_MAX_SIZE, workerMXBean, XnioWorkerMXBean::getMaxWorkerPoolSize)
				.description("XWork max worker pool size")
				.tags(tags)
				.tag(METRIC_CATEGORY, workerMXBean.getName())
				.register(registry);
		Gauge.builder(namePrefix + METRIC_NAME_X_WORK_WORKER_POOL_SIZE, workerMXBean, XnioWorkerMXBean::getWorkerPoolSize)
				.description("XWork worker pool size")
				.tags(tags)
				.tag(METRIC_CATEGORY, workerMXBean.getName())
				.register(registry);
		Gauge.builder(namePrefix + METRIC_NAME_X_WORK_WORKER_THREAD_BUSY_COUNT, workerMXBean, XnioWorkerMXBean::getBusyWorkerThreadCount)
				.description("XWork busy worker thread count")
				.tags(tags)
				.tag(METRIC_CATEGORY, workerMXBean.getName())
				.register(registry);
		//  Number of I/O threads to create for the worker.
		Gauge.builder(namePrefix + METRIC_NAME_X_WORK_IO_THREAD_COUNT, workerMXBean, XnioWorkerMXBean::getIoThreadCount)
				.description("XWork I/O thread count")
				.tags(tags)
				.tag(METRIC_CATEGORY, workerMXBean.getName())
				.register(registry);
		Gauge.builder(namePrefix + METRIC_NAME_X_WORK_WORKER_QUEUE_SIZE, workerMXBean, XnioWorkerMXBean::getWorkerQueueSize)
				.description("XWork worker queue size")
				.tags(tags)
				.tag(METRIC_CATEGORY, workerMXBean.getName())
				.register(registry);
	}

}
