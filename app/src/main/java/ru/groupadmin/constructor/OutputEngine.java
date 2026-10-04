package ru.groupadmin.constructor;

import java.util.List;
import java.util.Map;

final class OutputEngine {
    static String render(DbHelper db, CardTemplate template, List<FieldDef> fields, Map<Long,String> rawValues) {
        Map<String,String> valuesByName = RecordLogic.computeDisplayValues(db, fields, rawValues);
        String pattern = template.outputTemplate == null ? "" : template.outputTemplate;
        if (pattern.trim().isEmpty()) pattern = autoPattern(fields);
        String out = pattern;
        for (FieldDef f : fields) {
            String v = valuesByName.get(f.name);
            out = out.replace("{" + f.name + "}", v == null ? "" : v);
        }
        return out.replaceAll("(?m)^[^\\S\\r\\n]*[:—-][^\\S\\r\\n]*$", "")
                .replaceAll("\\n{3,}", "\\n\\n").trim();
    }

    static String autoPattern(List<FieldDef> fields) {
        StringBuilder b = new StringBuilder();
        boolean first=true;
        for (FieldDef f: fields) {
            if (FieldDef.PHOTO.equals(f.type)) continue;
            if (first) {
                b.append("{").append(f.name).append("}");
                first=false;
            } else {
                b.append("\n").append(f.name).append(": {").append(f.name).append("}");
            }
        }
        return b.toString();
    }
}
