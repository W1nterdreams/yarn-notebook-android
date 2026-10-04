package ru.groupadmin.constructor;

final class Catalog {
    long id;
    String name;
    long createdAt;
}

final class CardTemplate {
    long id;
    long catalogId;
    String name;
    String outputTemplate;
    long createdAt;
}

final class FieldDef {
    static final String TEXT = "TEXT";
    static final String MULTILINE = "MULTILINE";
    static final String INTEGER = "INTEGER";
    static final String DECIMAL = "DECIMAL";
    static final String PRICE = "PRICE";
    static final String CHECKBOX = "CHECKBOX";
    static final String DATE = "DATE";
    static final String SINGLE_CHOICE = "SINGLE_CHOICE";
    static final String MULTI_CHOICE = "MULTI_CHOICE";
    static final String PHOTO = "PHOTO";
    static final String FORMULA = "FORMULA";
    static final String REPEAT_GROUP = "REPEAT_GROUP";

    long id;
    long templateId;
    String name;
    String type;
    int position;
    boolean required;
    String unit;
    String defaultValue;
    String optionsJson;
    String formula;
    boolean showInList;
    boolean searchable;
    boolean archived;

    static String humanType(String type) {
        if (MULTILINE.equals(type)) return "Многострочный текст";
        if (INTEGER.equals(type)) return "Целое число";
        if (DECIMAL.equals(type)) return "Дробное число";
        if (PRICE.equals(type)) return "Цена";
        if (CHECKBOX.equals(type)) return "Да / Нет";
        if (DATE.equals(type)) return "Дата";
        if (SINGLE_CHOICE.equals(type)) return "Выбор из списка";
        if (MULTI_CHOICE.equals(type)) return "Несколько вариантов";
        if (PHOTO.equals(type)) return "Фотография";
        if (FORMULA.equals(type)) return "Формула";
        if (REPEAT_GROUP.equals(type)) return "Повторяемая группа";
        return "Текст";
    }

    static String[] allTypes() {
        return new String[]{TEXT, MULTILINE, INTEGER, DECIMAL, PRICE, CHECKBOX, DATE,
                SINGLE_CHOICE, MULTI_CHOICE, REPEAT_GROUP, PHOTO, FORMULA};
    }
}

final class RecordItem {
    long id;
    long templateId;
    String status;
    long createdAt;
    long updatedAt;
}
