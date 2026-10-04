package ru.groupadmin.constructor;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.util.List;

public class CatalogActivity extends Activity {
    private static final int REQ_EXPORT=201;
    private DbHelper db;
    private long catalogId;
    private LinearLayout list;
    private Catalog catalog;

    @Override public void onCreate(Bundle b){super.onCreate(b);db=new DbHelper(this);catalogId=getIntent().getLongExtra("catalog_id",0);render();}
    @Override protected void onResume(){super.onResume();if(list!=null)refresh();}

    private void render(){
        catalog=db.getCatalog(catalogId); if(catalog==null){finish();return;}
        LinearLayout root=Ui.page(this);
        android.widget.Button back=Ui.outlineButton(this,"← Базы");
        back.setOnClickListener(v->finish());
        root.addView(back,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(this,44)));

        root.addView(Ui.title(this,catalog.name));
        root.addView(Ui.subtitle(this,"Здесь хранятся разные типы товаров. У каждого типа свой набор полей, формул и шаблон вывода."));

        android.widget.Button add=Ui.primaryButton(this,"＋ Создать тип товара");
        root.addView(add,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(this,52)));
        root.addView(Ui.spacer(this,6));
        android.widget.Button export=Ui.outlineButton(this,"Экспортировать базу в JSON");
        root.addView(export,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(this,46)));
        root.addView(Ui.sectionTitle(this,"ТИПЫ ТОВАРОВ"));

        list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);root.addView(Ui.scroll(this,list),new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));setContentView(root);
        add.setOnClickListener(v->newTemplate());export.setOnClickListener(v->exportCatalog());refresh();
    }

    private void refresh(){
        catalog=db.getCatalog(catalogId);list.removeAllViews();List<CardTemplate> ts=db.getTemplates(catalogId);
        if(ts.isEmpty()){LinearLayout c=Ui.card(this);c.addView(Ui.text(this,"Нет типов товаров",18,Ui.TEXT,true));c.addView(Ui.text(this,"Например: «Пряжа», «Двигатель», «Автозапчасть», «Одежда». Для каждого типа задаётся независимый набор полей, формул и шаблон вывода.",14,Ui.MUTED,false));list.addView(c);return;}
        for(CardTemplate t:ts){
            LinearLayout c=Ui.card(this);
            c.addView(Ui.text(this,t.name,19,Ui.TEXT,true));

            LinearLayout stats=Ui.row(this);
            stats.addView(Ui.badge(this,"Полей: "+db.getFields(t.id,false).size(),Ui.PRIMARY_DARK,Ui.PRIMARY_SOFT));
            stats.addView(Ui.spacer(this,6));
            stats.addView(Ui.badge(this,"Товаров: "+db.countRecords(t.id),Ui.SUCCESS,Ui.SUCCESS_BG));
            c.addView(Ui.spacer(this,9));
            c.addView(stats);
            c.addView(Ui.spacer(this,10));

            LinearLayout r=Ui.row(this);
            android.widget.Button open=Ui.primaryButton(this,"Открыть товары");
            android.widget.Button fields=Ui.outlineButton(this,"Настроить поля");
            r.addView(open,new LinearLayout.LayoutParams(0,Ui.dp(this,46),1.2f));
            r.addView(fields,new LinearLayout.LayoutParams(0,Ui.dp(this,46),1));
            c.addView(r);
            c.addView(Ui.spacer(this,6));

            LinearLayout r2=Ui.row(this);
            android.widget.Button rename=Ui.outlineButton(this,"Переименовать");
            android.widget.Button del=Ui.dangerButton(this,"Удалить тип");
            r2.addView(rename,new LinearLayout.LayoutParams(0,Ui.dp(this,44),1));
            r2.addView(del,new LinearLayout.LayoutParams(0,Ui.dp(this,44),1));
            c.addView(r2);
            open.setOnClickListener(v->openTemplate(t.id));fields.setOnClickListener(v->openFields(t.id));rename.setOnClickListener(v->rename(t));del.setOnClickListener(v->delete(t));list.addView(c);
        }
    }

    private void newTemplate(){EditText e=new EditText(this);e.setHint("Например: Товар");new AlertDialog.Builder(this).setTitle("Новый тип товара").setView(e).setPositiveButton("Создать",(d,w)->{String n=e.getText().toString().trim();if(n.isEmpty())return;long id=db.createTemplate(catalogId,n);openFields(id);}).setNegativeButton("Отмена",null).show();}
    private void rename(CardTemplate t){EditText e=new EditText(this);e.setText(t.name);e.setSelection(e.length());new AlertDialog.Builder(this).setTitle("Название типа").setView(e).setPositiveButton("Сохранить",(d,w)->{String n=e.getText().toString().trim();if(!n.isEmpty()){db.renameTemplate(t.id,n);refresh();}}).setNegativeButton("Отмена",null).show();}
    private void delete(CardTemplate t){new AlertDialog.Builder(this).setTitle("Удалить тип товара?").setMessage("Будут удалены его поля и все товары этого типа.").setPositiveButton("Удалить",(d,w)->{db.deleteTemplate(t.id);refresh();}).setNegativeButton("Отмена",null).show();}
    private void openTemplate(long id){Intent i=new Intent(this,TemplateActivity.class);i.putExtra("template_id",id);startActivity(i);}
    private void openFields(long id){Intent i=new Intent(this,FieldsActivity.class);i.putExtra("template_id",id);startActivity(i);}

    private void exportCatalog(){Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("application/json");i.putExtra(Intent.EXTRA_TITLE,BackupManager.safeFileName(catalog.name)+".json");startActivityForResult(i,REQ_EXPORT);}
    @Override protected void onActivityResult(int req,int res,Intent data){super.onActivityResult(req,res,data);if(req==REQ_EXPORT&&res==RESULT_OK&&data!=null&&data.getData()!=null){try{BackupManager.exportCatalog(this,db,catalogId,data.getData());Toast.makeText(this,"Экспорт готов",Toast.LENGTH_LONG).show();}catch(Exception e){Toast.makeText(this,"Ошибка экспорта: "+e.getMessage(),Toast.LENGTH_LONG).show();}}}
}
