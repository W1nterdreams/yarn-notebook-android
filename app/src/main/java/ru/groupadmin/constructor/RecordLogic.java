package ru.groupadmin.constructor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class RecordLogic {
    static Map<String,String> computeRawByName(List<FieldDef> fields, Map<Long,String> rawValues) {
        return computeRawByName(fields, rawValues, 0d);
    }

    static Map<String,String> computeRawByName(List<FieldDef> fields, Map<Long,String> rawValues, double quantity) {
        Map<String,String> out = new LinkedHashMap<>();
        for (FieldDef f: fields) {
            if (!FieldDef.FORMULA.equals(f.type)) out.put(f.name, rawValues.getOrDefault(f.id, ""));
        }

        String stock = Stock.format(quantity);
        out.put("Количество", stock);

        int passes = Math.max(2, fields.size()+1);
        boolean stillChanging = false;
        for (int pass=0; pass<passes; pass++) {
            boolean changed=false;
            for (FieldDef f: fields) {
                if (!FieldDef.FORMULA.equals(f.type)) continue;
                out.put("Количество", stock);
                FormulaEngine.Result r = FormulaEngine.evaluate(f.formula, out);
                String next = r.text;
                String old = out.put(f.name, next);
                if (old == null || !old.equals(next)) changed=true;
            }
            stillChanging = changed;
            if (!changed) break;
        }
        out.put("Количество", stock);

        if (stillChanging) {
            for (FieldDef f: fields) {
                if (FieldDef.FORMULA.equals(f.type) && !"Количество".equals(f.name)) {
                    out.put(f.name, "#ЦИКЛ");
                }
            }
            out.put("Количество", stock);
        }
        return out;
    }

    static Map<String,String> computeDisplayValues(DbHelper db, List<FieldDef> fields, Map<Long,String> rawValues) {
        return computeDisplayValues(db, fields, rawValues, 0d);
    }

    static Map<String,String> computeDisplayValues(DbHelper db, List<FieldDef> fields,
                                                   Map<Long,String> rawValues, double quantity) {
        Map<String,String> rawByName = computeRawByName(fields, rawValues, quantity);
        Map<String,String> out = new LinkedHashMap<>();
        for (FieldDef f: fields) {
            String raw = FieldDef.FORMULA.equals(f.type)
                    ? rawByName.get(f.name)
                    : rawValues.getOrDefault(f.id, "");
            out.put(f.name, db.getDisplayValue(f, raw));
        }
        out.put("Количество", Stock.format(quantity));
        return out;
    }

    static String titleFor(List<FieldDef> fields, Map<Long,String> rawValues) {
        Map<String,String> computed = computeRawByName(fields, rawValues);
        for (FieldDef f: fields) {
            if (FieldDef.PHOTO.equals(f.type) || FieldDef.FORMULA.equals(f.type)) continue;
            String v = rawValues.get(f.id);
            if (v != null && !v.trim().isEmpty()) return v.trim();
        }
        for (FieldDef f: fields) if (FieldDef.FORMULA.equals(f.type)) {
            String v=computed.get(f.name);
            if(v!=null && !v.isEmpty()) return v;
        }
        return "Без названия";
    }
}
