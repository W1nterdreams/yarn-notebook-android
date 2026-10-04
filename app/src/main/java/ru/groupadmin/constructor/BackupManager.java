package ru.groupadmin.constructor;

import android.content.Context;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class BackupManager {
    static void exportCatalog(Context context, DbHelper db, long catalogId, Uri uri) throws Exception {
        Catalog c = db.getCatalog(catalogId);
        if (c == null) throw new IllegalArgumentException("База не найдена");

        JSONObject root = new JSONObject();
        root.put("format", "group-admin-constructor");
        root.put("version", 2);
        root.put("exported_at", System.currentTimeMillis());
        JSONObject jc = new JSONObject();
        jc.put("name", c.name);
        JSONArray templates = new JSONArray();
        for (CardTemplate t : db.getTemplates(c.id)) {
            JSONObject jt = new JSONObject();
            jt.put("name", t.name);
            jt.put("output_template", t.outputTemplate == null ? "" : t.outputTemplate);
            jt.put("quantity_unit", t.quantityUnit == null ? "шт." : t.quantityUnit);
            JSONArray fields = new JSONArray();
            List<FieldDef> defs = db.getFields(t.id, true);
            for (FieldDef f : defs) {
                JSONObject jf = new JSONObject();
                jf.put("old_id", f.id);
                jf.put("name", f.name);
                jf.put("type", f.type);
                jf.put("position", f.position);
                jf.put("required", f.required);
                jf.put("unit", f.unit);
                jf.put("default_value", f.defaultValue);
                jf.put("options_json", f.optionsJson);
                jf.put("formula", f.formula);
                jf.put("show_in_list", f.showInList);
                jf.put("searchable", f.searchable);
                jf.put("archived", f.archived);
                fields.put(jf);
            }
            jt.put("fields", fields);

            JSONArray records = new JSONArray();
            for (RecordItem r : db.getRecords(t.id)) {
                JSONObject jr = new JSONObject();
                jr.put("status", r.status);
                jr.put("quantity", r.quantity);
                jr.put("created_at", r.createdAt);
                jr.put("updated_at", r.updatedAt);
                JSONObject vals = new JSONObject();
                for (Map.Entry<Long, String> e : db.getValues(r.id).entrySet()) {
                    vals.put(String.valueOf(e.getKey()), e.getValue());
                }
                jr.put("values", vals);
                records.put(jr);
            }
            jt.put("records", records);
            templates.put(jt);
        }
        jc.put("templates", templates);
        root.put("catalog", jc);

        try (OutputStream out = context.getContentResolver().openOutputStream(uri, "wt")) {
            if (out == null) throw new IllegalStateException("Не удалось открыть файл");
            out.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
        }
    }

    static long importCatalog(Context context, DbHelper db, Uri uri) throws Exception {
        StringBuilder b = new StringBuilder();
        try (InputStream in = context.getContentResolver().openInputStream(uri);
             BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) b.append(line).append('\n');
        }
        JSONObject root = new JSONObject(b.toString());
        if (!"group-admin-constructor".equals(root.optString("format"))) throw new IllegalArgumentException("Неизвестный формат файла");
        JSONObject jc = root.getJSONObject("catalog");
        long catalogId = db.createCatalog(jc.optString("name", "Импортированная база") + " · импорт");

        JSONArray templates = jc.optJSONArray("templates");
        if (templates == null) return catalogId;
        for (int ti = 0; ti < templates.length(); ti++) {
            JSONObject jt = templates.getJSONObject(ti);
            long templateId = db.createTemplate(catalogId, jt.optString("name", "Тип товара"), jt.optString("quantity_unit", "шт."));
            db.setOutputTemplate(templateId, jt.optString("output_template", ""));
            Map<Long, Long> fieldMap = new HashMap<>();
            JSONArray fields = jt.optJSONArray("fields");
            if (fields != null) {
                for (int fi = 0; fi < fields.length(); fi++) {
                    JSONObject jf = fields.getJSONObject(fi);
                    FieldDef f = new FieldDef();
                    f.templateId = templateId;
                    f.name = jf.optString("name", "Поле");
                    f.type = jf.optString("type", FieldDef.TEXT);
                    f.position = jf.optInt("position", (fi + 1) * 10);
                    f.required = jf.optBoolean("required", false);
                    f.unit = jf.optString("unit", "");
                    f.defaultValue = jf.optString("default_value", "");
                    f.optionsJson = jf.optString("options_json", "[]");
                    f.formula = jf.optString("formula", "");
                    f.showInList = jf.optBoolean("show_in_list", true);
                    f.searchable = jf.optBoolean("searchable", true);
                    f.archived = jf.optBoolean("archived", false);
                    long newId = db.saveField(f);
                    fieldMap.put(jf.optLong("old_id", -1), newId);
                }
            }
            JSONArray records = jt.optJSONArray("records");
            if (records != null) {
                for (int ri = 0; ri < records.length(); ri++) {
                    JSONObject jr = records.getJSONObject(ri);
                    long recordId = db.createRecord(templateId);
                    JSONObject vals = jr.optJSONObject("values");
                    if (vals != null) {
                        java.util.Iterator<String> keys = vals.keys();
                        while (keys.hasNext()) {
                            String k = keys.next();
                            long oldId;
                            try { oldId = Long.parseLong(k); } catch (Exception e) { continue; }
                            Long newId = fieldMap.get(oldId);
                            if (newId != null) db.setValue(recordId, newId, vals.optString(k, ""));
                        }
                    }
                    db.setRecordQuantity(recordId, jr.optDouble("quantity", 0));
                    if ("SAVED".equals(jr.optString("status"))) db.markRecordSaved(recordId);
                }
            }
        }
        return catalogId;
    }

    static String safeFileName(String s) {
        if (s == null || s.trim().isEmpty()) return "catalog";
        return s.trim().replaceAll("[\\\\/:*?\"<>|]+", "_");
    }
}
