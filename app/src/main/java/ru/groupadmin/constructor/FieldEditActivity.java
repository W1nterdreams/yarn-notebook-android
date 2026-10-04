package ru.groupadmin.constructor;

import android.app.Activity;
import android.os.Bundle;
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FieldEditActivity extends Activity {
    private DbHelper db;
    private long templateId;
    private long fieldId;
    private FieldDef original;
    private EditText name, unit, def, options, formula;
    private EditText counterPrefix, counterSuffix, counterStart, counterStep, counterDigits, counterNext;
    private Spinner type;
    private CheckBox required, showInList, searchable;
    private LinearLayout optionsBox, formulaBox, repeatBox, repeatList, counterBox, unitBox, defaultBox;
    private String[] types;
    private final List<GroupSubEditor> groupEditors = new ArrayList<>();

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
        android.widget.Button back = Ui.outlineButton(this, "← Поля");
        back.setOnClickListener(v -> finish());
        root.addView(back, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(this,44)));
        root.addView(Ui.title(this, original == null ? "Новое поле" : "Изменить поле"));
        root.addView(Ui.subtitle(this,"Настройте, как это поле будет выглядеть при вводе товара и как его использовать в списке, поиске и формулах."));

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);

        form.addView(Ui.sectionTitle(this,"ОСНОВНОЕ"));
        name = input("Название поля", false);
        form.addView(label("Название"));
        form.addView(name);

        type = new Spinner(this);
        types = FieldDef.allTypes();
        String[] labels = new String[types.length];
        for(int i=0;i<types.length;i++) labels[i]=FieldDef.humanType(types[i]);
        type.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
        type.setBackground(Ui.inputBackground(this));
        form.addView(label("Тип ввода"));
        form.addView(type, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(this,52)));

        unitBox = new LinearLayout(this);
        unitBox.setOrientation(LinearLayout.VERTICAL);
        unit = input("Например: ₽, кг, м, шт.", false);
        unitBox.addView(label("Единица измерения (необязательно)"));
        unitBox.addView(unit);
        form.addView(unitBox);

        defaultBox = new LinearLayout(this);
        defaultBox.setOrientation(LinearLayout.VERTICAL);
        def = input("Значение для нового товара", false);
        defaultBox.addView(label("Значение по умолчанию"));
        defaultBox.addView(def);
        form.addView(defaultBox);

        form.addView(Ui.sectionTitle(this,"ПОВЕДЕНИЕ ПОЛЯ"));

        required = new CheckBox(this);
        required.setText("Обязательное поле");
        required.setTextColor(Ui.TEXT);
        form.addView(required);

        showInList = new CheckBox(this);
        showInList.setText("Показывать значение в списке товаров");
        showInList.setTextColor(Ui.TEXT);
        showInList.setChecked(true);
        showInList.setVisibility(View.GONE);
        form.addView(showInList);

        form.addView(Ui.text(this,
                "Какие 4 параметра показывать на товарной карточке, настраивается отдельно через «Вид карточки».",
                12,Ui.MUTED,false));

        searchable = new CheckBox(this);
        searchable.setText("Участвует в поиске");
        searchable.setTextColor(Ui.TEXT);
        searchable.setChecked(true);
        form.addView(searchable);

        form.addView(Ui.sectionTitle(this,"НАСТРОЙКА ТИПА"));

        optionsBox = new LinearLayout(this);
        optionsBox.setOrientation(LinearLayout.VERTICAL);
        optionsBox.addView(label("Варианты выбора — по одному в строке"));
        options = input("Красный\nСиний\nЗелёный", true);
        options.setMinLines(5);
        optionsBox.addView(options);
        form.addView(optionsBox);

        repeatBox = new LinearLayout(this);
        repeatBox.setOrientation(LinearLayout.VERTICAL);
        repeatBox.addView(label("Подполя повторяемой группы"));
        LinearLayout repeatHint = Ui.infoCard(this, Ui.PRIMARY_SOFT, Ui.BORDER);
        repeatHint.addView(Ui.text(this,
                "Повторяемая группа подходит для состава, комплектации и любых наборов, где у одного товара может быть несколько строк. Например: «Материал + %».",
                13, Ui.PRIMARY_DARK, false));
        repeatBox.addView(repeatHint);
        repeatList = new LinearLayout(this);
        repeatList.setOrientation(LinearLayout.VERTICAL);
        repeatBox.addView(repeatList);
        android.widget.Button addSub = Ui.outlineButton(this, "＋ Добавить подполе");
        repeatBox.addView(addSub, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this,48)));
        addSub.setOnClickListener(v -> addGroupSubfield("", FieldDef.TEXT, ""));
        form.addView(repeatBox);

        counterBox = new LinearLayout(this);
        counterBox.setOrientation(LinearLayout.VERTICAL);
        counterBox.addView(label("Автоматическая нумерация"));

        LinearLayout counterHint = Ui.infoCard(this, Ui.PRIMARY_SOFT, Ui.BORDER);
        counterHint.addView(Ui.text(this,
                "Номер присваивается автоматически при создании товара и больше не редактируется. Можно сделать простой № 1, 2, 3 или артикул вида ART-0001.",
                13, Ui.PRIMARY_DARK, false));
        counterBox.addView(counterHint);

        counterPrefix = input("Префикс, например ART-", false);
        counterSuffix = input("Суффикс, например -VK", false);
        counterStart = input("Начать с", false);
        counterStep = input("Шаг", false);
        counterDigits = input("Минимум цифр", false);
        counterNext = input("Следующий номер", false);

        counterStart.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        counterStep.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        counterDigits.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        counterNext.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);

        counterBox.addView(label("Префикс"));
        counterBox.addView(counterPrefix);
        counterBox.addView(label("Суффикс"));
        counterBox.addView(counterSuffix);

        LinearLayout counterNums1 = Ui.row(this);
        LinearLayout startBox = new LinearLayout(this); startBox.setOrientation(LinearLayout.VERTICAL);
        startBox.addView(label("Начать с")); startBox.addView(counterStart);
        LinearLayout stepBox = new LinearLayout(this); stepBox.setOrientation(LinearLayout.VERTICAL);
        stepBox.addView(label("Шаг")); stepBox.addView(counterStep);
        counterNums1.addView(startBox,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        counterNums1.addView(Ui.hSpacer(this,6));
        counterNums1.addView(stepBox,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        counterBox.addView(counterNums1);

        LinearLayout counterNums2 = Ui.row(this);
        LinearLayout digitsBox = new LinearLayout(this); digitsBox.setOrientation(LinearLayout.VERTICAL);
        digitsBox.addView(label("Минимум цифр")); digitsBox.addView(counterDigits);
        LinearLayout nextBox = new LinearLayout(this); nextBox.setOrientation(LinearLayout.VERTICAL);
        nextBox.addView(label("Следующий номер")); nextBox.addView(counterNext);
        counterNums2.addView(digitsBox,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        counterNums2.addView(Ui.hSpacer(this,6));
        counterNums2.addView(nextBox,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        counterBox.addView(counterNums2);

        counterBox.addView(Ui.text(this,
                "Пример: префикс ART-, число 1 и 4 цифры → ART-0001. Удалённые номера повторно не используются.",
                12, Ui.MUTED, false));
        form.addView(counterBox);

        formulaBox = new LinearLayout(this);
        formulaBox.setOrientation(LinearLayout.VERTICAL);
        formulaBox.addView(label("Формула"));
        formula = input("={Цена}*{Количество}", true);
        formula.setMinLines(3);
        formulaBox.addView(formula);
        formulaBox.addView(Ui.text(this,
                "Ссылки на поля пишутся в фигурных скобках. Доступны + − × / % ^, скобки и функции ROUND, MIN, MAX, ABS, CEIL, FLOOR, IF. Аргументы функций разделяются точкой с запятой. Пример: =IF({Количество}>0;{Цена}*{Количество};0)",
                13,Ui.MUTED,false));
        form.addView(formulaBox);

        android.widget.Button save=Ui.primaryButton(this,"Сохранить поле");
        form.addView(Ui.spacer(this,8));
        form.addView(save,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(this,54)));
        root.addView(Ui.scroll(this,form),new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));
        setContentView(root);

        type.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id){updateConditional();}
            @Override public void onNothingSelected(android.widget.AdapterView<?> p){}
        });
        save.setOnClickListener(v->save());
        fill();
        updateConditional();
    }

    private TextView label(String s){
        TextView v=Ui.text(this,s,14,Ui.TEXT,true);
        v.setPadding(0,Ui.dp(this,10),0,Ui.dp(this,3));
        return v;
    }

    private EditText input(String hint, boolean multi){
        EditText e=new EditText(this);
        e.setHint(hint);
        e.setTextColor(Ui.TEXT);
        e.setHintTextColor(Ui.MUTED);
        e.setBackground(Ui.inputBackground(this));
        e.setPadding(Ui.dp(this,12),Ui.dp(this,10),Ui.dp(this,12),Ui.dp(this,10));
        if(!multi)e.setSingleLine(true);
        return e;
    }

    private void fill(){
        if(original==null)return;
        name.setText(original.name);
        unit.setText(original.unit);
        def.setText(original.defaultValue);
        required.setChecked(original.required);
        showInList.setChecked(original.showInList);
        searchable.setChecked(original.searchable);
        formula.setText(original.formula);
        for(int i=0;i<types.length;i++)if(types[i].equals(original.type)){type.setSelection(i);break;}

        if(FieldDef.REPEAT_GROUP.equals(original.type)) {
            for(RepeatGroup.SubField s : RepeatGroup.parseConfig(original.optionsJson)) {
                addGroupSubfield(s.name, s.type, s.unit);
            }
        } else if(FieldDef.AUTO_COUNTER.equals(original.type)) {
            AutoCounter.Config cfg=AutoCounter.parse(original.optionsJson);
            counterPrefix.setText(cfg.prefix);
            counterSuffix.setText(cfg.suffix);
            counterStart.setText(String.valueOf(cfg.start));
            counterStep.setText(String.valueOf(cfg.step));
            counterDigits.setText(String.valueOf(cfg.digits));
            counterNext.setText(String.valueOf(db.getCounterNext(original.id)));
        } else {
            try{
                JSONArray a=new JSONArray(original.optionsJson);
                StringBuilder b=new StringBuilder();
                for(int i=0;i<a.length();i++){
                    if(i>0)b.append('\n');
                    b.append(a.optString(i));
                }
                options.setText(b.toString());
            }catch(Exception ignored){}
        }
    }

    private void updateConditional(){
        String t=types[type.getSelectedItemPosition()];
        boolean choices=FieldDef.SINGLE_CHOICE.equals(t)||FieldDef.MULTI_CHOICE.equals(t);
        boolean repeat=FieldDef.REPEAT_GROUP.equals(t);
        boolean formulaType=FieldDef.FORMULA.equals(t);
        boolean counterType=FieldDef.AUTO_COUNTER.equals(t);

        optionsBox.setVisibility(choices?View.VISIBLE:View.GONE);
        repeatBox.setVisibility(repeat?View.VISIBLE:View.GONE);
        counterBox.setVisibility(counterType?View.VISIBLE:View.GONE);
        formulaBox.setVisibility(formulaType?View.VISIBLE:View.GONE);
        unitBox.setVisibility((repeat||counterType)?View.GONE:View.VISIBLE);
        defaultBox.setVisibility((formulaType||repeat||counterType)?View.GONE:View.VISIBLE);

        required.setEnabled(!formulaType&&!counterType);
        if(counterType) required.setChecked(false);
        searchable.setEnabled(!FieldDef.PHOTO.equals(t));

        if(counterType && counterStart.getText().toString().trim().isEmpty()){
            counterStart.setText("1");
            counterStep.setText("1");
            counterDigits.setText("0");
            counterNext.setText("1");
        }

        if(repeat && groupEditors.isEmpty()) {
            addGroupSubfield("Значение", FieldDef.TEXT, "");
        }
    }

    private void addGroupSubfield(String initialName, String initialType, String initialUnit){
        GroupSubEditor ed = new GroupSubEditor();
        LinearLayout card = Ui.card(this);
        ed.root = card;

        ed.name = input("Название подполя", false);
        ed.name.setText(initialName);
        card.addView(ed.name);

        LinearLayout row = Ui.row(this);
        ed.type = new Spinner(this);
        String[] labels = new String[RepeatGroup.SUB_TYPES.length];
        for(int i=0;i<labels.length;i++) labels[i]=FieldDef.humanType(RepeatGroup.SUB_TYPES[i]);
        ed.type.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
        ed.type.setBackground(Ui.inputBackground(this));
        for(int i=0;i<RepeatGroup.SUB_TYPES.length;i++) if(RepeatGroup.SUB_TYPES[i].equals(initialType)) { ed.type.setSelection(i); break; }

        ed.unit = input("Ед. изм.", false);
        ed.unit.setText(initialUnit);

        row.addView(ed.type,new LinearLayout.LayoutParams(0,Ui.dp(this,50),1.4f));
        row.addView(ed.unit,new LinearLayout.LayoutParams(0,Ui.dp(this,50),1f));
        card.addView(row);

        android.widget.Button remove=Ui.dangerButton(this,"Удалить подполе");
        remove.setOnClickListener(v->{groupEditors.remove(ed);repeatList.removeView(card);});
        card.addView(remove,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(this,42)));

        groupEditors.add(ed);
        repeatList.addView(card);
    }

    private void save(){
        String n=name.getText().toString().trim();
        if(n.isEmpty()){toast("Введите название поля");return;}
        if(n.contains("{")||n.contains("}")){toast("В названии нельзя использовать { и }");return;}
        if("Количество".equalsIgnoreCase(n) || "Единица учёта".equalsIgnoreCase(n)){
            toast("Это системное поле уже встроено в каждый товар");
            return;
        }
        if(db.fieldNameExists(templateId,n,fieldId)){toast("Поле с таким названием уже есть");return;}
        String t=types[type.getSelectedItemPosition()];
        if(FieldDef.FORMULA.equals(t)&&formula.getText().toString().trim().isEmpty()){toast("Введите формулу");return;}

        FieldDef f=original==null?new FieldDef():original;
        f.templateId=templateId;
        f.name=n;
        f.type=t;
        f.required=required.isChecked();
        f.unit=unit.getText().toString().trim();
        f.defaultValue=def.getText().toString().trim();
        f.showInList=showInList.isChecked();
        f.searchable=searchable.isChecked();
        f.formula=formula.getText().toString().trim();

        long desiredCounterNext=-1;

        if(FieldDef.REPEAT_GROUP.equals(t)) {
            List<RepeatGroup.SubField> cfg = new ArrayList<>();
            Set<String> names = new HashSet<>();
            for(GroupSubEditor ed : groupEditors) {
                String subName = ed.name.getText().toString().trim();
                if(subName.isEmpty()) continue;
                String key=subName.toLowerCase();
                if(names.contains(key)){toast("Названия подполей не должны повторяться");return;}
                names.add(key);
                RepeatGroup.SubField s=new RepeatGroup.SubField();
                s.name=subName;
                s.type=RepeatGroup.SUB_TYPES[ed.type.getSelectedItemPosition()];
                s.unit=ed.unit.getText().toString().trim();
                cfg.add(s);
            }
            if(cfg.isEmpty()){toast("Добавьте хотя бы одно подполе");return;}
            f.optionsJson=RepeatGroup.encodeConfig(cfg);
            f.unit="";
            f.defaultValue="";
            f.formula="";
        } else if(FieldDef.AUTO_COUNTER.equals(t)) {
            AutoCounter.Config cfg=new AutoCounter.Config();
            cfg.prefix=counterPrefix.getText().toString();
            cfg.suffix=counterSuffix.getText().toString();
            try{cfg.start=Long.parseLong(counterStart.getText().toString().trim());}catch(Exception e){toast("Неверное начальное число");return;}
            try{cfg.step=Long.parseLong(counterStep.getText().toString().trim());}catch(Exception e){toast("Неверный шаг");return;}
            try{cfg.digits=Integer.parseInt(counterDigits.getText().toString().trim());}catch(Exception e){toast("Неверное количество цифр");return;}
            try{desiredCounterNext=Long.parseLong(counterNext.getText().toString().trim());}catch(Exception e){desiredCounterNext=cfg.start;}

            if(cfg.start<0){toast("Начальное число не может быть отрицательным");return;}
            if(cfg.step<=0){toast("Шаг должен быть больше нуля");return;}
            if(cfg.digits<0||cfg.digits>18){toast("Количество цифр: от 0 до 18");return;}
            if(desiredCounterNext<0){toast("Следующий номер не может быть отрицательным");return;}

            f.optionsJson=AutoCounter.encode(cfg);
            f.unit="";
            f.defaultValue="";
            f.formula="";
            f.required=false;
        } else {
            JSONArray a=new JSONArray();
            for(String line:options.getText().toString().split("\\r?\\n")){
                String x=line.trim();
                if(!x.isEmpty())a.put(x);
            }
            f.optionsJson=a.toString();
        }

        long savedId=db.saveField(f);
        if(FieldDef.AUTO_COUNTER.equals(t)){
            if(desiredCounterNext<0){
                AutoCounter.Config cfg=AutoCounter.parse(f.optionsJson);
                desiredCounterNext=cfg.start;
            }
            db.setCounterNext(savedId,desiredCounterNext);
            db.ensureAutoCounterValues(savedId);
        }
        toast("Поле сохранено");
        finish();
    }

    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}

    private static final class GroupSubEditor {
        LinearLayout root;
        EditText name;
        Spinner type;
        EditText unit;
    }
}
