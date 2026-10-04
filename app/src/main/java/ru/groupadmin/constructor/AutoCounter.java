package ru.groupadmin.constructor;

import org.json.JSONObject;

final class AutoCounter {
    static final class Config {
        String prefix = "";
        String suffix = "";
        long start = 1;
        long step = 1;
        int digits = 0;
    }

    static Config parse(String json) {
        Config c = new Config();
        try {
            JSONObject o = new JSONObject(json == null || json.trim().isEmpty() ? "{}" : json);
            c.prefix = o.optString("prefix", "");
            c.suffix = o.optString("suffix", "");
            c.start = o.optLong("start", 1);
            c.step = o.optLong("step", 1);
            c.digits = o.optInt("digits", 0);
        } catch (Exception ignored) {}

        if (c.start < 0) c.start = 0;
        if (c.step <= 0) c.step = 1;
        if (c.digits < 0) c.digits = 0;
        if (c.digits > 18) c.digits = 18;
        return c;
    }

    static String encode(Config c) {
        JSONObject o = new JSONObject();
        try {
            o.put("prefix", c.prefix == null ? "" : c.prefix);
            o.put("suffix", c.suffix == null ? "" : c.suffix);
            o.put("start", Math.max(0, c.start));
            o.put("step", Math.max(1, c.step));
            o.put("digits", Math.max(0, Math.min(18, c.digits)));
        } catch (Exception ignored) {}
        return o.toString();
    }

    static String format(Config c, long value) {
        String number = String.valueOf(Math.max(0, value));
        if (c.digits > 0 && number.length() < c.digits) {
            StringBuilder b = new StringBuilder();
            for (int i = number.length(); i < c.digits; i++) b.append('0');
            b.append(number);
            number = b.toString();
        }
        return (c.prefix == null ? "" : c.prefix) + number + (c.suffix == null ? "" : c.suffix);
    }

    static String example(Config c) {
        return format(c, c.start);
    }
}
