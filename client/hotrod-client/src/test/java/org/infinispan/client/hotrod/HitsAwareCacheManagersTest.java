package org.infinispan.client.hotrod;

import org.infinispan.Cache;
import org.infinispan.commands.VisitableCommand;
import org.infinispan.context.InvocationContext;
import org.infinispan.interceptors.base.CommandInterceptor;
import org.infinispan.manager.CacheContainer;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.server.hotrod.HotRodServer;
import org.infinispan.test.MultipleCacheManagersTest;
import org.testng.annotations.BeforeMethod;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Mircea.Markus@jboss.com
 * @since 4.1
 */
public abstract class HitsAwareCacheManagersTest extends MultipleCacheManagersTest {

   protected Map<SocketAddress, EmbeddedCacheManager> hrServ2CacheManager = new HashMap<SocketAddress, EmbeddedCacheManager>();
   protected Map<SocketAddress, HotRodServer> addr2hrServer = new HashMap<SocketAddress, HotRodServer>();

   @Override
   @BeforeMethod(alwaysRun=true)
   public void createBeforeMethod() throws Throwable {
      if (cleanupAfterMethod()) {
         hrServ2CacheManager.clear();
         addr2hrServer.clear();
      }
      super.createBeforeMethod();
   }

   protected HitCountInterceptor getHitCountInterceptor(Cache<Object, Object> cache) {
      HitCountInterceptor hitCountInterceptor = null;
      List<CommandInterceptor> interceptorChain = cache.getAdvancedCache().getInterceptorChain();
      for (CommandInterceptor interceptor : interceptorChain) {
         boolean isHitCountInterceptor = interceptor instanceof HitCountInterceptor;
         if (hitCountInterceptor != null && isHitCountInterceptor) {
            throw new IllegalStateException("Two HitCountInterceptors! " + interceptorChain);
         }
         if (isHitCountInterceptor) {
            hitCountInterceptor = (HitCountInterceptor) interceptor;
         }
      }
      return hitCountInterceptor;
   }

   protected void assertOnlyServerHit(SocketAddress serverAddress) {
      CacheContainer cacheContainer = hrServ2CacheManager.get(serverAddress);
      HitCountInterceptor interceptor = getHitCountInterceptor(cacheContainer.getCache());
      assert interceptor.getHits() == 1 : "Expected one hit but received " + interceptor.getHits();
      for (CacheContainer cm : hrServ2CacheManager.values()) {
         if (cm != cacheContainer) {
            interceptor = getHitCountInterceptor(cm.getCache());
            assert interceptor.getHits() == 0 : "Expected 0 hits but got " + interceptor.getHits();
         }
      }
   }

   protected void assertNoHits() {
      for (CacheContainer cm : hrServ2CacheManager.values()) {
         HitCountInterceptor interceptor = getHitCountInterceptor(cm.getCache());
         assert interceptor.getHits() == 0 : "Expected 0 hits but got " + interceptor.getHits();
      }
   }

   protected InetSocketAddress getAddress(HotRodServer hotRodServer) {
      InetSocketAddress socketAddress = new InetSocketAddress(hotRodServer.getHost(), hotRodServer.getPort());
      addr2hrServer.put(socketAddress, hotRodServer);
      return socketAddress;
   }

   protected void resetStats() {
      for (EmbeddedCacheManager manager : cacheManagers) {
         HitCountInterceptor cmi = getHitCountInterceptor(manager.getCache());
         cmi.reset();
      }
   }

   protected void addInterceptors() {
      for (EmbeddedCacheManager manager : cacheManagers) {
         addHitCountInterceptor(manager.getCache());
      }
   }

   private void addHitCountInterceptor(Cache<Object, Object> cache) {
      HitCountInterceptor interceptor = new HitCountInterceptor();
      cache.getAdvancedCache().addInterceptor(interceptor, 1);
   }

   /**
    * @author Mircea.Markus@jboss.com
    * @since 4.1
    */
   public static class HitCountInterceptor extends CommandInterceptor{

      private volatile int invocationCount;

      @Override
      protected Object handleDefault(InvocationContext ctx, VisitableCommand command) throws Throwable {
         if (ctx.isOriginLocal()) {
            invocationCount ++;
         }
         return super.handleDefault(ctx, command);
      }

      public int getHits() {
         return invocationCount;
      }

      public void reset() {
         invocationCount = 0;
      }
   }
}
