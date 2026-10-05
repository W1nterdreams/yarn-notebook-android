package ru.yarnnotebook.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Build;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public final class PhotoStore {
    private static final String LAYOUTS_ROOT = "layouts";
    private static final String PHOTOS = "photos";
    private static final String ACTIVE = "active";
    private static final String ARCHIVE = "archive";

    // Папки версии 0.2.22. Нужны только для мягкой миграции старых фото.
    private static final String LEGACY_ROOT = "product_photos";

    private static final int MAX_SIDE = 1200;
    private static final int JPEG_QUALITY = 78;

    private PhotoStore() { }

    private static File layoutDir(Context context, long layoutId) {
        return ensure(new File(context.getFilesDir(),
                LAYOUTS_ROOT + File.separator + "layout_" + layoutId));
    }

    private static File photosDir(Context context, long layoutId) {
        return ensure(new File(layoutDir(context, layoutId), PHOTOS));
    }

    private static File activeDir(Context context, long layoutId) {
        return ensure(new File(photosDir(context, layoutId), ACTIVE));
    }

    private static File archiveDir(Context context, long layoutId) {
        return ensure(new File(photosDir(context, layoutId), ARCHIVE));
    }

    private static File legacyActiveDir(Context context) {
        return ensure(new File(context.getFilesDir(), LEGACY_ROOT + File.separator + ACTIVE));
    }

    private static File legacyArchiveDir(Context context) {
        return ensure(new File(context.getFilesDir(), LEGACY_ROOT + File.separator + ARCHIVE));
    }

    private static File galleryTempDir(Context context) {
        return ensure(new File(context.getCacheDir(), "gallery"));
    }

    public static String fileName(long internalNumber) {
        return "yarn_" + internalNumber + ".jpg";
    }

    public static File photoFile(Context context, YarnRecord record) {
        if (record == null || record.photoFile == null || record.photoFile.trim().isEmpty()) return null;

        File target = new File(
                record.archived ? archiveDir(context, record.layoutId) : activeDir(context, record.layoutId),
                record.photoFile.trim());
        if (target.isFile()) return target;

        File legacy = new File(
                record.archived ? legacyArchiveDir(context) : legacyActiveDir(context),
                record.photoFile.trim());
        if (!legacy.isFile()) return target;

        // При первом обращении переносим фото 0.2.22 в папку соответствующей выкладки.
        if (moveFile(legacy, target)) return target;
        return legacy;
    }

    public static boolean exists(Context context, YarnRecord record) {
        File file = photoFile(context, record);
        return file != null && file.isFile();
    }

    public static String saveCompressed(Context context, Uri sourceUri, YarnRecord record) throws IOException {
        if (sourceUri == null) throw new IOException("Фотография не выбрана");
        if (record == null || record.internalNumber <= 0 || record.layoutId <= 0) {
            throw new IOException("Карточка товара ещё не сохранена");
        }

        File tempSource = File.createTempFile("yarn_gallery_", ".img", galleryTempDir(context));
        try {
            try (InputStream in = context.getContentResolver().openInputStream(sourceUri);
                 FileOutputStream out = new FileOutputStream(tempSource)) {
                if (in == null) throw new IOException("Не удалось открыть выбранную фотографию");
                byte[] buffer = new byte[32 * 1024];
                int read;
                while ((read = in.read(buffer)) >= 0) out.write(buffer, 0, read);
                out.flush();
            }
            return saveCompressedFile(context, tempSource, record);
        } finally {
            if (tempSource.exists()) tempSource.delete();
        }
    }

    private static String saveCompressedFile(Context context, File source, YarnRecord record) throws IOException {
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

        String name = fileName(record.internalNumber);
        File dir = record.archived
                ? archiveDir(context, record.layoutId)
                : activeDir(context, record.layoutId);
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

        // Если карточка пришла из 0.2.22 и старое фото ещё осталось в общей папке — убираем его.
        File legacy = new File(
                record.archived ? legacyArchiveDir(context) : legacyActiveDir(context),
                name);
        if (legacy.exists()) legacy.delete();

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
        if (record == null || record.photoFile == null || record.photoFile.trim().isEmpty()) return;
        String name = record.photoFile.trim();

        File current = new File(
                record.archived ? archiveDir(context, record.layoutId) : activeDir(context, record.layoutId),
                name);
        if (current.exists()) current.delete();

        // На случай карточки, фото которой ещё не успело мигрировать из 0.2.22.
        File legacy = new File(
                record.archived ? legacyArchiveDir(context) : legacyActiveDir(context),
                name);
        if (legacy.exists()) legacy.delete();

        cleanupEmptyLayoutFolders(context, record.layoutId);
    }

    public static boolean moveToArchive(Context context, YarnRecord record) {
        return move(context, record, true);
    }

    public static boolean moveToActive(Context context, YarnRecord record) {
        return move(context, record, false);
    }

    private static boolean move(Context context, YarnRecord record, boolean toArchive) {
        if (record == null || record.photoFile == null || record.photoFile.trim().isEmpty()) return true;

        File from = photoFile(context, record);
        if (from == null || !from.isFile()) return true;

        File to = new File(
                toArchive ? archiveDir(context, record.layoutId) : activeDir(context, record.layoutId),
                record.photoFile.trim());
        if (from.equals(to)) return true;

        boolean ok = moveFile(from, to);
        if (ok) cleanupEmptyLayoutFolders(context, record.layoutId);
        return ok;
    }

    private static boolean moveFile(File from, File to) {
        if (from == null || !from.isFile()) return false;
        File parent = to.getParentFile();
        if (parent != null) ensure(parent);

        if (to.exists() && !to.delete()) return false;
        if (from.renameTo(to)) return true;

        try (FileInputStream in = new FileInputStream(from);
             FileOutputStream out = new FileOutputStream(to)) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = in.read(buffer)) >= 0) out.write(buffer, 0, read);
            out.flush();
            if (!from.delete()) {
                to.delete();
                return false;
            }
            return true;
        } catch (Exception ignored) {
            if (to.exists()) to.delete();
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

    private static void cleanupEmptyLayoutFolders(Context context, long layoutId) {
        File layout = new File(new File(context.getFilesDir(), LAYOUTS_ROOT), "layout_" + layoutId);
        File photos = new File(layout, PHOTOS);
        File active = new File(photos, ACTIVE);
        File archive = new File(photos, ARCHIVE);

        deleteIfEmpty(active);
        deleteIfEmpty(archive);
        deleteIfEmpty(photos);
        deleteIfEmpty(layout);
    }

    private static void deleteIfEmpty(File dir) {
        if (dir == null || !dir.isDirectory()) return;
        File[] children = dir.listFiles();
        if (children != null && children.length == 0) dir.delete();
    }

    private static File ensure(File dir) {
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }
}
