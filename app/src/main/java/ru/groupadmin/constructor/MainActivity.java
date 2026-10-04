package ru.groupadmin.constructor;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class MainActivity extends Activity {
    private static final int REQ_IMPORT = 101;
    private DbHelper db;
    private LinearLayout list;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new DbHelper(this);
        render();
    }

    @Override protected void onResume() {
        super.onResume();
        if (db != null && list != null) refreshList();
    }

    private void render() {
        LinearLayout root = Ui.page(this);
        root.addView(Ui.title(this, "Конструктор товаров"));
        root.addView(Ui.subtitle(this, "Собирайте свою базу под любой товар: пряжу, двигатель, одежду, запчасти или совершенно другую категорию."));

        android.widget.Button add=Ui.primaryButton(this,"＋ Создать новую базу");
        root.addView(add,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(this,52)));

        LinearLayout actions=Ui.row(this);
        android.widget.Button demo=Ui.outlineButton(this,"Демо");
        android.widget.Button imp=Ui.outlineButton(this,"Импорт JSON");
        actions.addView(demo,new LinearLayout.LayoutParams(0,Ui.dp(this,46),1));
        actions.addView(imp,new LinearLayout.LayoutParams(0,Ui.dp(this,46),1));
        root.addView(Ui.spacer(this,6));
        root.addView(actions);
        root.addView(Ui.sectionTitle(this,"МОИ БАЗЫ"));

        list=new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL);
        android.widget.ScrollView scroll=Ui.scroll(this,list);
        root.addView(scroll,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));
        setContentView(root);

        add.setOnClickListener(v->createCatalogDialog());
        demo.setOnClickListener(v->createDemo());
        imp.setOnClickListener(v->openImport());
        refreshList();
    }

    private void refreshList() {
        list.removeAllViews();
        List<Catalog> catalogs=db.getCatalogs();
        if(catalogs.isEmpty()) {
            LinearLayout card=Ui.card(this);
            card.addView(Ui.text(this,"Баз пока нет",18,Ui.TEXT,true));
            card.addView(Ui.text(this,"Создайте пустую базу или нажмите «Демо», чтобы увидеть формулы, варианты выбора и шаблон вывода.",14,Ui.MUTED,false));
            list.addView(card);
            return;
        }
        for(Catalog c:catalogs) {
            LinearLayout card=Ui.card(this);
            card.addView(Ui.text(this,c.name,19,Ui.TEXT,true));
            LinearLayout stats=Ui.row(this);
            stats.addView(Ui.badge(this,"Типов: "+db.countTemplates(c.id),Ui.PRIMARY_DARK,Ui.PRIMARY_SOFT));
            stats.addView(Ui.hSpacer(this,6));
            stats.addView(Ui.badge(this,"Товаров: "+db.countRecordsForCatalog(c.id),Ui.SUCCESS,Ui.SUCCESS_BG));
            card.addView(Ui.spacer(this,10));
            card.addView(stats);
            card.addView(Ui.spacer(this,10));
            LinearLayout row=Ui.row(this);
            android.widget.Button open=Ui.primaryButton(this,"Открыть");
            android.widget.Button rename=Ui.outlineButton(this,"Название");
            android.widget.Button del=Ui.dangerButton(this,"Удалить");
            row.addView(open,new LinearLayout.LayoutParams(0,Ui.dp(this,46),1));
            row.addView(rename,new LinearLayout.LayoutParams(0,Ui.dp(this,46),1));
            row.addView(del,new LinearLayout.LayoutParams(0,Ui.dp(this,46),1));
            card.addView(row);
            open.setOnClickListener(v->openCatalog(c.id));
            rename.setOnClickListener(v->renameCatalog(c));
            del.setOnClickListener(v->confirmDelete(c));
            list.addView(card);
        }
    }

    private void openCatalog(long id) {
        Intent i=new Intent(this,CatalogActivity.class); i.putExtra("catalog_id",id); startActivity(i);
    }

    private void createCatalogDialog() {
        EditText e=new EditText(this); e.setHint("Например: Магазин одежды"); e.setSingleLine(true);
        new AlertDialog.Builder(this).setTitle("Новая база").setView(e)
                .setPositiveButton("Создать",(d,w)->{
                    String name=e.getText().toString().trim();
                    if(name.isEmpty()){toast("Введите название");return;}
                    long id=db.createCatalog(name); openCatalog(id);
                }).setNegativeButton("Отмена",null).show();
    }

    private void renameCatalog(Catalog c) {
        EditText e=new EditText(this); e.setText(c.name); e.setSelection(e.length());
        new AlertDialog.Builder(this).setTitle("Название базы").setView(e)
                .setPositiveButton("Сохранить",(d,w)->{String x=e.getText().toString().trim(); if(!x.isEmpty()){db.renameCatalog(c.id,x);refreshList();}})
                .setNegativeButton("Отмена",null).show();
    }

    private void confirmDelete(Catalog c) {
        new AlertDialog.Builder(this).setTitle("Удалить базу?")
                .setMessage("Будут удалены все типы товаров, поля и записи базы «"+c.name+"». Отменить это нельзя.")
                .setPositiveButton("Удалить",(d,w)->{db.deleteCatalog(c.id);refreshList();})
                .setNegativeButton("Отмена",null).show();
    }

    private void createDemo() {
        long catalogId = db.createCatalog("Демо: универсальный магазин");
        createYarnDemo(catalogId);
        createEngineDemo(catalogId);
        createPartDemo(catalogId);
        toast("Созданы три совершенно разных типа товаров: пряжа, двигатель и запчасть");
        openCatalog(catalogId);
    }

    private void createYarnDemo(long catalogId) {
        long tid = db.createTemplate(catalogId, "Пряжа", "боб.");
        addDemoField(tid,"Название",FieldDef.TEXT,true,"","",true,true,"");
        addDemoField(tid,"Производитель",FieldDef.TEXT,false,"","",true,true,"");
        addDemoField(tid,"Состав",FieldDef.REPEAT_GROUP,false,"","",true,true,RepeatGroup.compositionConfig());
        addDemoField(tid,"Метраж",FieldDef.DECIMAL,false,"м/100 г","",true,true,"");
        addDemoField(tid,"Цвет",FieldDef.TEXT,false,"","",true,true,"");
        addDemoField(tid,"Цена за кг",FieldDef.PRICE,false,"₽/кг","",true,true,"");
        addDemoField(tid,"Вес",FieldDef.DECIMAL,false,"г","",true,false,"");
        addDemoField(tid,"Стоимость",FieldDef.FORMULA,false,"₽","",true,false,"={Цена за кг}*{Вес}/1000");
        addDemoField(tid,"Фото",FieldDef.PHOTO,false,"","",false,false,"");
        addDemoField(tid,"Описание",FieldDef.MULTILINE,false,"","",false,true,"");
        db.setOutputTemplate(tid,"{Название}\nПроизводитель: {Производитель}\nСостав: {Состав}\nМетраж: {Метраж}\nЦвет: {Цвет}\nЦена: {Цена за кг}\nВес: {Вес}\nВ наличии: {Количество} {Единица учёта}\nСтоимость: {Стоимость}\n\n{Описание}");
        long r=db.createRecord(tid);
        setDemoValue(tid,r,"Название","Cariaggi Cashmere");
        setDemoValue(tid,r,"Производитель","Cariaggi");
        setDemoValue(tid,r,"Состав","[{\"Материал\":\"Кашемир\",\"Доля\":\"100\"}]");
        setDemoValue(tid,r,"Метраж","1400");
        setDemoValue(tid,r,"Цвет","Графит");
        setDemoValue(tid,r,"Цена за кг","8200");
        setDemoValue(tid,r,"Вес","350");
        setDemoValue(tid,r,"Описание","Пример товара для пряжи. Все поля можно переименовать, удалить или дополнить.");
        db.setRecordQuantity(r,4);
        db.markRecordSaved(r);
    }

    private void createEngineDemo(long catalogId) {
        long tid = db.createTemplate(catalogId, "Двигатель", "шт.");
        addDemoField(tid,"Название",FieldDef.TEXT,true,"","",true,true,"");
        addDemoField(tid,"Марка автомобиля",FieldDef.SINGLE_CHOICE,false,"","",true,true,"BMW\nMercedes-Benz\nVolkswagen\nAudi\nToyota\nДругая");
        addDemoField(tid,"Модель",FieldDef.TEXT,false,"","",true,true,"");
        addDemoField(tid,"Код двигателя",FieldDef.TEXT,false,"","",true,true,"");
        addDemoField(tid,"Объём",FieldDef.DECIMAL,false,"л","",true,false,"");
        addDemoField(tid,"Мощность",FieldDef.INTEGER,false,"л.с.","",true,false,"");
        addDemoField(tid,"Пробег",FieldDef.INTEGER,false,"км","",true,false,"");
        addDemoField(tid,"Топливо",FieldDef.SINGLE_CHOICE,false,"","",false,true,"Бензин\nДизель\nГибрид\nДругое");
        addDemoField(tid,"Состояние",FieldDef.SINGLE_CHOICE,false,"","",true,true,"Новый\nКонтрактный\nБ/у\nНа запчасти");
        addDemoField(tid,"Цена",FieldDef.PRICE,false,"₽","",true,true,"");
        addDemoField(tid,"Скидка",FieldDef.DECIMAL,false,"%","0",false,false,"");
        addDemoField(tid,"Цена со скидкой",FieldDef.FORMULA,false,"₽","",true,false,"=ROUND({Цена}*(100-{Скидка})/100;0)");
        addDemoField(tid,"Фото",FieldDef.PHOTO,false,"","",false,false,"");
        addDemoField(tid,"Комментарий",FieldDef.MULTILINE,false,"","",false,true,"");
        db.setOutputTemplate(tid,"{Название}\nМарка: {Марка автомобиля}\nМодель: {Модель}\nКод двигателя: {Код двигателя}\nОбъём: {Объём}\nМощность: {Мощность}\nПробег: {Пробег}\nТопливо: {Топливо}\nСостояние: {Состояние}\nВ наличии: {Количество} {Единица учёта}\nЦена: {Цена}\nЦена со скидкой: {Цена со скидкой}\n\n{Комментарий}");
        long r=db.createRecord(tid);
        setDemoValue(tid,r,"Название","Двигатель B48B20");
        setDemoValue(tid,r,"Марка автомобиля","BMW");
        setDemoValue(tid,r,"Модель","3 Series G20");
        setDemoValue(tid,r,"Код двигателя","B48B20");
        setDemoValue(tid,r,"Объём","2.0");
        setDemoValue(tid,r,"Мощность","184");
        setDemoValue(tid,r,"Пробег","62000");
        setDemoValue(tid,r,"Топливо","Бензин");
        setDemoValue(tid,r,"Состояние","Контрактный");
        setDemoValue(tid,r,"Цена","245000");
        setDemoValue(tid,r,"Скидка","5");
        setDemoValue(tid,r,"Комментарий","Пример: завтра вместо пряжи вы продаёте двигатель — программа использует уже совсем другой набор полей.");
        db.setRecordQuantity(r,2);
        db.markRecordSaved(r);
    }

    private void createPartDemo(long catalogId) {
        long tid = db.createTemplate(catalogId, "Автозапчасть", "шт.");
        addDemoField(tid,"Название",FieldDef.TEXT,true,"","",true,true,"");
        addDemoField(tid,"Категория",FieldDef.SINGLE_CHOICE,false,"","",true,true,"Кузов\nДвигатель\nПодвеска\nЭлектрика\nСалон\nДругое");
        addDemoField(tid,"Артикул / OEM",FieldDef.TEXT,false,"","",true,true,"");
        addDemoField(tid,"Марка",FieldDef.TEXT,false,"","",true,true,"");
        addDemoField(tid,"Модель",FieldDef.TEXT,false,"","",true,true,"");
        addDemoField(tid,"Год",FieldDef.INTEGER,false,"","",true,true,"");
        addDemoField(tid,"Сторона",FieldDef.SINGLE_CHOICE,false,"","",false,true,"Левая\nПравая\nПеред\nЗад\nНе применимо");
        addDemoField(tid,"Состояние",FieldDef.SINGLE_CHOICE,false,"","",true,true,"Новая\nБ/у\nВосстановленная");
        addDemoField(tid,"Цена",FieldDef.PRICE,false,"₽","",true,true,"");
        addDemoField(tid,"Итого",FieldDef.FORMULA,false,"₽","",true,false,"={Цена}*{Количество}");
        addDemoField(tid,"Фото",FieldDef.PHOTO,false,"","",false,false,"");
        db.setOutputTemplate(tid,"{Название}\nКатегория: {Категория}\nOEM: {Артикул / OEM}\nМарка/модель: {Марка} {Модель}\nГод: {Год}\nСторона: {Сторона}\nСостояние: {Состояние}\nВ наличии: {Количество} {Единица учёта}\nЦена: {Цена}\nИтого: {Итого}");
        long r=db.createRecord(tid);
        setDemoValue(tid,r,"Название","Фара передняя левая");
        setDemoValue(tid,r,"Категория","Кузов");
        setDemoValue(tid,r,"Артикул / OEM","63117419619");
        setDemoValue(tid,r,"Марка","BMW");
        setDemoValue(tid,r,"Модель","G20");
        setDemoValue(tid,r,"Год","2021");
        setDemoValue(tid,r,"Сторона","Левая");
        setDemoValue(tid,r,"Состояние","Б/у");
        setDemoValue(tid,r,"Цена","68000");
        db.setRecordQuantity(r,10);
        db.markRecordSaved(r);
    }

    private void setDemoValue(long templateId,long recordId,String fieldName,String value){
        for(FieldDef f:db.getFields(templateId,false)) if(fieldName.equals(f.name)){db.setValue(recordId,f.id,value);return;}
    }

    private void addDemoField(long tid,String name,String type,boolean req,String unit,String def,boolean list,boolean search,String extra){
        FieldDef f=new FieldDef();f.templateId=tid;f.name=name;f.type=type;f.required=req;f.unit=unit;f.defaultValue=def;f.showInList=list;f.searchable=search;
        if(FieldDef.FORMULA.equals(type))f.formula=extra; else if(FieldDef.REPEAT_GROUP.equals(type))f.optionsJson=extra; else if(FieldDef.SINGLE_CHOICE.equals(type)||FieldDef.MULTI_CHOICE.equals(type)){
            org.json.JSONArray a=new org.json.JSONArray(); for(String x:extra.split("\\n")) if(!x.trim().isEmpty())a.put(x.trim()); f.optionsJson=a.toString();
        }
        db.saveField(f);
    }

    private void openImport(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("application/json"); i.addCategory(Intent.CATEGORY_OPENABLE); startActivityForResult(i,REQ_IMPORT);
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==REQ_IMPORT && resultCode==RESULT_OK && data!=null && data.getData()!=null){
            try{long id=BackupManager.importCatalog(this,db,data.getData());toast("База импортирована");openCatalog(id);}catch(Exception e){toast("Ошибка импорта: "+e.getMessage());}
        }
    }

    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
}
