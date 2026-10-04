package ru.groupadmin.constructor;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.math.BigDecimal;

final class Stock {
    interface OnChanged { void run(); }

    static String format(double value) {
        if (Math.abs(value) < 0.0000000001) value = 0;
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    static String display(double value, String unit) {
        String u = unit == null ? "" : unit.trim();
        return format(value) + (u.isEmpty() ? "" : " " + u);
    }

    static void showAdjustDialog(Activity activity, DbHelper db, long recordId,
                                 CardTemplate template, boolean add, OnChanged callback) {
        RecordItem current = db.getRecord(recordId);
        if (current == null) return;

        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(activity, 18);
        box.setPadding(p, Ui.dp(activity, 8), p, 0);

        box.addView(Ui.text(activity,
                "Сейчас: " + display(current.quantity, template.quantityUnit),
                14, Ui.MUTED, false));

        EditText amount = new EditText(activity);
        amount.setSingleLine(true);
        amount.setHint(add ? "Сколько добавить" : "Сколько списать");
        amount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        amount.setText("1");
        amount.setSelectAllOnFocus(true);
        amount.setBackground(Ui.inputBackground(activity));
        amount.setPadding(Ui.dp(activity,12),Ui.dp(activity,10),Ui.dp(activity,12),Ui.dp(activity,10));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(activity, 52));
        lp.setMargins(0, Ui.dp(activity, 10), 0, 0);
        box.addView(amount, lp);

        String unit = template.quantityUnit == null || template.quantityUnit.trim().isEmpty()
                ? "ед." : template.quantityUnit.trim();

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(add ? "Добавить к остатку" : "Списать из остатка")
                .setMessage(add
                        ? "Введите количество в " + unit
                        : "Введите проданное или списанное количество в " + unit)
                .setView(box)
                .setPositiveButton(add ? "Добавить" : "Списать", null)
                .setNegativeButton("Отмена", null)
                .create();

        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String raw = amount.getText().toString().trim().replace(',', '.');
            double delta;
            try { delta = Double.parseDouble(raw); }
            catch (Exception e) {
                Toast.makeText(activity, "Введите число", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!(delta > 0)) {
                Toast.makeText(activity, "Количество должно быть больше нуля", Toast.LENGTH_SHORT).show();
                return;
            }

            RecordItem latest = db.getRecord(recordId);
            if (latest == null) { dialog.dismiss(); return; }
            if (!add && delta > latest.quantity + 0.000000001) {
                Toast.makeText(activity,
                        "Нельзя списать " + format(delta) + ": в наличии " +
                                display(latest.quantity, template.quantityUnit),
                        Toast.LENGTH_LONG).show();
                return;
            }

            db.adjustRecordQuantity(recordId, add ? delta : -delta);
            dialog.dismiss();
            if (callback != null) callback.run();
        }));
        dialog.show();
        amount.requestFocus();
    }
}
