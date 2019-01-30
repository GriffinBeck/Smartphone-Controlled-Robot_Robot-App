package me.griffin.robotcontrolapp;

import android.Manifest;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.InputType;
import android.util.Log;
import android.util.Size;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Session;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;

import java.io.ByteArrayOutputStream;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Set;

import me.griffin.robotcontrolapp.remoteconnection.ClientThread;
import me.griffinbeck.server.BackLoadedCommandPacket;
import me.griffinbeck.server.cmdresponses.CommandArguments;

public class MainActivity extends AppCompatActivity implements JoystickView.JoystickListener {
    /*
     * Notifications from UsbService will be received here.
     */
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case UsbService.ACTION_USB_PERMISSION_GRANTED: // USB PERMISSION GRANTED
                    Toast.makeText(context, "USB Ready", Toast.LENGTH_SHORT).show();
                    break;
                case UsbService.ACTION_USB_PERMISSION_NOT_GRANTED: // USB PERMISSION NOT GRANTED
                    Toast.makeText(context, "USB Permission not granted", Toast.LENGTH_SHORT).show();
                    break;
                case UsbService.ACTION_NO_USB: // NO USB CONNECTED
                    Toast.makeText(context, "No USB connected", Toast.LENGTH_SHORT).show();
                    break;
                case UsbService.ACTION_USB_DISCONNECTED: // USB DISCONNECTED
                    Toast.makeText(context, "USB disconnected", Toast.LENGTH_SHORT).show();
                    break;
                case UsbService.ACTION_USB_NOT_SUPPORTED: // USB NOT SUPPORTED
                    Toast.makeText(context, "USB device not supported", Toast.LENGTH_SHORT).show();
                    break;
                default:
                    Toast.makeText(context, "Actions occured: " + intent.getAction(), Toast.LENGTH_LONG).show();
                    break;
            }
        }
    };
    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(final ImageReader reader) {

            // Process the image.
            Image image = reader.acquireNextImage();
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            final byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            image.close();
        }
    };
    //Camera Streaming
    //private Camera mCamera;
    //public MyCameraView cameraPreview;
    public TextView serverStatus;
    protected CameraDevice cameraDevice;
    protected CameraCaptureSession cameraCaptureSessions;
    protected CaptureRequest captureRequest;
    protected CaptureRequest.Builder captureRequestBuilder;
    SurfaceTexture mPreviewSurfaceTexture;
    private Handler cameraHandler;
    //End of Camera Stream vars
    private ToggleButton toggleSerial;
    private ToggleButton toggleServer;
    private TextView ipText;
    private ScrollView consoleScroll;
    private TextView console;
    //private Server server;
    private ClientThread clientThread;
    private MyHandler mHandler;
    private final ServiceConnection usbConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName arg0, IBinder arg1) {
            CurrentCommandHolder.usbService = ((UsbService.UsbBinder) arg1).getService();
            CurrentCommandHolder.usbService.setHandler(getmHandler());
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            CurrentCommandHolder.usbService = null;
            Log.e("UsbService", "Service disconected");
        }
    };
    private JoystickView joystick;
    private Menu menu;
    //Start Camera2 Code
    private Handler cameraCaptuerHandler;
    private TextureView textureView;
    private Runnable cameraViewChecker = new Runnable() {
        @Override
        public void run() {
            try {
                cameraHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (CurrentCommandHolder.serverConnectionOpen && CurrentCommandHolder.isIsCameraOpen()) {
                            textureView.setVisibility(View.VISIBLE);
                            joystick.setVisibility(View.INVISIBLE);
                            Log.i("RobotControl", "Queued Frame");
                            if (textureView.getBitmap() != null)
                                CurrentCommandHolder.addNetworkPacket(new BackLoadedCommandPacket(new String[]{CommandArguments.BACKLOADED_PACKET_IMG.toString()}, getBitMapByteArray(textureView.getBitmap())));
                        } else {
                            textureView.setVisibility(View.INVISIBLE);
                            joystick.setVisibility(View.VISIBLE);
                        }

                    }
                }); //this function can change value of mInterval.
            } finally {
                // 100% guarantee that this always happens, even if
                // your update method throws an exception
                cameraHandler.postDelayed(cameraViewChecker, 1000 / 10);
            }
        }
    };
    private String cameraId;
    private Size imageDimension;
    TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            //open your camera here
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            // Transform you image captured size according to the surface width and height
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        }
    };
    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            //This is called when the camera is open
            //Log.e(TAG, "onOpened");
            cameraDevice = camera;
            createCameraPreview();
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            cameraDevice.close();
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            cameraDevice.close();
            cameraDevice = null;
        }
    };
    final CameraCaptureSession.CaptureCallback captureCallbackListener = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            //Toast.makeText(MainActivity.this, "Saved:" + file, Toast.LENGTH_SHORT).show();
            createCameraPreview();
        }
    };
    private Session arSession;

    private byte[] getBitMapByteArray(Bitmap bitmap) {
        //Bitmap bmp = intent.getExtras().get("data");
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.WEBP, 25, stream);
        byte[] byteArray = stream.toByteArray();
        bitmap.recycle();
        return byteArray;
    }

    private void openCamera() {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        //Log.e(TAG, "is camera open");
        try {
            String cameraId = manager.getCameraIdList()[0];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;
            imageDimension = map.getOutputSizes(SurfaceTexture.class)[0];
            // Add permission for camera and let user grant the permission
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CAMERA}, 101);
                return;
            }
            manager.openCamera(cameraId, stateCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        //Log.e(TAG, "openCamera X");
    }

    protected void createCameraPreview() {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(imageDimension.getWidth(), imageDimension.getHeight());
            Surface surface = new Surface(texture);
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_ZERO_SHUTTER_LAG);
            captureRequestBuilder.addTarget(surface);
            //captureRequestBuilder.addTarget(mOnImageAvailableListener);
            cameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    //The camera is already closed
                    if (null == cameraDevice) {
                        return;
                    }
                    // When the session is ready, we start displaying the preview.
                    cameraCaptureSessions = cameraCaptureSession;
                    updatePreview();
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Toast.makeText(MainActivity.this, "Configuration change", Toast.LENGTH_SHORT).show();
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    protected void updatePreview() {
        if (null == cameraDevice) {
            //Log.e(TAG, "updatePreview error, return");
        }
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
        try {
            cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), null, cameraCaptuerHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    //End Camera2 Code

    private void closeCamera() {
        if (null != cameraDevice) {
            cameraDevice.close();
            cameraDevice = null;
        }
        /*if (null != imageReader) {
            imageReader.close();
            imageReader = null;
        }*/
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        joystick = new JoystickView(this);
        setContentView(R.layout.activity_main);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED) {// Permission is not granted
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.INTERNET}, 101);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 101);
        }
        if (ContextCompat.checkSelfPermission(this, "com.android.example.USB_PERMISSION") != PackageManager.PERMISSION_GRANTED) {// Permission is not granted
            ActivityCompat.requestPermissions(this, new String[]{"com.android.example.USB_PERMISSION"}, 101);
        }
        runARCoreCheck();
        //Camera Stream
        /*mCamera = getCameraInstance();
        cameraPreview = new MyCameraView(this, mCamera);
        FrameLayout preview = (FrameLayout) findViewById(R.id.camera_preview);
        preview.addView(cameraPreview);*/
        cameraHandler = new Handler(Looper.myLooper());
        cameraViewChecker.run();
        //END Camera Stream Code
        //Start Camera2 Code
        textureView = findViewById(R.id.camera_preview);
        assert textureView != null;
        textureView.setSurfaceTextureListener(textureListener);
        //End Camera2 Code
        //ipText = findViewById(R.id.ipAddress);
        consoleScroll = findViewById(R.id.consoleScroll);
        console = findViewById(R.id.console);
        consoleScroll.fullScroll(View.FOCUS_DOWN);
        //server = new Server(getLocalIpAddress());
        mHandler = new MyHandler(this);
        /*toggleSerial.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                //Log.d("Main Method", "Button was ToggledAJASVDKBVJHBASKBAJSCAHCASC");
                if (isChecked) {
                    CurrentCommandHolder.startThread(MainActivity.this);
                    //serialComms.startAutoSerial();
                } else {
                    CurrentCommandHolder.stopThread(MainActivity.this);
                    //serialComms.stopAutoSerial();
                }
            }
        });
        toggleServer.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                //Log.d("Main Method", "Button was ToggledAJASVDKBVJHBASKBAJSCAHCASC");
                *//**
         * TODO: Convert to one time button press
         *//*
                //Log.e("BUTTON", "BUTTTON WAS PRESSED Casdasdasdasdasdasdasda");
                if (isChecked) {
                    //Log.e("BUTTON", "BUTTTON WAS PRESSED CORRECTLY");
                    //server.startServer();
                    openConnectionDialog();
                    *//*new Timer().schedule(new TimerTask() {
                        @Override
                        public void run() {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (CurrentCommandHolder.serverSatus) {
                                        ipText.setText("IP Address: " + CurrentCommandHolder.getIp() + ":" + CurrentCommandHolder.getPort());
                                    }
                                }
                            });
                        }
                    }, 1000);*//*
                }
            }
        });*/
        CurrentCommandHolder.usbService = new UsbService();
        startService();
        setFilters();
    }

    public BroadcastReceiver getmUsbReceiver() {
        return mUsbReceiver;
    }

    public MyHandler getmHandler() {
        return mHandler;
    }

    @Override
    public void onResume() {
        super.onResume();
        setFilters();  // Start listening notifications from UsbService
        startService(); // Start UsbService(if it was not started before) and Bind it
        if (textureView.isAvailable()) {
            openCamera();
        } else {
            textureView.setSurfaceTextureListener(textureListener);
        }
        runARCoreCheck();
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(mUsbReceiver);
        unbindService(usbConnection);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

    }

    @Override
    public void onJoystickMoved(float xPercent, float yPercent, int id) {
        CurrentCommandHolder.addCommand("d", yPercent, xPercent);
        //autoSerial(yPercent,xPercent);
        Log.d("Main Method", "X percent: " + xPercent + " Y percent: " + yPercent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menubar, menu);
        this.menu = menu;
        hideNetworkConnectedIcon();
        hideUSBConnectedIcon();
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.connect_to_server:
                openConnectionDialog();
                return true;
            case R.id.serial_toggle:
                CurrentCommandHolder.toggleThread(MainActivity.this);
                if (CurrentCommandHolder.getSerialStatus()) {
                    item.setTitle("Toggle Serial Off");
                    //showUSBConnectedIcon();
                } else {
                    item.setTitle("Toggle Serial On");
                    //hideUSBConnectedIcon();
                }
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void showNetworkConnectedIcon() {
        menu.findItem(R.id.network_connected_icon).setVisible(true);
    }

    public void hideNetworkConnectedIcon() {
        menu.findItem(R.id.network_connected_icon).setVisible(false);
    }

    public void showUSBConnectedIcon() {
        menu.findItem(R.id.serial_connected_icon).setVisible(true);
    }

    public void hideUSBConnectedIcon() {
        menu.findItem(R.id.serial_connected_icon).setVisible(false);
    }

    private void runARCoreCheck() {
        try {
            if (arSession == null) {
                switch (ArCoreApk.getInstance().requestInstall(this, false)) {
                    case INSTALLED:
                        // Success, create the AR session.
                        arSession = new Session(this);
                        break;
                    case INSTALL_REQUESTED:
                        // Ensures next invocation of requestInstall() will either return
                        // INSTALLED or throw an exception.
                        //mUserRequestedInstall = false;
                        return;
                }
            }
        } catch (UnavailableUserDeclinedInstallationException e) {
            // Display an appropriate message to the user and return gracefully.
            Toast.makeText(this, "TODO: handle exception " + e, Toast.LENGTH_LONG)
                    .show();
            return;
        } catch (Exception e) {  // Current catch statements.
            return;  // mSession is still null.
        }
    }

    private void openConnectionDialog() {
        //Log.e("Alert", "Creating alert");
        final AlertDialog.Builder alert = new AlertDialog.Builder(MainActivity.this);
        final AlertDialog dialog;
        alert.setTitle("Enter Connection Details for the Server");
        //LayoutInflater inflater = this.getLayoutInflater();
        LinearLayout dialogLayout = new LinearLayout(this);
        dialogLayout.setDividerPadding(10);
        //View dialogLayout = inflater.inflate(R.layout.connect_to_server, null);
        dialogLayout.setOrientation(LinearLayout.VERTICAL);
        final EditText ipInput = new EditText(this);
        ipInput.setHint("IP Address Ex:(127.0.0.0)");
        ipInput.setFitsSystemWindows(true);
        final EditText portInput = new EditText(this);
        portInput.setHint("Port Ex:(3068)");
        portInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        portInput.setRawInputType(Configuration.KEYBOARD_12KEY);
        dialogLayout.addView(ipInput);
        dialogLayout.addView(portInput);
        alert.setView(dialogLayout);

        alert.setPositiveButton("Connect", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                final String ip = ipInput.getText().toString().trim();
                final String portString = portInput.getText().toString().trim();
                if (ip.length() == 0 || portString.length() == 0) {
                    alert.show();
                } else {
                    final int port = Integer.parseInt(portString);
                    clientThread = new ClientThread(ip, port, MainActivity.this);
                    clientThread.start();
                }
                //showNetworkConnectedIcon();
            }
        });
        alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                //Put actions for CANCEL button here, or leave in blank
            }
        });
        dialog = alert.show();
        dialog.show();
        Log.i("Alert", "showing dialog");
    }

    private void startService(Class<?> service, ServiceConnection serviceConnection, Bundle extras) {
        if (!UsbService.SERVICE_CONNECTED) {
            Intent startService = new Intent(this, service);
            if (extras != null && !extras.isEmpty()) {
                Set<String> keys = extras.keySet();
                for (String key : keys) {
                    String extra = extras.getString(key);
                    startService.putExtra(key, extra);
                }
            }
            startService(startService);
        }
        Intent bindingIntent = new Intent(this, service);
        bindService(bindingIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    public void startService() {
        startService(UsbService.class, usbConnection, null);
    }

    public void setFilters() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbService.ACTION_USB_PERMISSION_GRANTED);
        filter.addAction(UsbService.ACTION_NO_USB);
        filter.addAction(UsbService.ACTION_USB_DISCONNECTED);
        filter.addAction(UsbService.ACTION_USB_NOT_SUPPORTED);
        filter.addAction(UsbService.ACTION_USB_PERMISSION_NOT_GRANTED);
        registerReceiver(mUsbReceiver, filter);
    }

    private static class MyHandler extends Handler {
        private final WeakReference<MainActivity> mActivity;

        public MyHandler(MainActivity activity) {
            mActivity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case UsbService.MESSAGE_FROM_SERIAL_PORT:
                    String data = (String) msg.obj;
                    mActivity.get().console.append(data);
                    mActivity.get().consoleScroll.fullScroll(View.FOCUS_DOWN);
                    //Toast.makeText(mActivity.get(),data,Toast.LENGTH_LONG).show();
                    break;
                case UsbService.CTS_CHANGE:
                    Toast.makeText(mActivity.get(), "CTS_CHANGE", Toast.LENGTH_LONG).show();
                    break;
                case UsbService.DSR_CHANGE:
                    Toast.makeText(mActivity.get(), "DSR_CHANGE", Toast.LENGTH_LONG).show();
                    break;
            }
        }
    }
}
