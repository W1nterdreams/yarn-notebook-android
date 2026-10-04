package ru.groupadmin.constructor;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class RepeatGroup {
    static final String[] SUB_TYPES = {
            FieldDef.TEXT, FieldDef.INTEGER, FieldDef.DECIMAL, FieldDef.PRICE, FieldDef.CHECKBOX
    };

    static final class SubField {
        String name = "";
        String type = FieldDef.TEXT;
        String unit = "";
    }

    static List<SubField> parseConfig(String json) {
        List<SubField> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(json == null || json.isEmpty() ? "[]" : json);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) continue;
                SubField s = new SubField();
                s.name = o.optString("name", "").trim();
                s.type = o.optString("type", FieldDef.TEXT);
                s.unit = o.optString("unit", "").trim();
                if (!s.name.isEmpty()) out.add(s);
            }
        } catch (Exception ignored) {}
        return out;
    }

    static String encodeConfig(List<SubField> fields) {
        JSONArray a = new JSONArray();
        for (SubField s : fields) {
            if (s == null || s.name == null || s.name.trim().isEmpty()) continue;
            JSONObject o = new JSONObject();
            try {
                o.put("name", s.name.trim());
                o.put("type", s.type == null ? FieldDef.TEXT : s.type);
                o.put("unit", s.unit == null ? "" : s.unit.trim());
                a.put(o);
            } catch (Exception ignored) {}
        }
        return a.toString();
    }

    static JSONArray parseRows(String raw) {
        try { return new JSONArray(raw == null || raw.isEmpty() ? "[]" : raw); }
        catch (Exception e) { return new JSONArray(); }
    }

    static String display(String configJson, String raw) {
        List<SubField> fields = parseConfig(configJson);
        JSONArray rows = parseRows(raw);
        List<String> renderedRows = new ArrayList<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) continue;
            List<String> parts = new ArrayList<>();
            for (SubField s : fields) {
                String v = row.optString(s.name, "").trim();
                if (FieldDef.CHECKBOX.equals(s.type)) {
                    if ("1".equals(v) || "true".equalsIgnoreCase(v)) v = "Да";
                    else if (!v.isEmpty()) v = "Нет";
                }
                if (v.isEmpty()) continue;
                if (!s.unit.isEmpty()) v += " " + s.unit;
                parts.add(s.name + ": " + v);
            }
            if (!parts.isEmpty()) renderedRows.add(String.join(", ", parts));
        }
        return String.join("; ", renderedRows);
    }

    static String compositionConfig() {
        List<SubField> fields = new ArrayList<>();
        SubField material = new SubField();
        material.name = "Материал";
        material.type = FieldDef.TEXT;
        fields.add(material);
        SubField share = new SubField();
        share.name = "Доля";
        share.type = FieldDef.DECIMAL;
        share.unit = "%";
        fields.add(share);
        return encodeConfig(fields);
    }
}
