//region imports
package com.nordicsemi.nrfUARTv2;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.util.Date;
import com.nordicsemi.nrfUARTv2.UartService;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
//endregion

public class MainActivity extends Activity implements RadioGroup.OnCheckedChangeListener {
    //region Modifiers
    private static final int REQUEST_SELECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;
    private static final int UART_PROFILE_READY = 10;
    public static final String TAG = "nRFUART";
    private static final int UART_PROFILE_CONNECTED = 20;
    private static final int UART_PROFILE_DISCONNECTED = 21;
    private static final int STATE_OFF = 10;
    private int mState = UART_PROFILE_DISCONNECTED;
    private UartService mService = null;
    private BluetoothDevice mDevice = null;
    private BluetoothAdapter mBtAdapter = null;
    private Button btnConnectDisconnect,btnAll;
    private TextView temperature,humidity,pressure,battery;
    private ImageView light;
    //endregion

    //region onCreate
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        mBtAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBtAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        Typeface face=Typeface.createFromAsset(getAssets(),"fonts/font.ttf");
        temperature=(TextView) findViewById(R.id.temperature);
        temperature.setTypeface(face);
        humidity=(TextView) findViewById(R.id.humidity);
        humidity.setTypeface(face);
        pressure=(TextView) findViewById(R.id.pressure);
        pressure.setTypeface(face);
        light=(ImageView) findViewById(R.id.light);
        battery=(TextView) findViewById(R.id.battery);
        battery.setTypeface(face);
        btnConnectDisconnect=(Button) findViewById(R.id.btn_select);
        btnAll=(Button) findViewById(R.id.getAll);
        service_init();

        //region Connect/Disconnect btn
        // Handle Disconnect & Connect button
        btnConnectDisconnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mBtAdapter.isEnabled()) {
                    Log.i(TAG, "onClick - BT not enabled yet");
                    Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
                }
                else {
                	if (btnConnectDisconnect.getText().equals("Connect")){
                		//Connect button pressed, open DeviceListActivity class, with popup windows that scan for devices
            			Intent newIntent = new Intent(MainActivity.this, DeviceListActivity.class);
            			startActivityForResult(newIntent, REQUEST_SELECT_DEVICE);
                        btnConnectDisconnect.setBackground(getResources().getDrawable(R.drawable.bt_connected));
        			} else {
        				//Disconnect button pressed
        				if (mDevice!=null)
        				{
        					mService.disconnect();
                            btnConnectDisconnect.setBackground(getResources().getDrawable(R.drawable.bt_connect));
        				}
        			}
                }
            }
        });
        //endregion

        //region btnAll
        btnAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String [] callVariables = {"/light /", "/temperature /", "/humidity /", "/pressure /", "/battery /"};
                byte[] value;
                try {
                    for (int i = 0; i < 5; i++) {
                        value = callVariables[i].getBytes("UTF-8");
                        mService.writeRXCharacteristic(value);
                        Thread.sleep(250);
                    }
                } catch (UnsupportedEncodingException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                } catch (InterruptedException ex) {
                    // TODO Auto-generated catch block
                    ex.printStackTrace();
                }
            }
        });
        //endregion

    }
    //endregion

    //region Service Connection
    //UART service connected/disconnected
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder rawBinder) {
        		mService = ((UartService.LocalBinder) rawBinder).getService();
        		Log.d(TAG, "onServiceConnected mService= " + mService);
        		if (!mService.initialize()) {
                    Log.e(TAG, "Unable to initialize Bluetooth");
                    finish();
                }

        }

        public void onServiceDisconnected(ComponentName classname) {
       ////     mService.disconnect(mDevice);
        		mService = null;
        }
    };
    //endregion

    //region Handler
    private Handler mHandler = new Handler() {
        @Override
        //Handler events that received from UART service 
        public void handleMessage(Message msg) {
  
        }
    };
    //endregion

    //region UART Handlers
    private final BroadcastReceiver UARTStatusChangeReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            final Intent mIntent = intent;
            //region if(Connected)
            //*********************//
            if (action.equals(UartService.ACTION_GATT_CONNECTED)) {
            	 runOnUiThread(new Runnable() {
                     public void run() {
                             Log.d(TAG, "UART_CONNECT_MSG");
                             btnConnectDisconnect.setText("Disconnect");
                             btnAll.setEnabled(true);
                         btnConnectDisconnect.setBackground(getResources().getDrawable(R.drawable.bt_connected_complete));
                             mState = UART_PROFILE_CONNECTED;
                     }
            	 });
            }
            //endregion
            //region if(Disconnected)
            //*********************//
            if (action.equals(UartService.ACTION_GATT_DISCONNECTED)) {
            	 runOnUiThread(new Runnable() {
                     public void run() {
                             Log.d(TAG, "UART_DISCONNECT_MSG");
                             btnConnectDisconnect.setText("Connect");
                             btnAll.setEnabled(false);
                             mState = UART_PROFILE_DISCONNECTED;
                             mService.close();
                             //setUiState();
                             btnConnectDisconnect.setBackground(getResources().getDrawable(R.drawable.bt_connect));
                         
                     }
                 });
            }
            //endregion
            //region if(Discovered)
            //*********************//
            if (action.equals(UartService.ACTION_GATT_SERVICES_DISCOVERED)) {
             	 mService.enableTXNotification();
            }
            //endregion
            //region if(DataAvailable)
            //*********************//
            if (action.equals(UartService.ACTION_DATA_AVAILABLE)) {
              
                 final byte[] txValue = intent.getByteArrayExtra(UartService.EXTRA_DATA);
                 runOnUiThread(new Runnable() {
                     public void run() {
                         try {
                         	String text = new String(txValue, "UTF-8");
                             int firstDigit = Integer.parseInt(text.substring(0, 1));
                             float num = Float.parseFloat(text);
                             if (firstDigit == 1){
                                 float newNum = num - 100;
                                 String newText = String.valueOf(newNum).substring(0,4);
                                 temperature.setText(newText);
                             } else if (firstDigit == 2) {
                                 float newNum = num - 200;
                                 String newText = String.valueOf(newNum).substring(0,2);
                                 humidity.setText(newText);
                             } else if (firstDigit == 3) {
                                 float newNum = Math.round(num) - 250;
                                 String a = Float.toString(newNum);
                                 int newDigit = Integer.parseInt(a.substring(0, 1));
                                 if (newDigit == 1) {
                                     String newText = String.valueOf(newNum).substring(0, 3);
                                     pressure.setText(newText);
                                 } else {
                                     String newText = String.valueOf(newNum).substring(0, 4);
                                     pressure.setText(newText);
                                 }
                             } else if (firstDigit == 4) {
                                 float newNum = num - 400;
                                 if (newNum > 3){
                                     light.setBackground(getResources().getDrawable(R.drawable.light_sunny));
                                 } else if (newNum > 1) {
                                     light.setBackground(getResources().getDrawable(R.drawable.light_cloudy));
                                 } else {
                                     light.setBackground(getResources().getDrawable(R.drawable.light_dark));
                                 }
                             } else if (firstDigit == 5) {
                                 float newNum = num - 500;
                                 String newText = String.valueOf(newNum).substring(0,4);
                                 battery.setText(newText);
                             }
                         } catch (Exception e) {
                             Log.e(TAG, e.toString());
                         }
                     }
                 });
             }
            //endregion
            //region if(UARTincompat)
            //*********************//
            if (action.equals(UartService.DEVICE_DOES_NOT_SUPPORT_UART)){
            	showMessage("Device doesn't support UART. Disconnecting");
            	mService.disconnect();
            }
            //endregion
        }
    };
    //endregion

    //region Service Intents
    private void service_init() {
        Intent bindIntent = new Intent(this, UartService.class);
        bindService(bindIntent, mServiceConnection, Context.BIND_AUTO_CREATE);

        LocalBroadcastManager.getInstance(this).registerReceiver(UARTStatusChangeReceiver, makeGattUpdateIntentFilter());
    }
    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(UartService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(UartService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(UartService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(UartService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(UartService.DEVICE_DOES_NOT_SUPPORT_UART);
        return intentFilter;
    }
    //endregion

    //region onActivities
    //region onStart
    @Override
    public void onStart() {
        super.onStart();
    }
    //endregion

    //region onDestroy
    @Override
    public void onDestroy() {
    	 super.onDestroy();
        Log.d(TAG, "onDestroy()");
        
        try {
        	LocalBroadcastManager.getInstance(this).unregisterReceiver(UARTStatusChangeReceiver);
        } catch (Exception ignore) {
            Log.e(TAG, ignore.toString());
        } 
        unbindService(mServiceConnection);
        mService.stopSelf();
        mService= null;
        android.os.Process.killProcess(android.os.Process.myPid());
    }
    //endregion

    //region onStop
    @Override
    protected void onStop() {
        Log.d(TAG, "onStop");
        super.onStop();
        finish();
    }
    //endregion

    //region onPause
    @Override
    protected void onPause() {
        Log.d(TAG, "onPause");
        super.onPause();
    }
    //endregion

    //region onRestart
    @Override
    protected void onRestart() {
        super.onRestart();
        Log.d(TAG, "onRestart");
    }
    //endregion

    //region onResume
    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        if (!mBtAdapter.isEnabled()) {
            Log.i(TAG, "onResume - BT not enabled yet");
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        }
    }
    //endregion

    //region onConfigChanged
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }
    //endregion
    //endregion

    //region BTRequest
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {

        case REQUEST_SELECT_DEVICE:
        	//When the DeviceListActivity return, with the selected device address
            if (resultCode == Activity.RESULT_OK && data != null) {
                String deviceAddress = data.getStringExtra(BluetoothDevice.EXTRA_DEVICE);
                mDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(deviceAddress);
                Log.d(TAG, "... onActivityResultdevice.address==" + mDevice + "mserviceValue" + mService);
                mService.connect(deviceAddress);
                            

            }
            break;
        case REQUEST_ENABLE_BT:
            // When the request to enable Bluetooth returns
            if (resultCode == Activity.RESULT_OK) {
                Toast.makeText(this, "Bluetooth has turned on ", Toast.LENGTH_SHORT).show();

            } else {
                // User did not enable Bluetooth or an error occurred
                Log.d(TAG, "BT not enabled");
                Toast.makeText(this, "Problem in BT Turning ON ", Toast.LENGTH_SHORT).show();
                finish();
            }
            break;
        default:
            Log.e(TAG, "wrong request code");
            break;
        }
    }
    //endregion

    //region Misc
    @Override
    public void onCheckedChanged(RadioGroup group, int checkedId) {

    }

    private void showMessage(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();

    }
    //endregion

    //region onBackPressed
    @Override
    public void onBackPressed() {
        if (mState == UART_PROFILE_CONNECTED) {
            Intent startMain = new Intent(Intent.ACTION_MAIN);
            startMain.addCategory(Intent.CATEGORY_HOME);
            startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(startMain);
            showMessage("nRFUART's running in background.\n             Disconnect to exit");
        }
        else {
            new AlertDialog.Builder(this)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setTitle(R.string.popup_title)
            .setMessage(R.string.popup_message)
            .setPositiveButton(R.string.popup_yes, new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
   	                finish();
                }
            })
            .setNegativeButton(R.string.popup_no, null)
            .show();
        }
    }
    //endregion

    //region Create Options Menu
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main_screen, menu);
        return true;
    }
    //endregion

    //region Options Menu Handling
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_restartApp) {
            recreate();
        }
        if (id == R.id.action_reset) {
            String setResetMessage = "/digital/3/1 /";
            byte[] value;
            try {
                //send data to service
                value = setResetMessage.getBytes("UTF-8");
                mService.writeRXCharacteristic(value);
            } catch (UnsupportedEncodingException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        return super.onOptionsItemSelected(item);
    }
    //endregion
}
