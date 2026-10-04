package ru.groupadmin.constructor;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.SearchView;
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

    @Override public void onCreate(Bundle b){super.onCreate(b);db=new DbHelper(this);templateId=getIntent().getLongExtra("template_id",0);render();}
    @Override protected void onResume(){super.onResume();if(list!=null)refresh();}

    private void render(){
        template=db.getTemplate(templateId);if(template==null){finish();return;}
        LinearLayout root=Ui.page(this);
        android.widget.Button back=Ui.outlineButton(this,"← Типы товаров");
        back.setOnClickListener(v->finish());
        root.addView(back,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(this,44)));

        root.addView(Ui.title(this,template.name));
        root.addView(Ui.subtitle(this,"Список товаров этого типа. Поля, формулы и текст для публикации настраиваются отдельно."));

        android.widget.Button add=Ui.primaryButton(this,"＋ Добавить товар");
        root.addView(add,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(this,52)));

        LinearLayout actions=Ui.row(this);
        android.widget.Button fieldsBtn=Ui.outlineButton(this,"Настроить поля");
        android.widget.Button output=Ui.outlineButton(this,"Шаблон вывода");
        actions.addView(fieldsBtn,new LinearLayout.LayoutParams(0,Ui.dp(this,46),1));
        actions.addView(output,new LinearLayout.LayoutParams(0,Ui.dp(this,46),1));
        root.addView(Ui.spacer(this,6));
        root.addView(actions);

        root.addView(Ui.sectionTitle(this,"ПОИСК И ТОВАРЫ"));
        search=new SearchView(this);
        search.setQueryHint("Поиск по товарам");
        search.setIconifiedByDefault(false);
        search.setBackground(Ui.inputBackground(this));
        search.setPadding(Ui.dp(this,6),0,Ui.dp(this,6),0);
        root.addView(search,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(this,54)));
        list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);root.addView(Ui.scroll(this,list),new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));setContentView(root);
        add.setOnClickListener(v->createRecord());fieldsBtn.setOnClickListener(v->openFields());output.setOnClickListener(v->openOutput());
        search.setOnQueryTextListener(new SearchView.OnQueryTextListener(){@Override public boolean onQueryTextSubmit(String q){refresh();return true;}@Override public boolean onQueryTextChange(String q){refresh();return true;}});
        refresh();
    }

    private void refresh(){
        template=db.getTemplate(templateId);fields=db.getFields(templateId,false);list.removeAllViews();String q=search==null?"":search.getQuery().toString().trim().toLowerCase(Locale.ROOT);
        List<RecordItem> all=db.getRecords(templateId);List<RecordItem> shown=new ArrayList<>();for(RecordItem r:all)if(matches(r,q))shown.add(r);
        if(fields.isEmpty()){
            LinearLayout c=Ui.infoCard(this,Ui.PRIMARY_SOFT,Ui.BORDER);
            c.addView(Ui.text(this,"Сначала настройте поля",18,Ui.PRIMARY_DARK,true));
            c.addView(Ui.text(this,"Без полей товар будет пустым. Нажмите «Настроить поля» и задайте структуру: название, цену, состав, параметры, фото и т. д.",14,Ui.MUTED,false));
            list.addView(c);return;
        }
        if(shown.isEmpty()){
            LinearLayout c=Ui.infoCard(this,Ui.CARD,Ui.BORDER);
            c.addView(Ui.text(this,q.isEmpty()?"Товаров пока нет":"Ничего не найдено",18,Ui.TEXT,true));
            c.addView(Ui.text(this,q.isEmpty()?"Создайте первый товар. Обязательными будут только те поля, которые вы сами так отметили.":"Поиск идёт по полям, для которых включена настройка «Участвует в поиске».",14,Ui.MUTED,false));
            list.addView(c);return;
        }
        for(RecordItem r:shown)addRecordCard(r);
    }

    private boolean matches(RecordItem r,String q){if(q.isEmpty())return true;Map<Long,String> vals=db.getValues(r.id);Map<String,String> computed=RecordLogic.computeDisplayValues(db,fields,vals);for(FieldDef f:fields){if(!f.searchable)continue;String x=computed.get(f.name);if(x!=null&&x.toLowerCase(Locale.ROOT).contains(q))return true;}return false;}

    private void addRecordCard(RecordItem r){
        Map<Long,String> vals=db.getValues(r.id);Map<String,String> display=RecordLogic.computeDisplayValues(db,fields,vals);
        LinearLayout c=Ui.card(this);
        String title=RecordLogic.titleFor(fields,vals);
        LinearLayout head=Ui.row(this);
        android.widget.TextView tv=Ui.text(this,title,18,Ui.TEXT,true);
        head.addView(tv,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        if("DRAFT".equals(r.status)) head.addView(Ui.badge(this,"Черновик",Ui.DRAFT,Ui.DRAFT_BG));
        else head.addView(Ui.badge(this,"Сохранён",Ui.SUCCESS,Ui.SUCCESS_BG));
        c.addView(head);
        c.addView(Ui.spacer(this,8));

        int shown=0;
        for(FieldDef f:fields){
            if(!f.showInList||FieldDef.PHOTO.equals(f.type))continue;
            String v=display.get(f.name);
            if(v==null||v.isEmpty())continue;
            c.addView(Ui.text(this,f.name+": "+v,13,Ui.MUTED,false));
            c.addView(Ui.spacer(this,4));
            if(++shown>=4)break;
        }

        c.addView(Ui.spacer(this,8));
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
        open.setOnClickListener(v->openRecord(r.id));dup.setOnClickListener(v->{long id=db.duplicateRecord(r.id);openRecord(id);});copy.setOnClickListener(v->copyText(r.id));del.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Удалить товар?").setMessage(title).setPositiveButton("Удалить",(d,w)->{db.deleteRecord(r.id);refresh();}).setNegativeButton("Отмена",null).show());list.addView(c);
    }

    private void createRecord(){if(db.getFields(templateId,false).isEmpty()){toast("Сначала добавьте хотя бы одно поле");openFields();return;}long id=db.createRecord(templateId);openRecord(id);}
    private void openRecord(long id){Intent i=new Intent(this,RecordEditActivity.class);i.putExtra("record_id",id);startActivity(i);}
    private void openFields(){Intent i=new Intent(this,FieldsActivity.class);i.putExtra("template_id",templateId);startActivity(i);}
    private void openOutput(){Intent i=new Intent(this,OutputTemplateActivity.class);i.putExtra("template_id",templateId);startActivity(i);}
    private void copyText(long recordId){String text=OutputEngine.render(db,template,fields,db.getValues(recordId));ClipboardManager cm=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);cm.setPrimaryClip(ClipData.newPlainText("Товар",text));toast("Текст товара скопирован");}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
}
