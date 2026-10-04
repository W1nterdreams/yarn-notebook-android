package ru.groupadmin.constructor;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TemplateActivity extends Activity {
    private DbHelper db;
    private long templateId;
    private CardTemplate template;
    private LinearLayout list;
    private SearchView search;
    private List<FieldDef> fields;

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        db=new DbHelper(this);
        templateId=getIntent().getLongExtra("template_id",0);
        render();
    }

    @Override protected void onResume(){
        super.onResume();
        if(list!=null)refresh();
    }

    private void render(){
        template=db.getTemplate(templateId);
        if(template==null){finish();return;}

        LinearLayout root=Ui.page(this);
        android.widget.Button back=Ui.outlineButton(this,"← Типы товаров");
        back.setOnClickListener(v->finish());
        root.addView(back,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(this,44)));

        root.addView(Ui.title(this,template.name));
        root.addView(Ui.subtitle(this,
                "Количество есть у каждого товара всегда. Быстро добавляйте поступление кнопкой «+» или списывайте проданное кнопкой «−»."));

        android.widget.Button add=Ui.primaryButton(this,"＋ Добавить товар");
        root.addView(add,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(this,52)));

        LinearLayout actions=Ui.row(this);
        android.widget.Button fieldsBtn=Ui.outlineButton(this,"Настроить поля");
        android.widget.Button output=Ui.outlineButton(this,"Шаблон вывода");
        actions.addView(fieldsBtn,new LinearLayout.LayoutParams(0,Ui.dp(this,46),1));
        actions.addView(output,new LinearLayout.LayoutParams(0,Ui.dp(this,46),1));
        root.addView(Ui.spacer(this,6));
        root.addView(actions);

        LinearLayout unitInfo=Ui.infoCard(this,Ui.PRIMARY_SOFT,Ui.BORDER);
        unitInfo.addView(Ui.text(this,"Единица учёта: "+template.quantityUnit,13,Ui.PRIMARY_DARK,true));
        root.addView(unitInfo);

        root.addView(Ui.sectionTitle(this,"ПОИСК И ТОВАРЫ"));
        search=new SearchView(this);
        search.setQueryHint("Поиск по товарам");
        search.setIconifiedByDefault(false);
        search.setBackground(Ui.inputBackground(this));
        search.setPadding(Ui.dp(this,6),0,Ui.dp(this,6),0);
        root.addView(search,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(this,54)));

        list=new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(Ui.scroll(this,list),new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));
        setContentView(root);

        add.setOnClickListener(v->createRecord());
        fieldsBtn.setOnClickListener(v->openFields());
        output.setOnClickListener(v->openOutput());
        search.setOnQueryTextListener(new SearchView.OnQueryTextListener(){
            @Override public boolean onQueryTextSubmit(String q){refresh();return true;}
            @Override public boolean onQueryTextChange(String q){refresh();return true;}
        });
        refresh();
    }

    private void refresh(){
        template=db.getTemplate(templateId);
        fields=db.getFields(templateId,false);
        list.removeAllViews();

        String q=search==null?"":search.getQuery().toString().trim().toLowerCase(Locale.ROOT);
        List<RecordItem> all=db.getRecords(templateId);
        List<RecordItem> shown=new ArrayList<>();
        for(RecordItem r:all) if(matches(r,q)) shown.add(r);

        if(shown.isEmpty()){
            LinearLayout c=Ui.infoCard(this,Ui.CARD,Ui.BORDER);
            c.addView(Ui.text(this,q.isEmpty()?"Товаров пока нет":"Ничего не найдено",18,Ui.TEXT,true));
            c.addView(Ui.text(this,
                    q.isEmpty()
                            ?"Создайте первый товар. Даже без дополнительных полей у него уже будет встроенный остаток."
                            :"Поиск идёт по настроенным полям, названию и остатку.",
                    14,Ui.MUTED,false));
            list.addView(c);
            return;
        }

        for(RecordItem r:shown) addRecordCard(r);
    }

    private boolean matches(RecordItem r,String q){
        if(q.isEmpty())return true;

        String stock=(Stock.display(r.quantity,template.quantityUnit)).toLowerCase(Locale.ROOT);
        if(stock.contains(q))return true;

        Map<Long,String> vals=db.getValues(r.id);
        String title=RecordLogic.titleFor(fields,vals);
        if(title.toLowerCase(Locale.ROOT).contains(q))return true;

        Map<String,String> computed=RecordLogic.computeDisplayValues(db,fields,vals,r.quantity);
        for(FieldDef f:fields){
            if(!f.searchable)continue;
            String x=computed.get(f.name);
            if(x!=null&&x.toLowerCase(Locale.ROOT).contains(q))return true;
        }
        return false;
    }

    private void addRecordCard(RecordItem r){
        Map<Long,String> vals=db.getValues(r.id);
        Map<String,String> display=RecordLogic.computeDisplayValues(db,fields,vals,r.quantity);

        LinearLayout c=Ui.card(this);
        String title=RecordLogic.titleFor(fields,vals);

        LinearLayout head=Ui.row(this);
        TextView tv=Ui.text(this,title,18,Ui.TEXT,true);
        head.addView(tv,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        if("DRAFT".equals(r.status)) head.addView(Ui.badge(this,"Черновик",Ui.DRAFT,Ui.DRAFT_BG));
        else head.addView(Ui.badge(this,"Сохранён",Ui.SUCCESS,Ui.SUCCESS_BG));
        c.addView(head);

        c.addView(Ui.spacer(this,10));
        addStockControl(c,r);

        int shown=0;
        for(FieldDef f:fields){
            if(!f.showInList||FieldDef.PHOTO.equals(f.type))continue;
            if("Количество".equals(f.name))continue;
            String v=display.get(f.name);
            if(v==null||v.isEmpty())continue;
            c.addView(Ui.spacer(this,7));
            c.addView(Ui.text(this,f.name+": "+v,13,Ui.MUTED,false));
            if(++shown>=4)break;
        }

        c.addView(Ui.spacer(this,12));
        LinearLayout row=Ui.row(this);
        android.widget.Button open=Ui.primaryButton(this,"Открыть");
        android.widget.Button copy=Ui.outlineButton(this,"Скопировать текст");
        row.addView(open,new LinearLayout.LayoutParams(0,Ui.dp(this,44),1));
        row.addView(copy,new LinearLayout.LayoutParams(0,Ui.dp(this,44),1.25f));
        c.addView(row);

        c.addView(Ui.spacer(this,6));
        LinearLayout row2=Ui.row(this);
        android.widget.Button dup=Ui.outlineButton(this,"Создать копию");
        android.widget.Button del=Ui.dangerButton(this,"Удалить");
        row2.addView(dup,new LinearLayout.LayoutParams(0,Ui.dp(this,42),1));
        row2.addView(del,new LinearLayout.LayoutParams(0,Ui.dp(this,42),1));
        c.addView(row2);

        open.setOnClickListener(v->openRecord(r.id));
        dup.setOnClickListener(v->{long id=db.duplicateRecord(r.id);openRecord(id);});
        copy.setOnClickListener(v->copyText(r.id));
        del.setOnClickListener(v->new AlertDialog.Builder(this)
                .setTitle("Удалить товар?")
                .setMessage(title)
                .setPositiveButton("Удалить",(d,w)->{db.deleteRecord(r.id);refresh();})
                .setNegativeButton("Отмена",null)
                .show());

        list.addView(c);
    }

    private void addStockControl(LinearLayout parent, RecordItem r){
        LinearLayout stock=Ui.infoCard(this,Ui.PRIMARY_SOFT,Ui.BORDER);
        LinearLayout row=Ui.row(this);

        LinearLayout textBox=new LinearLayout(this);
        textBox.setOrientation(LinearLayout.VERTICAL);
        textBox.addView(Ui.text(this,"В наличии",12,Ui.MUTED,true));
        TextView amount=Ui.text(this,Stock.display(r.quantity,template.quantityUnit),22,Ui.PRIMARY_DARK,true);
        textBox.addView(amount);
        row.addView(textBox,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));

        android.widget.Button minus=Ui.dangerButton(this,"−");
        android.widget.Button plus=Ui.primaryButton(this,"＋");
        minus.setTextSize(22);
        plus.setTextSize(20);
        row.addView(minus,new LinearLayout.LayoutParams(Ui.dp(this,56),Ui.dp(this,48)));
        row.addView(Ui.spacer(this,6));
        row.addView(plus,new LinearLayout.LayoutParams(Ui.dp(this,56),Ui.dp(this,48)));

        stock.addView(row);
        TextView hint=Ui.text(this,"− списать проданное   •   + добавить поступление",11,Ui.MUTED,false);
        hint.setGravity(Gravity.START);
        hint.setPadding(0,Ui.dp(this,7),0,0);
        stock.addView(hint);

        minus.setOnClickListener(v->Stock.showAdjustDialog(this,db,r.id,template,false,this::refresh));
        plus.setOnClickListener(v->Stock.showAdjustDialog(this,db,r.id,template,true,this::refresh));
        parent.addView(stock);
    }

    private void createRecord(){
        long id=db.createRecord(templateId);
        openRecord(id);
    }

    private void openRecord(long id){
        Intent i=new Intent(this,RecordEditActivity.class);
        i.putExtra("record_id",id);
        startActivity(i);
    }

    private void openFields(){
        Intent i=new Intent(this,FieldsActivity.class);
        i.putExtra("template_id",templateId);
        startActivity(i);
    }

    private void openOutput(){
        Intent i=new Intent(this,OutputTemplateActivity.class);
        i.putExtra("template_id",templateId);
        startActivity(i);
    }

    private void copyText(long recordId){
        RecordItem r=db.getRecord(recordId);
        if(r==null)return;
        String text=OutputEngine.render(db,template,fields,db.getValues(recordId),r.quantity);
        ClipboardManager cm=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("Товар",text));
        toast("Текст товара скопирован");
    }

    private void toast(String s){
        Toast.makeText(this,s,Toast.LENGTH_LONG).show();
    }
}
