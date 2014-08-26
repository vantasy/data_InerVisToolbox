package com.example.inervistoolbox.app;

import android.annotation.TargetApi;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by vanta_000 on 2014/8/22.
 */
public class SensorMonitor implements SensorEventListener {

    private Context context;

    private SensorManager mSensorManager;
    private Sensor mAccelerometer;

    private TextView accText;

    // 定义时间戳
    private long startTimestamp     = 0l;   //  MainActivity里 init()结束后的系统时间
    // 下面几个时间戳均减去了startTimestamp
    private long accTimestamp       = 0l;   //  每次onSensorChanged()且Event == ACC时的系统时间
    private long logTimestamp       = 0l;   //  每次往TXT文件中写数据的时间

    // 定义计数器
    private int count = 1;

//    // 控制传感器帧率定时器
//    public Timer timerSensorCapture;
//    public Handler sensorHandler;

    private float[] accOutput                   = new float[3];     // 加速度计变量

    // 文件
    private static File sensorDir = null;
    private FileOutputStream sensorFOS;
    private File sensorData;


    public SensorMonitor(Context context, TextView accText, long startTimestamp, File dataDir) { //, Handler mHandler, Runnable mUpdateTimeTask

        this.context  = context;
        this.accText  = accText;
        this.startTimestamp = startTimestamp;
        this.sensorDir = dataDir;

        initSensor();

//        sensorHandler = new Handler() {
//
//            @Override
//            public void handleMessage(Message msg) {
//                if(msg.what == 0x122){
//                    saveSensorData();
//                }
//            }
//        };
    }

    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
    public void initSensor(){

        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);

        mAccelerometer      = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        mSensorManager.registerListener(this, mAccelerometer , SensorManager.SENSOR_DELAY_FASTEST);

//        timerSensorCapture = new Timer();
//        timerSensorCapture.schedule(new sensorCaptureThread(), 500, MainActivity.sensorCaptureFPS);

        // 如果没有从MainActivity传来startTimestamp,在此初始化
        if (startTimestamp == 0){
            startTimestamp = System.currentTimeMillis();
        }
    }

//    class sensorCaptureThread extends TimerTask {
//        @Override
//        public void run() {
//            sensorHandler.sendEmptyMessage(0x122);
//        }
//    }

    public void releaseSensor(){

        //TODO: 需要对文件fos关闭
        mSensorManager.unregisterListener(this);
        Toast.makeText(context, "Sensor Stopped..", Toast.LENGTH_SHORT).show();
//        timerSensorCapture.cancel();
    }

    public void saveSensorData(FileOutputStream sensorFOS){

       accText.setText("Sensor # " + count + "\n" +  accOutput[0] + " " + accOutput[1] + " " + accOutput[2]);
        try{
            sensorFOS.write( (accOutput[0] + " " + accOutput[1] + " " + accOutput[2] + "\n").getBytes() );
            sensorFOS.flush();

        } catch (IOException e){
            e.printStackTrace();
        }
    }

    public void stopSaveSensorData(FileOutputStream sensorFOS) {
        try{
            sensorFOS.close();
        } catch (IOException e){
            e.printStackTrace();
        }
    }

    public static File getSensorFile(File dataDir, String fileName){
        File sensorFile;
        sensorFile = new File( dataDir.getPath() + File.separator + "data_imu_" + fileName + ".txt");
        return sensorFile;
    }

    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
    @Override
    public void onSensorChanged(SensorEvent event){

        if(event.sensor.getType()==Sensor.TYPE_ACCELEROMETER)
        {
//          accTimestamp = System.currentTimeMillis() - startTimestamp;
            accTimestamp = System.nanoTime(); // Kalibr needs timestamp in nanosecond
            accOutput = event.values.clone();

        }



    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // can be safely ignored for this demo
    }

}
