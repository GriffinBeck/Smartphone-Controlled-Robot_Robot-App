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
import android.opengl.GLES20;
import android.opengl.GLException;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Camera;
import com.google.ar.core.Frame;
import com.google.ar.core.Plane;
import com.google.ar.core.PointCloud;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.IntBuffer;
import java.util.Set;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import me.griffin.robotcontrolapp.arcore.HelloArActivity;
import me.griffin.robotcontrolapp.arcore.helpers.CameraPermissionHelper;
import me.griffin.robotcontrolapp.arcore.helpers.DisplayRotationHelper;
import me.griffin.robotcontrolapp.arcore.helpers.SnackbarHelper;
import me.griffin.robotcontrolapp.arcore.rendering.BackgroundRenderer;
import me.griffin.robotcontrolapp.arcore.rendering.ObjectRenderer;
import me.griffin.robotcontrolapp.arcore.rendering.PlaneRenderer;
import me.griffin.robotcontrolapp.arcore.rendering.PointCloudRenderer;
import me.griffin.robotcontrolapp.autonomous.AutonomousManager;
import me.griffin.robotcontrolapp.remoteconnection.ClientThread;
import me.griffinbeck.server.BackLoadedCommandPacket;
import me.griffinbeck.server.cmdresponses.CommandArguments;

public class MainActivity extends AppCompatActivity implements JoystickView.JoystickListener, GLSurfaceView.Renderer {
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
    //Camera Streaming
    private Handler cameraHandler;
    //End of Camera Stream vars
    private ScrollView consoleScroll;
    private TextView console;
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
    private int[] bitmapArrayToSend;
    private long timeOfLastFrame;
    private int height, width;

    private byte[] getBitMapByteArray(Bitmap bitmap) {
        //Bitmap bmp = intent.getExtras().get("data");
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.WEBP, 25, stream);
        byte[] byteArray = stream.toByteArray();
        bitmap.recycle();
        return byteArray;
    }
    //End Camera Code

    ///////////ARCORE STARTS HERE
    private static final String TAG = HelloArActivity.class.getSimpleName();

    public BroadcastReceiver getmUsbReceiver() {
        return mUsbReceiver;
    }

    public MyHandler getmHandler() {
        return mHandler;
    }

    private final SnackbarHelper messageSnackbarHelper = new SnackbarHelper();
    private final BackgroundRenderer backgroundRenderer = new BackgroundRenderer();

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

    private final ObjectRenderer virtualObject = new ObjectRenderer();
    private final ObjectRenderer virtualObjectShadow = new ObjectRenderer();
    private final PlaneRenderer planeRenderer = new PlaneRenderer();
    private final PointCloudRenderer pointCloudRenderer = new PointCloudRenderer();
    // Temporary matrix allocated here to reduce number of allocations for each frame.
    private final float[] anchorMatrix = new float[16];
    // Rendering. The Renderers are created here, and initialized when the GL surface is created.
    private GLSurfaceView surfaceView;
    private boolean installRequested;
    private Session arSession;
    private DisplayRotationHelper displayRotationHelper;
    private Runnable cameraViewChecker = new Runnable() {
        @Override
        public void run() {
            try {
                cameraHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (CurrentCommandHolder.serverConnectionOpen && CurrentCommandHolder.isIsCameraOpen()) {

                            joystick.setVisibility(View.INVISIBLE);
                            Log.i("RobotControl", "Queued Frame");
                            if (bitmapArrayToSend != null) {
                                //CurrentCommandHolder.addNetworkPacket(new BackLoadedCommandPacket(new String[]{CommandArguments.BACKLOADED_PACKET_IMG.toString()}, bitmapToByteArray(bitmapFromSourceArray(bitmapArrayToSend, width, height))));
                                CurrentCommandHolder.addNetworkPacket(new BackLoadedCommandPacket(new String[]{CommandArguments.BACKLOADED_PACKET_IMG.toString()}, gl_pixelArrayToBitmapWEBPArray(bitmapArrayToSend, width, height)));
                            }
                        } else {
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

    @Override
    public void onResume() {
        super.onResume();
        setFilters();  // Start listening notifications from UsbService
        startService(); // Start UsbService(if it was not started before) and Bind it
        /*if (textureView.isAvailable()) {
            openCamera();
        } else {
            textureView.setSurfaceTextureListener(textureListener);
        }*/
        arCoreOnResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(mUsbReceiver);
        unbindService(usbConnection);
        arCoreOnPause();
    }

    private void arCoreOnCreate() {
        surfaceView = findViewById(R.id.camera_preview);
        displayRotationHelper = new DisplayRotationHelper(this);

        // Set up renderer.
        surfaceView.setPreserveEGLContextOnPause(true);
        surfaceView.setEGLContextClientVersion(2);
        surfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0); // Alpha used for plane blending.
        surfaceView.setRenderer(this);
        surfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        surfaceView.setWillNotDraw(false);

        installRequested = false;
    }

    private void arCoreOnPause() {
        if (arSession != null) {
            // Note that the order matters - GLSurfaceView is paused first so that it does not try
            // to query the session. If Session is paused before GLSurfaceView, GLSurfaceView may
            // still call session.update() and get a SessionPausedException.
            displayRotationHelper.onPause();
            surfaceView.onPause();
            arSession.pause();
        }
    }

    private void arCoreOnResume() {
        if (arSession == null) {
            Exception exception = null;
            String message = null;
            try {
                switch (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
                    case INSTALL_REQUESTED:
                        installRequested = true;
                        return;
                    case INSTALLED:
                        break;
                }

                // ARCore requires camera permissions to operate. If we did not yet obtain runtime
                // permission on Android M and above, now is a good time to ask the user for it.
                if (!CameraPermissionHelper.hasCameraPermission(this)) {
                    CameraPermissionHelper.requestCameraPermission(this);
                    return;
                }

                // Create the session.
                arSession = new Session(/* context= */ this);

            } catch (UnavailableArcoreNotInstalledException
                    | UnavailableUserDeclinedInstallationException e) {
                message = "Please install ARCore";
                exception = e;
            } catch (UnavailableApkTooOldException e) {
                message = "Please update ARCore";
                exception = e;
            } catch (UnavailableSdkTooOldException e) {
                message = "Please update this app";
                exception = e;
            } catch (UnavailableDeviceNotCompatibleException e) {
                message = "This device does not support AR";
                exception = e;
            } catch (Exception e) {
                message = "Failed to create AR session";
                exception = e;
            }

            if (message != null) {
                messageSnackbarHelper.showError(this, message);
                Log.e(TAG, "Exception creating session", exception);
                return;
            }
        }

        // Note that order matters - see the note in onPause(), the reverse applies here.
        try {
            arSession.resume();
        } catch (CameraNotAvailableException e) {
            // In some cases (such as another camera app launching) the camera may be given to
            // a different app instead. Handle this properly by showing a message and recreate the
            // session at the next iteration.
            messageSnackbarHelper.showError(this, "Camera not available. Please restart the app.");
            arSession = null;
            return;
        }

        surfaceView.onResume();
        displayRotationHelper.onResume();

        messageSnackbarHelper.showMessage(this, "Searching for surfaces...");
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);

        // Prepare the rendering objects. This involves reading shaders, so may throw an IOException.
        try {
            // Create the texture and pass it to ARCore session to be filled during update().
            backgroundRenderer.createOnGlThread(/*context=*/ this);
            planeRenderer.createOnGlThread(/*context=*/ this, "models/trigrid.png");
            pointCloudRenderer.createOnGlThread(/*context=*/ this);

            virtualObjectShadow.createOnGlThread(
                    /*context=*/ this, "models/andy_shadow.obj", "models/andy_shadow.png");
            virtualObjectShadow.setBlendMode(ObjectRenderer.BlendMode.Shadow);
            virtualObjectShadow.setMaterialProperties(1.0f, 0.0f, 0.0f, 1.0f);

        } catch (IOException e) {
            Log.e(TAG, "Failed to read an asset file", e);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        //FullScreenHelper.setFullScreenOnWindowFocusChanged(this, hasFocus);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        displayRotationHelper.onSurfaceChanged(width, height);
        GLES20.glViewport(0, 0, width, height);
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
        arCoreOnCreate();
        //Camera2
        cameraHandler = new Handler(Looper.myLooper());
        new Thread(cameraViewChecker).start();
        //End Camera2 Code
        //ipText = findViewById(R.id.ipAddress);
        consoleScroll = findViewById(R.id.consoleScroll);
        console = findViewById(R.id.console);
        consoleScroll.fullScroll(View.FOCUS_DOWN);
        //server = new Server(getLocalIpAddress());
        mHandler = new MyHandler(this);

        CurrentCommandHolder.usbService = new UsbService();
        startService();
        setFilters();
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        // Clear screen to notify driver it should not load any pixels from previous frame.
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        if (arSession == null) {
            Log.e(TAG, "SESSION IS NULL");
            return;
        }
        // Notify ARCore session that the view size changed so that the perspective matrix and
        // the video background can be properly adjusted.
        displayRotationHelper.updateSessionIfNeeded(arSession);

        try {
            arSession.setCameraTextureName(backgroundRenderer.getTextureId());

            // Obtain the current frame from ARSession. When the configuration is set to
            // UpdateMode.BLOCKING (it is by default), this will throttle the rendering to the
            // camera framerate.
            Frame frame = arSession.update();
            Camera camera = frame.getCamera();

            // Handle one tap per frame.

            // Draw background.
            backgroundRenderer.draw(frame);

            // If not tracking, don't draw 3d objects.
            if (camera.getTrackingState() == TrackingState.PAUSED) {
                return;
            }

            // Get projection matrix.
            float[] projmtx = new float[16];
            camera.getProjectionMatrix(projmtx, 0, 0.1f, 100.0f);

            // Get camera matrix and draw.
            float[] viewmtx = new float[16];
            camera.getViewMatrix(viewmtx, 0);

            // Compute lighting from average intensity of the image.
            // The first three components are color scaling factors.
            // The last one is the average pixel intensity in gamma space.
            final float[] colorCorrectionRgba = new float[4];
            frame.getLightEstimate().getColorCorrection(colorCorrectionRgba, 0);

            // Visualize tracked points.
            PointCloud pointCloud = frame.acquirePointCloud();
            pointCloudRenderer.update(pointCloud);
            pointCloudRenderer.draw(viewmtx, projmtx);

            // Application is responsible for releasing the point cloud resources after
            // using it.
            pointCloud.release();

            // Check if we detected at least one plane. If so, hide the loading message.
            if (messageSnackbarHelper.isShowing()) {
                for (Plane plane : arSession.getAllTrackables(Plane.class)) {
                    if (plane.getTrackingState() == TrackingState.TRACKING) {
                        messageSnackbarHelper.hide(this);
                        break;
                    }
                }
            }

            // Visualize planes.
            planeRenderer.drawPlanes(
                    arSession.getAllTrackables(Plane.class), camera.getDisplayOrientedPose(), projmtx);
            if (System.currentTimeMillis() - 100 > timeOfLastFrame /*&& CurrentCommandHolder.isIsCameraOpen()*/) {
                //bitmapArrayToSend = createBitmapArrayFromGLSurface(0, 0, surfaceView.getWidth(), surfaceView.getHeight(), gl);
                bitmapArrayToSend = glPixelArray(0, 0, surfaceView.getWidth(), surfaceView.getHeight(), gl);
            }
            if (CurrentCommandHolder.autonomousEnabled) {
                AutonomousManager.updateFrame(frame);
            }
        } catch (Throwable t) {
            // Avoid crashing the application due to unhandled exceptions.
            Log.e(TAG, "Exception on the OpenGL thread", t);
        }

    }

    private byte[] gl_pixelArrayToBitmapWEBPArray(int[] gl_buffer, int width, int height) {
        int bitmapSource[] = new int[width * height];
        int offset1, offset2;
        for (int i = 0; i < height; i++) {
            offset1 = i * width;
            offset2 = (height - i - 1) * width;
            for (int j = 0; j < width; j++) {
                int texturePixel = gl_buffer[offset1 + j];
                int blue = (texturePixel >> 16) & 0xff;
                int red = (texturePixel << 16) & 0x00ff0000;
                int pixel = (texturePixel & 0xff00ff00) | red | blue;
                bitmapSource[offset2 + j] = pixel;
            }
        }
        return bitmapToByteArray(bitmapFromSourceArray(bitmapSource, width, height));
    }

    private int[] glPixelArray(int x, int y, int w, int h, GL10 gl) {
        int[] buffer = new int[w * h];
        IntBuffer intBuffer = IntBuffer.wrap(buffer);
        intBuffer.position(0);
        try {
            gl.glReadPixels(x, y, w, h, GL10.GL_RGBA, GL10.GL_UNSIGNED_BYTE, intBuffer);
            width = w;
            height = h;
            return buffer;
        } catch (GLException e) {
            return null;
        }
    }

    private int[] createBitmapArrayFromGLSurface(int x, int y, int w, int h, GL10 gl)
            throws OutOfMemoryError {
        int bitmapBuffer[] = new int[w * h];
        int bitmapSource[] = new int[w * h];
        IntBuffer intBuffer = IntBuffer.wrap(bitmapBuffer);
        intBuffer.position(0);

        try {
            gl.glReadPixels(x, y, w, h, GL10.GL_RGBA, GL10.GL_UNSIGNED_BYTE, intBuffer);
            int offset1, offset2;
            for (int i = 0; i < h; i++) {
                offset1 = i * w;
                offset2 = (h - i - 1) * w;
                for (int j = 0; j < w; j++) {
                    int texturePixel = bitmapBuffer[offset1 + j];
                    int blue = (texturePixel >> 16) & 0xff;
                    int red = (texturePixel << 16) & 0x00ff0000;
                    int pixel = (texturePixel & 0xff00ff00) | red | blue;
                    bitmapSource[offset2 + j] = pixel;
                }
            }
        } catch (GLException e) {
            return null;
        }
        height = h;
        width = w;
        return bitmapSource;
        /*ByteArrayOutputStream stream = new ByteArrayOutputStream();
        Bitmap.createBitmap(bitmapSource, w, h, Bitmap.Config.ARGB_8888);

        return stream.toByteArray();*/
        //return Bitmap.createBitmap(bitmapSource, w, h, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.WEBP,10,);
    }

    private Bitmap bitmapFromSourceArray(int[] arr, int w, int h) {
        return Bitmap.createBitmap(arr, w, h, Bitmap.Config.ARGB_8888);
    }

    private byte[] bitmapToByteArray(Bitmap bitmap) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.WEBP, 10, stream);
        return stream.toByteArray();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
            Toast.makeText(this, "Camera permission is needed to run this application", Toast.LENGTH_LONG)
                    .show();
            if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
                // Permission denied with checking "Do not ask again".
                CameraPermissionHelper.launchPermissionSettings(this);
            }
            finish();
        }
    }

}
