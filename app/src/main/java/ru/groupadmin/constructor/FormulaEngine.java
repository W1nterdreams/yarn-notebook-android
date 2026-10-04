package ru.groupadmin.constructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class FormulaEngine {
    static final class Result {
        final boolean ok;
        final double value;
        final String text;
        Result(boolean ok, double value, String text) { this.ok=ok; this.value=value; this.text=text; }
    }

    static Result evaluate(String formula, Map<String,String> values) {
        if (formula == null || formula.trim().isEmpty()) return new Result(true, 0, "0");
        try {
            Parser p = new Parser(formula.trim(), values);
            double v = p.parse();
            if (Double.isNaN(v) || Double.isInfinite(v)) throw new IllegalArgumentException("Некорректный результат");
            return new Result(true, v, format(v));
        } catch (Exception e) {
            return new Result(false, 0, "#ОШИБКА");
        }
    }

    static String format(double v) {
        if (Math.abs(v) < 0.0000000001) v = 0;
        DecimalFormat df = new DecimalFormat("0.##########");
        return df.format(v).replace(',', '.');
    }

    private static final class Parser {
        private final String s;
        private final Map<String,String> values;
        private int pos;

        Parser(String s, Map<String,String> values) {
            this.s = s.startsWith("=") ? s.substring(1) : s;
            this.values = values;
        }

        double parse() {
            double v = comparison();
            ws();
            if (pos != s.length()) throw new IllegalArgumentException("Лишний текст");
            return v;
        }

        private double comparison() {
            double a = expr();
            ws();
            if (match(">=")) return a >= expr() ? 1 : 0;
            if (match("<=")) return a <= expr() ? 1 : 0;
            if (match("==")) return Math.abs(a-expr()) < 1e-12 ? 1 : 0;
            if (match("!=")) return Math.abs(a-expr()) >= 1e-12 ? 1 : 0;
            if (match(">")) return a > expr() ? 1 : 0;
            if (match("<")) return a < expr() ? 1 : 0;
            return a;
        }

        private double expr() {
            double v = term();
            while (true) {
                ws();
                if (match("+")) v += term();
                else if (match("-")) v -= term();
                else return v;
            }
        }

        private double term() {
            double v = power();
            while (true) {
                ws();
                if (match("*")) v *= power();
                else if (match("/")) v /= power();
                else if (match("%")) v %= power();
                else return v;
            }
        }

        private double power() {
            double v = unary();
            ws();
            if (match("^")) v = Math.pow(v, power());
            return v;
        }

        private double unary() {
            ws();
            if (match("+")) return unary();
            if (match("-")) return -unary();
            return atom();
        }

        private double atom() {
            ws();
            if (match("(")) {
                double v = comparison();
                expect(")");
                return v;
            }
            if (peek() == '{') return variable();
            if (Character.isLetter(peek())) return function();
            return number();
        }

        private double variable() {
            expect("{");
            int start = pos;
            while (pos < s.length() && s.charAt(pos) != '}') pos++;
            if (pos >= s.length()) throw new IllegalArgumentException("Нет }");
            String name = s.substring(start, pos).trim();
            pos++;
            return toNumber(values.get(name));
        }

        private double function() {
            int start = pos;
            while (pos < s.length() && (Character.isLetterOrDigit(s.charAt(pos)) || s.charAt(pos)=='_')) pos++;
            String name = s.substring(start,pos).toUpperCase();
            expect("(");
            List<Double> args = new ArrayList<>();
            ws();
            if (!match(")")) {
                do { args.add(comparison()); ws(); } while (match(";"));
                expect(")");
            }
            switch (name) {
                case "ABS": return req(args,1,Math.abs(args.get(0)));
                case "CEIL": return req(args,1,Math.ceil(args.get(0)));
                case "FLOOR": return req(args,1,Math.floor(args.get(0)));
                case "ROUND": {
                    if (args.size()<1 || args.size()>2) throw new IllegalArgumentException();
                    int scale = args.size()==2 ? (int)Math.round(args.get(1)) : 0;
                    return BigDecimal.valueOf(args.get(0)).setScale(scale, RoundingMode.HALF_UP).doubleValue();
                }
                case "MIN": {
                    if (args.isEmpty()) throw new IllegalArgumentException();
                    double v=args.get(0); for(double x:args) v=Math.min(v,x); return v;
                }
                case "MAX": {
                    if (args.isEmpty()) throw new IllegalArgumentException();
                    double v=args.get(0); for(double x:args) v=Math.max(v,x); return v;
                }
                case "IF": {
                    if (args.size()!=3) throw new IllegalArgumentException();
                    return args.get(0)!=0 ? args.get(1) : args.get(2);
                }
                default: throw new IllegalArgumentException("Функция");
            }
        }

        private double req(List<Double> args, int n, double value) {
            if (args.size()!=n) throw new IllegalArgumentException();
            return value;
        }

        private double number() {
            ws();
            int start=pos;
            boolean sep=false;
            while (pos<s.length()) {
                char c=s.charAt(pos);
                if (Character.isDigit(c)) pos++;
                else if ((c=='.' || c==',') && !sep) { sep=true; pos++; }
                else break;
            }
            if (start==pos) throw new IllegalArgumentException("Число");
            return Double.parseDouble(s.substring(start,pos).replace(',','.'));
        }

        private double toNumber(String raw) {
            if (raw==null || raw.trim().isEmpty()) return 0;
            String x=raw.trim().replace(" ","").replace(',','.');
            if ("true".equalsIgnoreCase(x) || "да".equalsIgnoreCase(x)) return 1;
            if ("false".equalsIgnoreCase(x) || "нет".equalsIgnoreCase(x)) return 0;
            try { return Double.parseDouble(x); } catch (Exception e) { return 0; }
        }

        private char peek() { return pos<s.length()?s.charAt(pos):'\0'; }
        private void ws() { while(pos<s.length() && Character.isWhitespace(s.charAt(pos))) pos++; }
        private boolean match(String x) { ws(); if(s.startsWith(x,pos)){pos+=x.length();return true;} return false; }
        private void expect(String x) { if(!match(x)) throw new IllegalArgumentException("Ожидалось "+x); }
    }
}
