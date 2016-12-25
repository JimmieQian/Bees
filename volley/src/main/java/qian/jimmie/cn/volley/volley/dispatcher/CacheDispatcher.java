/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package qian.jimmie.cn.volley.volley.dispatcher;


import java.util.concurrent.BlockingQueue;

import qian.jimmie.cn.volley.volley.core.interfaces.Cache;
import qian.jimmie.cn.volley.volley.core.interfaces.ResponseDelivery;
import qian.jimmie.cn.volley.volley.exception.VolleyLog;
import qian.jimmie.cn.volley.volley.request.Request;
import qian.jimmie.cn.volley.volley.respone.NetworkResponse;
import qian.jimmie.cn.volley.volley.respone.Response;

/**
 * Provides a thread for performing cache triage on a queue of requests.
 * <p>
 * Requests added to the specified cache queue are resolved from cache.
 * Any deliverable response is posted back to the caller via a
 * {@link ResponseDelivery}.  Cache misses and responses that require
 * refresh are enqueued on the specified network queue for processing
 * by a {@link NetworkDispatcher}.
 */
public class CacheDispatcher extends Thread {
    private static final String TAG = "volley";

    private static final boolean DEBUG = VolleyLog.DEBUG;

    /**
     * cache分发队列
     */
    private final BlockingQueue<Request<?>> mCacheQueue;

    /**
     * 网络分发队列
     */
    private final BlockingQueue<Request<?>> mNetworkQueue;


    private final Cache mCache;

    /**
     * 响应分发
     */
    private final ResponseDelivery mDelivery;

    /**
     * 是否退出缓存分发线程
     */
    private volatile boolean mQuit = false;

    /**
     * @param cacheQueue   Queue of incoming requests for triage
     * @param networkQueue Queue to post requests that require network to
     * @param cache        Cache interface to use for resolution
     * @param delivery     Delivery interface to use for posting responses
     */
    public CacheDispatcher(
            BlockingQueue<Request<?>> cacheQueue, BlockingQueue<Request<?>> networkQueue,
            Cache cache, ResponseDelivery delivery) {
        mCacheQueue = cacheQueue;
        mNetworkQueue = networkQueue;
        mCache = cache;
        mDelivery = delivery;
    }

    /**
     * 退出分发线程,如果还有任务在执行,不保证能够执行
     */
    public void quit() {
        mQuit = true;
        interrupt();
    }

    @Override
    public void run() {
        if (DEBUG) VolleyLog.v("start new dispatcher");
        // 设置线程优先级 10
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);

        // 缓存初始化
        mCache.initialize();

        Request<?> request;
        while (true) {
            // 为了释放之前的请求的引用,避免内存泄漏
            request = null;
            try {
                // 从缓存中取一条请求
                request = mCacheQueue.take();
            } catch (InterruptedException e) {
                // 如果是手动退出,则直接返回
                if (mQuit) {
                    return;
                }
                // 如果是其他意外打断,则继续下一个请求
                continue;
            }


            try {
                request.addMarker("cache-queue-take");

                // 如果那条请求被取消,则直接结束请求
                if (request.isCanceled()) {
                    // 结束请求
                    request.finish("cache-discard-canceled");
                    continue;
                }

                // 试着从缓存中检索请求
                Cache.Entry entry = mCache.get(request.getCacheKey());
                // 如果检索不到,则将请求添加到网络请求队列
                if (entry == null) {
                    request.addMarker("cache-miss");
                    // Cache miss; send off to the network dispatcher.
                    mNetworkQueue.put(request);
                    continue;
                }

                // 缓存处理和分发
                if (!entry.refreshNeeded()) {
                    cache_hit(request, entry);
                } else if (!entry.isExpired()) {
                    cache_need_refresh(request, entry);
                } else {
                    cache_expired(request, entry);
                }
            } catch (Exception e) {
                VolleyLog.e(e, "Unhandled exception %s", e.toString());
            }
        }
    }

    /**
     * 如果没有到过期时间,直接分发
     */
    private void cache_hit(Request<?> request, Cache.Entry entry) {
        request.addMarker("cache-hit");
        Response<?> response = request.parseNetworkResponse(
                new NetworkResponse(entry.data, entry.responseHeaders));
        mDelivery.postResponse(request, response);
    }

    /**
     * 过了过期时间 , 但是没过 过期时间+stale-while-revalidate
     * 直接分发缓存,但是还需要 去网络请求刷新缓存
     */
    private void cache_need_refresh(Request<?> request, Cache.Entry entry) {
        request.addMarker("cache-hit-refresh-needed");
        request.setCacheEntry(entry);
        Response<?> response = request.parseNetworkResponse(
                new NetworkResponse(entry.data, entry.responseHeaders));
        // 该值为true表示 在相应分发过程中,请求不能结束
        response.intermediate = true;
        // 为了能够修改 request,有新启一个request
        final Request<?> finalRequest = request;
        // 分发相应,并且执行runnable
        mDelivery.postResponse(request, response, new Runnable() {
            @Override
            public void run() {
                try {
                    mNetworkQueue.put(finalRequest);
                } catch (InterruptedException e) {
                    // Not much we can do about this.
                }
            }
        });
    }

    private void cache_expired(Request<?> request, Cache.Entry entry) throws InterruptedException {
        request.addMarker("cache-hit-expired");
        request.setCacheEntry(entry);
        mNetworkQueue.put(request);
    }
}
