/*
 * Copyright (C) 2013, 2017 Brett Wooldridge
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.reploop.hikari.pool;

import org.junit.Test;
import org.reploop.hikari.HikariConfig;
import org.reploop.hikari.HikariDataSource;
import org.reploop.hikari.mocks.StubDataSource;
import org.reploop.hikari.util.ClockSource;
import org.reploop.hikari.util.UtilityElf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.Executors.newFixedThreadPool;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.*;
import static org.reploop.hikari.pool.TestElf.getPool;
import static org.reploop.hikari.pool.TestElf.newHikariConfig;

/**
 * @author Matthew Tambara (matthew.tambara@liferay.com)
 */
public class ConnectionPoolSizeVsThreadsTest {

   private static final Logger LOGGER = LoggerFactory.getLogger(ConnectionPoolSizeVsThreadsTest.class);

   private static final int ITERATIONS = 50_000;

   @Test
   public void testPoolSizeAboutSameSizeAsThreadCount() throws Exception {
      final int threadCount = 50;
      final Counts counts = testPoolSize(2 /*minIdle*/,
         100 /*maxPoolSize*/,
         threadCount,
         1 /*workTimeMs*/,
         0 /*restTimeMs*/,
         20 /*connectionAcquisitionTimeMs*/,
         ITERATIONS,
         SECONDS.toMillis(2) /*postTestTimeMs*/);

      // maxActive may never make it to threadCount but it shouldn't be any higher
      assertEquals(threadCount, counts.maxActive, 15 /*delta*/);
      assertEquals(threadCount, counts.maxTotal, 5 /*delta*/);
   }

   @Test
   public void testSlowConnectionTimeBurstyWork() throws Exception {
      // setup a bursty work load, 50 threads all needing to do around 100 units of work.
      // Using a more realistic time for connection startup of 250 ms and only 5 seconds worth of work will mean that we end up finishing
      // all of the work before we actually have setup 50 connections even though we have requested 50 connections
      final int threadCount = 50;
      final int workItems = threadCount * 100;
      final int workTimeMs = 0;
      final int connectionAcquisitionTimeMs = 250;
      final Counts counts = testPoolSize(2 /*minIdle*/,
         100 /*maxPoolSize*/,
         threadCount,
         workTimeMs,
         0 /*restTimeMs*/,
         connectionAcquisitionTimeMs,
         workItems /*iterations*/,
         SECONDS.toMillis(3) /*postTestTimeMs*/);

      // hard to put exact bounds on how many thread we will use but we can put an upper bound on usage (if there was only one thread)
      final long totalWorkTime = workItems * workTimeMs;
      final long connectionMax = totalWorkTime / connectionAcquisitionTimeMs;
      assertTrue(connectionMax <= counts.maxActive);
      assertEquals(connectionMax, counts.maxTotal, 2 + 2 /*delta*/);
   }

   private Counts testPoolSize(final int minIdle, final int maxPoolSize, final int threadCount,
                               final long workTimeMs, final long restTimeMs, final long connectionAcquisitionTimeMs,
                               final int iterations, final long postTestTimeMs) throws Exception {

      LOGGER.info("Starting test (minIdle={}, maxPoolSize={}, threadCount={}, workTimeMs={}, restTimeMs={}, connectionAcquisitionTimeMs={}, iterations={}, postTestTimeMs={})",
         minIdle, maxPoolSize, threadCount, workTimeMs, restTimeMs, connectionAcquisitionTimeMs, iterations, postTestTimeMs);

      final HikariConfig config = newHikariConfig();
      config.setMinimumIdle(minIdle);
      config.setMaximumPoolSize(maxPoolSize);
      config.setInitializationFailTimeout(Long.MAX_VALUE);
      config.setConnectionTimeout(2500);
      config.setDataSourceClassName("org.reploop.hikari.mocks.StubDataSource");

      final AtomicReference<Exception> ref = new AtomicReference<>(null);

      // Initialize HikariPool with no initial connections and room to grow
      try (final HikariDataSource ds = new HikariDataSource(config)) {
         final StubDataSource stubDataSource = ds.unwrap(StubDataSource.class);
         // connection acquisition takes more than 0 ms in a real system
         stubDataSource.setConnectionAcquistionTime(connectionAcquisitionTimeMs);

         final ExecutorService threadPool = newFixedThreadPool(threadCount);
         final CountDownLatch allThreadsDone = new CountDownLatch(iterations);
         for (int i = 0; i < iterations; i++) {
            threadPool.submit(() -> {
               if (ref.get() == null) {
                  UtilityElf.quietlySleep(restTimeMs);
                  try (Connection c2 = ds.getConnection()) {
                     UtilityElf.quietlySleep(workTimeMs);
                  } catch (Exception e) {
                     ref.set(e);
                  }
               }
               allThreadsDone.countDown();
            });
         }

         final HikariPool pool = getPool(ds);

         // collect pool usage data while work is still being done
         final Counts underLoad = new Counts();
         while (allThreadsDone.getCount() > 0 || pool.getTotalConnections() < minIdle) {
            UtilityElf.quietlySleep(50);
            underLoad.updateMaxCounts(pool);
         }

         // wait for long enough any pending acquisitions have already been done
         LOGGER.info("Test Over, waiting for post delay time {}ms ", postTestTimeMs);
         UtilityElf.quietlySleep(connectionAcquisitionTimeMs + workTimeMs + restTimeMs);

         // collect pool data while there is no work to do.
         final Counts postLoad = new Counts();
         final long start = ClockSource.currentTime();
         while (ClockSource.elapsedMillis(start) < postTestTimeMs) {
            UtilityElf.quietlySleep(50);
            postLoad.updateMaxCounts(pool);
         }

         allThreadsDone.await();

         threadPool.shutdown();
         threadPool.awaitTermination(30, SECONDS);

         if (ref.get() != null) {
            LOGGER.error("Task failed", ref.get());
            fail("Task failed");
         }

         LOGGER.info("Under Load... {}", underLoad);
         LOGGER.info("Post Load.... {}", postLoad);

         // verify that the no connections created after the work has stopped
         if (postTestTimeMs > 0) {
            if (postLoad.maxActive != 0) {
               fail("Max Active was greater than 0 after test was done");
            }

            final int createdAfterWorkAllFinished = postLoad.maxTotal - underLoad.maxTotal;
            assertEquals("Connections were created when there was no waiting consumers", 0, createdAfterWorkAllFinished, 1 /*delta*/);
         }

         return underLoad;
      }
   }

   private static class Counts {
      int maxTotal = 0;
      int maxActive = 0;

      void updateMaxCounts(final HikariPool pool) {
         maxTotal = Math.max(pool.getTotalConnections(), maxTotal);
         maxActive = Math.max(pool.getActiveConnections(), maxActive);
      }

      @Override
      public String toString() {
         return "Counts{" +
            "maxTotal=" + maxTotal +
            ", maxActive=" + maxActive +
            '}';
      }
   }
}
