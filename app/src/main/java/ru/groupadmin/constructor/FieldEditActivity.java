package ru.groupadmin.constructor;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;

public class FieldEditActivity extends Activity {
    private DbHelper db;
    private long templateId;
    private long fieldId;
    private FieldDef original;
    private EditText name, unit, def, options, formula;
    private Spinner type;
    private CheckBox required, showInList, searchable;
    private LinearLayout optionsBox, formulaBox;
    private String[] types;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        db = new DbHelper(this);
        templateId = getIntent().getLongExtra("template_id",0);
        fieldId = getIntent().getLongExtra("field_id",0);
        original = fieldId > 0 ? db.getField(fieldId) : null;
        render();
    }

    private void render() {
        LinearLayout root = Ui.page(this);
        android.widget.Button back = Ui.button(this, "← Поля");
        back.setOnClickListener(v -> finish());
        root.addView(back, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(this,44)));
        root.addView(Ui.title(this, original == null ? "Новое поле" : "Изменить поле"));

        LinearLayout form = new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL);
        name = input("Название поля", false); form.addView(label("Название")); form.addView(name);
        type = new Spinner(this);
        types = FieldDef.allTypes();
        String[] labels = new String[types.length];
        for(int i=0;i<types.length;i++) labels[i]=FieldDef.humanType(types[i]);
        type.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
        form.addView(label("Тип ввода")); form.addView(type, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(this,52)));

        unit = input("Например: ₽, кг, м, шт.", false); form.addView(label("Единица измерения (необязательно)")); form.addView(unit);
        def = input("Значение для новой карточки", false); form.addView(label("Значение по умолчанию")); form.addView(def);

        required = new CheckBox(this); required.setText("Обязательное поле"); required.setTextColor(Ui.TEXT); form.addView(required);
        showInList = new CheckBox(this); showInList.setText("Показывать значение в списке карточек"); showInList.setTextColor(Ui.TEXT); showInList.setChecked(true); form.addView(showInList);
        searchable = new CheckBox(this); searchable.setText("Участвует в поиске"); searchable.setTextColor(Ui.TEXT); searchable.setChecked(true); form.addView(searchable);

        optionsBox = new LinearLayout(this); optionsBox.setOrientation(LinearLayout.VERTICAL);
        optionsBox.addView(label("Варианты выбора — по одному в строке"));
        options = input("Красный\nСиний\nЗелёный", true); options.setMinLines(5); optionsBox.addView(options);
        form.addView(optionsBox);

        formulaBox = new LinearLayout(this); formulaBox.setOrientation(LinearLayout.VERTICAL);
        formulaBox.addView(label("Формула"));
        formula = input("={Цена}*{Количество}", true); formula.setMinLines(3); formulaBox.addView(formula);
        formulaBox.addView(Ui.text(this,"Ссылки на поля пишутся в фигурных скобках. Доступны + − × / % ^, скобки и функции ROUND, MIN, MAX, ABS, CEIL, FLOOR, IF. Аргументы функций разделяются точкой с запятой. Пример: =IF({Количество}>0;{Цена}*{Количество};0)",13,Ui.MUTED,false));
        form.addView(formulaBox);

        android.widget.Button save=Ui.primaryButton(this,"Сохранить поле"); form.addView(Ui.spacer(this,8)); form.addView(save,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(this,54)));
        root.addView(Ui.scroll(this,form),new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));
        setContentView(root);

        type.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id){updateConditional();}
            @Override public void onNothingSelected(android.widget.AdapterView<?> p){}
        });
        save.setOnClickListener(v->save());
        fill(); updateConditional();
    }

    private TextView label(String s){TextView v=Ui.text(this,s,14,Ui.TEXT,true);v.setPadding(0,Ui.dp(this,10),0,Ui.dp(this,3));return v;}
    private EditText input(String hint, boolean multi){EditText e=new EditText(this);e.setHint(hint);e.setTextColor(Ui.TEXT);e.setHintTextColor(Ui.MUTED);e.setBackgroundColor(android.graphics.Color.WHITE);e.setPadding(Ui.dp(this,12),Ui.dp(this,10),Ui.dp(this,12),Ui.dp(this,10)); if(!multi)e.setSingleLine(true); return e;}

    private void fill(){
        if(original==null)return;
        name.setText(original.name); unit.setText(original.unit); def.setText(original.defaultValue); required.setChecked(original.required); showInList.setChecked(original.showInList); searchable.setChecked(original.searchable); formula.setText(original.formula);
        for(int i=0;i<types.length;i++)if(types[i].equals(original.type)){type.setSelection(i);break;}
        try{JSONArray a=new JSONArray(original.optionsJson);StringBuilder b=new StringBuilder();for(int i=0;i<a.length();i++){if(i>0)b.append('\n');b.append(a.optString(i));}options.setText(b.toString());}catch(Exception ignored){}
    }

    private void updateConditional(){
        String t=types[type.getSelectedItemPosition()];
        boolean choices=FieldDef.SINGLE_CHOICE.equals(t)||FieldDef.MULTI_CHOICE.equals(t);
        optionsBox.setVisibility(choices?View.VISIBLE:View.GONE);
        formulaBox.setVisibility(FieldDef.FORMULA.equals(t)?View.VISIBLE:View.GONE);
        def.setEnabled(!FieldDef.FORMULA.equals(t));
        required.setEnabled(!FieldDef.FORMULA.equals(t));
        searchable.setEnabled(!FieldDef.PHOTO.equals(t));
    }

    private void save(){
        String n=name.getText().toString().trim(); if(n.isEmpty()){toast("Введите название поля");return;}
        if(n.contains("{")||n.contains("}")){toast("В названии нельзя использовать { и }");return;}
        if(db.fieldNameExists(templateId,n,fieldId)){toast("Поле с таким названием уже есть");return;}
        String t=types[type.getSelectedItemPosition()];
        if(FieldDef.FORMULA.equals(t)&&formula.getText().toString().trim().isEmpty()){toast("Введите формулу");return;}
        FieldDef f=original==null?new FieldDef():original;f.templateId=templateId;f.name=n;f.type=t;f.required=required.isChecked();f.unit=unit.getText().toString().trim();f.defaultValue=def.getText().toString().trim();f.showInList=showInList.isChecked();f.searchable=searchable.isChecked();f.formula=formula.getText().toString().trim();
        JSONArray a=new JSONArray();for(String line:options.getText().toString().split("\\r?\\n")){String x=line.trim();if(!x.isEmpty())a.put(x);}f.optionsJson=a.toString();
        db.saveField(f);toast("Поле сохранено");finish();
    }
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
}
