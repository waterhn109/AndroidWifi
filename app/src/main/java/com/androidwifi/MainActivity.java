package com.androidwifi;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.format.Formatter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MainActivity extends ListActivity {

    private static final String TAG = "MainActivity";
    private static final boolean D = true;

    private WifiManager wifiManager;
    private WifiInfo current_wifiInfo;// 当前连接的wifi
    // 扫描结果列表
    private List<ScanResult> list_scan;// wifi列表
    private ScanResult scanResult;
    private int current_wifi_index;//当前点击的wifi索引

    // 添加有图片的ListActivity
    private ArrayList<HashMap<String, Object>> hashmap_wifi_items = new ArrayList<HashMap<String, Object>>();
    ;
    private SimpleAdapter simpleAdapter;

    private ProgressDialog progressDialog;
    private EditText edt_wifi_password = null;
    private AlertDialog connect_wifi_alertdialog;

    private boolean is_connect_wifi_thread = false;
    private ConnectWifiThread connect_wifi_thread = null;

    private final String PREFERENCES_NAME = "userinfo";
    private String wifi_password = "";

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wifi_list);

        wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        openWifi();
        current_wifiInfo = wifiManager.getConnectionInfo();
        getAllNetWorkList();
        simpleAdapter = new SimpleAdapter(this, hashmap_wifi_items,
                R.layout.item_wifi_list, new String[]{"ItemTitle",
                "ItemImage"}, new int[]{R.id.wifiTextView,
                R.id.wifiImageView});
        this.setListAdapter(simpleAdapter);
    }

    Handler handler = new Handler() {
        public void handleMessage(Message msg) {
            if (D) Log.v(TAG, "msg.what = " + msg.what);
            SharedPreferences vPreferences = getSharedPreferences(
                    PREFERENCES_NAME, Activity.MODE_PRIVATE);

            SharedPreferences.Editor vEditor = vPreferences.edit();
            switch (msg.what) {
                case 0:
                    new RefreshSsidThread().start();
                    break;
                case 1:
                    new AlertDialog.Builder(MainActivity.this)
                            .setMessage("连接失败，请重新连接!")
                            .setPositiveButton("确定",
                                    new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog,
                                                            int whichButton) {
                                        }
                                    }).show();
                    break;
                case 3:
                    LayoutInflater inflater = getLayoutInflater();
                    View layout = inflater.inflate(R.layout.access_point_dialog,
                            (ViewGroup) findViewById(R.id.dialog));
                    edt_wifi_password = (EditText) layout
                            .findViewById(R.id.edtTextWifiPassword);
                    edt_wifi_password.setText(vPreferences.getString(
                            "\"" + list_scan.get(current_wifi_index).SSID + "\"", ""));

                    connect_wifi_alertdialog = new AlertDialog.Builder(MainActivity.this)
                            .setView(layout)
                            .setTitle(list_scan.get(current_wifi_index).SSID)
                            .setNegativeButton("取消", null)
                            .setPositiveButton("连接",
                                    new DialogInterface.OnClickListener() {

                                        @Override
                                        public void onClick(DialogInterface dialog,
                                                            int which) {
                                            // TODO Auto-generated method stub
                                            wifi_password = edt_wifi_password.getText()
                                                    .toString();
                                            connectionConfiguration(current_wifi_index,
                                                    edt_wifi_password.getText().toString());
                                        }
                                    }).show();
                    break;
                case 4:
                    vEditor.putString("\"" + list_scan.get(current_wifi_index).SSID + "\"", wifi_password);
                    vEditor.commit();
                    Toast.makeText(MainActivity.this, "连接成功 ip地址为：" + Formatter.formatIpAddress(current_wifiInfo.getIpAddress()), Toast.LENGTH_SHORT).show();
                    break;
                default:
                    break;
            }
            super.handleMessage(msg);
        }
    };

    public void openWifi() {
        if (!wifiManager.isWifiEnabled()) {
            wifiManager.setWifiEnabled(true);
        }
    }

    public void getAllNetWorkList() {
        // 每次点击扫描之前清空上一次的扫描结果
        // int vIntSecurity = -1;
        String vStr = "";
        hashmap_wifi_items.clear();

        wifiManager.startScan();
        // 开始扫描网络
        list_scan = wifiManager.getScanResults();
        if (list_scan != null) {
            for (int i = 0; i < list_scan.size(); i++) {
                if (D) Log.d(TAG, "listSize = " + list_scan.size());
                // 得到扫描结果
                HashMap<String, Object> vMap = new HashMap<String, Object>();
                scanResult = list_scan.get(i);
                if (0 == getSecurity(scanResult)) {
                    vStr = "NO-PASSWORD";
                } else if (1 == getSecurity(scanResult)) {
                    vStr = "WEP";
                } else if (2 == getSecurity(scanResult)) {
                    vStr = "WPA-PSK";
                }

                if (Math.abs(scanResult.level) > 100) {
                    vMap.put("ItemTitle", scanResult.SSID + "(" + vStr + ")");
                    vMap.put("ItemImage", R.drawable.wifi_full);
                } else if (Math.abs(scanResult.level) > 70) {
                    vMap.put("ItemTitle", scanResult.SSID + "(" + vStr + ")");
                    vMap.put("ItemImage", R.drawable.wifi_mid);
                } else if (Math.abs(scanResult.level) > 50) {
                    vMap.put("ItemTitle", scanResult.SSID + "(" + vStr + ")");
                    vMap.put("ItemImage", R.drawable.wifi_low);
                } else {
                    vMap.put("ItemTitle", scanResult.SSID + "(" + vStr + ")");
                    vMap.put("ItemImage", R.drawable.wifi_low);
                }
                hashmap_wifi_items.add(vMap);
            }
        }
    }

    public int getSecurity(ScanResult result) {
        if (result.capabilities.contains("WEP")) {
            return 1;
        } else if (result.capabilities.contains("PSK")) {
            return 2;
        } else if (result.capabilities.contains("EAP")) {
            return 3;
        }
        return 0;
    }

    public void refreshWifiList(View view) {
        getAllNetWorkList();
        simpleAdapter.notifyDataSetChanged();
    }

    protected void onListItemClick(ListView l, View v, int position, long id) {
        if (connect_wifi_thread != null) {
            connect_wifi_thread.cancel(true);
            is_connect_wifi_thread = false;
            if (D) Log.d(TAG, "cancel the wifi thread");
        }
        current_wifi_index = position;
        handler.sendEmptyMessage(3);
    }


    class ConnectWifiThread extends AsyncTask<String, Integer, String> {

        @Override
        protected String doInBackground(String... params) {
            // TODO Auto-generated method stub
            int index = Integer.parseInt(params[0]);
            if (index > list_scan.size()) {
                return null;
            }
            if (D) Log.d(TAG, "ssid = " + list_scan.get(index).SSID);
            if (D) Log.d(TAG, "sucure type = " + getSecurity(list_scan.get(index)));
            WifiConfiguration config = CreateWifiInfo(list_scan.get(index).SSID,
                    params[1], getSecurity(list_scan.get(index)));

            int netId = wifiManager.addNetwork(config);
            if (null != config) {
                wifiManager.enableNetwork(netId, true);
                return list_scan.get(index).SSID;
            }

            return null;
        }

        @Override
        protected void onPostExecute(String result) {
            // TODO Auto-generated method stub
            if (D) Log.d(TAG, "onPostExecute");
            if (D) Log.d(TAG, result + "***" + list_scan.get(current_wifi_index));
            if (list_scan.get(current_wifi_index).SSID.equals(result)) {
                handler.sendEmptyMessage(0);
            } else {
                handler.sendEmptyMessage(1);
            }
            super.onPostExecute(result);
        }

        public WifiConfiguration CreateWifiInfo(String SSID, String Password,
                                                int Type) {
            if (D) Log.d(TAG, "SSID = " + SSID + "password " + Password + "type ="
                    + Type);
            WifiConfiguration config = new WifiConfiguration();
            config.allowedAuthAlgorithms.clear();
            config.allowedGroupCiphers.clear();
            config.allowedKeyManagement.clear();
            config.allowedPairwiseCiphers.clear();
            config.allowedProtocols.clear();
            config.SSID = "\"" + SSID + "\"";
            if (Type == 0) {
                config.wepKeys[0] = "\"" + "\"";
                config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
                config.wepTxKeyIndex = 0;
            } else if (Type == 1) {
                config.preSharedKey = "\"" + Password + "\"";
                config.hiddenSSID = true;
                config.allowedAuthAlgorithms
                        .set(WifiConfiguration.AuthAlgorithm.SHARED);
                config.allowedGroupCiphers
                        .set(WifiConfiguration.GroupCipher.CCMP);
                config.allowedGroupCiphers
                        .set(WifiConfiguration.GroupCipher.TKIP);
                config.allowedGroupCiphers
                        .set(WifiConfiguration.GroupCipher.WEP40);
                config.allowedGroupCiphers
                        .set(WifiConfiguration.GroupCipher.WEP104);
                config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
                config.wepTxKeyIndex = 0;
            } else if (Type == 2) {
                if (D) Log.d(TAG, "into type wpa");
                config.preSharedKey = "\"" + Password + "\"";
                config.hiddenSSID = true;
                config.allowedAuthAlgorithms
                        .set(WifiConfiguration.AuthAlgorithm.OPEN);
                config.allowedGroupCiphers
                        .set(WifiConfiguration.GroupCipher.TKIP);
                config.allowedKeyManagement
                        .set(WifiConfiguration.KeyMgmt.WPA_PSK);
                config.allowedPairwiseCiphers
                        .set(WifiConfiguration.PairwiseCipher.TKIP);
                // config.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
                config.allowedGroupCiphers
                        .set(WifiConfiguration.GroupCipher.CCMP);
                config.allowedPairwiseCiphers
                        .set(WifiConfiguration.PairwiseCipher.CCMP);
                config.status = WifiConfiguration.Status.ENABLED;
            } else {
                return null;
            }
            return config;
        }

    }

    public void connectionConfiguration(int index, String passwrod) {
        progressDialog = ProgressDialog.show(MainActivity.this, "正在连接...",
                "请稍候...", true);
        connect_wifi_thread = new ConnectWifiThread();
        connect_wifi_thread.execute(index + "", passwrod);
    }

    class RefreshSsidThread extends Thread {

        @Override
        public void run() {
            // TODO Auto-generated method stub
            is_connect_wifi_thread = true;
            int i = 0;
            while (is_connect_wifi_thread) {
                if (wifiManager == null) {
                    wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
                }
                current_wifiInfo = wifiManager.getConnectionInfo();

                if (D) Log.d(TAG, "++++" + current_wifiInfo.getSSID());
                if (D) Log.d(TAG, "++++" + (list_scan.get(current_wifi_index).SSID).toString());
                if (D) Log.d(TAG, "++++" + current_wifiInfo.getIpAddress());
                if (("\"" + list_scan.get(current_wifi_index).SSID + "\"")
                        .equals(current_wifiInfo.getSSID())
                        && 0 != current_wifiInfo.getIpAddress()) {
                    if (null != progressDialog) {
                        progressDialog.dismiss();
                    }
                    handler.sendEmptyMessage(4);
                    is_connect_wifi_thread = false;
                } else if (6 == (i++)) {
                    if (null != progressDialog) {
                        progressDialog.dismiss();
                    }
                    is_connect_wifi_thread = false;
                    handler.sendEmptyMessage(1);
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    is_connect_wifi_thread = false;
                    e.printStackTrace();
                }
            }
            super.run();
        }
    }

    @Override
    protected void onResume() {
        // TODO Auto-generated method stub
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        // TODO Auto-generated method stub
        if (connect_wifi_alertdialog != null) {
            connect_wifi_alertdialog.dismiss();
        }
        super.onDestroy();
    }
}