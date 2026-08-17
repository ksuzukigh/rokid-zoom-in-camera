package io.github.ksuzukigh.rokidzoomincamera;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.GradientDrawable;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Size;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final String TAG = "RokidZoomInCamera";
    private static final int CAMERA_PERMISSION = 7;
    private static final int AUDIO_PERMISSION = 8;
    private static final float[] ZOOM_STEPS = {1f, 1.5f, 2f, 3f, 4f};
    private static final String BUTTON_DOWN = "com.android.action.ACTION_SPRITE_BUTTON_DOWN";
    private static final String BUTTON_UP = "com.android.action.ACTION_SPRITE_BUTTON_UP";
    private static final String BUTTON_LONG = "com.android.action.ACTION_SPRITE_BUTTON_LONG_PRESS";
    private static final String BUTTON_VERY_LONG = "com.android.action.ACTION_SPRITE_BUTTON_VERY_VERY_LONG_PRESS";

    private final Handler mainHandler = new Handler();
    private FrameLayout root;
    private TextureView preview;
    private TextView zoomLabel;
    private TextView statusLabel;
    private TextView recordingIndicator;
    private TextView batteryLabel;
    private HandlerThread cameraThread;
    private Handler cameraHandler;
    private CameraDevice camera;
    private CameraCaptureSession session;
    private CaptureRequest.Builder repeatingBuilder;
    private CameraCharacteristics characteristics;
    private ImageReader imageReader;
    private Size previewSize;
    private Size videoSize;
    private Rect sensorRect;
    private float maximumZoom = 1f;
    private int sensorOrientation;
    private int zoomIndex = 0;
    private boolean opening;
    private boolean resumed;
    private boolean takingPhoto;
    private boolean recording;
    private boolean recorderStarted;
    private boolean stopVideoRequested;
    private boolean stopVideoScheduled;
    private boolean videoStartPending;
    private boolean buttonReceiverRegistered;
    private boolean batteryReceiverRegistered;
    private boolean userLeaving;
    private long videoStartedAt;
    private boolean longPressHandled;
    private long buttonDownAt;
    private float touchDownX;
    private float touchDownY;
    private boolean touchLongActivated;
    private MediaRecorder recorder;
    private Uri pendingVideo;
    private ParcelFileDescriptor videoFile;
    private PowerManager.WakeLock recordingWakeLock;
    private PowerManager.WakeLock displayWakeLock;

    private final Runnable finishVideo = this::finishVideoNow;
    private final Runnable wakeDisplay = this::wakeDisplayForRecording;
    private final Runnable updateRecordingClock = new Runnable() {
        @Override public void run() {
            if (!recording || !recorderStarted || recordingIndicator == null) return;
            long seconds = Math.max(0L,
                    (SystemClock.elapsedRealtime() - videoStartedAt) / 1000L);
            recordingIndicator.setText(RecordingDisplay.label(seconds));
            mainHandler.postDelayed(this, 1000L);
        }
    };

    private final Runnable touchLongPress = () -> {
        touchLongActivated = true;
        startVideo();
    };

    private final Runnable heartbeat = new Runnable() {
        @Override public void run() {
            if (!resumed && !recording) return;
            sendSafetyAction(ButtonSafetyService.ACTION_HEARTBEAT);
            mainHandler.postDelayed(this, 2000L);
        }
    };

    private final BroadcastReceiver buttonReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BUTTON_DOWN.equals(action)) {
                buttonDownAt = SystemClock.elapsedRealtime();
                longPressHandled = false;
            } else if (BUTTON_LONG.equals(action) || BUTTON_VERY_LONG.equals(action)) {
                if (!longPressHandled) {
                    longPressHandled = true;
                    if (!recording) startVideo();
                }
            } else if (BUTTON_UP.equals(action)) {
                if (recording && !longPressHandled) {
                    stopVideo();
                } else if (!longPressHandled && SystemClock.elapsedRealtime() - buttonDownAt < 900L) {
                    takePhoto();
                }
            }
        }
    };

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS,
                    BatteryManager.BATTERY_STATUS_UNKNOWN);
            boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL;
            int percent = BatteryDisplay.percentage(level, scale);
            if (batteryLabel != null) {
                batteryLabel.setText(BatteryDisplay.label(percent, charging));
            }
        }
    };

    private final TextureView.SurfaceTextureListener surfaceListener = new TextureView.SurfaceTextureListener() {
        @Override public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            configureTransform(width, height);
            if (camera == null) openCamera();
            else if (!recording && cameraHandler != null) cameraHandler.post(MainActivity.this::createPreviewSession);
        }
        @Override public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            configureTransform(width, height);
        }
        @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) { return true; }
        @Override public void onSurfaceTextureUpdated(SurfaceTexture surface) {}
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        PowerManager power = getSystemService(PowerManager.class);
        recordingWakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "RokidZoomInCamera:recording");
        recordingWakeLock.setReferenceCounted(false);
        displayWakeLock = power.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP |
                        PowerManager.ON_AFTER_RELEASE,
                "RokidZoomInCamera:keep-recording-visible");
        displayWakeLock.setReferenceCounted(false);
        buildScreen();
    }

    private void buildScreen() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.setOnTouchListener((view, event) -> handleTouch(event));
        preview = new TextureView(this);
        preview.setSurfaceTextureListener(surfaceListener);
        root.addView(preview, new FrameLayout.LayoutParams(-1, -1));

        root.addView(new ReticleView(this), new FrameLayout.LayoutParams(-1, -1));

        zoomLabel = new TextView(this);
        zoomLabel.setTextColor(Color.rgb(140, 255, 140));
        zoomLabel.setTextSize(40);
        zoomLabel.setGravity(Gravity.CENTER);
        zoomLabel.setShadowLayer(7f, 0f, 2f, Color.BLACK);
        FrameLayout.LayoutParams zoomParams = new FrameLayout.LayoutParams(dp(150), dp(80), Gravity.TOP | Gravity.START);
        zoomParams.leftMargin = dp(14);
        zoomParams.topMargin = dp(10);
        root.addView(zoomLabel, zoomParams);

        batteryLabel = new TextView(this);
        batteryLabel.setText("電池 --%");
        batteryLabel.setTextColor(Color.WHITE);
        batteryLabel.setTextSize(15);
        batteryLabel.setGravity(Gravity.CENTER);
        batteryLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        batteryLabel.setShadowLayer(6f, 0f, 2f, Color.BLACK);
        GradientDrawable batteryBackground = new GradientDrawable();
        batteryBackground.setColor(Color.argb(165, 0, 0, 0));
        batteryBackground.setCornerRadius(dp(12));
        batteryLabel.setBackground(batteryBackground);
        FrameLayout.LayoutParams batteryParams = new FrameLayout.LayoutParams(
                dp(150), dp(42), Gravity.TOP | Gravity.END);
        batteryParams.topMargin = dp(10);
        batteryParams.rightMargin = dp(14);
        root.addView(batteryLabel, batteryParams);

        recordingIndicator = new TextView(this);
        recordingIndicator.setText(RecordingDisplay.label(0L));
        recordingIndicator.setTextColor(Color.WHITE);
        recordingIndicator.setTextSize(14);
        recordingIndicator.setGravity(Gravity.CENTER);
        recordingIndicator.setTypeface(null, android.graphics.Typeface.BOLD);
        GradientDrawable recordingBackground = new GradientDrawable();
        recordingBackground.setColor(Color.argb(225, 180, 0, 0));
        recordingBackground.setCornerRadius(dp(14));
        recordingIndicator.setBackground(recordingBackground);
        recordingIndicator.setVisibility(View.GONE);
        FrameLayout.LayoutParams recordingParams = new FrameLayout.LayoutParams(
                dp(175), dp(62), Gravity.TOP | Gravity.END);
        recordingParams.topMargin = dp(60);
        recordingParams.rightMargin = dp(14);
        root.addView(recordingIndicator, recordingParams);

        statusLabel = new TextView(this);
        statusLabel.setTextColor(Color.WHITE);
        statusLabel.setTextSize(17);
        statusLabel.setGravity(Gravity.CENTER);
        statusLabel.setPadding(dp(12), dp(8), dp(12), dp(8));
        statusLabel.setBackgroundColor(Color.argb(150, 0, 0, 0));
        FrameLayout.LayoutParams statusParams = new FrameLayout.LayoutParams(-1, dp(66), Gravity.BOTTOM);
        root.addView(statusLabel, statusParams);
        updateZoomUi();
        setStatus("準備中…");
        setContentView(root);
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        userLeaving = false;
        startCameraThread();
        registerButtonReceiver();
        registerBatteryReceiver();
        sendSafetyAction(ButtonSafetyService.ACTION_CLAIM);
        mainHandler.removeCallbacks(heartbeat);
        mainHandler.post(heartbeat);
        if (preview.isAvailable()) {
            if (camera == null) openCamera();
            else if (!recording && cameraHandler != null) cameraHandler.post(this::createPreviewSession);
        }
    }

    @Override protected void onPause() {
        resumed = false;
        if (recording && !userLeaving) {
            Log.d(TAG, "Screen slept while recording; keeping the camera active");
            mainHandler.removeCallbacks(wakeDisplay);
            mainHandler.postDelayed(wakeDisplay, 250L);
            super.onPause();
            return;
        }
        mainHandler.removeCallbacks(heartbeat);
        unregisterButtonReceiver();
        unregisterBatteryReceiver();
        if (recording) {
            if (recorderStarted) finishVideoNow();
            else abortVideo();
        }
        closeCamera();
        sendSafetyAction(ButtonSafetyService.ACTION_RELEASE);
        stopCameraThread();
        super.onPause();
    }

    @Override protected void onUserLeaveHint() {
        userLeaving = true;
        videoStartPending = false;
        super.onUserLeaveHint();
    }

    @Override public void onBackPressed() {
        userLeaving = true;
        videoStartPending = false;
        super.onBackPressed();
    }

    @Override protected void onDestroy() {
        videoStartPending = false;
        mainHandler.removeCallbacks(heartbeat);
        mainHandler.removeCallbacks(finishVideo);
        mainHandler.removeCallbacks(wakeDisplay);
        mainHandler.removeCallbacks(updateRecordingClock);
        if (recording) {
            if (recorderStarted) finishVideoNow();
            else abortVideo();
        }
        closeCamera();
        unregisterButtonReceiver();
        unregisterBatteryReceiver();
        sendSafetyAction(ButtonSafetyService.ACTION_RELEASE);
        stopCameraThread();
        releaseRecordingWakeLock();
        releaseDisplayWakeLock();
        super.onDestroy();
    }

    private void sendSafetyAction(String action) {
        Intent intent = new Intent(this, ButtonSafetyService.class).setAction(action);
        try {
            if (ButtonSafetyService.ACTION_CLAIM.equals(action)) startForegroundService(intent);
            else startService(intent);
        } catch (RuntimeException error) {
            Log.w(TAG, "Button safety service action failed", error);
            if (ButtonSafetyService.ACTION_RELEASE.equals(action)) RokidButtonControl.restore(this);
        }
    }

    private void registerButtonReceiver() {
        if (buttonReceiverRegistered) return;
        IntentFilter filter = new IntentFilter();
        filter.addAction(BUTTON_DOWN);
        filter.addAction(BUTTON_UP);
        filter.addAction(BUTTON_LONG);
        filter.addAction(BUTTON_VERY_LONG);
        try {
            registerReceiver(buttonReceiver, filter);
            buttonReceiverRegistered = true;
        } catch (RuntimeException ignored) {}
    }

    private void registerBatteryReceiver() {
        if (batteryReceiverRegistered) return;
        try {
            Intent sticky = registerReceiver(batteryReceiver,
                    new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            batteryReceiverRegistered = true;
            if (sticky != null) batteryReceiver.onReceive(this, sticky);
        } catch (RuntimeException error) {
            Log.w(TAG, "Battery status receiver registration failed", error);
        }
    }

    private void unregisterBatteryReceiver() {
        if (!batteryReceiverRegistered) return;
        try {
            unregisterReceiver(batteryReceiver);
        } catch (IllegalArgumentException ignored) {
        } finally {
            batteryReceiverRegistered = false;
        }
    }

    private void unregisterButtonReceiver() {
        if (!buttonReceiverRegistered) return;
        try { unregisterReceiver(buttonReceiver); } catch (RuntimeException ignored) {}
        buttonReceiverRegistered = false;
    }

    private void startCameraThread() {
        if (cameraThread != null) return;
        cameraThread = new HandlerThread("RokidZoomInCamera");
        cameraThread.start();
        cameraHandler = new Handler(cameraThread.getLooper());
    }

    private void stopCameraThread() {
        HandlerThread thread = cameraThread;
        cameraThread = null;
        cameraHandler = null;
        if (thread != null) {
            thread.quitSafely();
            try { thread.join(1200L); } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
        }
    }

    private void openCamera() {
        if (!resumed || opening || camera != null || !preview.isAvailable() || cameraHandler == null) return;
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION);
            return;
        }
        opening = true;
        setStatus("カメラを準備中…");
        try {
            CameraManager manager = getSystemService(CameraManager.class);
            String cameraId = findBackCamera(manager);
            characteristics = manager.getCameraCharacteristics(cameraId);
            sensorRect = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
            Float max = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
            maximumZoom = max == null ? 1f : max;
            Integer orientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            sensorOrientation = orientation == null ? 0 : orientation;
            android.hardware.camera2.params.StreamConfigurationMap map =
                    characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map == null) throw new CameraAccessException(CameraAccessException.CAMERA_ERROR);
            previewSize = choosePreviewSize(map.getOutputSizes(SurfaceTexture.class));
            videoSize = chooseVideoSize(map.getOutputSizes(MediaRecorder.class));
            Size photoSize = Collections.max(Arrays.asList(map.getOutputSizes(android.graphics.ImageFormat.JPEG)),
                    Comparator.comparingLong(size -> (long) size.getWidth() * size.getHeight()));
            imageReader = ImageReader.newInstance(photoSize.getWidth(), photoSize.getHeight(), android.graphics.ImageFormat.JPEG, 2);
            imageReader.setOnImageAvailableListener(this::savePhoto, cameraHandler);
            configureTransform(preview.getWidth(), preview.getHeight());
            manager.openCamera(cameraId, cameraCallback, cameraHandler);
        } catch (Exception error) {
            opening = false;
            Log.e(TAG, "Could not open camera", error);
            setStatus("カメラを使えません\n初回設定を確認してください");
        }
    }

    private String findBackCamera(CameraManager manager) throws CameraAccessException {
        for (String id : manager.getCameraIdList()) {
            Integer facing = manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING);
            if (facing == null || facing == CameraCharacteristics.LENS_FACING_BACK) return id;
        }
        throw new CameraAccessException(CameraAccessException.CAMERA_ERROR);
    }

    private final CameraDevice.StateCallback cameraCallback = new CameraDevice.StateCallback() {
        @Override public void onOpened(CameraDevice device) {
            opening = false;
            if (!resumed) { device.close(); return; }
            camera = device;
            createPreviewSession();
        }
        @Override public void onDisconnected(CameraDevice device) {
            opening = false;
            device.close();
            camera = null;
            setStatus("カメラ接続が切れました");
        }
        @Override public void onError(CameraDevice device, int error) {
            opening = false;
            device.close();
            camera = null;
            setStatus("標準カメラと競合しています\n初回設定を確認してください");
        }
    };

    private void createPreviewSession() {
        CameraDevice device = camera;
        SurfaceTexture texture = preview.getSurfaceTexture();
        if (device == null || texture == null || previewSize == null || cameraHandler == null) return;
        try {
            if (session != null) session.close();
            texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            runOnUiThread(this::configurePreviewLayout);
            Surface surface = new Surface(texture);
            repeatingBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            repeatingBuilder.addTarget(surface);
            setCommonControls(repeatingBuilder);
            device.createCaptureSession(Arrays.asList(surface, imageReader.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override public void onConfigured(CameraCaptureSession value) {
                    if (camera == null || recording) { value.close(); return; }
                    session = value;
                    try {
                        value.setRepeatingRequest(repeatingBuilder.build(), null, cameraHandler);
                        setStatus("写真：ボタン1回　動画：長押し（音声あり）\n倍率：左右へスワイプ");
                        if (videoStartPending) runOnUiThread(MainActivity.this::startVideo);
                    } catch (CameraAccessException error) {
                        setStatus("映像を開始できませんでした");
                    }
                }
                @Override public void onConfigureFailed(CameraCaptureSession value) {
                    setStatus("映像を準備できませんでした");
                }
            }, cameraHandler);
        } catch (CameraAccessException error) {
            setStatus("映像を開始できませんでした");
        }
    }

    private void setCommonControls(CaptureRequest.Builder builder) {
        builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
        builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
        if (sensorRect != null) builder.set(CaptureRequest.SCALER_CROP_REGION,
                ZoomMath.crop(sensorRect, ZOOM_STEPS[zoomIndex], maximumZoom));
    }

    private void changeZoom(int direction) {
        if (recording) return;
        int next = Math.max(0, Math.min(ZOOM_STEPS.length - 1, zoomIndex + direction));
        if (next == zoomIndex) return;
        zoomIndex = next;
        updateZoomUi();
        CaptureRequest.Builder builder = repeatingBuilder;
        CameraCaptureSession current = session;
        if (builder != null && current != null && cameraHandler != null) {
            try {
                setCommonControls(builder);
                current.setRepeatingRequest(builder.build(), null, cameraHandler);
            } catch (CameraAccessException ignored) {}
        }
    }

    private void updateZoomUi() {
        if (zoomLabel != null) zoomLabel.setText(String.format(Locale.US, "%.1f×", ZOOM_STEPS[zoomIndex]));
    }

    private void takePhoto() {
        Log.d(TAG, "takePhoto requested: camera=" + (camera != null) +
                " session=" + (session != null) + " busy=" + takingPhoto + " recording=" + recording);
        CameraDevice device = camera;
        CameraCaptureSession current = session;
        if (device == null || current == null || takingPhoto || recording) return;
        takingPhoto = true;
        setStatus("撮影中…");
        try {
            CaptureRequest.Builder still = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            still.addTarget(imageReader.getSurface());
            setCommonControls(still);
            still.set(CaptureRequest.JPEG_ORIENTATION, jpegOrientation());
            current.capture(still.build(), new CameraCaptureSession.CaptureCallback() {
                @Override public void onCaptureCompleted(CameraCaptureSession captureSession,
                                                         CaptureRequest request,
                                                         TotalCaptureResult result) {
                    Log.d(TAG, "Still capture completed");
                    Handler handler = cameraHandler;
                    if (handler != null) handler.postDelayed(() -> savePhoto(imageReader), 250L);
                }

                @Override public void onCaptureFailed(CameraCaptureSession captureSession,
                                                      CaptureRequest request,
                                                      CaptureFailure failure) {
                    takingPhoto = false;
                    Log.e(TAG, "Still capture failed, reason=" + failure.getReason());
                    setStatus("撮影できませんでした");
                }
            }, cameraHandler);
        } catch (CameraAccessException error) {
            takingPhoto = false;
            setStatus("撮影できませんでした");
        }
    }

    private void savePhoto(ImageReader reader) {
        Log.d(TAG, "savePhoto callback");
        if (reader == null) return;
        try (Image image = reader.acquireNextImage()) {
            if (image == null) {
                Log.d(TAG, "JPEG is not ready yet");
                return;
            }
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] jpeg = new byte[buffer.remaining()];
            buffer.get(jpeg);
            String name = "RokidZoomIn_" + timestamp() + "_" + String.format(Locale.US, "%.1fx.jpg", ZOOM_STEPS[zoomIndex]);
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/Camera");
            values.put(MediaStore.Images.Media.IS_PENDING, 1);
            Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IllegalStateException("MediaStore insert failed");
            try (OutputStream output = getContentResolver().openOutputStream(uri, "w")) {
                if (output == null) throw new IllegalStateException("MediaStore stream failed");
                output.write(jpeg);
            }
            ContentValues ready = new ContentValues();
            ready.put(MediaStore.Images.Media.IS_PENDING, 0);
            getContentResolver().update(uri, ready, null, null);
            runOnUiThread(() -> setStatus("保存しました　" + String.format(Locale.US, "%.1f×", ZOOM_STEPS[zoomIndex])));
        } catch (Exception error) {
            Log.e(TAG, "Could not save photo", error);
            runOnUiThread(() -> setStatus("写真を保存できませんでした"));
        } finally {
            takingPhoto = false;
            mainHandler.postDelayed(() -> {
                if (!recording && resumed) setStatus("写真：ボタン1回　動画：長押し（音声あり）\n倍率：左右へスワイプ");
            }, 1400L);
        }
    }

    private void startVideo() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            videoStartPending = true;
            setStatus("動画撮影にはマイクの許可が必要です");
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, AUDIO_PERMISSION);
            return;
        }
        CameraDevice device = camera;
        SurfaceTexture texture = preview.getSurfaceTexture();
        if (recording) return;
        if (device == null || session == null || texture == null || videoSize == null) {
            videoStartPending = true;
            setStatus("動画を準備中…");
            return;
        }
        videoStartPending = false;
        recording = true;
        recorderStarted = false;
        stopVideoRequested = false;
        stopVideoScheduled = false;
        mainHandler.removeCallbacks(finishVideo);
        acquireRecordingWakeLock();
        Log.d(TAG, "Video preparing");
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Video.Media.DISPLAY_NAME, "RokidZoomIn_" + timestamp() + "_" +
                    String.format(Locale.US, "%.1fx.mp4", ZOOM_STEPS[zoomIndex]));
            values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
            values.put(MediaStore.Video.Media.RELATIVE_PATH, "DCIM/Camera");
            values.put(MediaStore.Video.Media.IS_PENDING, 1);
            pendingVideo = getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
            if (pendingVideo == null) throw new IllegalStateException("MediaStore insert failed");
            videoFile = getContentResolver().openFileDescriptor(pendingVideo, "w");
            if (videoFile == null) throw new IllegalStateException("MediaStore file failed");
            recorder = new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
            recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setOutputFile(videoFile.getFileDescriptor());
            recorder.setAudioEncodingBitRate(128_000);
            recorder.setAudioSamplingRate(48_000);
            recorder.setAudioChannels(1);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setVideoEncodingBitRate(10_000_000);
            recorder.setVideoFrameRate(30);
            recorder.setVideoSize(videoSize.getWidth(), videoSize.getHeight());
            recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            recorder.setOrientationHint(jpegOrientation());
            recorder.prepare();

            session.close();
            texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            runOnUiThread(this::configurePreviewLayout);
            Surface previewSurface = new Surface(texture);
            Surface recordSurface = recorder.getSurface();
            repeatingBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            repeatingBuilder.addTarget(previewSurface);
            repeatingBuilder.addTarget(recordSurface);
            setCommonControls(repeatingBuilder);
            device.createCaptureSession(Arrays.asList(previewSurface, recordSurface), new CameraCaptureSession.StateCallback() {
                @Override public void onConfigured(CameraCaptureSession value) {
                    if (!recording || camera == null) { value.close(); return; }
                    session = value;
                    try {
                        value.setRepeatingRequest(repeatingBuilder.build(), null, cameraHandler);
                        recorder.start();
                        runOnUiThread(() -> {
                            if (!recording) return;
                            recorderStarted = true;
                            videoStartedAt = SystemClock.elapsedRealtime();
                            Log.d(TAG, "Video recording started; stopRequested=" + stopVideoRequested);
                            recordingIndicator.setText(RecordingDisplay.label(0L));
                            recordingIndicator.setVisibility(View.VISIBLE);
                            mainHandler.removeCallbacks(updateRecordingClock);
                            mainHandler.post(updateRecordingClock);
                            setStatus("ボタン1回で終了・保存");
                            if (stopVideoRequested) stopVideo();
                        });
                    } catch (Exception error) {
                        failVideo();
                    }
                }
                @Override public void onConfigureFailed(CameraCaptureSession value) { failVideo(); }
            }, cameraHandler);
        } catch (Exception error) {
            Log.e(TAG, "Could not start video", error);
            failVideo();
        }
    }

    private void stopVideo() {
        if (!recording) return;
        if (!recorderStarted) {
            stopVideoRequested = true;
            setStatus("録画を開始中…");
            Log.d(TAG, "Video stop requested while preparing");
            return;
        }
        if (stopVideoScheduled) return;
        long remaining = 800L - (SystemClock.elapsedRealtime() - videoStartedAt);
        if (remaining > 0L) {
            stopVideoScheduled = true;
            mainHandler.postDelayed(finishVideo, remaining);
            return;
        }
        finishVideoNow();
    }

    private void finishVideoNow() {
        mainHandler.removeCallbacks(finishVideo);
        stopVideoScheduled = false;
        videoStartPending = false;
        if (!recording || !recorderStarted) return;
        recording = false;
        recorderStarted = false;
        stopVideoRequested = false;
        boolean saved = false;
        try {
            if (recorder != null) {
                recorder.stop();
                saved = true;
            }
        } catch (RuntimeException error) {
            Log.w(TAG, "Video was too short or could not stop", error);
        }
        releaseRecorder(saved);
        releaseRecordingWakeLock();
        releaseDisplayWakeLock();
        mainHandler.removeCallbacks(updateRecordingClock);
        recordingIndicator.setVisibility(View.GONE);
        setStatus(saved ? "動画を保存しました" : "動画を保存できませんでした");
        if (resumed && camera != null && cameraHandler != null) cameraHandler.postDelayed(this::createPreviewSession, 250L);
    }

    private void failVideo() {
        runOnUiThread(() -> {
            mainHandler.removeCallbacks(finishVideo);
            recording = false;
            recorderStarted = false;
            stopVideoRequested = false;
            stopVideoScheduled = false;
            videoStartPending = false;
            releaseRecorder(false);
            releaseRecordingWakeLock();
            releaseDisplayWakeLock();
            mainHandler.removeCallbacks(updateRecordingClock);
            recordingIndicator.setVisibility(View.GONE);
            setStatus("動画を開始できませんでした");
            if (resumed && camera != null && cameraHandler != null) cameraHandler.postDelayed(this::createPreviewSession, 250L);
        });
    }

    private void abortVideo() {
        mainHandler.removeCallbacks(finishVideo);
        recording = false;
        recorderStarted = false;
        stopVideoRequested = false;
        stopVideoScheduled = false;
        videoStartPending = false;
        releaseRecorder(false);
        releaseRecordingWakeLock();
        releaseDisplayWakeLock();
        mainHandler.removeCallbacks(updateRecordingClock);
        recordingIndicator.setVisibility(View.GONE);
    }

    private void acquireRecordingWakeLock() {
        if (recordingWakeLock != null && !recordingWakeLock.isHeld()) {
            recordingWakeLock.acquire(2L * 60L * 60L * 1000L);
        }
    }

    private void releaseRecordingWakeLock() {
        if (recordingWakeLock != null && recordingWakeLock.isHeld()) recordingWakeLock.release();
    }

    private void wakeDisplayForRecording() {
        if (!recording || userLeaving || displayWakeLock == null) return;
        if (displayWakeLock.isHeld()) displayWakeLock.release();
        displayWakeLock.acquire(3000L);
        Log.d(TAG, "Woke the display to protect an active recording");
    }

    private void releaseDisplayWakeLock() {
        mainHandler.removeCallbacks(wakeDisplay);
        if (displayWakeLock != null && displayWakeLock.isHeld()) displayWakeLock.release();
    }

    private void releaseRecorder(boolean keepFile) {
        if (recorder != null) {
            try { recorder.reset(); } catch (RuntimeException ignored) {}
            recorder.release();
            recorder = null;
        }
        if (videoFile != null) {
            try { videoFile.close(); } catch (Exception ignored) {}
            videoFile = null;
        }
        if (pendingVideo != null) {
            if (keepFile) {
                ContentValues ready = new ContentValues();
                ready.put(MediaStore.Video.Media.IS_PENDING, 0);
                getContentResolver().update(pendingVideo, ready, null, null);
            } else {
                getContentResolver().delete(pendingVideo, null, null);
            }
            pendingVideo = null;
        }
    }

    private void closeCamera() {
        opening = false;
        if (session != null) { session.close(); session = null; }
        if (camera != null) { camera.close(); camera = null; }
        if (imageReader != null) { imageReader.close(); imageReader = null; }
        repeatingBuilder = null;
    }

    private boolean handleTouch(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            touchDownX = event.getX();
            touchDownY = event.getY();
            touchLongActivated = false;
            mainHandler.removeCallbacks(touchLongPress);
            mainHandler.postDelayed(touchLongPress, 700L);
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            if (Math.abs(event.getX() - touchDownX) > dp(22) ||
                    Math.abs(event.getY() - touchDownY) > dp(22)) {
                mainHandler.removeCallbacks(touchLongPress);
            }
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            mainHandler.removeCallbacks(touchLongPress);
            float dx = event.getX() - touchDownX;
            float dy = event.getY() - touchDownY;
            if (touchLongActivated) {
                // A long press starts recording. Releasing it must not stop the video;
                // the next short press is the deliberate stop action.
            } else if (recording) {
                stopVideo();
            } else if (Math.abs(dx) > dp(55) && Math.abs(dx) > Math.abs(dy)) {
                changeZoom(dx > 0 ? 1 : -1);
            } else if (Math.abs(dx) < dp(18) && Math.abs(dy) < dp(18)) {
                takePhoto();
            }
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            mainHandler.removeCallbacks(touchLongPress);
            return true;
        }
        return true;
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        Log.d(TAG, "onKeyDown key=" + keyCode + " repeat=" + event.getRepeatCount());
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            if (event.getRepeatCount() == 0) changeZoom(1);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (event.getRepeatCount() == 0) changeZoom(-1);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_CAMERA || keyCode == KeyEvent.KEYCODE_STEM_PRIMARY ||
                keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            if (event.getRepeatCount() == 0) buttonDownAt = SystemClock.elapsedRealtime();
            else if (!longPressHandled && event.isLongPress()) { longPressHandled = true; startVideo(); }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override public boolean onKeyUp(int keyCode, KeyEvent event) {
        Log.d(TAG, "onKeyUp key=" + keyCode + " longHandled=" + longPressHandled);
        if (keyCode == KeyEvent.KEYCODE_CAMERA || keyCode == KeyEvent.KEYCODE_STEM_PRIMARY ||
                keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            if (recording && !longPressHandled) stopVideo();
            else if (!recording && !longPressHandled) takePhoto();
            longPressHandled = false;
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == CAMERA_PERMISSION) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                setStatus("カメラの許可が必要です");
            }
            return;
        }
        if (requestCode == AUDIO_PERMISSION) {
            boolean granted = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
            if (granted) {
                startVideo();
            } else {
                videoStartPending = false;
                setStatus("動画撮影にはマイクの許可が必要です");
            }
        }
    }

    private void configureTransform(int viewWidth, int viewHeight) {
        configurePreviewLayout();
    }

    private void configurePreviewLayout() {
        if (previewSize == null || preview == null || root == null) return;
        int viewWidth = root.getWidth();
        int viewHeight = root.getHeight();
        if (viewWidth == 0 || viewHeight == 0) {
            root.post(this::configurePreviewLayout);
            return;
        }
        int displayDegrees = rotationDegrees(getDisplay().getRotation());
        int rotation = (sensorOrientation - displayDegrees + 360) % 360;
        boolean swapped = rotation == 90 || rotation == 270;
        PreviewGeometry.Layout layout = PreviewGeometry.centerCrop(
                viewWidth,
                viewHeight,
                previewSize.getWidth(),
                previewSize.getHeight(),
                swapped
        );
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) preview.getLayoutParams();
        if (params.width != layout.width || params.height != layout.height ||
                params.gravity != Gravity.CENTER) {
            params.width = layout.width;
            params.height = layout.height;
            params.gravity = Gravity.CENTER;
            preview.setLayoutParams(params);
        }
        // RV101/YodaOS presents the camera buffer in the display's natural
        // orientation. Match the view to that aspect ratio and let the root crop
        // only the excess edges; never stretch width and height independently.
        preview.setTransform(new Matrix());
        Log.d(TAG, "Preview layout root=" + viewWidth + "x" + viewHeight +
                " buffer=" + previewSize.getWidth() + "x" + previewSize.getHeight() +
                " swapped=" + swapped + " child=" + layout.width + "x" + layout.height);
    }

    private int jpegOrientation() {
        int display = rotationDegrees(getDisplay().getRotation());
        return (sensorOrientation + display) % 360;
    }

    private static int rotationDegrees(int rotation) {
        if (rotation == Surface.ROTATION_90) return 90;
        if (rotation == Surface.ROTATION_180) return 180;
        if (rotation == Surface.ROTATION_270) return 270;
        return 0;
    }

    private static Size choosePreviewSize(Size[] sizes) {
        List<Size> candidates = new ArrayList<>();
        List<Size> hudRatioCandidates = new ArrayList<>();
        for (Size size : sizes) {
            long pixels = (long) size.getWidth() * size.getHeight();
            if (pixels <= 1920L * 1080L && pixels >= 640L * 480L) {
                candidates.add(size);
                float ratio = (float) Math.max(size.getWidth(), size.getHeight()) /
                        Math.min(size.getWidth(), size.getHeight());
                if (Math.abs(ratio - 4f / 3f) < 0.03f) hudRatioCandidates.add(size);
            }
        }
        if (candidates.isEmpty()) candidates.addAll(Arrays.asList(sizes));
        // RV101's physical HUD is 480x640 (3:4 portrait). Prefer a 4:3 camera
        // stream so YodaOS can present it at the native HUD ratio without any
        // non-uniform scaling. The device provides 1600x1200 for this purpose.
        if (!hudRatioCandidates.isEmpty()) candidates = hudRatioCandidates;
        return Collections.max(candidates, Comparator.comparingLong(size -> (long) size.getWidth() * size.getHeight()));
    }

    private static Size chooseVideoSize(Size[] sizes) {
        List<Size> candidates = new ArrayList<>();
        for (Size size : sizes) {
            long pixels = (long) size.getWidth() * size.getHeight();
            float ratio = (float) Math.max(size.getWidth(), size.getHeight()) / Math.min(size.getWidth(), size.getHeight());
            if (pixels <= 1920L * 1080L && pixels >= 1280L * 720L && Math.abs(ratio - 16f / 9f) < 0.08f) candidates.add(size);
        }
        if (candidates.isEmpty()) return choosePreviewSize(sizes);
        return Collections.max(candidates, Comparator.comparingLong(size -> (long) size.getWidth() * size.getHeight()));
    }

    private String timestamp() {
        return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
    }

    private void setStatus(String message) {
        runOnUiThread(() -> statusLabel.setText(message));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class ReticleView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ReticleView(Context context) {
            super(context);
            setClickable(false);
            paint.setColor(Color.argb(190, 255, 255, 255));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2f * getResources().getDisplayMetrics().density);
        }
        @Override protected void onDraw(android.graphics.Canvas canvas) {
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float gap = 8f * getResources().getDisplayMetrics().density;
            float length = 22f * getResources().getDisplayMetrics().density;
            canvas.drawLine(cx - length, cy, cx - gap, cy, paint);
            canvas.drawLine(cx + gap, cy, cx + length, cy, paint);
            canvas.drawLine(cx, cy - length, cx, cy - gap, paint);
            canvas.drawLine(cx, cy + gap, cx, cy + length, paint);
        }
    }
}
