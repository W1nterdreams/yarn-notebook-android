package ru.yarnnotebook.app;

import com.vk.api.sdk.VK;
import com.vk.api.sdk.VKApiCallback;
import com.vk.api.sdk.VKApiJSONResponseParser;
import com.vk.api.sdk.VKApiManager;
import com.vk.api.sdk.VKMethodCall;
import com.vk.api.sdk.internal.ApiCommand;

import org.json.JSONObject;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public final class VkApi {
    public interface Callback {
        void success(Object response);
        void fail(Exception error);
    }

    private VkApi() { }

    public static void call(String method, Map<String, Object> params, Callback callback) {
        Map<String, String> safe = stringify(params);
        VK.execute(new RawCommand(method, safe), new VKApiCallback<Object>() {
            @Override public void success(Object result) {
                callback.success(result);
            }

            @Override public void fail(Exception error) {
                callback.fail(error);
            }
        });
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

    private static final class RawCommand extends ApiCommand<Object> {
        private final String method;
        private final Map<String, String> params;

        RawCommand(String method, Map<String, String> params) {
            this.method = method;
            this.params = params;
        }

        @Override
        protected Object onExecute(VKApiManager manager) throws java.io.IOException, com.vk.api.sdk.exceptions.VKApiException, InterruptedException {
            VKMethodCall.Builder builder = new VKMethodCall.Builder()
                    .method(method)
                    .version(manager.getConfig().getVersion())
                    .args(params);
            VKMethodCall call = builder.build();
            return manager.execute(call, new VKApiJSONResponseParser<Object>() {
                @Override public Object parse(JSONObject responseJson) {
                    Object value = responseJson.opt("response");
                    return value == JSONObject.NULL ? null : value;
                }
            });
        }
    }
}
