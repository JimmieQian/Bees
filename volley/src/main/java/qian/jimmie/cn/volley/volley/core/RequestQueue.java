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

package qian.jimmie.cn.volley.volley.core;


import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import qian.jimmie.cn.volley.volley.Bees;
import qian.jimmie.cn.volley.volley.core.interfaces.Cache;
import qian.jimmie.cn.volley.volley.core.interfaces.Network;
import qian.jimmie.cn.volley.volley.core.interfaces.ResponseDelivery;
import qian.jimmie.cn.volley.volley.dispatcher.CacheDispatcher;
import qian.jimmie.cn.volley.volley.dispatcher.ExecutorDelivery;
import qian.jimmie.cn.volley.volley.dispatcher.NetworkDispatcher;
import qian.jimmie.cn.volley.volley.exception.VolleyLog;
import qian.jimmie.cn.volley.volley.request.Request;

/**
 * A request dispatch queue with a thread pool of dispatchers.
 * <p>
 * Calling {@link #add(Request)} will enqueue the given Request for dispatch,
 * resolving from either cache or network on a worker thread, and then delivering
 * a parsed response on the main thread.
 */
public class RequestQueue {
    private static final String TAG = "Volley";

    /**
     * 请求的结束的回调
     */
    public static interface RequestFinishedListener<T> {
        /**
         * 一个请求结束时被调用
         */
        void onRequestFinished(Request<T> request);
    }

    /**
     * 使用原子类型的整型数组,保证线程安全
     * 用于获取一个单调递增的序列
     */
    private AtomicInteger mSequenceGenerator = new AtomicInteger();

    /**
     * 暂存区域的等待队列
     */
    private final Map<String, Queue<Request<?>>> mWaitingRequests =
            new HashMap<String, Queue<Request<?>>>();

    /**
     * 未结束的所有请求都在这个队列中
     */
    private final Set<Request<?>> mCurrentRequests = new HashSet<Request<?>>();

    /**
     * 走缓存的队列
     * 使用优先级队列(里面存储的对象必须是实现Comparable接口。
     * 队列通过这个接口的compare方法确定对象的priority。)
     */
    private final PriorityBlockingQueue<Request<?>> mCacheQueue =
            new PriorityBlockingQueue<Request<?>>();

    /**
     * 走网络请求的队列
     */
    private final PriorityBlockingQueue<Request<?>> mNetworkQueue =
            new PriorityBlockingQueue<Request<?>>();

    /**
     * 网络请求默认的处理(分发)线程个数 (4)
     * Number of network request dispatcher threads to start.
     */
    private static final int DEFAULT_NETWORK_THREAD_POOL_SIZE = 4;

    /**
     * 缓存接口,为了检索和存储响应
     */
    private final Cache mCache;

    /**
     * 处理请求的接口
     */
    private final Network mNetwork;

    /**
     * 响应分发机制
     */
    private final ResponseDelivery mDelivery;

    /**
     * 网络请求分发
     */
    private NetworkDispatcher[] mDispatchers;

    /**
     * 缓存分发
     */
    private CacheDispatcher mCacheDispatcher;

    /**
     * 响应回调队列
     */
//    private List<RequestFinishedListener> mFinishedListeners =
//            new ArrayList<>();
    public RequestQueue(Cache cache, Network network, int threadPoolSize,
                        ResponseDelivery delivery) {
        mCache = cache;
        mNetwork = network;
        // 网络分发器
        mDispatchers = new NetworkDispatcher[threadPoolSize];
        mDelivery = delivery;
    }

    public RequestQueue(Cache cache, Network network, int threadPoolSize) {
        this(cache, network, threadPoolSize,
                // 传入持有mainLooper的handler,进行相应分发
                new ExecutorDelivery(new Handler(Looper.getMainLooper())));
    }

    public RequestQueue(Cache cache, Network network) {
        // 使用默认县城池大小 4个
        this(cache, network, DEFAULT_NETWORK_THREAD_POOL_SIZE);
    }

    /**
     * 请求队列开始分发轮询工作
     */
    public void start() {
        // 确保分发器没有在工作
        stop();

        // 缓存分发
        mCacheDispatcher = new CacheDispatcher(mCacheQueue, mNetworkQueue, mCache, mDelivery);
        mCacheDispatcher.start();

        // 网络分发
        for (int i = 0; i < mDispatchers.length; i++) {
            NetworkDispatcher networkDispatcher = new NetworkDispatcher(mNetworkQueue, mNetwork,
                    mCache, mDelivery);
            mDispatchers[i] = networkDispatcher;
            networkDispatcher.start();
        }
    }

    /**
     * Stops the cache and network dispatchers.
     */
    public void stop() {
        if (mCacheDispatcher != null) {
            mCacheDispatcher.quit();
        }
        for (int i = 0; i < mDispatchers.length; i++) {
            if (mDispatchers[i] != null) {
                mDispatchers[i].quit();
            }
        }
    }

    /**
     * 获取序列号,
     */
    public int getSequenceNumber() {
        // 自增1
        return mSequenceGenerator.incrementAndGet();
    }

    public Cache getCache() {
        return mCache;
    }

    public interface RequestFilter {
        boolean apply(Request<?> request);
    }

    public void cancelAll(RequestFilter filter) {
        synchronized (mCurrentRequests) {
            for (Request<?> request : mCurrentRequests) {
                if (filter.apply(request)) {
                    request.cancel();
                }
            }
        }
    }

    public void cancelAll(final Object tag) {
        if (tag == null) {
            throw new IllegalArgumentException("Cannot cancelAll with a null tag");
        }
        cancelAll(new RequestFilter() {
            @Override
            public boolean apply(Request<?> request) {
                return request.getTag() == tag;
            }
        });
    }

    public <T> Request<T> add(Request<T> request) {
        // 设置请求队列,并且将请求添加到请求列表中
        request.setRequestQueue(this);
        addQuaryParams(request);
        synchronized (mCurrentRequests) {
            mCurrentRequests.add(request);
        }

        // 设置序列号(用于保证序列递增 FIFO)
        request.setSequence(getSequenceNumber());
        request.addMarker("add-to-queue");

        // 如果是不可缓存的,则加入网络请求队列
        if (!request.shouldCache()) {
            mNetworkQueue.add(request);
            return request;
        }

        // Insert request into stage if there's already a request with the same cache key in flight.
        synchronized (mWaitingRequests) {
            String cacheKey = request.getCacheKey();
            if (mWaitingRequests.containsKey(cacheKey)) {
                // 等待队列中已经有该请求
                // There is already a request in flight. Queue up.
                Queue<Request<?>> stagedRequests = mWaitingRequests.get(cacheKey);
                if (stagedRequests == null) {
                    stagedRequests = new LinkedList<Request<?>>();
                }
                stagedRequests.add(request);
                mWaitingRequests.put(cacheKey, stagedRequests);
                if (VolleyLog.DEBUG) {
                    VolleyLog.v("Request for cacheKey=%s is in flight, putting on hold.", cacheKey);
                }
            } else {
                // Insert 'null' queue for this cacheKey, indicating there is now a request in
                // flight.
                mWaitingRequests.put(cacheKey, null);
                mCacheQueue.add(request);
            }
            return request;
        }
    }

    /**
     * 被 {@link Request#finish(String)} 调用, 表明请求已结束
     * <p>
     * <p>Releases waiting requests for <code>request.getCacheKey()</code> if
     * <code>request.shouldCache()</code>.</p>
     */
    public <T> void finish(Request<T> request) {
        // Remove from the set of requests currently being processed.
        // 从 set(请求总队列) 队列中移除.
        // 使用同步块
        synchronized (mCurrentRequests) {
            mCurrentRequests.remove(request);
        }
        // 回调所有在结束队列中的请求
        // ? 为何没有移除 监控的操作?
//        synchronized (mFinishedListeners) {
//            for (RequestFinishedListener<T> listener : mFinishedListeners) {
//                listener.onRequestFinished(request);
//            }
//        }

        // 缓存处理
        if (request.shouldCache()) {
            synchronized (mWaitingRequests) {
                String cacheKey = request.getCacheKey();
                // 结束时,根据cacheKey移除等待的请求
                // 释放缓存队列中的cacheKey对应的请求队列
                Queue<Request<?>> waitingRequests = mWaitingRequests.remove(cacheKey);
                VolleyLog.e("waitingRequests == %s", cacheKey);
                if (waitingRequests != null) {
                    if (VolleyLog.DEBUG) {
                        VolleyLog.v("Releasing %d waiting requests for cacheKey=%s.",
                                waitingRequests.size(), cacheKey);
                    }
                    // 将缓存队列中,结束的cacheKey对应的请求
                    mCacheQueue.addAll(waitingRequests);
                }
            }
        }
    }

//    public <T> void addRequestFinishedListener(RequestFinishedListener<T> listener) {
//        synchronized (mFinishedListeners) {
//            mFinishedListeners.add(listener);
//        }
//    }
//
//    /**
//     * Remove a RequestFinishedListener. Has no effect if listener was not previously added.
//     */
//    public <T> void removeRequestFinishedListener(RequestFinishedListener<T> listener) {
//        synchronized (mFinishedListeners) {
//            mFinishedListeners.remove(listener);
//        }
//    }

    private static void addQuaryParams(Request<?> request) {
        if (Bees.Method.GET != request.getMethod() && Bees.Method.HEAD != request.getMethod())
            return;
        Map<String, String> params = request.getParams();
        if (params == null) return;
        String oUrl = request.getOriginUrl();
        String symbol = oUrl.contains("?") ? "" : "?";
        StringBuilder encodeParams = new StringBuilder(symbol);
        for (String key : params.keySet())
            encodeParams.append(key).append("=").append(params.get(key)).append("&");
        request.setUrl(request.getUrl() + encodeParams.toString());
    }
}
