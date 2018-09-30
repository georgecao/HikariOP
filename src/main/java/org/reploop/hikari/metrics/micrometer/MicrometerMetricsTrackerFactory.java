package org.reploop.hikari.metrics.micrometer;

import io.micrometer.core.instrument.MeterRegistry;
import org.reploop.hikari.metrics.IMetricsTracker;
import org.reploop.hikari.metrics.MetricsTrackerFactory;
import org.reploop.hikari.metrics.PoolStats;

public class MicrometerMetricsTrackerFactory implements MetricsTrackerFactory {

   private final MeterRegistry registry;

   public MicrometerMetricsTrackerFactory(MeterRegistry registry) {
      this.registry = registry;
   }

   @Override
   public IMetricsTracker create(String poolName, PoolStats poolStats) {
      return new MicrometerMetricsTracker(poolName, poolStats, registry);
   }
}
