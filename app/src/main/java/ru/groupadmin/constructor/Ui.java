package ru.groupadmin.constructor;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

final class Ui {
    static final int BG = Color.rgb(245,246,248);
    static final int CARD = Color.WHITE;
    static final int TEXT = Color.rgb(22,22,22);
    static final int MUTED = Color.rgb(100,104,112);
    static final int PRIMARY = Color.rgb(91,75,196);
    static final int DANGER = Color.rgb(176,45,45);
    static final int DRAFT = Color.rgb(167,112,0);

    static int dp(Context c, int v) { return Math.round(v*c.getResources().getDisplayMetrics().density); }

    static LinearLayout page(Activity a) {
        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(a,12),dp(a,8),dp(a,12),dp(a,12));
        return root;
    }

    static TextView title(Context c, String text) {
        TextView v=text(c,text,24,TEXT,true); v.setPadding(dp(c,4),dp(c,8),dp(c,4),dp(c,6)); return v;
    }
    static TextView subtitle(Context c, String text) {
        TextView v=text(c,text,14,MUTED,false); v.setPadding(dp(c,4),0,dp(c,4),dp(c,10)); return v;
    }
    static TextView text(Context c, String s, int sp, int color, boolean bold) {
        TextView v=new TextView(c); v.setText(s); v.setTextSize(sp); v.setTextColor(color); if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD); return v;
    }

    static Button button(Context c, String text) {
        Button b=new Button(c); b.setText(text); b.setAllCaps(false); b.setTextColor(TEXT); b.setTextSize(14); return b;
    }

    static Button primaryButton(Context c, String text) {
        Button b=button(c,text); b.setTextColor(Color.WHITE); b.setBackground(round(PRIMARY, 12, c)); return b;
    }

    static LinearLayout card(Context c) {
        LinearLayout v=new LinearLayout(c); v.setOrientation(LinearLayout.VERTICAL); v.setPadding(dp(c,14),dp(c,12),dp(c,14),dp(c,12)); v.setBackground(round(CARD,14,c));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT); lp.setMargins(0,dp(c,6),0,dp(c,6)); v.setLayoutParams(lp); return v;
    }

    static GradientDrawable round(int color, int radiusDp, Context c) {
        GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(c,radiusDp)); return d;
    }

    static LinearLayout row(Context c) { LinearLayout r=new LinearLayout(c); r.setOrientation(LinearLayout.HORIZONTAL); r.setGravity(Gravity.CENTER_VERTICAL); return r; }

    static View spacer(Context c, int h) { View v=new View(c); v.setLayoutParams(new LinearLayout.LayoutParams(1,dp(c,h))); return v; }

    static ScrollView scroll(Context c, View child) { ScrollView s=new ScrollView(c); s.addView(child); return s; }
}
