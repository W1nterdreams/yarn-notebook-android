package ru.yarnnotebook.app;

import com.vk.id.AccessToken;
import com.vk.id.VKID;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public final class VkApi {
    private static final String API_BASE = "https://api.vk.ru/method/";
    private static final String API_VERSION = "5.199";

    public interface Callback {
        void success(Object response);
        void fail(Exception error);
    }

    private VkApi() { }

    public static void call(String method, Map<String, Object> params, Callback callback) {
        new Thread(() -> {
            try {
                AccessToken accessToken = VKID.Companion.getInstance().getAccessToken();
                if (accessToken == null || accessToken.getToken() == null || accessToken.getToken().isEmpty()) {
                    throw new IOException("Нет действующего токена VK ID. Выполните вход заново.");
                }

                Map<String, String> safe = stringify(params);
                safe.put("access_token", accessToken.getToken());
                safe.put("v", API_VERSION);

                JSONObject root = execute(method, safe);
                JSONObject error = root.optJSONObject("error");
                if (error != null) {
                    int code = error.optInt("error_code", 0);
                    String message = error.optString("error_msg", "Неизвестная ошибка VK API");
                    throw new IOException("VK API error " + code + ": " + message);
                }

                Object value = root.opt("response");
                callback.success(value == JSONObject.NULL ? null : value);
            } catch (Exception error) {
                callback.fail(error);
            }
        }, "VkApi-" + method).start();
    }

    private static JSONObject execute(String method, Map<String, String> params) throws Exception {
        URL url = new URL(API_BASE + method);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        connection.setRequestProperty("Accept", "application/json");

        byte[] body = formEncode(params).getBytes(StandardCharsets.UTF_8);
        connection.setFixedLengthStreamingMode(body.length);
        try (OutputStream out = connection.getOutputStream()) {
            out.write(body);
        }

        int status = connection.getResponseCode();
        InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        if (stream == null) {
            connection.disconnect();
            throw new IOException("VK API: пустой HTTP-ответ " + status);
        }

        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) response.append(line);
        } finally {
            connection.disconnect();
        }

        if (response.length() == 0) throw new IOException("VK API вернул пустой ответ");
        return new JSONObject(response.toString());
    }

    private static String formEncode(Map<String, String> params) throws Exception {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (out.length() > 0) out.append('&');
            out.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8.name()));
            out.append('=');
            out.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8.name()));
        }
        return out.toString();
    }

    private static Map<String, String> stringify(Map<String, Object> source) {
        Map<String, String> out = new HashMap<>();
        if (source == null) return out;
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            Object value = entry.getValue();
            if (value == null) continue;
            if (value instanceof Boolean) {
                out.put(entry.getKey(), ((Boolean) value) ? "1" : "0");
            } else if (value instanceof Collection) {
                StringBuilder joined = new StringBuilder();
                for (Object item : (Collection<?>) value) {
                    if (joined.length() > 0) joined.append(',');
                    joined.append(String.valueOf(item));
                }
                out.put(entry.getKey(), joined.toString());
            } else {
                out.put(entry.getKey(), String.valueOf(value));
            }
        }
        return out;
    }
}
