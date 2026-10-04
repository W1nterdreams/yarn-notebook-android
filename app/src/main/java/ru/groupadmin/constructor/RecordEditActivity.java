package ru.groupadmin.constructor;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class RecordEditActivity extends Activity {
    private static final int REQ_PHOTO = 301;
    private DbHelper db;
    private long recordId;
    private RecordItem record;
    private CardTemplate template;
    private List<FieldDef> fields;
    private final Map<Long, FieldWidget> widgets = new LinkedHashMap<>();
    private long pendingPhotoFieldId;
    private boolean building = true;
    private TextView stockAmount;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        db = new DbHelper(this);
        recordId = getIntent().getLongExtra("record_id",0);
        record = db.getRecord(recordId);
        if(record==null){finish();return;}
        template = db.getTemplate(record.templateId);
        fields = db.getFields(record.templateId,false);
        render();
    }

    private void render() {
        Map<Long,String> saved = db.getValues(recordId);
        LinearLayout root = Ui.page(this);
        Button back=Ui.outlineButton(this,"← Товары");
        back.setOnClickListener(v->finish());
        root.addView(back,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(this,44)));

        root.addView(Ui.title(this, template.name));
        LinearLayout stateBox = Ui.infoCard(this,
                "DRAFT".equals(record.status) ? Ui.DRAFT_BG : Ui.SUCCESS_BG,
                Ui.BORDER);
        TextView state=Ui.text(this,
                "DRAFT".equals(record.status)
                        ? "Черновик · изменения ещё не сохранены"
                        : "Сохранённый товар",
                13,
                "DRAFT".equals(record.status) ? Ui.DRAFT : Ui.SUCCESS,
                true);
        stateBox.addView(state);
        root.addView(stateBox);

        addStockControl(root);

        LinearLayout form=new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);

        for(FieldDef f:fields){
            LinearLayout box=Ui.card(this);
            TextView label=Ui.text(this,f.name+(f.required?" *":""),15,Ui.TEXT,true);
            box.addView(label);
            if(f.unit!=null&&!f.unit.isEmpty()
                    && !FieldDef.FORMULA.equals(f.type)
                    && !FieldDef.REPEAT_GROUP.equals(f.type)) {
                box.addView(Ui.text(this,"Единица: "+f.unit,12,Ui.MUTED,false));
            }
            FieldWidget w=createWidget(f,saved.getOrDefault(f.id,""));
            widgets.put(f.id,w);
            box.addView(w.root);
            form.addView(box);
        }

        root.addView(Ui.scroll(this,form),new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));

        LinearLayout actions=Ui.row(this);
        Button save=Ui.primaryButton(this,"Сохранить товар");
        Button copy=Ui.outlineButton(this,"Копировать текст");
        actions.addView(save,new LinearLayout.LayoutParams(0,Ui.dp(this,54),1));
        actions.addView(copy,new LinearLayout.LayoutParams(0,Ui.dp(this,54),1));
        root.addView(actions);
        setContentView(root);

        save.setOnClickListener(v->save());
        copy.setOnClickListener(v->copyOutput());
        building=false;
        recalc();
    }

    private FieldWidget createWidget(FieldDef f,String raw){
        FieldWidget w=new FieldWidget(f);

        if(FieldDef.AUTO_COUNTER.equals(f.type)){
            LinearLayout counter=Ui.infoCard(this,Ui.PRIMARY_SOFT,Ui.BORDER);
            TextView value=Ui.text(this,raw.isEmpty()?"Будет присвоен автоматически":raw,18,Ui.PRIMARY_DARK,true);
            counter.addView(value);
            counter.addView(Ui.text(this,"Автоматическое поле · вручную не редактируется",11,Ui.MUTED,false));
            w.raw=raw;
            w.root=counter;

        } else if(FieldDef.CHECKBOX.equals(f.type)){
            CheckBox c=new CheckBox(this);
            c.setText("Да");
            c.setTextColor(Ui.TEXT);
            c.setChecked("1".equals(raw)||"true".equalsIgnoreCase(raw));
            c.setOnCheckedChangeListener((b,x)->changed());
            w.input=c;
            w.root=c;

        } else if(FieldDef.DATE.equals(f.type)){
            Button b=Ui.outlineButton(this,raw.isEmpty()?"Выбрать дату":raw);
            b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
            b.setOnClickListener(v->pickDate(w,b));
            w.input=b;
            w.raw=raw;
            w.root=b;

        } else if(FieldDef.SINGLE_CHOICE.equals(f.type)){
            Spinner s=new Spinner(this);
            List<String> opts=options(f);
            List<String> withEmpty=new ArrayList<>();
            withEmpty.add("— не выбрано —");
            withEmpty.addAll(opts);
            s.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,withEmpty));
            s.setBackground(Ui.inputBackground(this));
            int pos=withEmpty.indexOf(raw);
            s.setSelection(Math.max(0,pos));
            s.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
                @Override public void onItemSelected(AdapterView<?> p,View v,int i,long id){changed();}
                @Override public void onNothingSelected(AdapterView<?> p){}
            });
            w.input=s;
            w.root=s;
            w.options=withEmpty;

        } else if(FieldDef.MULTI_CHOICE.equals(f.type)){
            Button b=Ui.outlineButton(this,"");
            w.input=b;
            w.options=options(f);
            w.selected=decodeMulti(raw);
            updateMultiButton(w,b);
            b.setOnClickListener(v->pickMulti(w,b));
            w.root=b;

        } else if(FieldDef.REPEAT_GROUP.equals(f.type)){
            w.groupConfig=RepeatGroup.parseConfig(f.optionsJson);
            LinearLayout groupRoot=new LinearLayout(this);
            groupRoot.setOrientation(LinearLayout.VERTICAL);
            w.groupRowsBox=new LinearLayout(this);
            w.groupRowsBox.setOrientation(LinearLayout.VERTICAL);
            groupRoot.addView(w.groupRowsBox);

            JSONArray rows=RepeatGroup.parseRows(raw);
            for(int i=0;i<rows.length();i++){
                JSONObject row=rows.optJSONObject(i);
                if(row!=null)addGroupRow(w,row);
            }
            if(w.groupRows.isEmpty()) addGroupRow(w,new JSONObject());

            Button addRow=Ui.outlineButton(this,"＋ Добавить строку");
            addRow.setOnClickListener(v->{addGroupRow(w,new JSONObject());changed();});
            groupRoot.addView(addRow,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(this,48)));
            w.root=groupRoot;

        } else if(FieldDef.PHOTO.equals(f.type)){
            LinearLayout photoBox=new LinearLayout(this);
            photoBox.setOrientation(LinearLayout.VERTICAL);
            ImageView img=new ImageView(this);
            img.setAdjustViewBounds(true);
            img.setMaxHeight(Ui.dp(this,220));
            img.setBackgroundColor(Color.rgb(238,238,242));
            photoBox.addView(img,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(this,180)));
            Button choose=Ui.outlineButton(this,raw.isEmpty()?"Выбрать фото":"Заменить фото");
            photoBox.addView(choose,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(this,48)));
            w.root=photoBox;
            w.image=img;
            w.raw=raw;
            showPhoto(w);
            choose.setOnClickListener(v->{
                pendingPhotoFieldId=f.id;
                Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.setType("image/*");
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                startActivityForResult(i,REQ_PHOTO);
            });

        } else if(FieldDef.FORMULA.equals(f.type)){
            TextView tv=Ui.text(this,"…",19,Ui.PRIMARY,true);
            tv.setPadding(Ui.dp(this,4),Ui.dp(this,10),Ui.dp(this,4),Ui.dp(this,10));
            w.formulaView=tv;
            w.root=tv;

        } else {
            EditText e=new EditText(this);
            e.setText(raw);
            e.setTextColor(Ui.TEXT);
            e.setHintTextColor(Ui.MUTED);
            e.setBackground(Ui.inputBackground(this));
            e.setPadding(Ui.dp(this,12),Ui.dp(this,10),Ui.dp(this,12),Ui.dp(this,10));

            if(FieldDef.MULTILINE.equals(f.type)){
                e.setMinLines(4);
                e.setGravity(Gravity.TOP|Gravity.START);
                e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            } else {
                e.setSingleLine(true);
            }

            if(FieldDef.INTEGER.equals(f.type))
                e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_SIGNED);
            if(FieldDef.DECIMAL.equals(f.type)||FieldDef.PRICE.equals(f.type))
                e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED);

            e.addTextChangedListener(new TextWatcher(){
                @Override public void beforeTextChanged(CharSequence s,int st,int c,int a){}
                @Override public void onTextChanged(CharSequence s,int st,int before,int count){changed();}
                @Override public void afterTextChanged(Editable e){}
            });
            w.input=e;
            w.root=e;
        }
        return w;
    }

    private void addGroupRow(FieldWidget w, JSONObject initial) {
        GroupRow row = new GroupRow();
        row.values = new LinkedHashMap<>();
        LinearLayout card = Ui.card(this);
        card.addView(Ui.badge(this,"Строка "+(w.groupRows.size()+1),Ui.PRIMARY_DARK,Ui.PRIMARY_SOFT));
        card.addView(Ui.spacer(this,8));

        for(RepeatGroup.SubField sub : w.groupConfig) {
            TextView label=Ui.text(this,sub.name + (sub.unit.isEmpty()?"":" · "+sub.unit),13,Ui.MUTED,true);
            card.addView(label);

            View input;
            String value=initial.optString(sub.name,"");
            if(FieldDef.CHECKBOX.equals(sub.type)) {
                CheckBox c=new CheckBox(this);
                c.setText("Да");
                c.setTextColor(Ui.TEXT);
                c.setChecked("1".equals(value)||"true".equalsIgnoreCase(value));
                c.setOnCheckedChangeListener((b,x)->changed());
                input=c;
            } else {
                EditText e=new EditText(this);
                e.setText(value);
                e.setTextColor(Ui.TEXT);
                e.setHintTextColor(Ui.MUTED);
                e.setBackground(Ui.inputBackground(this));
                e.setPadding(Ui.dp(this,10),Ui.dp(this,8),Ui.dp(this,10),Ui.dp(this,8));
                e.setSingleLine(true);
                if(FieldDef.INTEGER.equals(sub.type))
                    e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_SIGNED);
                if(FieldDef.DECIMAL.equals(sub.type)||FieldDef.PRICE.equals(sub.type))
                    e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED);
                e.addTextChangedListener(new TextWatcher(){
                    @Override public void beforeTextChanged(CharSequence s,int st,int c,int a){}
                    @Override public void onTextChanged(CharSequence s,int st,int before,int count){changed();}
                    @Override public void afterTextChanged(Editable e){}
                });
                input=e;
            }
            row.values.put(sub.name,input);
            card.addView(input,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(this,48)));
        }

        Button remove=Ui.dangerButton(this,"Удалить строку");
        remove.setOnClickListener(v->{
            w.groupRows.remove(row);
            w.groupRowsBox.removeView(card);
            if(w.groupRows.isEmpty()) addGroupRow(w,new JSONObject());
            changed();
        });
        card.addView(remove,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(this,42)));

        row.root=card;
        w.groupRows.add(row);
        w.groupRowsBox.addView(card);
    }

    private void addStockControl(LinearLayout root){
        LinearLayout stock=Ui.infoCard(this,Ui.PRIMARY_SOFT,Ui.BORDER);
        LinearLayout row=Ui.row(this);

        LinearLayout textBox=new LinearLayout(this);
        textBox.setOrientation(LinearLayout.VERTICAL);
        textBox.addView(Ui.text(this,"В наличии",12,Ui.MUTED,true));
        stockAmount=Ui.text(this,Stock.display(record.quantity,template.quantityUnit),23,Ui.PRIMARY_DARK,true);
        textBox.addView(stockAmount);
        row.addView(textBox,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));

        Button minus=Ui.dangerButton(this,"−");
        Button plus=Ui.primaryButton(this,"＋");
        minus.setTextSize(22);
        plus.setTextSize(20);
        row.addView(minus,new LinearLayout.LayoutParams(Ui.dp(this,58),Ui.dp(this,50)));
        row.addView(Ui.spacer(this,7));
        row.addView(plus,new LinearLayout.LayoutParams(Ui.dp(this,58),Ui.dp(this,50)));

        stock.addView(row);
        stock.addView(Ui.text(this,
                "Количество хранится отдельно от настраиваемых полей товара. Нажмите − или + и введите любое количество.",
                11,Ui.MUTED,false));

        minus.setOnClickListener(v->Stock.showAdjustDialog(this,db,recordId,template,false,this::stockChanged));
        plus.setOnClickListener(v->Stock.showAdjustDialog(this,db,recordId,template,true,this::stockChanged));
        root.addView(stock);
    }

    private void stockChanged(){
        RecordItem latest=db.getRecord(recordId);
        if(latest!=null)record=latest;
        if(stockAmount!=null)stockAmount.setText(Stock.display(record.quantity,template.quantityUnit));
        recalc();
    }

    private void changed(){if(!building)recalc();}

    private void recalc(){
        Map<Long,String> raw=collectRaw(false);
        Map<String,String> computed=RecordLogic.computeRawByName(fields,raw,record.quantity);
        for(FieldDef f:fields){
            if(!FieldDef.FORMULA.equals(f.type))continue;
            FieldWidget w=widgets.get(f.id);
            String x=computed.getOrDefault(f.name,"#ОШИБКА");
            String shown=x+((f.unit==null||f.unit.isEmpty())?"":" "+f.unit);
            if(w!=null&&w.formulaView!=null)w.formulaView.setText(shown);
        }
    }

    private Map<Long,String> collectRaw(boolean includeFormula){
        Map<Long,String> out=new LinkedHashMap<>();
        for(FieldDef f:fields){
            FieldWidget w=widgets.get(f.id);
            if(w==null)continue;
            if(FieldDef.FORMULA.equals(f.type)){
                if(includeFormula){
                    Map<String,String> comp=RecordLogic.computeRawByName(fields,out,record.quantity);
                    out.put(f.id,comp.getOrDefault(f.name,"#ОШИБКА"));
                }
                continue;
            }
            out.put(f.id,w.getRaw());
        }
        return out;
    }

    private boolean isEmptyValue(FieldDef f,String value) {
        if(value==null || value.trim().isEmpty()) return true;
        if(FieldDef.MULTI_CHOICE.equals(f.type) || FieldDef.REPEAT_GROUP.equals(f.type)) {
            try { return new JSONArray(value).length()==0; } catch(Exception e) { return true; }
        }
        return false;
    }

    private void save(){
        Map<Long,String> raw=collectRaw(false);
        for(FieldDef f:fields){
            if(!f.required||FieldDef.FORMULA.equals(f.type))continue;
            String v=raw.getOrDefault(f.id,"");
            if(isEmptyValue(f,v)){
                toast("Заполните обязательное поле «"+f.name+"»");
                return;
            }
        }
        for(Map.Entry<Long,String> e:raw.entrySet())db.setValue(recordId,e.getKey(),e.getValue());
        db.markRecordSaved(recordId);
        record=db.getRecord(recordId);
        toast("Товар сохранён");
    }

    private void copyOutput(){
        Map<Long,String> raw=collectRaw(false);
        String text=OutputEngine.render(db,template,fields,raw,record.quantity);
        ClipboardManager cm=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("Товар",text));
        toast("Текст товара скопирован");
    }

    private void pickDate(FieldWidget w,Button b){
        Calendar cal=Calendar.getInstance();
        if(w.raw!=null&&!w.raw.isEmpty()){
            try{
                java.util.Date d=new SimpleDateFormat("yyyy-MM-dd",Locale.US).parse(w.raw);
                if(d!=null)cal.setTime(d);
            }catch(Exception ignored){}
        }
        new DatePickerDialog(this,(v,y,m,d)->{
            w.raw=String.format(Locale.US,"%04d-%02d-%02d",y,m+1,d);
            b.setText(w.raw);
            changed();
        },cal.get(Calendar.YEAR),cal.get(Calendar.MONTH),cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void pickMulti(FieldWidget w,Button b){
        String[] opts=w.options.toArray(new String[0]);
        boolean[] checks=new boolean[opts.length];
        for(int i=0;i<opts.length;i++)checks[i]=w.selected.contains(opts[i]);
        new AlertDialog.Builder(this).setTitle(w.field.name)
                .setMultiChoiceItems(opts,checks,(d,which,on)->checks[which]=on)
                .setPositiveButton("Готово",(d,x)->{
                    w.selected.clear();
                    for(int i=0;i<opts.length;i++)if(checks[i])w.selected.add(opts[i]);
                    updateMultiButton(w,b);
                    changed();
                })
                .setNegativeButton("Отмена",null).show();
    }

    private void updateMultiButton(FieldWidget w,Button b){
        b.setText(w.selected.isEmpty()?"— не выбрано —":String.join(", ",w.selected));
    }

    private List<String> options(FieldDef f){
        List<String> out=new ArrayList<>();
        try{
            JSONArray a=new JSONArray(f.optionsJson);
            for(int i=0;i<a.length();i++){
                String x=a.optString(i).trim();
                if(!x.isEmpty())out.add(x);
            }
        }catch(Exception ignored){}
        return out;
    }

    private List<String> decodeMulti(String raw){
        List<String> out=new ArrayList<>();
        try{
            JSONArray a=new JSONArray(raw);
            for(int i=0;i<a.length();i++)out.add(a.optString(i));
        }catch(Exception ignored){}
        return out;
    }

    @Override protected void onActivityResult(int req,int res,Intent data){
        super.onActivityResult(req,res,data);
        if(req==REQ_PHOTO&&res==RESULT_OK&&data!=null&&data.getData()!=null){
            Uri uri=data.getData();
            try{
                getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }catch(Exception ignored){}
            FieldWidget w=widgets.get(pendingPhotoFieldId);
            if(w!=null){
                w.raw=uri.toString();
                showPhoto(w);
                changed();
            }
        }
    }

    private void showPhoto(FieldWidget w){
        if(w.image==null)return;
        if(w.raw==null||w.raw.isEmpty()){
            w.image.setImageDrawable(null);
            return;
        }
        try{w.image.setImageURI(Uri.parse(w.raw));}
        catch(Exception e){w.image.setImageDrawable(null);}
    }

    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}

    private final class FieldWidget {
        final FieldDef field;
        View root;
        View input;
        TextView formulaView;
        ImageView image;
        String raw="";
        List<String> options=new ArrayList<>();
        List<String> selected=new ArrayList<>();

        List<RepeatGroup.SubField> groupConfig=new ArrayList<>();
        List<GroupRow> groupRows=new ArrayList<>();
        LinearLayout groupRowsBox;

        FieldWidget(FieldDef f){field=f;}

        String getRaw(){
            if(FieldDef.AUTO_COUNTER.equals(field.type))
                return raw==null?"":raw;
            if(FieldDef.CHECKBOX.equals(field.type))
                return ((CheckBox)input).isChecked()?"1":"0";
            if(FieldDef.DATE.equals(field.type)||FieldDef.PHOTO.equals(field.type))
                return raw==null?"":raw;
            if(FieldDef.SINGLE_CHOICE.equals(field.type)){
                int p=((Spinner)input).getSelectedItemPosition();
                return p<=0?"":options.get(p);
            }
            if(FieldDef.MULTI_CHOICE.equals(field.type)){
                JSONArray a=new JSONArray();
                for(String x:selected)a.put(x);
                return a.toString();
            }
            if(FieldDef.REPEAT_GROUP.equals(field.type)){
                JSONArray rows=new JSONArray();
                for(GroupRow row:groupRows){
                    JSONObject o=row.toJson(groupConfig);
                    if(o.length()>0)rows.put(o);
                }
                return rows.toString();
            }
            if(input instanceof EditText)
                return ((EditText)input).getText().toString().trim();
            return "";
        }
    }

    private static final class GroupRow {
        LinearLayout root;
        Map<String,View> values;

        JSONObject toJson(List<RepeatGroup.SubField> config) {
            JSONObject out=new JSONObject();
            for(RepeatGroup.SubField sub:config){
                View v=values.get(sub.name);
                String value="";
                if(v instanceof CheckBox) value=((CheckBox)v).isChecked()?"1":"0";
                else if(v instanceof EditText) value=((EditText)v).getText().toString().trim();
                if(value.isEmpty() || ("0".equals(value) && v instanceof CheckBox)) continue;
                try{out.put(sub.name,value);}catch(Exception ignored){}
            }
            return out;
        }
    }
}
