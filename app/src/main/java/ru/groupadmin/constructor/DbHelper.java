package ru.groupadmin.constructor;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DbHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "group_admin_constructor.db";
    private static final int DB_VERSION = 2;

    public DbHelper(Context context) { super(context, DB_NAME, null, DB_VERSION); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("PRAGMA foreign_keys=ON");
        db.execSQL("CREATE TABLE catalogs (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, created_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE templates (id INTEGER PRIMARY KEY AUTOINCREMENT, catalog_id INTEGER NOT NULL, name TEXT NOT NULL, output_template TEXT NOT NULL DEFAULT '', quantity_unit TEXT NOT NULL DEFAULT 'шт.', created_at INTEGER NOT NULL, FOREIGN KEY(catalog_id) REFERENCES catalogs(id) ON DELETE CASCADE)");
        db.execSQL("CREATE TABLE fields (id INTEGER PRIMARY KEY AUTOINCREMENT, template_id INTEGER NOT NULL, name TEXT NOT NULL, type TEXT NOT NULL, position INTEGER NOT NULL, required INTEGER NOT NULL DEFAULT 0, unit TEXT NOT NULL DEFAULT '', default_value TEXT NOT NULL DEFAULT '', options_json TEXT NOT NULL DEFAULT '[]', formula TEXT NOT NULL DEFAULT '', show_in_list INTEGER NOT NULL DEFAULT 1, searchable INTEGER NOT NULL DEFAULT 1, archived INTEGER NOT NULL DEFAULT 0, FOREIGN KEY(template_id) REFERENCES templates(id) ON DELETE CASCADE)");
        db.execSQL("CREATE TABLE records (id INTEGER PRIMARY KEY AUTOINCREMENT, template_id INTEGER NOT NULL, status TEXT NOT NULL DEFAULT 'DRAFT', quantity REAL NOT NULL DEFAULT 0, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, FOREIGN KEY(template_id) REFERENCES templates(id) ON DELETE CASCADE)");
        db.execSQL("CREATE TABLE field_values (record_id INTEGER NOT NULL, field_id INTEGER NOT NULL, value TEXT NOT NULL DEFAULT '', PRIMARY KEY(record_id, field_id), FOREIGN KEY(record_id) REFERENCES records(id) ON DELETE CASCADE, FOREIGN KEY(field_id) REFERENCES fields(id) ON DELETE CASCADE)");
        db.execSQL("CREATE INDEX idx_templates_catalog ON templates(catalog_id)");
        db.execSQL("CREATE INDEX idx_fields_template ON fields(template_id, archived, position)");
        db.execSQL("CREATE INDEX idx_records_template ON records(template_id, updated_at DESC)");
    }

    @Override public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE templates ADD COLUMN quantity_unit TEXT NOT NULL DEFAULT 'шт.'");
            db.execSQL("ALTER TABLE records ADD COLUMN quantity REAL NOT NULL DEFAULT 0");

            // Старое пользовательское поле «Количество» становится системным остатком.
            // Само поле не удаляем: архивируем, чтобы данные не терялись.
            db.execSQL("UPDATE templates SET quantity_unit = COALESCE((" +
                    "SELECT NULLIF(f.unit,'') FROM fields f " +
                    "WHERE f.template_id=templates.id AND f.name='Количество' LIMIT 1" +
                    "), quantity_unit)");
            db.execSQL("UPDATE records SET quantity = COALESCE((" +
                    "SELECT CAST(REPLACE(fv.value, ',', '.') AS REAL) " +
                    "FROM field_values fv JOIN fields f ON f.id=fv.field_id " +
                    "WHERE fv.record_id=records.id AND f.name='Количество' LIMIT 1" +
                    "), quantity)");
            db.execSQL("UPDATE fields SET archived=1 WHERE name='Количество'");
        }
    }

    public long createCatalog(String name) {
        ContentValues cv=new ContentValues();
        cv.put("name",name.trim());
        cv.put("created_at",System.currentTimeMillis());
        return getWritableDatabase().insertOrThrow("catalogs",null,cv);
    }

    public void renameCatalog(long id,String name){
        ContentValues cv=new ContentValues();
        cv.put("name",name.trim());
        getWritableDatabase().update("catalogs",cv,"id=?",new String[]{String.valueOf(id)});
    }

    public void deleteCatalog(long id){
        getWritableDatabase().delete("catalogs","id=?",new String[]{String.valueOf(id)});
    }

    public List<Catalog> getCatalogs(){
        List<Catalog> out=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,name,created_at FROM catalogs ORDER BY created_at DESC",null)){
            while(c.moveToNext()){
                Catalog x=new Catalog();
                x.id=c.getLong(0);
                x.name=c.getString(1);
                x.createdAt=c.getLong(2);
                out.add(x);
            }
        }
        return out;
    }

    public Catalog getCatalog(long id){
        try(Cursor c=getReadableDatabase().rawQuery(
                "SELECT id,name,created_at FROM catalogs WHERE id=?",
                new String[]{String.valueOf(id)})){
            if(c.moveToFirst()){
                Catalog x=new Catalog();
                x.id=c.getLong(0);
                x.name=c.getString(1);
                x.createdAt=c.getLong(2);
                return x;
            }
        }
        return null;
    }

    public long createTemplate(long catalogId,String name){
        return createTemplate(catalogId,name,"шт.");
    }

    public long createTemplate(long catalogId,String name,String quantityUnit){
        ContentValues cv=new ContentValues();
        cv.put("catalog_id",catalogId);
        cv.put("name",name.trim());
        cv.put("output_template","");
        cv.put("quantity_unit",normalizeUnit(quantityUnit));
        cv.put("created_at",System.currentTimeMillis());
        return getWritableDatabase().insertOrThrow("templates",null,cv);
    }

    public void renameTemplate(long id,String name){
        CardTemplate t=getTemplate(id);
        setTemplateSettings(id,name,t==null?"шт.":t.quantityUnit);
    }

    public void setTemplateSettings(long id,String name,String quantityUnit){
        ContentValues cv=new ContentValues();
        cv.put("name",name.trim());
        cv.put("quantity_unit",normalizeUnit(quantityUnit));
        getWritableDatabase().update("templates",cv,"id=?",new String[]{String.valueOf(id)});
    }

    public void deleteTemplate(long id){
        getWritableDatabase().delete("templates","id=?",new String[]{String.valueOf(id)});
    }

    public List<CardTemplate> getTemplates(long catalogId){
        List<CardTemplate> out=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery(
                "SELECT id,catalog_id,name,output_template,quantity_unit,created_at FROM templates WHERE catalog_id=? ORDER BY created_at ASC",
                new String[]{String.valueOf(catalogId)})){
            while(c.moveToNext()) out.add(templateFrom(c));
        }
        return out;
    }

    public CardTemplate getTemplate(long id){
        try(Cursor c=getReadableDatabase().rawQuery(
                "SELECT id,catalog_id,name,output_template,quantity_unit,created_at FROM templates WHERE id=?",
                new String[]{String.valueOf(id)})){
            return c.moveToFirst()?templateFrom(c):null;
        }
    }

    private CardTemplate templateFrom(Cursor c){
        CardTemplate x=new CardTemplate();
        x.id=c.getLong(0);
        x.catalogId=c.getLong(1);
        x.name=c.getString(2);
        x.outputTemplate=c.getString(3);
        x.quantityUnit=c.getString(4);
        x.createdAt=c.getLong(5);
        return x;
    }

    public void setOutputTemplate(long templateId,String text){
        ContentValues cv=new ContentValues();
        cv.put("output_template",text==null?"":text);
        getWritableDatabase().update("templates",cv,"id=?",new String[]{String.valueOf(templateId)});
    }

    private static String normalizeUnit(String unit){
        String u=unit==null?"":unit.trim();
        return u.isEmpty()?"шт.":u;
    }

    public boolean fieldNameExists(long templateId,String name,long exceptId){
        try(Cursor c=getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM fields WHERE template_id=? AND lower(name)=lower(?) AND id<>?",
                new String[]{String.valueOf(templateId),name.trim(),String.valueOf(exceptId)})){
            return c.moveToFirst()&&c.getInt(0)>0;
        }
    }

    public long saveField(FieldDef f){
        SQLiteDatabase db=getWritableDatabase();
        String oldName=null;
        if(f.id>0){
            FieldDef old=getField(f.id);
            if(old!=null)oldName=old.name;
        }

        ContentValues cv=new ContentValues();
        cv.put("template_id",f.templateId);
        cv.put("name",f.name.trim());
        cv.put("type",f.type);
        cv.put("position",f.position);
        cv.put("required",f.required?1:0);
        cv.put("unit",nz(f.unit));
        cv.put("default_value",nz(f.defaultValue));
        cv.put("options_json",nz(f.optionsJson,"[]"));
        cv.put("formula",nz(f.formula));
        cv.put("show_in_list",f.showInList?1:0);
        cv.put("searchable",f.searchable?1:0);
        cv.put("archived",f.archived?1:0);

        if(f.id>0){
            db.update("fields",cv,"id=?",new String[]{String.valueOf(f.id)});
            if(oldName!=null&&!oldName.equals(f.name)) renameReferences(f.templateId,oldName,f.name);
            return f.id;
        }

        if(f.position<=0) f.position=nextFieldPosition(f.templateId);
        cv.put("position",f.position);
        return db.insertOrThrow("fields",null,cv);
    }

    private static String nz(String s){return s==null?"":s;}
    private static String nz(String s,String fallback){return s==null||s.isEmpty()?fallback:s;}

    private int nextFieldPosition(long templateId){
        try(Cursor c=getReadableDatabase().rawQuery(
                "SELECT COALESCE(MAX(position),0)+10 FROM fields WHERE template_id=?",
                new String[]{String.valueOf(templateId)})){
            return c.moveToFirst()?c.getInt(0):10;
        }
    }

    public FieldDef getField(long id){
        try(Cursor c=getReadableDatabase().rawQuery(
                "SELECT id,template_id,name,type,position,required,unit,default_value,options_json,formula,show_in_list,searchable,archived FROM fields WHERE id=?",
                new String[]{String.valueOf(id)})){
            return c.moveToFirst()?fieldFrom(c):null;
        }
    }

    public List<FieldDef> getFields(long templateId,boolean includeArchived){
        List<FieldDef> out=new ArrayList<>();
        String sql="SELECT id,template_id,name,type,position,required,unit,default_value,options_json,formula,show_in_list,searchable,archived FROM fields WHERE template_id=?"
                +(includeArchived?"":" AND archived=0")+" ORDER BY position,id";
        try(Cursor c=getReadableDatabase().rawQuery(sql,new String[]{String.valueOf(templateId)})){
            while(c.moveToNext())out.add(fieldFrom(c));
        }
        return out;
    }

    private FieldDef fieldFrom(Cursor c){
        FieldDef f=new FieldDef();
        f.id=c.getLong(0);
        f.templateId=c.getLong(1);
        f.name=c.getString(2);
        f.type=c.getString(3);
        f.position=c.getInt(4);
        f.required=c.getInt(5)!=0;
        f.unit=c.getString(6);
        f.defaultValue=c.getString(7);
        f.optionsJson=c.getString(8);
        f.formula=c.getString(9);
        f.showInList=c.getInt(10)!=0;
        f.searchable=c.getInt(11)!=0;
        f.archived=c.getInt(12)!=0;
        return f;
    }

    public void setFieldArchived(long id,boolean archived){
        ContentValues cv=new ContentValues();
        cv.put("archived",archived?1:0);
        getWritableDatabase().update("fields",cv,"id=?",new String[]{String.valueOf(id)});
    }

    public void moveField(long fieldId,int direction){
        FieldDef current=getField(fieldId);
        if(current==null)return;
        List<FieldDef> list=getFields(current.templateId,false);
        int idx=-1;
        for(int i=0;i<list.size();i++) if(list.get(i).id==fieldId){idx=i;break;}
        int other=idx+direction;
        if(idx<0||other<0||other>=list.size())return;
        FieldDef b=list.get(other);
        SQLiteDatabase db=getWritableDatabase();
        db.beginTransaction();
        try{
            ContentValues aCv=new ContentValues();
            aCv.put("position",b.position);
            ContentValues bCv=new ContentValues();
            bCv.put("position",current.position);
            db.update("fields",aCv,"id=?",new String[]{String.valueOf(current.id)});
            db.update("fields",bCv,"id=?",new String[]{String.valueOf(b.id)});
            db.setTransactionSuccessful();
        }finally{
            db.endTransaction();
        }
    }

    private void renameReferences(long templateId,String oldName,String newName){
        String oldToken="{"+oldName+"}",newToken="{"+newName+"}";
        SQLiteDatabase db=getWritableDatabase();
        CardTemplate t=getTemplate(templateId);
        if(t!=null&&t.outputTemplate!=null&&t.outputTemplate.contains(oldToken))
            setOutputTemplate(templateId,t.outputTemplate.replace(oldToken,newToken));

        for(FieldDef f:getFields(templateId,true)){
            if(f.formula!=null&&f.formula.contains(oldToken)){
                ContentValues cv=new ContentValues();
                cv.put("formula",f.formula.replace(oldToken,newToken));
                db.update("fields",cv,"id=?",new String[]{String.valueOf(f.id)});
            }
        }
    }

    public long createRecord(long templateId){
        long now=System.currentTimeMillis();
        ContentValues cv=new ContentValues();
        cv.put("template_id",templateId);
        cv.put("status","DRAFT");
        cv.put("quantity",0d);
        cv.put("created_at",now);
        cv.put("updated_at",now);
        long id=getWritableDatabase().insertOrThrow("records",null,cv);

        for(FieldDef f:getFields(templateId,false)){
            if(!FieldDef.FORMULA.equals(f.type)&&f.defaultValue!=null&&!f.defaultValue.isEmpty())
                setValue(id,f.id,f.defaultValue);
        }
        return id;
    }

    public long duplicateRecord(long recordId){
        RecordItem r=getRecord(recordId);
        if(r==null)return 0;
        long id=createRecord(r.templateId);
        for(Map.Entry<Long,String> e:getValues(recordId).entrySet())
            setValue(id,e.getKey(),e.getValue());
        setRecordQuantity(id,r.quantity);
        return id;
    }

    public RecordItem getRecord(long id){
        try(Cursor c=getReadableDatabase().rawQuery(
                "SELECT id,template_id,status,quantity,created_at,updated_at FROM records WHERE id=?",
                new String[]{String.valueOf(id)})){
            if(!c.moveToFirst())return null;
            return recordFrom(c);
        }
    }

    public List<RecordItem> getRecords(long templateId){
        List<RecordItem> out=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery(
                "SELECT id,template_id,status,quantity,created_at,updated_at FROM records WHERE template_id=? ORDER BY updated_at DESC,id DESC",
                new String[]{String.valueOf(templateId)})){
            while(c.moveToNext()) out.add(recordFrom(c));
        }
        return out;
    }

    private RecordItem recordFrom(Cursor c){
        RecordItem r=new RecordItem();
        r.id=c.getLong(0);
        r.templateId=c.getLong(1);
        r.status=c.getString(2);
        r.quantity=c.getDouble(3);
        r.createdAt=c.getLong(4);
        r.updatedAt=c.getLong(5);
        return r;
    }

    public void markRecordSaved(long id){
        ContentValues cv=new ContentValues();
        cv.put("status","SAVED");
        cv.put("updated_at",System.currentTimeMillis());
        getWritableDatabase().update("records",cv,"id=?",new String[]{String.valueOf(id)});
    }

    public void touchRecord(long id){
        ContentValues cv=new ContentValues();
        cv.put("updated_at",System.currentTimeMillis());
        getWritableDatabase().update("records",cv,"id=?",new String[]{String.valueOf(id)});
    }

    public void setRecordQuantity(long id,double quantity){
        ContentValues cv=new ContentValues();
        cv.put("quantity",Math.max(0d,quantity));
        cv.put("updated_at",System.currentTimeMillis());
        getWritableDatabase().update("records",cv,"id=?",new String[]{String.valueOf(id)});
    }

    public void adjustRecordQuantity(long id,double delta){
        SQLiteDatabase db=getWritableDatabase();
        db.beginTransaction();
        try{
            RecordItem r=getRecord(id);
            if(r!=null){
                ContentValues cv=new ContentValues();
                cv.put("quantity",Math.max(0d,r.quantity+delta));
                cv.put("updated_at",System.currentTimeMillis());
                db.update("records",cv,"id=?",new String[]{String.valueOf(id)});
            }
            db.setTransactionSuccessful();
        }finally{
            db.endTransaction();
        }
    }

    public void deleteRecord(long id){
        getWritableDatabase().delete("records","id=?",new String[]{String.valueOf(id)});
    }

    public void setValue(long recordId,long fieldId,String value){
        ContentValues cv=new ContentValues();
        cv.put("record_id",recordId);
        cv.put("field_id",fieldId);
        cv.put("value",value==null?"":value);
        getWritableDatabase().insertWithOnConflict(
                "field_values",null,cv,SQLiteDatabase.CONFLICT_REPLACE);
    }

    public Map<Long,String> getValues(long recordId){
        Map<Long,String> out=new LinkedHashMap<>();
        try(Cursor c=getReadableDatabase().rawQuery(
                "SELECT field_id,value FROM field_values WHERE record_id=?",
                new String[]{String.valueOf(recordId)})){
            while(c.moveToNext())out.put(c.getLong(0),c.getString(1));
        }
        return out;
    }

    public int countTemplates(long catalogId){
        try(Cursor c=getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM templates WHERE catalog_id=?",
                new String[]{String.valueOf(catalogId)})){
            return c.moveToFirst()?c.getInt(0):0;
        }
    }

    public int countRecordsForCatalog(long catalogId){
        try(Cursor c=getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM records WHERE template_id IN (SELECT id FROM templates WHERE catalog_id=?)",
                new String[]{String.valueOf(catalogId)})){
            return c.moveToFirst()?c.getInt(0):0;
        }
    }

    public int countRecords(long templateId){
        try(Cursor c=getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM records WHERE template_id=?",
                new String[]{String.valueOf(templateId)})){
            return c.moveToFirst()?c.getInt(0):0;
        }
    }

    public String getDisplayValue(FieldDef f,String raw){
        if(raw==null||raw.isEmpty())return "";
        if(FieldDef.CHECKBOX.equals(f.type))return "1".equals(raw)?"Да":"Нет";
        if(FieldDef.MULTI_CHOICE.equals(f.type)){
            try{
                JSONArray a=new JSONArray(raw);
                List<String> vals=new ArrayList<>();
                for(int i=0;i<a.length();i++)vals.add(a.optString(i));
                return String.join(", ",vals);
            }catch(Exception ignored){
                return raw;
            }
        }
        if(FieldDef.REPEAT_GROUP.equals(f.type)) return RepeatGroup.display(f.optionsJson, raw);
        if(FieldDef.PHOTO.equals(f.type))return raw.isEmpty()?"":"Фото выбрано";
        return raw+((f.unit==null||f.unit.isEmpty())?"":" "+f.unit);
    }
}
