package ru.yarnnotebook.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Build;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public final class PhotoStore {
    private static final String ROOT = "product_photos";
    private static final String ACTIVE = "active";
    private static final String ARCHIVE = "archive";
    private static final int MAX_SIDE = 1200;
    private static final int JPEG_QUALITY = 78;

    private PhotoStore() { }

    public static File activeDir(Context context) {
        return ensure(new File(context.getFilesDir(), ROOT + File.separator + ACTIVE));
    }

    public static File archiveDir(Context context) {
        return ensure(new File(context.getFilesDir(), ROOT + File.separator + ARCHIVE));
    }

    public static File cameraTempDir(Context context) {
        return ensure(new File(context.getCacheDir(), "camera"));
    }

    public static File newCameraTempFile(Context context) throws IOException {
        return File.createTempFile("yarn_camera_", ".jpg", cameraTempDir(context));
    }

    public static String fileName(long internalNumber) {
        return "yarn_" + internalNumber + ".jpg";
    }

    public static File photoFile(Context context, YarnRecord record) {
        if (record == null || record.photoFile == null || record.photoFile.trim().isEmpty()) return null;
        File dir = record.archived ? archiveDir(context) : activeDir(context);
        return new File(dir, record.photoFile.trim());
    }

    public static boolean exists(Context context, YarnRecord record) {
        File file = photoFile(context, record);
        return file != null && file.isFile();
    }

    public static String saveCompressed(Context context, File source, long internalNumber, boolean archived) throws IOException {
        if (source == null || !source.isFile()) throw new IOException("Фотография не найдена");

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(source.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IOException("Не удалось прочитать фотографию");

        int sample = 1;
        int largest = Math.max(bounds.outWidth, bounds.outHeight);
        while (largest / sample > MAX_SIDE * 2) sample *= 2;

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = Math.max(1, sample);
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap = BitmapFactory.decodeFile(source.getAbsolutePath(), options);
        if (bitmap == null) throw new IOException("Не удалось декодировать фотографию");

        bitmap = applyOrientation(source, bitmap);

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int max = Math.max(width, height);
        if (max > MAX_SIDE) {
            float scale = MAX_SIDE / (float) max;
            Bitmap scaled = Bitmap.createScaledBitmap(
                    bitmap,
                    Math.max(1, Math.round(width * scale)),
                    Math.max(1, Math.round(height * scale)),
                    true);
            if (scaled != bitmap) bitmap.recycle();
            bitmap = scaled;
        }

        String name = fileName(internalNumber);
        File dir = archived ? archiveDir(context) : activeDir(context);
        File target = new File(dir, name);
        File temp = new File(dir, name + ".tmp");

        try (FileOutputStream out = new FileOutputStream(temp)) {
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) {
                throw new IOException("Не удалось сжать фотографию");
            }
            out.flush();
        } finally {
            bitmap.recycle();
        }

        if (target.exists() && !target.delete()) {
            temp.delete();
            throw new IOException("Не удалось заменить старую фотографию");
        }
        if (!temp.renameTo(target)) {
            temp.delete();
            throw new IOException("Не удалось сохранить фотографию");
        }
        return name;
    }

    public static Bitmap loadThumbnail(Context context, YarnRecord record, int targetPx) {
        File file = photoFile(context, record);
        if (file == null || !file.isFile()) return null;

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

        int sample = 1;
        int largest = Math.max(bounds.outWidth, bounds.outHeight);
        while (largest / sample > targetPx * 2) sample *= 2;

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = Math.max(1, sample);
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
    }

    public static void deletePhoto(Context context, YarnRecord record) {
        File file = photoFile(context, record);
        if (file != null && file.exists()) file.delete();
    }

    public static boolean moveToArchive(Context context, YarnRecord record) {
        return move(context, record, true);
    }

    public static boolean moveToActive(Context context, YarnRecord record) {
        return move(context, record, false);
    }

    private static boolean move(Context context, YarnRecord record, boolean toArchive) {
        if (record == null || record.photoFile == null || record.photoFile.trim().isEmpty()) return true;
        File from = new File(record.archived ? archiveDir(context) : activeDir(context), record.photoFile.trim());
        if (!from.exists()) return true;

        File to = new File(toArchive ? archiveDir(context) : activeDir(context), record.photoFile.trim());
        if (to.exists() && !to.delete()) return false;
        if (from.renameTo(to)) return true;

        try {
            java.nio.file.Files.copy(from.toPath(), to.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return from.delete();
        } catch (Exception ignored) {
            return false;
        }
    }

    private static Bitmap applyOrientation(File source, Bitmap bitmap) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return bitmap;
        try {
            android.media.ExifInterface exif = new android.media.ExifInterface(source.getAbsolutePath());
            int orientation = exif.getAttributeInt(
                    android.media.ExifInterface.TAG_ORIENTATION,
                    android.media.ExifInterface.ORIENTATION_NORMAL);
            float degrees = 0f;
            if (orientation == android.media.ExifInterface.ORIENTATION_ROTATE_90) degrees = 90f;
            else if (orientation == android.media.ExifInterface.ORIENTATION_ROTATE_180) degrees = 180f;
            else if (orientation == android.media.ExifInterface.ORIENTATION_ROTATE_270) degrees = 270f;
            if (degrees == 0f) return bitmap;

            Matrix matrix = new Matrix();
            matrix.postRotate(degrees);
            Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            if (rotated != bitmap) bitmap.recycle();
            return rotated;
        } catch (Exception ignored) {
            return bitmap;
        }
    }

    private static File ensure(File dir) {
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }
}
