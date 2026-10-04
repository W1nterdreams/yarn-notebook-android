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
        android.widget.Button back = Ui.button(this, "← " + template.name);
        back.setOnClickListener(v -> finish());
        root.addView(back, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 44)));
        root.addView(Ui.title(this, "Поля товара"));
        root.addView(Ui.subtitle(this, "Порядок полей здесь станет порядком ввода в товаре. Удаление безопасное: поле сначала архивируется, данные не стираются."));

        LinearLayout actions = Ui.row(this);
        android.widget.Button add = Ui.primaryButton(this, "＋ Добавить поле");
        android.widget.Button archive = Ui.button(this, "Архив");
        actions.addView(add, new LinearLayout.LayoutParams(0, Ui.dp(this, 50), 1));
        actions.addView(archive, new LinearLayout.LayoutParams(0, Ui.dp(this, 50), 1));
        root.addView(actions);

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
            LinearLayout c = Ui.card(this);
            c.addView(Ui.text(this, "Пока нет полей", 18, Ui.TEXT, true));
            c.addView(Ui.text(this, "Добавьте любые характеристики товара: текст, числа, цену, список, дату, фото или вычисляемую формулу.", 14, Ui.MUTED, false));
            list.addView(c);
            return;
        }
        for (int i = 0; i < fields.size(); i++) {
            FieldDef f = fields.get(i);
            LinearLayout c = Ui.card(this);
            c.addView(Ui.text(this, (i + 1) + ". " + f.name, 18, Ui.TEXT, true));
            StringBuilder meta = new StringBuilder(FieldDef.humanType(f.type));
            if (f.required) meta.append(" · обязательное");
            if (f.showInList) meta.append(" · в списке");
            if (f.searchable) meta.append(" · поиск");
            if (f.unit != null && !f.unit.isEmpty()) meta.append(" · ").append(f.unit);
            c.addView(Ui.text(this, meta.toString(), 13, Ui.MUTED, false));
            if (FieldDef.FORMULA.equals(f.type)) c.addView(Ui.text(this, "Формула: " + f.formula, 13, Ui.PRIMARY, false));

            LinearLayout r = Ui.row(this);
            android.widget.Button up = Ui.button(this, "↑");
            android.widget.Button down = Ui.button(this, "↓");
            android.widget.Button edit = Ui.primaryButton(this, "Изменить");
            android.widget.Button hide = Ui.button(this, "В архив");
            r.addView(up, new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 0.6f));
            r.addView(down, new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 0.6f));
            r.addView(edit, new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1.4f));
            r.addView(hide, new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1.2f));
            c.addView(r);
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
                            .setPositiveButton("Вернуть", (d2,w) -> { db.setFieldArchived(f.id, false); refresh(); })
                            .setNegativeButton("Отмена", null).show();
                }).setNegativeButton("Закрыть", null).show();
    }
}
