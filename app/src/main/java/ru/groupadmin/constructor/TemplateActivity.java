package ru.groupadmin.constructor;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SearchView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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

        android.widget.Button add=Ui.primaryButton(this,"＋ Добавить товар");
        root.addView(add,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(this,52)));

        root.addView(Ui.spacer(this,6));
        LinearLayout actions=Ui.row(this);
        android.widget.Button fieldsBtn=Ui.outlineButton(this,"Поля");
        android.widget.Button cardViewBtn=Ui.outlineButton(this,"Вид карточки");
        actions.addView(fieldsBtn,new LinearLayout.LayoutParams(0,Ui.dp(this,46),1));
        actions.addView(Ui.hSpacer(this,6));
        actions.addView(cardViewBtn,new LinearLayout.LayoutParams(0,Ui.dp(this,46),1));
        root.addView(actions);

        root.addView(Ui.spacer(this,6));
        android.widget.Button output=Ui.outlineButton(this,"Шаблон вывода текста");
        root.addView(output,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(this,46)));

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
        cardViewBtn.setOnClickListener(v->showCardAppearanceDialog());
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
                            ?"Создайте первый товар."
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
        addStockControl(c,r,vals);

        for(FieldDef f:getReferenceFields()){
            String v=display.get(f.name);
            if(v==null||v.trim().isEmpty())continue;

            LinearLayout info=Ui.row(this);
            TextView name=Ui.text(this,f.name,12,Ui.MUTED,true);
            TextView value=Ui.text(this,v,13,Ui.TEXT,false);
            value.setGravity(Gravity.END);
            info.addView(name,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,0.42f));
            info.addView(value,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,0.58f));
            c.addView(Ui.spacer(this,8));
            c.addView(info);
        }

        c.addView(Ui.spacer(this,12));
        LinearLayout row=Ui.row(this);
        android.widget.Button open=Ui.primaryButton(this,"Открыть");
        android.widget.Button copy=Ui.outlineButton(this,"Скопировать текст");
        row.addView(open,new LinearLayout.LayoutParams(0,Ui.dp(this,44),1));
        row.addView(Ui.hSpacer(this,6));
        row.addView(copy,new LinearLayout.LayoutParams(0,Ui.dp(this,44),1.25f));
        c.addView(row);

        c.addView(Ui.spacer(this,6));
        LinearLayout row2=Ui.row(this);
        android.widget.Button dup=Ui.outlineButton(this,"Создать копию");
        android.widget.Button del=Ui.dangerButton(this,"Удалить");
        row2.addView(dup,new LinearLayout.LayoutParams(0,Ui.dp(this,42),1));
        row2.addView(Ui.hSpacer(this,6));
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

    private List<FieldDef> getReferenceFields(){
        List<FieldDef> result=new ArrayList<>();
        long[] configured={template.cardField1,template.cardField2,template.cardField3,template.cardField4};
        boolean hasConfigured=false;
        for(long id:configured) if(id>0){hasConfigured=true;break;}

        if(hasConfigured){
            for(long id:configured){
                if(id<=0)continue;
                FieldDef f=findField(id);
                if(f!=null && !FieldDef.PHOTO.equals(f.type)) result.add(f);
            }
            return result;
        }

        for(FieldDef f:fields){
            if(!f.showInList || FieldDef.PHOTO.equals(f.type))continue;
            result.add(f);
            if(result.size()>=4)break;
        }
        return result;
    }

    private FieldDef findField(long id){
        for(FieldDef f:fields) if(f.id==id)return f;
        return null;
    }

    private void showCardAppearanceDialog(){
        fields=db.getFields(templateId,false);
        List<FieldDef> candidates=new ArrayList<>();
        for(FieldDef f:fields){
            if(FieldDef.PHOTO.equals(f.type))continue;
            candidates.add(f);
        }

        if(candidates.isEmpty()){
            toast("Сначала добавьте поля товара");
            return;
        }

        List<String> labels=new ArrayList<>();
        labels.add("— не показывать —");
        for(FieldDef f:candidates) labels.add(f.name);

        ArrayAdapter<String> adapter=new ArrayAdapter<>(
                this,android.R.layout.simple_spinner_dropdown_item,labels);

        LinearLayout form=new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int p=Ui.dp(this,18);
        form.setPadding(p,Ui.dp(this,4),p,0);

        Spinner[] spinners=new Spinner[4];
        long[] current={template.cardField1,template.cardField2,template.cardField3,template.cardField4};

        for(int i=0;i<4;i++){
            TextView label=Ui.text(this,(i+1)+" параметр",13,Ui.MUTED,true);
            form.addView(label);

            Spinner spinner=new Spinner(this);
            spinner.setAdapter(adapter);
            spinner.setBackground(Ui.inputBackground(this));
            spinner.setSelection(positionForField(candidates,current[i]));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(this,52));
            lp.setMargins(0,Ui.dp(this,4),0,Ui.dp(this,10));
            form.addView(spinner,lp);
            spinners[i]=spinner;
        }

        AlertDialog dialog=new AlertDialog.Builder(this)
                .setTitle("Вид карточки товара")
                .setMessage("Выберите до четырёх справочных параметров. Они будут показаны в карточке товара в указанном порядке.")
                .setView(form)
                .setPositiveButton("Сохранить",null)
                .setNegativeButton("Отмена",null)
                .create();

        dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            long[] ids=new long[4];
            Set<Long> used=new HashSet<>();

            for(int i=0;i<4;i++){
                int pos=spinners[i].getSelectedItemPosition();
                ids[i]=pos<=0?0:candidates.get(pos-1).id;
                if(ids[i]>0 && !used.add(ids[i])){
                    toast("Один параметр нельзя выбрать дважды");
                    return;
                }
            }

            db.setCardFields(templateId,ids[0],ids[1],ids[2],ids[3]);
            template=db.getTemplate(templateId);
            dialog.dismiss();
            refresh();
        }));
        dialog.show();
    }

    private int positionForField(List<FieldDef> candidates,long fieldId){
        if(fieldId<=0)return 0;
        for(int i=0;i<candidates.size();i++) if(candidates.get(i).id==fieldId)return i+1;
        return 0;
    }

    private void addStockControl(LinearLayout parent, RecordItem r, Map<Long,String> values){
        LinearLayout stock=Ui.infoCard(this,Ui.PRIMARY_SOFT,Ui.BORDER);
        LinearLayout row=Ui.row(this);

        String photoUri=findPhotoUri(values);
        if(photoUri!=null){
            ImageView photo=new ImageView(this);
            photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
            photo.setBackground(Ui.roundStroke(Ui.CARD,Ui.BORDER,1,12,this));
            photo.setClipToOutline(true);
            try{
                photo.setImageURI(Uri.parse(photoUri));
            }catch(Exception ignored){
                photo.setImageDrawable(null);
            }
            row.addView(photo,new LinearLayout.LayoutParams(Ui.dp(this,72),Ui.dp(this,72)));
            row.addView(Ui.hSpacer(this,10));
        }

        LinearLayout textBox=new LinearLayout(this);
        textBox.setOrientation(LinearLayout.VERTICAL);
        textBox.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);

        TextView stockLabel=Ui.text(this,"В наличии",12,Ui.MUTED,true);
        stockLabel.setGravity(Gravity.END);
        textBox.addView(stockLabel);

        TextView amount=Ui.text(this,Stock.display(r.quantity,template.quantityUnit),22,Ui.PRIMARY_DARK,true);
        amount.setGravity(Gravity.END);
        textBox.addView(amount);

        row.addView(textBox,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        row.addView(Ui.hSpacer(this,10));

        android.widget.Button minus=Ui.dangerButton(this,"−");
        android.widget.Button plus=Ui.primaryButton(this,"＋");
        minus.setTextSize(22);
        plus.setTextSize(20);
        row.addView(minus,new LinearLayout.LayoutParams(Ui.dp(this,56),Ui.dp(this,52)));
        row.addView(Ui.hSpacer(this,6));
        row.addView(plus,new LinearLayout.LayoutParams(Ui.dp(this,56),Ui.dp(this,52)));

        stock.addView(row);

        minus.setOnClickListener(v->Stock.showAdjustDialog(this,db,r.id,template,false,this::refresh));
        plus.setOnClickListener(v->Stock.showAdjustDialog(this,db,r.id,template,true,this::refresh));
        parent.addView(stock);
    }

    private String findPhotoUri(Map<Long,String> values){
        for(FieldDef f:fields){
            if(!FieldDef.PHOTO.equals(f.type)) continue;
            String uri=values.get(f.id);
            if(uri!=null && !uri.trim().isEmpty()) return uri.trim();
        }
        return null;
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
