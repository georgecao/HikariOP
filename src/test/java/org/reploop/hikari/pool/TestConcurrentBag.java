/*
 * Copyright (C) 2013 Brett Wooldridge
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

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.reploop.hikari.HikariConfig;
import org.reploop.hikari.HikariDataSource;
import org.reploop.hikari.util.ConcurrentBag;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.Assert.*;
import static org.reploop.hikari.pool.TestElf.*;

/**
 * @author Brett Wooldridge
 */
public class TestConcurrentBag {
   private static HikariDataSource ds;
   private static HikariPool pool;

   @BeforeClass
   public static void setup() {
      HikariConfig config = newHikariConfig();
      config.setMinimumIdle(1);
      config.setMaximumPoolSize(2);
      config.setInitializationFailTimeout(0);
      config.setConnectionTestQuery("VALUES 1");
      config.setDataSourceClassName("org.reploop.hikari.mocks.StubDataSource");

      ds = new HikariDataSource(config);
      pool = getPool(ds);
   }

   @AfterClass
   public static void teardown() {
      ds.close();
   }

   @Test
   public void testConcurrentBag() throws Exception {
      try (ConcurrentBag<PoolEntry> bag = new ConcurrentBag<>((x) -> CompletableFuture.completedFuture(Boolean.TRUE))) {
         assertEquals(0, bag.values(8).size());

         PoolEntry reserved = pool.newPoolEntry();
         bag.add(reserved);
         bag.reserve(reserved);      // reserved

         PoolEntry inuse = pool.newPoolEntry();
         bag.add(inuse);
         bag.borrow(2, MILLISECONDS); // in use

         PoolEntry notinuse = pool.newPoolEntry();
         bag.add(notinuse); // not in use

         bag.dumpState();

         ByteArrayOutputStream baos = new ByteArrayOutputStream();
         PrintStream ps = new PrintStream(baos, true);
         setSlf4jTargetStream(ConcurrentBag.class, ps);

         bag.requite(reserved);

         bag.remove(notinuse);
         assertTrue(new String(baos.toByteArray()).contains("not borrowed or reserved"));

         bag.unreserve(notinuse);
         assertTrue(new String(baos.toByteArray()).contains("was not reserved"));

         bag.remove(inuse);
         bag.remove(inuse);
         assertTrue(new String(baos.toByteArray()).contains("not borrowed or reserved"));

         bag.close();
         try {
            PoolEntry bagEntry = pool.newPoolEntry();
            bag.add(bagEntry);
            assertNotEquals(bagEntry, bag.borrow(100, MILLISECONDS));
         } catch (IllegalStateException e) {
            assertTrue(new String(baos.toByteArray()).contains("ignoring add()"));
         }

         assertNotNull(notinuse.toString());
      }
   }
}
