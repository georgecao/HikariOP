package org.reploop.hikari.pool;

import org.junit.Assert;
import org.junit.Test;
import org.reploop.hikari.mocks.StubConnection;
import org.reploop.hikari.pool.TestElf.FauxWebClassLoader;
import org.reploop.hikari.util.JavassistProxyFactory;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.Statement;
import java.util.stream.Stream;

public class TestJavassistCodegen {
   @Test
   public void testCodegen() throws Exception {
      String tmp = System.getProperty("java.io.tmpdir");
      JavassistProxyFactory.main(tmp + (tmp.endsWith("/") ? "" : "/"));

      Path base = Paths.get(tmp, "target/classes/org/reploop/hikari/pool".split("/"));
      Assert.assertTrue("", Files.isRegularFile(base.resolve("HikariProxyConnection.class")));
      Assert.assertTrue("", Files.isRegularFile(base.resolve("HikariProxyStatement.class")));
      Assert.assertTrue("", Files.isRegularFile(base.resolve("HikariProxyCallableStatement.class")));
      Assert.assertTrue("", Files.isRegularFile(base.resolve("HikariProxyPreparedStatement.class")));
      Assert.assertTrue("", Files.isRegularFile(base.resolve("HikariProxyResultSet.class")));
      Assert.assertTrue("", Files.isRegularFile(base.resolve("ProxyFactory.class")));

      FauxWebClassLoader fauxClassLoader = new FauxWebClassLoader();
      Class<?> proxyFactoryClass = fauxClassLoader.loadClass("org.reploop.hikari.pool.ProxyFactory");

      Connection connection = new StubConnection();

      Class<?> fastListClass = fauxClassLoader.loadClass("org.reploop.hikari.util.FastList");
      Object fastList = fastListClass.getConstructor(Class.class).newInstance(Statement.class);

      Object proxyConnection = getMethod(proxyFactoryClass, "getProxyConnection")
         .invoke(null,
            null /*poolEntry*/,
            connection,
            fastList,
            null /*leakTask*/,
            0L /*now*/,
            Boolean.FALSE /*isReadOnly*/,
            Boolean.FALSE /*isAutoCommit*/);
      Assert.assertNotNull(proxyConnection);

      Object proxyStatement = getMethod(proxyConnection.getClass(), "createStatement", 0)
         .invoke(proxyConnection);
      Assert.assertNotNull(proxyStatement);
   }

   private Method getMethod(Class<?> clazz, String methodName, Integer... parameterCount) {
      return Stream.of(clazz.getDeclaredMethods())
         .filter(method -> method.getName().equals(methodName))
         .filter(method -> (parameterCount.length == 0 || parameterCount[0] == method.getParameterCount()))
         .peek(method -> method.setAccessible(true))
         .findFirst()
         .orElseThrow(RuntimeException::new);
   }
}
