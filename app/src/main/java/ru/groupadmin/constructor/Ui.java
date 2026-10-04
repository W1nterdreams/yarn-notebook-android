package ru.groupadmin.constructor;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

final class Ui {
    static final int BG = Color.rgb(247, 246, 250);
    static final int CARD = Color.WHITE;
    static final int TEXT = Color.rgb(32, 27, 37);
    static final int MUTED = Color.rgb(108, 101, 115);

    static final int PRIMARY = Color.rgb(102, 80, 164);
    static final int PRIMARY_DARK = Color.rgb(79, 55, 139);
    static final int PRIMARY_SOFT = Color.rgb(238, 233, 250);

    static final int BORDER = Color.rgb(229, 224, 234);
    static final int FIELD_BG = Color.rgb(250, 249, 252);

    static final int DANGER = Color.rgb(186, 26, 26);
    static final int DANGER_BG = Color.rgb(255, 218, 214);
    static final int DRAFT = Color.rgb(152, 86, 0);
    static final int DRAFT_BG = Color.rgb(255, 237, 204);
    static final int SUCCESS = Color.rgb(46, 110, 54);
    static final int SUCCESS_BG = Color.rgb(220, 244, 222);

    static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static LinearLayout page(Activity a) {
        Window window = a.getWindow();
        window.setStatusBarColor(BG);
        window.setNavigationBarColor(BG);
        int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        window.getDecorView().setSystemUiVisibility(flags);

        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        final int left = dp(a, 14);
        final int top = dp(a, 8);
        final int right = dp(a, 14);
        final int bottom = dp(a, 12);
        root.setPadding(left, top, right, bottom);

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(left + bars.left, top + bars.top, right + bars.right, bottom + bars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
        return root;
    }

    static TextView title(Context c, String text) {
        TextView v = text(c, text, 25, TEXT, true);
        v.setPadding(dp(c, 4), dp(c, 10), dp(c, 4), dp(c, 5));
        v.setLetterSpacing(-0.01f);
        return v;
    }

    static TextView subtitle(Context c, String text) {
        TextView v = text(c, text, 14, MUTED, false);
        v.setLineSpacing(0, 1.12f);
        v.setPadding(dp(c, 4), 0, dp(c, 4), dp(c, 12));
        return v;
    }

    static TextView sectionTitle(Context c, String text) {
        TextView v = text(c, text, 13, MUTED, true);
        v.setAllCaps(true);
        v.setLetterSpacing(0.05f);
        v.setPadding(dp(c, 4), dp(c, 12), dp(c, 4), dp(c, 4));
        return v;
    }

    static TextView text(Context c, String s, int sp, int color, boolean bold) {
        TextView v = new TextView(c);
        v.setText(s);
        v.setTextSize(sp);
        v.setTextColor(color);
        v.setIncludeFontPadding(false);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }

    static Button button(Context c, String text) {
        Button b = new Button(c);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(PRIMARY_DARK);
        b.setTextSize(13);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setMinHeight(dp(c, 44));
        b.setMinWidth(0);
        b.setPadding(dp(c, 10), 0, dp(c, 10), 0);
        b.setBackground(ripple(PRIMARY_SOFT, Color.argb(28, 102, 80, 164), 13, c, 0, 0));
        return b;
    }

    static Button primaryButton(Context c, String text) {
        Button b = button(c, text);
        b.setTextColor(Color.WHITE);
        b.setBackground(ripple(PRIMARY, Color.argb(45, 255, 255, 255), 13, c, 0, 0));
        return b;
    }

    static Button outlineButton(Context c, String text) {
        Button b = button(c, text);
        b.setTextColor(PRIMARY_DARK);
        b.setBackground(ripple(CARD, Color.argb(22, 102, 80, 164), 13, c, BORDER, 1));
        return b;
    }

    static Button dangerButton(Context c, String text) {
        Button b = button(c, text);
        b.setTextColor(DANGER);
        b.setBackground(ripple(DANGER_BG, Color.argb(25, 186, 26, 26), 13, c, 0, 0));
        return b;
    }

    static TextView badge(Context c, String text, int textColor, int bgColor) {
        TextView v = Ui.text(c, text, 11, textColor, true);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(c, 9), dp(c, 5), dp(c, 9), dp(c, 5));
        v.setBackground(round(bgColor, 50, c));
        return v;
    }

    static LinearLayout card(Context c) {
        LinearLayout v = new LinearLayout(c);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setPadding(dp(c, 15), dp(c, 14), dp(c, 15), dp(c, 14));
        v.setBackground(roundStroke(CARD, BORDER, 1, 16, c));
        if (Build.VERSION.SDK_INT >= 21) v.setElevation(dp(c, 1));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.setMargins(0, dp(c, 6), 0, dp(c, 6));
        v.setLayoutParams(lp);
        return v;
    }

    static LinearLayout infoCard(Context c, int bg, int stroke) {
        LinearLayout v = new LinearLayout(c);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12));
        v.setBackground(roundStroke(bg, stroke, 1, 14, c));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.setMargins(0, dp(c, 5), 0, dp(c, 7));
        v.setLayoutParams(lp);
        return v;
    }

    static GradientDrawable round(int color, int radiusDp, Context c) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    static GradientDrawable roundStroke(int fill, int stroke, int strokeDp, int radiusDp, Context c) {
        GradientDrawable d = round(fill, radiusDp, c);
        d.setStroke(dp(c, strokeDp), stroke);
        return d;
    }

    static android.graphics.drawable.Drawable inputBackground(Context c) {
        return ripple(FIELD_BG, Color.argb(18, 102, 80, 164), 12, c, BORDER, 1);
    }

    private static android.graphics.drawable.Drawable ripple(
            int fill, int rippleColor, int radiusDp, Context c, int strokeColor, int strokeDp
    ) {
        GradientDrawable content = round(fill, radiusDp, c);
        if (strokeDp > 0) content.setStroke(dp(c, strokeDp), strokeColor);
        if (Build.VERSION.SDK_INT >= 21) {
            return new RippleDrawable(ColorStateList.valueOf(rippleColor), content, null);
        }
        return content;
    }

    static LinearLayout row(Context c) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        return r;
    }

    static View spacer(Context c, int h) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(c, h)));
        return v;
    }

    static ScrollView scroll(Context c, View child) {
        ScrollView s = new ScrollView(c);
        s.setFillViewport(true);
        s.setClipToPadding(false);
        s.setPadding(0, 0, 0, dp(c, 4));
        s.addView(child);
        return s;
    }
}
