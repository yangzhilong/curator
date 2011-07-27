/*
 Copyright 2011 Netflix, Inc.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
 */
package com.netflix.curator.framework.recipes.cache;

import com.netflix.curator.framework.CuratorFramework;
import com.netflix.curator.framework.CuratorFrameworkFactory;
import com.netflix.curator.framework.recipes.BaseClassForTests;
import com.netflix.curator.retry.RetryOneTime;
import junit.framework.Assert;
import org.testng.annotations.Test;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class TestPathChildrenCache extends BaseClassForTests
{
    @Test
    public void     testModes() throws Exception
    {
        CuratorFramework client = CuratorFrameworkFactory.newClient(server.getConnectString(), new RetryOneTime(1));
        client.start();
        try
        {
            client.create().forPath("/test", new byte[0]);

            for ( PathChildrenCacheMode mode : PathChildrenCacheMode.values() )
            {
                internalTestMode(client, mode);

                client.delete().forPath("/test/one");
                client.delete().forPath("/test/two");
            }
        }
        finally
        {
            client.close();
        }
    }

    private void     internalTestMode(CuratorFramework client, PathChildrenCacheMode testMode) throws Exception
    {
        PathChildrenCache       cache = new PathChildrenCache(client, "/test", testMode);

        final CountDownLatch    latch = new CountDownLatch(2);
        cache.addListener
        (
            new PathChildrenCacheListener()
            {
                @Override
                public void handleException(CuratorFramework client, Exception exception)
                {
                }

                @Override
                public void childEvent(CuratorFramework client, PathChildrenCacheEvent event) throws Exception
                {
                    if ( event.getType() == PathChildrenCacheEvent.Type.CHILD_ADDED )
                    {
                        latch.countDown();
                    }
                }

                @Override
                public void notifyClientClosing(CuratorFramework client)
                {
                }
            }
        );
        cache.start();

        client.create().forPath("/test/one", "one".getBytes());
        client.create().forPath("/test/two", "two".getBytes());
        Assert.assertTrue(latch.await(10, TimeUnit.SECONDS));

        for ( ChildData data : cache.getCurrentData() )
        {
            switch ( testMode )
            {
                case CACHE_DATA_AND_STAT:
                {
                    Assert.assertNotNull(data.getData());
                    Assert.assertNotNull(data.getStat());
                    break;
                }

                case CACHE_DATA:
                {
                    Assert.assertNotNull(data.getData());
                    Assert.assertNull(data.getStat());
                    break;
                }

                case CACHE_PATHS_ONLY:
                {
                    Assert.assertNull(data.getData());
                    Assert.assertNull(data.getStat());
                    break;
                }
            }
        }

        cache.close();
    }

    @Test
    public void     testException() throws Exception
    {
        CuratorFramework client = CuratorFrameworkFactory.newClient(server.getConnectString(), new RetryOneTime(1));
        client.start();
        try
        {
            client.create().forPath("/test", new byte[0]);

            final CountDownLatch    latch = new CountDownLatch(1);
            PathChildrenCache       cache = new PathChildrenCache(client, "/test", PathChildrenCacheMode.CACHE_DATA_AND_STAT);
            cache.addListener
            (
                new PathChildrenCacheListener()
                {
                    @Override
                    public void handleException(CuratorFramework client, Exception exception)
                    {
                        if ( exception instanceof IllegalAccessException )
                        {
                            latch.countDown();
                        }
                    }

                    @Override
                    public void childEvent(CuratorFramework client, PathChildrenCacheEvent event) throws Exception
                    {
                        throw new IllegalAccessException();
                    }

                    @Override
                    public void notifyClientClosing(CuratorFramework client)
                    {
                    }
                }
            );
            cache.start();

            client.create().forPath("/test/one", "hey there".getBytes());
            Assert.assertTrue(latch.await(10, TimeUnit.SECONDS));

            cache.close();
        }
        finally
        {
            client.close();
        }
    }

    @Test
    public void     testBasics() throws Exception
    {
        CuratorFramework client = CuratorFrameworkFactory.newClient(server.getConnectString(), new RetryOneTime(1));
        client.start();
        try
        {
            client.create().forPath("/test", new byte[0]);

            final BlockingQueue<PathChildrenCacheEvent.Type>        events = new LinkedBlockingQueue<PathChildrenCacheEvent.Type>();
            PathChildrenCache       cache = new PathChildrenCache(client, "/test", PathChildrenCacheMode.CACHE_DATA_AND_STAT);
            cache.addListener
            (
                new PathChildrenCacheListener()
                {
                    @Override
                    public void handleException(CuratorFramework client, Exception exception)
                    {
                    }

                    @Override
                    public void childEvent(CuratorFramework client, PathChildrenCacheEvent event) throws Exception
                    {
                        if ( event.getData().getPath().equals("/test/one") )
                        {
                            events.offer(event.getType());
                        }
                    }

                    @Override
                    public void notifyClientClosing(CuratorFramework client)
                    {
                    }
                }
            );
            cache.start();

            client.create().forPath("/test/one", "hey there".getBytes());
            Assert.assertEquals(events.poll(10, TimeUnit.SECONDS), PathChildrenCacheEvent.Type.CHILD_ADDED);

            client.setData().forPath("/test/one", "sup!".getBytes());
            Assert.assertEquals(events.poll(10, TimeUnit.SECONDS), PathChildrenCacheEvent.Type.CHILD_UPDATED);
            Assert.assertEquals(new String(cache.getCurrentData("/test/one").getData()), "sup!");

            client.delete().forPath("/test/one");
            Assert.assertEquals(events.poll(10, TimeUnit.SECONDS), PathChildrenCacheEvent.Type.CHILD_REMOVED);

            cache.close();
        }
        finally
        {
            client.close();
        }
    }
}
