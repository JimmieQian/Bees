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

package qian.jimmie.cn.volley.volley.respone;


import java.io.Serializable;
import java.util.Collections;
import java.util.Map;

import qian.jimmie.cn.volley.volley.constance.HttpStatus;


/**
 * 从 stack 返回的 response 再处理 头部,实体,状态码数据
 * Data and headers returned from {@link Network#performRequest(Request)}.
 */
public class NetworkResponse implements Serializable {
    private static final long serialVersionUID = -20150728102000L;

    /**
     * Creates a new network response.
     *
     * @param statusCode    the HTTP status code
     * @param data          Response body
     * @param headers       Headers returned with this response, or null for none
     * @param notModified   True if the server returned a 304 and the data was already in cache
     * @param networkTimeMs Round-trip network time to receive network response
     */
    public NetworkResponse(int statusCode, byte[] data, Map<String, String> headers,
                           boolean notModified, long networkTimeMs, boolean hasError) {
        this.data = data;
        this.headers = headers;
        this.hasError = hasError;
        this.statusCode = statusCode;
        this.notModified = notModified;
        this.networkTimeMs = networkTimeMs;
    }

    public NetworkResponse(int statusCode, byte[] data, Map<String, String> headers,
                           boolean notModified) {
        this(statusCode, data, headers, notModified, 0, false);
    }

    public NetworkResponse(byte[] data, boolean hasError) {
        this(HttpStatus.SC_OK, data, Collections.<String, String>emptyMap(), false, 0, hasError);
    }

    public NetworkResponse(byte[] data, Map<String, String> headers) {
        this(HttpStatus.SC_OK, data, headers, false, 0, false);
    }

    /**
     * The HTTP status code.
     */
    public final int statusCode;

    /**
     * Raw data from this response.
     */
    public byte[] data;

    /**
     * Response headers.
     */
    public Map<String, String> headers;

    /**
     * True : 304 未修改
     */
    public boolean notModified;

    /**
     * 请求过程的时长
     */
    public long networkTimeMs;

    public final boolean hasError;
}

