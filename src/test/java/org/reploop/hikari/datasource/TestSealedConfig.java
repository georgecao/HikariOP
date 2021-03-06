package org.reploop.hikari.datasource;

import org.junit.Test;
import org.reploop.hikari.HikariConfig;
import org.reploop.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.Assert.fail;
import static org.reploop.hikari.pool.TestElf.newHikariConfig;

public class TestSealedConfig {
   @Test(expected = IllegalStateException.class)
   public void testSealed1() throws SQLException {
      HikariConfig config = newHikariConfig();
      config.setDataSourceClassName("org.reploop.hikari.mocks.StubDataSource");

      try (HikariDataSource ds = new HikariDataSource(config)) {
         ds.setDataSourceClassName("org.reploop.hikari.mocks.StubDataSource");
         fail("Exception should have been thrown");
      }
   }

   @Test(expected = IllegalStateException.class)
   public void testSealed2() throws SQLException {
      HikariDataSource ds = new HikariDataSource();
      ds.setDataSourceClassName("org.reploop.hikari.mocks.StubDataSource");

      try (HikariDataSource closeable = ds) {
         try (Connection connection = ds.getConnection()) {
            ds.setDataSourceClassName("org.reploop.hikari.mocks.StubDataSource");
            fail("Exception should have been thrown");
         }
      }
   }

   @Test(expected = IllegalStateException.class)
   public void testSealed3() throws SQLException {
      HikariDataSource ds = new HikariDataSource();
      ds.setDataSourceClassName("org.reploop.hikari.mocks.StubDataSource");

      try (HikariDataSource closeable = ds) {
         try (Connection connection = ds.getConnection()) {
            ds.setAutoCommit(false);
            fail("Exception should have been thrown");
         }
      }
   }

   @Test
   public void testSealedAccessibleMethods() throws SQLException {
      HikariConfig config = newHikariConfig();
      config.setDataSourceClassName("org.reploop.hikari.mocks.StubDataSource");

      try (HikariDataSource ds = new HikariDataSource(config)) {
         ds.setConnectionTimeout(5000);
         ds.setValidationTimeout(5000);
         ds.setIdleTimeout(30000);
         ds.setLeakDetectionThreshold(60000);
         ds.setMaxLifetime(1800000);
         ds.setMinimumIdle(5);
         ds.setMaximumPoolSize(8);
         ds.setPassword("password");
         ds.setUsername("username");
      }
   }
}
