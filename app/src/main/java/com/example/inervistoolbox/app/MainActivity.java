package com.example.inervistoolbox.app;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.text.SimpleDateFormat;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.Message;
import android.os.Environment;
import android.os.Bundle;
import android.os.Handler;

import android.graphics.PixelFormat;
import android.support.v7.app.ActionBarActivity;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.view.View;
import android.util.Log;
import android.widget.TextView;
import android.widget.Button;
import android.widget.FrameLayout;


public class MainActivity extends ActionBarActivity {

    private final int COUNT_DOWN_NUM = 8;

    private Camera mCamera;
    private CameraPreview mPreview;
    private SensorMonitor acc;

    // ---------- 参数设置 ----------

    public static final int maxImageNum      = 30;      // 最多采集图像数量
//    public static final int captureFPS       = 1000;    // 每多少秒采集一张图片
    public static final int sensorCaptureFPS = 50;      // 每多少秒采集一次传感器数据

    private int imgWidth  = 640;
    private int imgHeight = 480;

    // ---------- 布尔变量 ----------

    public boolean isCapturing          = false;    // 记录相机是否在进行采集
    public boolean isSensorCapturing    = false;    // 记录传感器是否在进行采集
    public boolean isPreviewing         = false;     // 记录是否在进行预览

    // ---------- 显示元素 ----------

    private Button buttonCapture, buttonStop;
    public TextView accView;
    public TextView countDownView;
    public TextView cameraStatus;
    FrameLayout preview;

    // ------- 句柄以及定时器 -------

    private Handler handler;
    private Timer timerCaptureFPS;      // 控制采集帧率定时器
    private Timer timerStop;            // 控制停止定时器
    private Timer timerCountDown;       // 倒计时计时器
    public  Timer timerSensorCapture;   // 控制传感器采集帧率定时器

    // ------- 计数器与时间戳 -------

    public static int imageNum = 1;
    public int countDownNum = COUNT_DOWN_NUM;
    private long timestamp;
    private long startTimestamp;
    static private long cameraCaptureStartTimestamp;
    static private long cameraCaptureFinishTimestamp;
    static private long cameraCaptureTimestamp;
    private long onShutterTimestamp;

    // ------- 文件 ----------------

    private File imageInfoData;
    private FileOutputStream imgInfoFOS;
    private FileOutputStream sensorFOS;
    private static File dataDir;

    // ------- 其他变量 --------------
    private static final String TAG = "MyActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        buttonCapture = (Button) findViewById(R.id.button_capture);
        buttonStop = (Button) findViewById(R.id.button_stop);

        accView = (TextView) findViewById(R.id.acc_xcoor);
        countDownView = (TextView) findViewById(R.id.count_down);
        cameraStatus = (TextView) findViewById(R.id.camera_status);

        preview = (FrameLayout) findViewById(R.id.camera_preview);

        // Create的时候，首先让stop button不可用
        buttonStop.setEnabled(false);

        buttonCapture.setOnClickListener(new OnButtonClick());
        buttonStop.setOnClickListener(new OnButtonClick());

        handler = new Handler() {

            @Override
            public void handleMessage(Message msg) {
                switch(msg.what)
                {
                    case 0x122: // capture ONE frame of accelerometer data
                        acc.saveSensorData(sensorFOS);
                        break;
                    case 0x123: //  countDownNum--
                        countDownNum  -= 1;
                        countDownView.setText(countDownNum + " second to capture");
                        break;
                    case 0x124: // imageNum > maxImageNum, stop
                        stop();
                        break;
                    case 0x125: // countDownNum == 0, stop sensor_saving, reset countDownNum
                        //  ------- stop sensor saving -----------
                        try{
                            sensorFOS.close();
                        } catch (IOException e){
                            e.printStackTrace();
                        }
                        timerSensorCapture.cancel();
                        countDownView.setText("Stop sensor saving");
                        // ---------------------------------------
                        imageNum += 1;
                        countDownNum = COUNT_DOWN_NUM;
                        break;
                    case 0x126: // countDownNum == 1, capture image
                        takeOneShot();
                        countDownNum  -= 1;
                        countDownView.setText(countDownNum + " second to capture");
                        break;
                    case 0x127: // countDownNum == 2, start sensor_saving
                        countDownView.setText("Start sensor saving");
                        File sensorFile;
                        sensorFile = getSensorFile();
                        try{
                            sensorFOS = new FileOutputStream(sensorFile);
                        } catch (FileNotFoundException e){
                            e.printStackTrace();
                        }

                        timerSensorCapture = new Timer();
                        timerSensorCapture.schedule(new sensorCaptureThread(),new Date(),sensorCaptureFPS);
                        countDownNum  -= 1;
                        break;
                    case 0x128:  // CountNum == COUNT_DOWN_NUM, start preview
                        if (mCamera != null && !isPreviewing) {
                            try
                            {
                                mCamera.startPreview();
                            } catch (Exception e){
                                Log.d(TAG, "Error starting camera preview: " + e.getMessage());
                            }
                            isPreviewing = true;
                        }
                        countDownNum  -= 1;
                        countDownView.setText(countDownNum + " second to capture");
                        break;
                }
            }
        };
    }

    public class OnButtonClick implements View.OnClickListener {

        @Override
        public void onClick(View v){
            switch (v.getId())
            {
                case R.id.button_capture:

                    init();

                    startTimestamp = System.currentTimeMillis();

                    timerStop = new Timer();
                    timerStop.schedule(new stopThread(),new Date(),500);

                    timerCountDown = new Timer();
                    timerCountDown.schedule(new countDownThread(),1000,1000);

//                    timerCaptureFPS = new Timer();
//                    timerCaptureFPS.schedule(new captureThread(), 1000, captureFPS);

                    acc = new SensorMonitor(v.getContext(),accView,startTimestamp,dataDir);
//                    acc.timerSensorCapture =
                    break;

                case R.id.button_stop:

                    stop();
                    isSensorCapturing = false;
                    acc.releaseSensor();

                    break;
            }
        }

    }

    class captureThread extends TimerTask{
        @Override
        public void run() {
            handler.sendEmptyMessage(0x123);
        }
    }

    class sensorCaptureThread extends TimerTask{
        @Override
        public void run() {handler.sendEmptyMessage(0x122);}
    }

    class stopThread extends TimerTask{
        @Override
        public void run(){

            if(imageNum > maxImageNum){
                handler.sendEmptyMessage(0x124);
                this.cancel();
            }
        }
    }

    class countDownThread extends TimerTask{
        @Override
        public void run(){
            if (countDownNum == COUNT_DOWN_NUM) {
                handler.sendEmptyMessage(0x128);
            }
            else if (countDownNum == 2){
                handler.sendEmptyMessage(0x127);
            }
            else if (countDownNum == 1){
                handler.sendEmptyMessage(0x126);
            }
            else if (countDownNum == 0){
                handler.sendEmptyMessage(0x125);
            }
            else {
                handler.sendEmptyMessage(0x123);
            }
        }
    }

    private void init(){

        mCamera = getCameraInstance();

        // --------- Config Camera --------
        Camera.Parameters parameters = mCamera.getParameters();

        parameters.setPictureFormat(PixelFormat.JPEG);
        parameters.setPictureSize(imgWidth, imgHeight);
        parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_FIXED);

        mCamera.setParameters(parameters);

        // ------------------

        createDataDir();

        imageInfoData = getImgInfoFile();

        try{
            imgInfoFOS = new FileOutputStream(imageInfoData);
        } catch (FileNotFoundException e){
            e.printStackTrace();
        }

        cameraStatus.setText("Camera Status : OK");

        mPreview = new CameraPreview(this, mCamera);
        preview.addView(mPreview);

        buttonStop.setEnabled(true);

    }

    Camera.ShutterCallback mShutterCallback = new Camera.ShutterCallback() {
        @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
        @Override
        public void onShutter() {
            mCamera.enableShutterSound(true);
            onShutterTimestamp = System.nanoTime();
        }
    };

    private static void createDataDir(){

        String dataFolder = new SimpleDateFormat("yyyyMMdd_HHmm").format(new Date());
        dataDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), dataFolder);

        if (!dataDir.exists()){
            if (!dataDir.mkdirs()){
                Log.d("MyCameraApp", "failed to create image directory");
            }
        }
    }

    private void takeOneShot() {

        buttonCapture.setEnabled(false);

//        cameraCaptureStartTimestamp = System.currentTimeMillis() - startTimestamp;
        cameraCaptureStartTimestamp = System.nanoTime();

        if (mCamera != null && !isCapturing && isPreviewing)
        {

            isCapturing = true;
            isPreviewing = false;
            mCamera.takePicture(mShutterCallback, null, mPicture);

        }

        isCapturing = false;

//        cameraCaptureFinishTimestamp = System.currentTimeMillis() - startTimestamp;
        cameraCaptureFinishTimestamp = System.nanoTime();

        long dT = cameraCaptureFinishTimestamp - cameraCaptureStartTimestamp;

        cameraStatus.setText("Camera Status : Image No." + imageNum );

        try{
            imgInfoFOS.write((imageNum + " " + cameraCaptureStartTimestamp + " " + onShutterTimestamp + " " + cameraCaptureFinishTimestamp + "\n").getBytes());
            imgInfoFOS.flush();
        } catch (IOException e){
            e.printStackTrace();
        }

    }

    private void stop() {

        try{
            imgInfoFOS.close();
        } catch (IOException e){
            e.printStackTrace();
        }

        releaseCamera();
        cameraStatus.setText("End");
        acc.releaseSensor();
        timerCountDown.cancel();

    }

    @Override
    protected void onPause() {
        super.onPause();

        if (isSensorCapturing){
            acc.releaseSensor();
        }

        releaseCamera();              // release the camera immediately on pause event
    }

    public static Camera getCameraInstance(){
        Camera c = null;
        try {
            c = Camera.open(); // attempt to get a Camera instance
        }
        catch (Exception e){
            // Camera is not available (in use or does not exist)
        }
        return c; // returns null if camera is unavailable
    }

    private PictureCallback mPicture = new PictureCallback() {

        @Override
        public void onPictureTaken(byte[] data, Camera camera) {

            File pictureFile = getOutputMediaFile( );

            if (pictureFile == null){
                Log.d(TAG, "Error creating media file, check storage permissions: " );
                return;
            }

            try {
                FileOutputStream fos = new FileOutputStream(pictureFile);
                fos.write(data);
                fos.close();
            } catch (FileNotFoundException e) {
                Log.d(TAG, "File not found: " + e.getMessage());
            } catch (IOException e) {
                Log.d(TAG, "Error accessing file: " + e.getMessage());
            }
        }
    };



    /** Create a File for saving an image or video */
    private static File getOutputMediaFile( ){

        // Create a media file name
//        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmssSSS").format(new Date());
//        String fileName = String.valueOf(cameraCaptureStartTimestamp);
        String fileName = String.format("%03d",imageNum);

        File mediaFile;

        mediaFile = new File(dataDir.getPath() + File.separator + "data_" + fileName + ".jpg");

        return mediaFile;
    }


    private void releaseCamera(){
        if (mCamera != null){
            mCamera.stopPreview();
            mCamera.release();        // release the camera for other applications
            mCamera = null;
        }
    }

    private static File getImgInfoFile(){

        File imageInfoFile;

        imageInfoFile = new File(dataDir.getPath() + File.separator +
                "ImgInfo" + ".txt");

        return imageInfoFile;
    }

    private static File getSensorFile()
    {
        String fileName = String.format("%03d",imageNum);
        File sensorFile;
        sensorFile = new File(dataDir.getPath() + File.separator + "data_imu_" + fileName + ".txt");
        return sensorFile;
    }

    private void sensorCapture(FileOutputStream fos){
        File sensorFile;
        sensorFile = getSensorFile();

        try{
            fos = new FileOutputStream(sensorFile);
        } catch (FileNotFoundException e){
            e.printStackTrace();
        }

        timerSensorCapture = new Timer();
        timerSensorCapture.schedule(new sensorCaptureThread(),sensorCaptureFPS);
    }

//    private void sensorCaptureStop(FileOutputStream fos){
//        try{
//            fos.close();
//        } catch (IOException e){
//            e.printStackTrace();
//        }
//    }







}
