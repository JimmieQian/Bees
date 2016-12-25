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

package qian.jimmie.cn.volley.volley.network;


import android.os.SystemClock;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;

import qian.jimmie.cn.volley.volley.Bees;
import qian.jimmie.cn.volley.volley.constance.HttpStatus;
import qian.jimmie.cn.volley.volley.core.interfaces.HttpStack;
import qian.jimmie.cn.volley.volley.effict.ByteArrayPool;
import qian.jimmie.cn.volley.volley.effict.PoolingByteArrayOutputStream;
import qian.jimmie.cn.volley.volley.exception.AuthFailureError;
import qian.jimmie.cn.volley.volley.exception.NetworkError;
import qian.jimmie.cn.volley.volley.exception.ServerError;
import qian.jimmie.cn.volley.volley.request.Request;
import qian.jimmie.cn.volley.volley.respone.NetworkResponse;

/**
 * An {@link HttpStack} based on {@link HttpURLConnection}.
 */
public class HurlStack implements HttpStack {
    private static final String TAG = "Volley";
    // 对于post的content-type需要单独处理(需要让传递的参数让服务端能够识别)
    private static final String HEADER_CONTENT_TYPE = "Content-Type";

    /**
     * 默认的缓存字节的大小(可以使字节数组高度复用)
     */
    private static int DEFAULT_POOL_SIZE = 4096;

    /**
     * ssl连接工厂
     */
    private final SSLSocketFactory mSslSocketFactory;

    /**
     * 字节池,减少内存分配,高复用
     */
    protected final ByteArrayPool mPool;


    public HurlStack() {
        this(null);
    }


    public HurlStack(SSLSocketFactory sslSocketFactory, ByteArrayPool pool) {
        this.mSslSocketFactory = sslSocketFactory;
        this.mPool = pool;
    }

    public HurlStack(SSLSocketFactory sslSocketFactory) {
        this(sslSocketFactory, new ByteArrayPool(DEFAULT_POOL_SIZE));
    }

    @Override
    public NetworkResponse performRequest(Request<?> request, Map<String, String> additionalHeaders) throws IOException, ServerError, NetworkError, AuthFailureError {
        NetworkResponse response = null;
        HttpURLConnection connection = null;
        long requestStart = SystemClock.elapsedRealtime();
        try {

            // 添加查询头部信息
            String url = request.getUrl();

            HashMap<String, String> map = new HashMap<>();
            // 放入请求中的头部信息
            map.putAll(request.getHeaders());
            // 放入缓存中的头部信息()
            map.putAll(additionalHeaders);

            // 新建URL对象
            URL parsedUrl = new URL(url);
            // 从URL和请求中获取http连接
            connection = openConnection(parsedUrl, request);

            // 通过 addRequestProperty 来添加请求的头部信息
            for (String headerName : map.keySet()) {
                connection.addRequestProperty(headerName, map.get(headerName));
            }
            // 设置请求方式
            setConnectionParametersForRequest(connection, request);

            // 建立连接
            InputStream in = connection.getInputStream();
            int contentLength = connection.getContentLength();
            byte[] body = streamToBytes(in, mPool, contentLength);

            // getResponseCode 中调用了 getInputStream 说明请求已经完成
            int responseCode = connection.getResponseCode();

            // 无法检索到返回码
            if (responseCode == -1) {
                throw new IOException("Could not retrieve response code from HttpUrlConnection.");
            }
            // 获取实体信息
            if (!hasResponseBody(request.getMethod(), responseCode)) {
                // 请求以收到应答(获取应答实体) 耗时较长...
                body = new byte[0];
            }
            Map<String, String> responeHeaders = new HashMap<>();
            // 获取头部信息
            for (Map.Entry<String, List<String>> header : connection.getHeaderFields().entrySet()) {
                if (header.getKey() == null) continue;
                List<String> ls = header.getValue();
                for (String s : ls) {
                    responeHeaders.put(header.getKey(), s);
                }
            }
            response = new NetworkResponse(responseCode, body, responeHeaders,
                    false, SystemClock.elapsedRealtime() - requestStart, false);
        } catch (IOException ioe) {
            if (connection == null) throw new IOException();
            byte[] errorBody = streamToBytes(connection.getErrorStream(), mPool, connection.getContentLength());
            if (errorBody == null) throw new IOException();
            response = new NetworkResponse(errorBody, true);
            return response;
        } finally {
            if (connection != null)
                connection.disconnect();
        }
        return response;
    }

    /**
     * 判断响应是否包含实体信息
     * 不是head请求
     * 大于等于200的响应
     * 不是 204 no content
     * 不是 304 not motified (缓存?)
     *
     * @param requestMethod request method
     * @param responseCode  response status code
     * @return whether the response has a body
     * @see <a href="https://tools.ietf.org/html/rfc7230#section-3.3">RFC 7230 section 3.3</a>
     */
    private static boolean hasResponseBody(int requestMethod, int responseCode) {
        return requestMethod != Bees.Method.HEAD
                && responseCode >= HttpStatus.SC_OK
                && responseCode != HttpStatus.SC_NO_CONTENT
                && responseCode != HttpStatus.SC_NOT_MODIFIED;
    }


    protected HttpURLConnection createConnection(URL url) throws IOException {
        return (HttpURLConnection) url.openConnection();
    }

    /**
     * Opens an {@link HttpURLConnection} with parameters.
     *
     * @param url .
     * @return an open connection
     * @throws IOException
     */
    private HttpURLConnection openConnection(URL url, Request<?> request) throws IOException {
        // 从URL中创建连接
        HttpURLConnection connection = createConnection(url);

        // 将重定向 交给httpURLConnection处理,无需自定义
        connection.setInstanceFollowRedirects(true);
        // 设置连接超时 和 读取超时
        int timeoutMs = request.getTimeoutMs();
        connection.setConnectTimeout(timeoutMs);
        connection.setReadTimeout(timeoutMs);
        connection.setUseCaches(false);
        connection.setDoInput(true);

        // 由使用者提供 SslSocketFactory ,用于安全传输
        // use caller-provided custom SslSocketFactory, if any, for HTTPS
        if ("https".equals(url.getProtocol()) && mSslSocketFactory != null) {
            ((HttpsURLConnection) connection).setSSLSocketFactory(mSslSocketFactory);
        }
        return connection;
    }

    /**
     * 根据request来设置请求的方式
     *
     * @param connection 连接
     * @param request    请求
     * @throws IOException
     * @throws AuthFailureError
     */
    @SuppressWarnings("deprecation")
    private static void setConnectionParametersForRequest(
            HttpURLConnection connection, Request<?> request) throws IOException, AuthFailureError {
        switch (request.getMethod()) {
            // 无需设置get,因为默认使用get
            case Bees.Method.GET:
                connection.setRequestMethod("GET");
                break;
            // 有body数据
            case Bees.Method.POST:
                connection.setRequestMethod("POST");
                addBodyIfExists(connection, request);
                break;
            case Bees.Method.HEAD:
                connection.setRequestMethod("HEAD");
                break;
            default:
                throw new IllegalStateException("Unknown method type.");
        }
    }

    /**
     * 如果 body(实体)存在,添加body数据到http连接中
     * connection.getOutputStream 获取body的写入流
     *
     * @param connection http连接
     * @param request    请求
     * @throws IOException
     * @throws AuthFailureError
     */
    private static void addBodyIfExists(HttpURLConnection connection, Request<?> request)
            throws IOException, AuthFailureError {
        byte[] body = request.getBody();
        if (body != null) {
            connection.setDoOutput(true);
            connection.setChunkedStreamingMode(0);
            connection.addRequestProperty(HEADER_CONTENT_TYPE, request.getBodyContentType());
            DataOutputStream out = new DataOutputStream(connection.getOutputStream());
            out.write(body);
            out.close();
        }
    }

    /**
     * 将实体InputStream转化为byte[]
     * Reads the contents of HttpEntity into a byte[].
     */
    private static byte[] streamToBytes(InputStream in, ByteArrayPool pool, int size) throws IOException {
        PoolingByteArrayOutputStream bytes = new PoolingByteArrayOutputStream(pool, size);
        byte[] buffer = null;
        try {
            if (in == null) {
                throw new IOException();
            }
            buffer = pool.getBuf(1024);
            int count;
            while ((count = in.read(buffer)) != -1) {
                bytes.write(buffer, 0, count);
            }
            return bytes.toByteArray();
        } finally {
            pool.returnBuf(buffer);
            bytes.close();
        }
    }
}
