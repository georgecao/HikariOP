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

import org.junit.Test;
import org.reploop.hikari.HikariConfig;
import org.reploop.hikari.HikariDataSource;
import org.reploop.hikari.mocks.StubConnection;
import org.reploop.hikari.mocks.StubDataSource;

import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.Assert.*;
import static org.reploop.hikari.pool.TestElf.getPool;
import static org.reploop.hikari.pool.TestElf.newHikariConfig;

/**
 * @author Brett Wooldridge
 */
public class UnwrapTest {
   @Test
   public void testUnwrapConnection() throws SQLException {
      HikariConfig config = newHikariConfig();
      config.setMinimumIdle(1);
      config.setMaximumPoolSize(1);
      config.setInitializationFailTimeout(0);
      config.setConnectionTestQuery("VALUES 1");
      config.setDataSourceClassName("org.reploop.hikari.mocks.StubDataSource");

      try (HikariDataSource ds = new HikariDataSource(config)) {
         ds.getConnection().close();
         assertSame("Idle connections not as expected", 1, getPool(ds).getIdleConnections());

         Connection connection = ds.getConnection();
         assertNotNull(connection);

         StubConnection unwrapped = connection.unwrap(StubConnection.class);
         assertTrue("unwrapped connection is not instance of StubConnection: " + unwrapped, (unwrapped != null && unwrapped instanceof StubConnection));
      }
   }

   @Test
   public void testUnwrapDataSource() throws SQLException {
      HikariConfig config = newHikariConfig();
      config.setMinimumIdle(1);
      config.setMaximumPoolSize(1);
      config.setInitializationFailTimeout(0);
      config.setConnectionTestQuery("VALUES 1");
      config.setDataSourceClassName("org.reploop.hikari.mocks.StubDataSource");

      try (HikariDataSource ds = new HikariDataSource(config)) {
         StubDataSource unwrap = ds.unwrap(StubDataSource.class);
         assertNotNull(unwrap);
         assertTrue(unwrap instanceof StubDataSource);

         assertTrue(ds.isWrapperFor(HikariDataSource.class));
         assertTrue(ds.unwrap(HikariDataSource.class) instanceof HikariDataSource);

         assertFalse(ds.isWrapperFor(getClass()));
         try {
            ds.unwrap(getClass());
         } catch (SQLException e) {
            assertTrue(e.getMessage().contains("Wrapped DataSource"));
         }
      }
   }
}
