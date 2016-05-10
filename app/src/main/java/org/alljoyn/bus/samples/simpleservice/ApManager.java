package org.alljoyn.bus.samples.simpleservice;

import android.content.Context;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Created by imacmovil on 02/05/16.
 */
public class ApManager {
    public static final String TAG = "ApManager";

    //check whether wifi hotspot on or off
    public static boolean isApOn(Context context) {
        WifiManager wifimanager = (WifiManager) context.getSystemService(context.WIFI_SERVICE);
        try {
            Method method = wifimanager.getClass().getDeclaredMethod("isWifiApEnabled");
            method.setAccessible(true);
            return (Boolean) method.invoke(wifimanager);
        }
        catch (Throwable ignored) {}
        return false;
    }

    // toggle wifi hotspot on or off
    public static boolean configApState(Context context) {
        WifiManager wifimanager = (WifiManager) context.getSystemService(context.WIFI_SERVICE);
//        WifiConfiguration wificonfiguration = null;
        WifiConfiguration wificonfiguration = new WifiConfiguration();
        wificonfiguration.SSID = "\"iRED ES00000_00\"";
        wificonfiguration.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
//        wificonfiguration.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
//        wificonfiguration.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
//        wificonfiguration.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
        wificonfiguration.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
//        wificonfiguration.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
//        wificonfiguration.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
//        wificonfiguration.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
//        wificonfiguration.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);

        try {
            // if WiFi is on, turn it off
            if(wifimanager.isWifiEnabled()){
                Log.d(TAG, "Apagar el WiFi");
                wifimanager.setWifiEnabled(false);
            }else{
                Log.d(TAG, "Encender el WiFi");
                wifimanager.setWifiEnabled(true);
            }
            boolean toTurnOn = !isApOn(context);
            Method method = wifimanager.getClass().getMethod("setWifiApEnabled", WifiConfiguration.class, boolean.class);
            method.invoke(wifimanager, wificonfiguration, toTurnOn);
            Log.d(TAG, "Endender el HotSpot ?" + toTurnOn);

            // volvemos a encender el WiFi
            if(!toTurnOn){
                Log.d(TAG, "Encender el WiFi");
                wifimanager.setWifiEnabled(true); // Hack para que se conecte sin problemas
                wifimanager.setWifiEnabled(false);
                wifimanager.setWifiEnabled(true);
            }
            return true;
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
}
