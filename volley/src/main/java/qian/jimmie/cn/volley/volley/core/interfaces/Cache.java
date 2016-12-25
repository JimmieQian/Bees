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

package qian.jimmie.cn.volley.volley.core.interfaces;

import java.util.Collections;
import java.util.Map;

/**
 * An interface for a cache keyed by a String with a byte array as data.
 */
public interface Cache {
    /**
     * Retrieves an entry from the cache.
     *
     * @param key Cache key
     * @return An {@link Entry} or null in the event of a cache miss
     */
    public Entry get(String key);

    /**
     * Adds or replaces an entry to the cache.
     *
     * @param key   Cache key
     * @param entry Data to store and metadata for cache coherency, TTL, etc.
     */
    public void put(String key, Entry entry);

    /**
     * Performs any potentially long-running actions needed to initialize the cache;
     * will be called from a worker thread.
     */
    public void initialize();

    /**
     * Invalidates an entry in the cache.
     *
     * @param key        Cache key
     * @param fullExpire True to fully expire the entry, false to soft expire
     */
    public void invalidate(String key, boolean fullExpire);

    /**
     * Removes an entry from the cache.
     *
     * @param key Cache key
     */
    public void remove(String key);

    /**
     * Empties the cache.
     */
    public void clear();

    /**
     * Data and metadata for an entry returned by the cache.
     */
    public static class Entry {
        /**
         * 缓存的响应数据
         */
        public byte[] data;

        /**
         * 在典型用法中，当一个URL被请求，Web服务器会返回资源和其相应的ETag值，它会被放置在HTTP的“ETag”字段中：
         * ETag: "686897696a7c876b7e"
         * 然后，客户端可以决定是否缓存这个资源和它的ETag。以后，如果客户端想再次请求相同的URL，将会发送一个包含已保存的ETag和“If-None-Match”字段的请求。
         * If-None-Match: "686897696a7c876b7e"
         * 客户端请求之后，服务器可能会比较客户端的ETag和当前版本资源的ETag。如果ETag值匹配，这就意味着资源没有改变，服务器便会发送回一个极短的响应，
         * 包含HTTP “304 未修改”的状态。304状态告诉客户端，它的缓存版本是最新的，并应该使用它。
         * 然而，如果ETag的值不匹配，这就意味着资源很可能发生了变化，那么，一个完整的响应就会被返回，
         * 包括资源的内容，就好像ETag没有被使用。这种情况下，客户端可以用新返回的资源和新的ETag替代先前的缓存版本。
         */
        public String etag;

        /**
         * 请求数据时,服务端的时间
         */
        public long serverDate;

        /**
         * 请求内容的最后修改时间
         */
        public long lastModified;

        /**
         * 缓存时间 = 过期时间 + stale-while-revalidate
         */
        public long ttl;

        /**
         * 软缓存时间  = 过期时间
         */
        public long softTtl;

        /**
         * Immutable response headers as received from server; must be non-null.
         */
        public Map<String, String> responseHeaders = Collections.emptyMap();

        /**
         * true: entry已过期
         */
        public boolean isExpired() {
            return this.ttl < System.currentTimeMillis();
        }

        /**
         * True : 需要从数据源中重新获取
         */
        public boolean refreshNeeded() {
            return this.softTtl < System.currentTimeMillis();
        }
    }

}
