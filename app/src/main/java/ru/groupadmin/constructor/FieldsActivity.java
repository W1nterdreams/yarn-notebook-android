package ru.groupadmin.constructor;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class FieldsActivity extends Activity {
    private DbHelper db;
    private long templateId;
    private LinearLayout list;
    private CardTemplate template;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        db = new DbHelper(this);
        templateId = getIntent().getLongExtra("template_id", 0);
        render();
    }

    @Override protected void onResume() {
        super.onResume();
        if (list != null) refresh();
    }

    private void render() {
        template = db.getTemplate(templateId);
        if (template == null) { finish(); return; }
        LinearLayout root = Ui.page(this);
        android.widget.Button back = Ui.outlineButton(this, "← " + template.name);
        back.setOnClickListener(v -> finish());
        root.addView(back, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 44)));

        root.addView(Ui.title(this, "Поля товара"));
        root.addView(Ui.subtitle(this, "Здесь вы проектируете форму товара. Порядок полей = порядок ввода. Поле можно архивировать без потери уже введённых данных."));

        android.widget.Button add = Ui.primaryButton(this, "＋ Добавить новое поле");
        root.addView(add, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 52)));
        root.addView(Ui.spacer(this,6));
        android.widget.Button archive = Ui.outlineButton(this, "Архив полей");
        root.addView(archive, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 44)));
        root.addView(Ui.sectionTitle(this,"СТРУКТУРА ТОВАРА"));

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(Ui.scroll(this, list), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);

        add.setOnClickListener(v -> editField(0));
        archive.setOnClickListener(v -> showArchive());
        refresh();
    }

    private void refresh() {
        list.removeAllViews();
        List<FieldDef> fields = db.getFields(templateId, false);
        if (fields.isEmpty()) {
            LinearLayout c = Ui.infoCard(this, Ui.PRIMARY_SOFT, Ui.BORDER);
            c.addView(Ui.text(this, "Пока нет полей", 18, Ui.PRIMARY_DARK, true));
            c.addView(Ui.text(this, "Добавьте любые характеристики: текст, числа, автосчётчик, цену, выбор из списка, повторяемые группы, дату, фото или формулу.", 14, Ui.MUTED, false));
            list.addView(c);
            return;
        }
        for (int i = 0; i < fields.size(); i++) {
            FieldDef f = fields.get(i);
            LinearLayout c = Ui.card(this);

            LinearLayout head = Ui.row(this);
            head.addView(Ui.text(this, (i + 1) + ". " + f.name, 18, Ui.TEXT, true),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            head.addView(Ui.badge(this, FieldDef.humanType(f.type), Ui.PRIMARY_DARK, Ui.PRIMARY_SOFT));
            c.addView(head);
            c.addView(Ui.spacer(this,8));

            LinearLayout meta = Ui.row(this);
            if (f.required) {
                meta.addView(Ui.badge(this,"Обязательное",Ui.DANGER,Ui.DANGER_BG));
                meta.addView(Ui.spacer(this,5));
            }
            if (f.showInList) {
                meta.addView(Ui.badge(this,"В списке",Ui.SUCCESS,Ui.SUCCESS_BG));
                meta.addView(Ui.spacer(this,5));
            }
            if (f.searchable) meta.addView(Ui.badge(this,"Поиск",Ui.PRIMARY_DARK,Ui.PRIMARY_SOFT));
            c.addView(meta);

            if (f.unit != null && !f.unit.isEmpty()) {
                c.addView(Ui.spacer(this,6));
                c.addView(Ui.text(this,"Единица измерения: "+f.unit,13,Ui.MUTED,false));
            }
            if (FieldDef.FORMULA.equals(f.type)) {
                c.addView(Ui.spacer(this,6));
                c.addView(Ui.text(this,"Формула: " + f.formula,13,Ui.PRIMARY_DARK,true));
            }
            if (FieldDef.REPEAT_GROUP.equals(f.type)) {
                c.addView(Ui.spacer(this,6));
                c.addView(Ui.text(this,"Подполей: " + RepeatGroup.parseConfig(f.optionsJson).size(),13,Ui.MUTED,false));
            }
            if (FieldDef.AUTO_COUNTER.equals(f.type)) {
                AutoCounter.Config cfg=AutoCounter.parse(f.optionsJson);
                c.addView(Ui.spacer(this,6));
                c.addView(Ui.text(this,
                        "Следующий: " + AutoCounter.format(cfg,db.getCounterNext(f.id)),
                        13,Ui.PRIMARY_DARK,true));
            }

            c.addView(Ui.spacer(this,10));
            LinearLayout r = Ui.row(this);
            android.widget.Button up = Ui.outlineButton(this, "↑ Выше");
            android.widget.Button down = Ui.outlineButton(this, "↓ Ниже");
            r.addView(up, new LinearLayout.LayoutParams(0, Ui.dp(this, 42), 1));
            r.addView(down, new LinearLayout.LayoutParams(0, Ui.dp(this, 42), 1));
            c.addView(r);

            c.addView(Ui.spacer(this,6));
            LinearLayout r2 = Ui.row(this);
            android.widget.Button edit = Ui.primaryButton(this, "Изменить");
            android.widget.Button hide = Ui.dangerButton(this, "В архив");
            r2.addView(edit, new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1.2f));
            r2.addView(hide, new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1));
            c.addView(r2);
            up.setEnabled(i > 0);
            down.setEnabled(i < fields.size() - 1);
            up.setOnClickListener(v -> { db.moveField(f.id, -1); refresh(); });
            down.setOnClickListener(v -> { db.moveField(f.id, 1); refresh(); });
            edit.setOnClickListener(v -> editField(f.id));
            hide.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle("Архивировать поле?")
                    .setMessage("Поле исчезнет из формы, но существующие значения сохранятся. Его можно вернуть из архива.")
                    .setPositiveButton("В архив", (d,w) -> { db.setFieldArchived(f.id, true); refresh(); })
                    .setNegativeButton("Отмена", null).show());
            list.addView(c);
        }
    }

    private void editField(long fieldId) {
        Intent i = new Intent(this, FieldEditActivity.class);
        i.putExtra("template_id", templateId);
        i.putExtra("field_id", fieldId);
        startActivity(i);
    }

    private void showArchive() {
        List<FieldDef> archived = new ArrayList<>();
        for (FieldDef f : db.getFields(templateId, true)) if (f.archived) archived.add(f);
        if (archived.isEmpty()) { Toast.makeText(this, "Архив пуст", Toast.LENGTH_SHORT).show(); return; }
        String[] names = new String[archived.size()];
        for (int i=0;i<archived.size();i++) names[i] = archived.get(i).name + " · " + FieldDef.humanType(archived.get(i).type);
        new AlertDialog.Builder(this).setTitle("Архив полей")
                .setItems(names, (d,which) -> {
                    FieldDef f = archived.get(which);
                    new AlertDialog.Builder(this).setTitle(f.name)
                            .setMessage("Вернуть поле в товар?")
                            .setPositiveButton("Вернуть", (d2,w) -> {
                                db.setFieldArchived(f.id, false);
                                if (FieldDef.AUTO_COUNTER.equals(f.type)) db.ensureAutoCounterValues(f.id);
                                refresh();
                            })
                            .setNegativeButton("Отмена", null).show();
                }).setNegativeButton("Закрыть", null).show();
    }
}
