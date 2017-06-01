package momodupi.raspisweeper;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.support.constraint.ConstraintLayout;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Timer;
import java.util.TimerTask;

import static android.content.ContentValues.TAG;
import static android.os.SystemClock.sleep;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;


public class MainActivity extends Activity {

    TextView infotextview, spdtextview;
    Switch swpswitch, rsvswitch;
    Button accButton;
    SeekBar spdseekBar;
    MjpegView cameramjpegview;


    private Timer timer_200ms;
    private SensorManager sensorManager;
    private Sensor sensor;

    private Socket socket;
    private BufferedReader bufferedReader;
    private OutputStream outputStream;

    private String URL = "http://192.168.1.109:8080/?action=stream";
    private String sndstr;

    private int sweeper_mode = 0, reverse_act = 0, motor_mode = 0, motor_speed = 0, motor_dirc, camera_mode = 0;
    private boolean socketcloseflag = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //getActionBar().hide();
        //getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_main);

        swpswitch = (Switch) findViewById(R.id.swpswitch);
        rsvswitch = (Switch) findViewById(R.id.rvsswitch);
        accButton = (Button) findViewById(R.id.accbutton);
        spdseekBar = (SeekBar) findViewById(R.id.spdseekBar);
        infotextview = (TextView) findViewById(R.id.infotextView);
        spdtextview = (TextView) findViewById(R.id.spdtextView);

        swpswitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    socket_conncet();
                    sleep(500);
                    sweeper_mode = 1;
                    new DoRead().execute(URL);
                }else {
                    infotextview.setText(R.string.swpshutdown);
                    cameramjpegview.stopPlayback();
                    cameramjpegview.destroyDrawingCache();
                    sweeper_mode = 2;
                }
            }
        });

        rsvswitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                reverse_act = (isChecked)? 1: 0;
            }
        });

        accButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    motor_mode = 1 + reverse_act;
                }
                else if (event.getAction() == MotionEvent.ACTION_UP) {
                    motor_mode = 0;
                }
                return false;
            }
        });

        spdseekBar.setMax(80);
        spdseekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                motor_speed = seekBar.getProgress();
            }
        });

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        if (sensorManager == null) {
            infotextview.setText(R.string.sensornava);
        } else {
            infotextview.setText(R.string.sensorava);
        }


        cameramjpegview = (MjpegView) findViewById(R.id.cameramjpegView);

        timer_200ms = new Timer();
        timer_200ms.schedule(task_200ms, 1000, 200);
    }

    public class DoRead extends AsyncTask<String, Void, MjpegInputStream> {
        protected MjpegInputStream doInBackground(String... url) {
            //TODO: if camera has authentication deal with it and don't just not work
            HttpResponse res = null;
            DefaultHttpClient httpclient = new DefaultHttpClient();
            Log.d(TAG, "1. Sending http request");
            try {
                res = httpclient.execute(new HttpGet(URI.create(url[0])));
                Log.d(TAG, "2. Request finished, status = " + res.getStatusLine().getStatusCode());
                if(res.getStatusLine().getStatusCode()==401){
                    //You must turn off camera User Access Control before this will work
                    return null;
                }
                return new MjpegInputStream(res.getEntity().getContent());
            } catch (ClientProtocolException e) {
                e.printStackTrace();
                Log.d(TAG, "Request failed-ClientProtocolException", e);
                //Error connecting to camera
            } catch (IOException e) {
                e.printStackTrace();
                Log.d(TAG, "Request failed-IOException", e);
                //Error connecting to camera
            }

            return null;
        }

        protected void onPostExecute(MjpegInputStream result) {
            cameramjpegview.setSource(result);
            cameramjpegview.setDisplayMode(MjpegView.SIZE_BEST_FIT);
            cameramjpegview.showFps(true);
        }
    }


    private TimerTask task_200ms = new TimerTask() {
        @Override
        public void run() {
            Message message = new Message();
            message.what = 0xaa;
            handler.sendMessage(message);
        }
    };

    public Handler handler = new Handler() {
        public void handleMessage(Message msg) {
        if (msg.what == 0xaa) {
            sndstr = "@@";
            sndstr += "T" + String.format("%01d", sweeper_mode) + 't';
            sndstr += "M" + String.format("%01d", motor_mode) + 'm';
            sndstr += "S" + String.format("%03d", motor_speed) + 's';

            if (motor_dirc < 0)
            {
                motor_dirc *= -1;
                sndstr += "D" + "-" + String.format("%02d", motor_dirc) + 'd';
            }
            else
            {
                sndstr += "D" + "+" + String.format("%02d", motor_dirc) + 'd';
            }
            sndstr += "C" + String.format("%01d", camera_mode) + 'c';
            sndstr += "!!\0";

            if (sweeper_mode == 1) {
                infotextview.setText(sndstr);
                if (socket.isConnected()) {
                    socket_send(sndstr);
                    spdtextview.setText(String.valueOf(motor_speed));
                }
            }
            else if (sweeper_mode == 2) {
                socket_send(sndstr);
                infotextview.setText(R.string.swpshutdown);
                spdtextview.setText(String.valueOf(0));
                socketcloseflag = true;
            }
        }
    };
};

    public void socket_conncet() {
        new Thread() {
            @Override
            public void run() {
                //String rsvstr = "";
                try {
                    socket = new Socket("192.168.1.109", 8000);
                    bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    outputStream = socket.getOutputStream();

                    while (true) {
/*
                        if (bufferedReader.readLine() != null) {
                            //rsvstr += "a";
                            //rsvHandler.sendMessage(rsvHandler.obtainMessage());
                        }
*/
                        if (sweeper_mode == 2) {
                            if (socketcloseflag) {
                                bufferedReader.close();
                                outputStream.close();
                                socket.close();
                                onDestroy();
                                finish();
                            }
                        }
                    }
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }

    public void socket_send(String sndstr) {
        final String str;
        str = sndstr;

        new Thread() {
            @Override
            public void run() {
                try {
                    byte buffer[] = new byte[100];
                    buffer = str.getBytes();
                    outputStream.write(buffer, 0, str.length());
                    outputStream.flush();
                    System.out.println("send");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }

    private SensorEventListener sensorEventListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor == null) {
                return;
            }

            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                double gy = event.values[1] / 10;
                motor_dirc = (int) (Math.acos(gy) * 180 / Math.PI - 90);
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }

};

    @Override
    protected void onResume() {
        if(getRequestedOrientation()!= ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE){
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }
        super.onResume();
        sensorManager.registerListener(sensorEventListener, sensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(sensorEventListener);
        cameramjpegview.stopPlayback();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        sensorManager.unregisterListener(sensorEventListener);
        cameramjpegview.stopPlayback();
        //this.finish();
    }
}
