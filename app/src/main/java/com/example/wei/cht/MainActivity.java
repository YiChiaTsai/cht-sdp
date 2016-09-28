package com.example.wei.cht;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.TrafficStats;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;
import android.os.Handler;

import com.loopj.android.http.*;

import java.text.SimpleDateFormat;
import java.util.Timer;
import java.util.TimerTask;

import cz.msebera.android.httpclient.Header;

public class MainActivity extends AppCompatActivity implements SensorEventListener {
//comment
    private TextView RX;
    private TextView TX;
    private Timer timer;

    private long mRX = 0;   // 紀錄上次的RX
    private long mTX = 0;   //紀錄上次的TX
    private long curRX;
    private long curTX;

    private boolean startSet = false;
    private int recordInterval = 24;    //1天分幾時段(預設一天24次)
    private static final int FLOW_UPDATE = 1;

    private String[] intervalAry = new String[] {"24", "12", "8", "6", "4", "2", "1"};
    private ArrayAdapter<String> adapter;
    private Spinner sp;
    //private SensorManager mSensorManager;
    //private Sensor mAccelerometer;
    private ToggleButton toggle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        RX = (TextView)findViewById(R.id.RX);   //螢幕上顯示RX的位置
        TX = (TextView)findViewById(R.id.TX);   //螢幕上顯示TX的位置

        sp = (Spinner)findViewById(R.id.SelectInterval);    //下拉選單
        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, intervalAry);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(adapter);
        sp.setOnItemSelectedListener(selectListener);   // 要指定listener讓按鈕知道有觸發時要做什麼

        toggle = (ToggleButton) findViewById(R.id.toggleButton);    //toggle button(按一次開啟，再按一次關閉那種)
        toggle.setChecked(false);   // 一開始為關閉
        toggle.setOnCheckedChangeListener(toggleListener);
    }

    /* For sensor，這裡沒用到 */
    @Override
    public final void onAccuracyChanged(Sensor sensor, int accuracy) {
        // do nothing
    }
    /* For sensor，這裡沒用到 */
    @Override
    public final void onSensorChanged(SensorEvent event) {
        RequestParams params = new RequestParams();
        TextView[] values = new TextView[3];
        /*values[0] = (TextView)findViewById(R.id.X_value);
        values[1] = (TextView)findViewById(R.id.Y_value);
        values[2] = (TextView)findViewById(R.id.Z_value);
        for(int i = 0; i < 3; i++) {
            values[i].setText(String.valueOf(event.values[i]));
        }*/
        params.put("X_Value", event.values[0]);
        params.put("Y_Value", event.values[1]);
        params.put("Z_Value", event.values[2]);
        invokeWS(params);
    }

    /* 呼叫webservice(沒用到) */
    public void invokeWS(RequestParams params) {
        AsyncHttpClient client =  new AsyncHttpClient();
        client.get("http://140.113.86.133:8080/TestREST/rest/hello/CHT", params, new AsyncHttpResponseHandler() {
            @Override
            public void onSuccess(int i, Header[] headers, byte[] bytes) {
                CharSequence cs = new String(bytes);
                Toast toast = Toast.makeText(getApplicationContext(), cs, Toast.LENGTH_SHORT);
                toast.show();
            }

            @Override
            public void onFailure(int i, Header[] headers, byte[] bytes, Throwable throwable) {
                Log.e("InvokeWS", Integer.toString(i));
                if (bytes != null) {
                    CharSequence cs = new String(bytes);
                    Toast toast = Toast.makeText(getApplicationContext(), cs, Toast.LENGTH_SHORT);
                    toast.show();
                }
            }
        });
    }
    /* 收到timer送來的msg時，處理自訂的工作(這邊就是去更西畫面) */
    private Handler handler = new Handler() {
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch(msg.what) {
                case 1:
                    SimpleDateFormat sDateFormate = new SimpleDateFormat("hh:mm:ss");   //取系統時間
                    String date = sDateFormate.format(new java.util.Date());
                    RX.append("\n" + date + " - " + Long.toString((curRX - mRX)/1024) + "KB");  //注意! 是兩時間點相減才是區間的流量
                    TX.append("\n" + date + " - " + Long.toString((curTX - mTX)/1024) + "KB");
                    mRX = curRX;
                    mTX = curTX;
                    break;
            }
        }
    };

    /* TimerTask會根據所設定的interval定時完成任務 */
    private TimerTask task = new TimerTask() {
        @Override
        public void run() {
            if (startSet) {
                curRX = TrafficStats.getMobileRxBytes();    // 跟系統要"到目前為止"所記錄的流量
                curTX = TrafficStats.getMobileTxBytes();
                Message msg = new Message();
                msg.what = FLOW_UPDATE; // 指定訊息種類
                handler.sendMessage(msg);   // 送出訊息通知handler工作
            }
        }
    };
    /* 下拉選單的處理 */
    private OnItemSelectedListener selectListener = new AdapterView.OnItemSelectedListener() {
        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            int seg = 24 / Integer.parseInt(intervalAry[position]); // 先去計算一天要更新幾次，再算每次interval幾秒
            recordInterval = seg * 3600 * 1000;     // 3600 seconds in a hour and converted to microsec
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {

        }
    };
    /* toggle button 處理 */
    private CompoundButton.OnCheckedChangeListener toggleListener = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if(isChecked) { // 開啟
                startSet = true;
                mRX = TrafficStats.getMobileRxBytes();  // 最初始的流量(是系統一直以來記錄的)
                mTX = TrafficStats.getMobileTxBytes();  // RX是receieve  TX是transmit

                if(mRX == TrafficStats.UNSUPPORTED || mTX == TrafficStats.UNSUPPORTED) {    //看SDK有沒有支援
                    Toast toast = Toast.makeText(getApplicationContext(), "TrafficStats is not supported!", Toast.LENGTH_SHORT);
                    toast.show();
                }
                timer = new Timer();    //  設定一個timer，告訴系統多久幫你執行一次
                //timer.schedule(task, 0, 60000);

    /* 下面四個都是創建project會預設有的function，我沒有做修改 */
                @Override
                public boolean onCreateOptionsMenu(Menu menu) {
                    // Inflate the menu; this adds items to the action bar if it is present.
                    getMenuInflater().inflate(R.menu.menu_main, menu);
                    return true;
                }

                @Override
                public boolean onOptionsItemSelected(MenuItem item) {
                    // Handle action bar item clicks here. The action bar will
                    // automatically handle clicks on the Home/Up button, so long
                    // as you specify a parent activity in AndroidManifest.xml.
                    int id = item.getItemId();

                    //noinspection SimplifiableIfStatement
                    if (id == R.id.action_settings) {
                timer.schedule(task, 0, recordInterval);
            } else {    //這邊有個bug，按一下開始記錄，再按一下變暫停，再按開啟一次就會crush，問題應該是出在這(console 大概提到說 Timertask的問題，先麻煩你們看看了)
                startSet = false;
                timer.cancel();
            }
        }
    };
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }
}
