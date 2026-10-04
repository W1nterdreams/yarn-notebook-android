package ru.groupadmin.constructor;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.util.List;

public class OutputTemplateActivity extends Activity {
    private DbHelper db;
    private long templateId;
    private CardTemplate template;
    private List<FieldDef> fields;
    private EditText editor;

    @Override public void onCreate(Bundle b){super.onCreate(b);db=new DbHelper(this);templateId=getIntent().getLongExtra("template_id",0);template=db.getTemplate(templateId);if(template==null){finish();return;}fields=db.getFields(templateId,false);render();}

    private void render(){
        LinearLayout root=Ui.page(this);android.widget.Button back=Ui.outlineButton(this,"← Товары");back.setOnClickListener(v->finish());root.addView(back,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(this,44)));
        root.addView(Ui.title(this,"Шаблон вывода"));
        root.addView(Ui.subtitle(this,"Соберите готовый текст товара для VK, объявления или сообщения. Значения полей вставляются через {Название поля}."));
        LinearLayout hint=Ui.infoCard(this,Ui.PRIMARY_SOFT,Ui.BORDER);
        hint.addView(Ui.text(this,"Пример: {Название}\nЦена: {Цена}\nВ наличии: {Количество} {Единица учёта}",13,Ui.PRIMARY_DARK,false));
        root.addView(hint);
        root.addView(Ui.sectionTitle(this,"ТЕКСТ ШАБЛОНА"));
        editor=new EditText(this);editor.setText(template.outputTemplate==null?"":template.outputTemplate);editor.setGravity(android.view.Gravity.TOP);editor.setMinLines(10);editor.setTextColor(Ui.TEXT);editor.setBackground(Ui.inputBackground(this));editor.setPadding(Ui.dp(this,12),Ui.dp(this,12),Ui.dp(this,12),Ui.dp(this,12));root.addView(editor,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));
        root.addView(Ui.sectionTitle(this,"ВСТАВИТЬ ПОЛЕ"));
        android.widget.HorizontalScrollView hs=new android.widget.HorizontalScrollView(this);
        LinearLayout buttons=Ui.row(this);
        android.widget.Button qty=Ui.primaryButton(this,"Количество");
        qty.setOnClickListener(v->insert("{Количество}"));
        buttons.addView(qty,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,Ui.dp(this,44)));
        android.widget.Button qtyUnit=Ui.outlineButton(this,"Единица учёта");
        qtyUnit.setOnClickListener(v->insert("{Единица учёта}"));
        buttons.addView(qtyUnit,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,Ui.dp(this,44)));
        for(FieldDef f:fields){
            if("Количество".equals(f.name)) continue;
            android.widget.Button b=Ui.outlineButton(this,f.name);
            b.setOnClickListener(v->insert("{"+f.name+"}"));
            buttons.addView(b,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,Ui.dp(this,44)));
        }
        hs.addView(buttons);root.addView(hs,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(this,50)));
        LinearLayout actions=Ui.row(this);android.widget.Button auto=Ui.outlineButton(this,"Авто");android.widget.Button preview=Ui.outlineButton(this,"Предпросмотр");android.widget.Button save=Ui.primaryButton(this,"Сохранить");actions.addView(auto,new LinearLayout.LayoutParams(0,Ui.dp(this,52),1));actions.addView(preview,new LinearLayout.LayoutParams(0,Ui.dp(this,52),1));actions.addView(save,new LinearLayout.LayoutParams(0,Ui.dp(this,52),1));root.addView(actions);setContentView(root);
        auto.setOnClickListener(v->editor.setText(OutputEngine.autoPattern(fields)));preview.setOnClickListener(v->preview());save.setOnClickListener(v->{db.setOutputTemplate(templateId,editor.getText().toString());toast("Шаблон сохранён");finish();});
    }
    private void insert(String s){int st=Math.max(0,editor.getSelectionStart());editor.getText().insert(st,s);}
    private void preview(){
        CardTemplate temp=new CardTemplate();temp.outputTemplate=editor.getText().toString();temp.quantityUnit=template.quantityUnit;List<RecordItem> records=db.getRecords(templateId);String text;
        if(records.isEmpty()){java.util.Map<Long,String> sample=new java.util.LinkedHashMap<>();for(FieldDef f:fields){if(FieldDef.FORMULA.equals(f.type))continue;sample.put(f.id,"["+f.name+"]");}text=OutputEngine.render(db,temp,fields,sample,10);}else{RecordItem first=records.get(0);text=OutputEngine.render(db,temp,fields,db.getValues(first.id),first.quantity);}
        new AlertDialog.Builder(this).setTitle("Предпросмотр").setMessage(text.isEmpty()?"Пустой шаблон":text).setPositiveButton("Копировать",(d,w)->{ClipboardManager cm=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);cm.setPrimaryClip(ClipData.newPlainText("Предпросмотр",text));toast("Скопировано");}).setNegativeButton("Закрыть",null).show();
    }
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
}
