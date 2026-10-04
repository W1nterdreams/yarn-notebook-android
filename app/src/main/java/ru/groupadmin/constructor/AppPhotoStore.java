package ru.groupadmin.constructor;

import android.content.Context;
import android.net.Uri;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

final class AppPhotoStore {
    private static final String ROOT = "product_photos";

    static String importPhoto(Context context, Uri source, long recordId, long fieldId, String previous) throws Exception {
        File dir = recordDir(context, recordId);
        if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Не удалось создать папку фото");

        String ext = extensionFor(context, source);
        File target = new File(dir, "field_" + fieldId + "_" + System.currentTimeMillis() + ext);

        try (InputStream in = context.getContentResolver().openInputStream(source);
             OutputStream out = new FileOutputStream(target)) {
            if (in == null) throw new IllegalStateException("Не удалось открыть выбранное фото");
            copy(in, out);
        } catch (Exception e) {
            //noinspection ResultOfMethodCallIgnored
            target.delete();
            throw e;
        }

        deleteOwnedPhoto(context, previous);
        return target.getAbsolutePath();
    }

    static String copyPhoto(Context context, String sourceStored, long recordId, long fieldId) {
        if (sourceStored == null || sourceStored.trim().isEmpty()) return "";
        File dir = recordDir(context, recordId);
        if (!dir.exists() && !dir.mkdirs()) return "";

        String ext = extensionFromStored(sourceStored);
        File target = new File(dir, "field_" + fieldId + "_" + System.currentTimeMillis() + ext);

        try (InputStream in = openStored(context, sourceStored);
             OutputStream out = new FileOutputStream(target)) {
            if (in == null) return "";
            copy(in, out);
            return target.getAbsolutePath();
        } catch (Exception e) {
            //noinspection ResultOfMethodCallIgnored
            target.delete();
            return "";
        }
    }

    static Uri displayUri(String stored) {
        if (stored == null || stored.trim().isEmpty()) return null;
        String s = stored.trim();
        if (s.startsWith("content://") || s.startsWith("file://")) return Uri.parse(s);
        return Uri.fromFile(new File(s));
    }

    static void deleteRecordPhotos(Context context, long recordId) {
        deleteRecursive(recordDir(context, recordId));
    }

    static void deleteOwnedPhoto(Context context, String stored) {
        if (stored == null || stored.trim().isEmpty()) return;
        String s = stored.trim();
        if (s.startsWith("content://")) return;

        File file;
        if (s.startsWith("file://")) {
            Uri uri = Uri.parse(s);
            String path = uri.getPath();
            if (path == null) return;
            file = new File(path);
        } else {
            file = new File(s);
        }

        try {
            File root = root(context).getCanonicalFile();
            File candidate = file.getCanonicalFile();
            String rootPath = root.getPath() + File.separator;
            if (candidate.getPath().startsWith(rootPath)) {
                //noinspection ResultOfMethodCallIgnored
                candidate.delete();
            }
        } catch (Exception ignored) {}
    }

    private static InputStream openStored(Context context, String stored) throws Exception {
        String s = stored.trim();
        if (s.startsWith("content://")) return context.getContentResolver().openInputStream(Uri.parse(s));
        if (s.startsWith("file://")) {
            String path = Uri.parse(s).getPath();
            return path == null ? null : new FileInputStream(new File(path));
        }
        return new FileInputStream(new File(s));
    }

    private static File root(Context context) {
        return new File(context.getFilesDir(), ROOT);
    }

    private static File recordDir(Context context, long recordId) {
        return new File(root(context), String.valueOf(recordId));
    }

    private static String extensionFor(Context context, Uri uri) {
        String mime = null;
        try { mime = context.getContentResolver().getType(uri); } catch (Exception ignored) {}
        if ("image/png".equalsIgnoreCase(mime)) return ".png";
        if ("image/webp".equalsIgnoreCase(mime)) return ".webp";
        if ("image/gif".equalsIgnoreCase(mime)) return ".gif";
        if ("image/heic".equalsIgnoreCase(mime) || "image/heif".equalsIgnoreCase(mime)) return ".heic";
        return ".jpg";
    }

    private static String extensionFromStored(String stored) {
        String s = stored.toLowerCase();
        if (s.endsWith(".png")) return ".png";
        if (s.endsWith(".webp")) return ".webp";
        if (s.endsWith(".gif")) return ".gif";
        if (s.endsWith(".heic") || s.endsWith(".heif")) return ".heic";
        return ".jpg";
    }

    private static void copy(InputStream in, OutputStream out) throws Exception {
        byte[] buffer = new byte[64 * 1024];
        int n;
        while ((n = in.read(buffer)) >= 0) out.write(buffer, 0, n);
        out.flush();
    }

    private static void deleteRecursive(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteRecursive(child);
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}
