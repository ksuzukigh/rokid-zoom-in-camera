package io.github.ksuzukigh.rokidzoomincamera;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import java.io.InputStream;
import java.io.OutputStream;

final class LegacyVideoMigrator {
    private static final String TAG = "RokidZoomInCamera";

    private LegacyVideoMigrator() {}

    static void copyForHiRokid(Context context) {
        ContentResolver resolver = context.getContentResolver();
        Uri videos = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        String[] projection = {
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.RELATIVE_PATH,
                MediaStore.Video.Media.DATE_TAKEN
        };
        String selection = MediaStore.Video.Media.RELATIVE_PATH + "=? AND "
                + MediaStore.Video.Media.DISPLAY_NAME + " LIKE ? AND "
                + MediaStore.Video.Media.IS_PENDING + "=0";
        String[] arguments = {
                MediaPaths.LEGACY_VIDEO_PATH,
                MediaPaths.VIDEO_FILE_PREFIX + "%.mp4"
        };

        try (Cursor cursor = resolver.query(videos, projection, selection, arguments, null)) {
            if (cursor == null) return;
            int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID);
            int nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME);
            int pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.RELATIVE_PATH);
            int dateTakenColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_TAKEN);
            while (cursor.moveToNext()) {
                String name = cursor.getString(nameColumn);
                String path = cursor.getString(pathColumn);
                if (!MediaPaths.isLegacyVideo(name, path) || destinationExists(resolver, videos, name)) {
                    continue;
                }
                Uri source = ContentUris.withAppendedId(videos, cursor.getLong(idColumn));
                long dateTaken = cursor.isNull(dateTakenColumn) ? 0L : cursor.getLong(dateTakenColumn);
                copyOne(resolver, videos, source, name, dateTaken);
            }
        } catch (Exception error) {
            Log.w(TAG, "Could not scan older videos for Hi Rokid", error);
        }
    }

    private static boolean destinationExists(ContentResolver resolver, Uri videos, String name) {
        String[] projection = {MediaStore.Video.Media._ID};
        String selection = MediaStore.Video.Media.RELATIVE_PATH + "=? AND "
                + MediaStore.Video.Media.DISPLAY_NAME + "=? AND "
                + MediaStore.Video.Media.IS_PENDING + "=0";
        String[] arguments = {MediaPaths.VIDEO_PATH + "/", name};
        try (Cursor cursor = resolver.query(videos, projection, selection, arguments, null)) {
            return cursor != null && cursor.moveToFirst();
        }
    }

    private static void copyOne(ContentResolver resolver, Uri videos, Uri source,
                                String name, long dateTaken) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Video.Media.DISPLAY_NAME, name);
        values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
        values.put(MediaStore.Video.Media.RELATIVE_PATH, MediaPaths.VIDEO_PATH);
        values.put(MediaStore.Video.Media.IS_PENDING, 1);
        if (dateTaken > 0L) values.put(MediaStore.Video.Media.DATE_TAKEN, dateTaken);

        Uri destination = null;
        try {
            destination = resolver.insert(videos, values);
            if (destination == null) throw new IllegalStateException("MediaStore insert failed");
            try (InputStream input = resolver.openInputStream(source);
                 OutputStream output = resolver.openOutputStream(destination, "w")) {
                if (input == null || output == null) {
                    throw new IllegalStateException("MediaStore stream failed");
                }
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            }

            ContentValues ready = new ContentValues();
            ready.put(MediaStore.Video.Media.IS_PENDING, 0);
            if (resolver.update(destination, ready, null, null) != 1) {
                throw new IllegalStateException("Could not publish copied video");
            }
            Log.i(TAG, "Copied older video for Hi Rokid: " + name);
        } catch (Exception error) {
            if (destination != null) {
                try {
                    resolver.delete(destination, null, null);
                } catch (Exception cleanupError) {
                    Log.w(TAG, "Could not remove incomplete Hi Rokid copy: " + name, cleanupError);
                }
            }
            Log.w(TAG, "Could not copy older video for Hi Rokid: " + name, error);
        }
    }
}
