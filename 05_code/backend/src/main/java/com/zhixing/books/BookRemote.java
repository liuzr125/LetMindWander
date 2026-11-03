package com.zhixing.books;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixing.common.ApiException;
import java.io.*;
import java.net.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Bounded HTTPS requests; no redirects, credentials in errors, or private-network destinations. */
@Service
public class BookRemote {
  private final ObjectMapper json;

  public BookRemote(ObjectMapper json) {
    this.json = json;
  }

  public byte[] get(String url, int limit) {
    return call(url, null, null, limit);
  }

  public JsonNode getJson(String url, String key) {
    try {
      return json.readTree(call(url, null, key, 2000000));
    } catch (ApiException e) {
      throw e;
    } catch (Exception e) {
      throw BookText.bad("书源返回格式错误");
    }
  }

  public JsonNode getJson(String url) {
    try {
      return json.readTree(get(url, 2000000));
    } catch (ApiException e) {
      throw e;
    } catch (Exception e) {
      throw BookText.bad("书源返回的书目格式错误");
    }
  }

  public JsonNode post(String url, String key, Object body) {
    try {
      return json.readTree(call(url, json.writeValueAsBytes(body), key, 12000000));
    } catch (ApiException e) {
      throw e;
    } catch (Exception e) {
      throw BookText.bad("图像服务响应格式错误");
    }
  }

  public static URL validate(String value) throws Exception {
    URI uri = new URI(value);
    if (!"https".equals(uri.getScheme())
        || uri.getHost() == null
        || uri.getUserInfo() != null
        || (uri.getPort() != -1 && uri.getPort() != 443)
        || uri.getFragment() != null) throw BookText.bad("书源及图像服务地址必须是无凭据的公网 HTTPS 地址");
    for (InetAddress address : InetAddress.getAllByName(uri.getHost()))
      if (address.isAnyLocalAddress()
          || address.isLoopbackAddress()
          || address.isLinkLocalAddress()
          || address.isSiteLocalAddress()
          || address.isMulticastAddress()
          || ((address.getAddress()[0] & 0xfe) == 0xfc)) throw BookText.bad("不允许访问内网书源");
    return uri.toURL();
  }

  private byte[] call(String url, byte[] body, String key, int limit) {
    HttpsURLConnectionHolder holder = new HttpsURLConnectionHolder();
    try {
      HttpURLConnection c = (HttpURLConnection) validate(url).openConnection();
      holder.connection = c;
      c.setInstanceFollowRedirects(false);
      c.setConnectTimeout(8000);
      c.setReadTimeout(60000);
      c.setRequestProperty("User-Agent", "ZhixingBookLibrary/1.0");
      if (key != null && !key.isEmpty()) c.setRequestProperty("Authorization", "Bearer " + key);
      if (body != null) {
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");

        try (OutputStream out = c.getOutputStream()) {
          out.write(body);
        }
      }
      int status = c.getResponseCode();
      if (status < 200 || status >= 300)
        throw new ApiException(
            HttpStatus.BAD_GATEWAY, "BOOK_REMOTE_FAILED", "书源或图像服务未成功响应（" + status + "），未自动重发");
      if (c.getContentLengthLong() > limit) throw BookText.bad("远程书籍或图片文件过大");
      try (InputStream in = c.getInputStream();
          ByteArrayOutputStream out = new ByteArrayOutputStream()) {
        byte[] chunk = new byte[8192];
        int size;
        while ((size = in.read(chunk)) != -1) {
          if (out.size() + size > limit) throw BookText.bad("远程文件超过大小限制");
          out.write(chunk, 0, size);
        }
        return out.toByteArray();
      }
    } catch (ApiException e) {
      throw e;
    } catch (Exception e) {
      throw new ApiException(
          HttpStatus.BAD_GATEWAY, "BOOK_REMOTE_UNCERTAIN", "网络请求失败或超时；付费请求结果需核对后再重试");
    } finally {
      if (holder.connection != null) holder.connection.disconnect();
    }
  }

  private static class HttpsURLConnectionHolder {
    HttpURLConnection connection;
  }
}
