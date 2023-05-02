package io.undertow.metrics;

import io.micrometer.common.lang.NonNull;
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

	@Override
	public void bindTo(@NonNull MeterRegistry registry, UndertowWebServer undertowWebServer, Undertow undertow, MetricsHandler metricsHandler, String namePrefix, Iterable<Tag> tags){

		// Find XnioWorkerMXBean
		XnioWorkerMXBean workerMXBean = undertow.getWorker().getMXBean();

		// xWorker 指标
		registerXWorker(registry, workerMXBean, namePrefix, tags);

	};

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
