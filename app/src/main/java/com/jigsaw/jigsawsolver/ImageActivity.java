package com.jigsaw.jigsawsolver;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.constraint.ConstraintLayout;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class ImageActivity extends AppCompatActivity implements ActivityCompat.OnRequestPermissionsResultCallback {
    private HandlerThread mCameraHandlerThread;
    private Handler mCameraHandler;

    private HandlerThread mServerHandlerThread;
    private Handler mServerHandler;

    private Channel mChannel;
    private CameraDevice mCameraDevice;

    private SurfaceView mDisplaySurfaceView;
    private Surface mDisplaySurface;

    private CameraCaptureSession previewCaptureSession = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image);
        mDisplaySurfaceView = (SurfaceView) findViewById(R.id.surfaceView);

        mCameraHandlerThread = new HandlerThread("CameraThread");
        mCameraHandlerThread.start();
        mCameraHandler = new Handler(mCameraHandlerThread.getLooper());

        mServerHandlerThread = new HandlerThread("ServerThread");
        mServerHandlerThread.start();
        mServerHandler = new Handler(mServerHandlerThread.getLooper());

        setupCamera();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mCameraHandlerThread.quit();
        mServerHandlerThread.quit();
        // TODO close camera device properly
        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
    }

    private void setupCamera() {
        ActivityCompat.requestPermissions(ImageActivity.this, new String[]{Manifest.permission.CAMERA}, 1);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        Log.d("Zhongxia", "permissions good");
        if (ContextCompat.checkSelfPermission(ImageActivity.this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            throw new RuntimeException("This shouldn't happen");
        }
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            String[] idList = cameraManager.getCameraIdList();
            Log.d("Zhongxia", "Length: " + idList.length + "IDs: " + idList[0] + ", " + idList[1]);
            String cameraId = idList[0];
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap configs = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size[] sizes = configs.getOutputSizes(ImageFormat.JPEG);
            if (sizes != null && sizes.length == 0) {
                throw new RuntimeException("No compatible sizes");
            }
            Size size = sizes[0];

            Log.d("Zhongxia", size.getWidth() + " " + size.getHeight());
            cameraManager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    Log.d("Zhongxia", "camera opened");
                    mCameraDevice = camera;

                    final SurfaceHolder displaySurfaceHolder = mDisplaySurfaceView.getHolder();
                    mDisplaySurface = displaySurfaceHolder.getSurface();
                    if (mDisplaySurface != null) {
                        startPreview();
                    }
                    displaySurfaceHolder.addCallback(new SurfaceHolder.Callback() {
                        @Override
                        public void surfaceCreated(final SurfaceHolder holder) {
                            Log.d("Zhongxia", "surface created");
                            mDisplaySurface = holder.getSurface();
                            startPreview();
                        }

                        @Override
                        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                            Log.d("Zhongxia", "surface changed");
                        }

                        @Override
                        public void surfaceDestroyed(SurfaceHolder holder) {
                            Log.d("Zhongxia", "surface destroyed");
                            mDisplaySurface = null;
                            previewCaptureSession = null;
                        }
                    });
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {
                    Log.d("Zhongxia", "camera disconnected");
                    mCameraDevice = null;
                }

                @Override
                public void onError(@NonNull CameraDevice camera, int error) {
                    Log.d("Zhongxia", "camera error");
                    mCameraDevice = null;
                }
            }, null);

        } catch (CameraAccessException cae) {
            throw new RuntimeException("Cannot create capture session");
        }
    }

    private void createCaptureSession(CaptureRequest.Builder captureRequestBuilder, CameraCaptureSession.StateCallback cameraStateCallback, Surface surface) {
        captureRequestBuilder.addTarget(surface);
        List<Surface> outputs = new ArrayList<Surface>();
        outputs.add(surface);
        try {
            mCameraDevice.createCaptureSession(outputs, cameraStateCallback, mCameraHandler);
        } catch (CameraAccessException cae) {
            throw new RuntimeException("Cannot create capture session");
        }
    }

    private void startPreview() {
        final CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {
            @Override
            public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                Log.d("Zhongxia", "capture completed");
            }

            @Override
            public void onCaptureFailed(@NonNull CameraCaptureSession session,
                                        @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
                Log.d("Zhongxia", "capture failed");
            }

            @Override
            public void onCaptureStarted(@NonNull CameraCaptureSession session,
                                         @NonNull CaptureRequest request, long timestamp, long frameNumber) {
                Log.d("Zhongxia", "capture started");
            }
        };
        try {
            final CaptureRequest.Builder captureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            captureRequestBuilder.set(CaptureRequest.COLOR_CORRECTION_MODE, CameraMetadata.COLOR_CORRECTION_MODE_HIGH_QUALITY);
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
            captureRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO);

            final CameraCaptureSession.StateCallback cameraStateCallback = new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    try {
                        Log.d("Zhongxia", "configured completed");
                        previewCaptureSession = session;
                        session.setRepeatingRequest(captureRequestBuilder.build(), captureListener, null);
                    } catch (CameraAccessException cae) {
                        throw new RuntimeException("Capture failed");
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Log.d("Zhongxia", "configured failed");
                    throw new RuntimeException("Capture configuration failed");
                }
            };
            createCaptureSession(captureRequestBuilder, cameraStateCallback, mDisplaySurface);
        } catch (CameraAccessException cae) {
            throw new RuntimeException("creating capture request failed");
        }
        mDisplaySurfaceView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                captureImage();
            }
        });
    }

    private void captureImage() {
        Log.d("DEBUG", "pressed");
        if (mCameraDevice == null) {
            return;
        }
        final CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {
            @Override
            public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                super.onCaptureCompleted(session, request, result);
            }
        };
        try {
            final CaptureRequest.Builder captureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            captureRequestBuilder.set(CaptureRequest.COLOR_CORRECTION_MODE, CameraMetadata.COLOR_CORRECTION_MODE_HIGH_QUALITY);
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
            captureRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO);
            final CameraCaptureSession.StateCallback cameraStateCallback = new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    try {
                        Log.d("Zhongxia", "configured completed");
                        session.capture(captureRequestBuilder.build(), captureListener, null);
                    } catch (CameraAccessException cae) {
                        throw new RuntimeException("Capture failed");
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Log.d("Zhongxia", "configured failed");
                    throw new RuntimeException("Capture configuration failed");
                }
            };
            previewCaptureSession.stopRepeating();
            previewCaptureSession = null;

            createCaptureSession(captureRequestBuilder, cameraStateCallback, mDisplaySurface);
        } catch (CameraAccessException cae) {
            throw new RuntimeException("Cannot create capture session");
        }
        mDisplaySurfaceView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startPreview();
            }
        });
    }

    public void submitImage(final byte[] inputImage) {
        mServerHandler.post(new Runnable() {
            @Override
            public void run() {
                byte[] output = mChannel.submit(inputImage);
            }
        });
    }
}
