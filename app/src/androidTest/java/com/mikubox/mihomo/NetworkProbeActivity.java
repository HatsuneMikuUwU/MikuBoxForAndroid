package com.mikubox.mihomo;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import java.net.*;
import java.io.*;
import java.util.Arrays;

/** Runs under the test APK's separate UID, like a browser captured by the VPN. */
public class NetworkProbeActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        new Thread(() -> {
            Bundle result = new Bundle();
            try {
                for (int size : new int[]{4 * 1024, 64 * 1024, 384 * 1024}) {
                    byte[] payload = new byte[size];
                    for (int i = 0; i < payload.length; i++) payload[i] = (byte)(i % 251);
                    HttpURLConnection c = (HttpURLConnection)new URL("http://10.0.2.2:18081/echo").openConnection(Proxy.NO_PROXY);
                    try {
                        c.setConnectTimeout(8000); c.setReadTimeout(8000);
                        c.setRequestMethod("POST"); c.setDoOutput(true); c.setFixedLengthStreamingMode(payload.length);
                        try (OutputStream out = c.getOutputStream()) { out.write(payload); }
                        if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
                        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                        try (InputStream in = c.getInputStream()) {
                            byte[] chunk = new byte[16384]; int count;
                            while ((count = in.read(chunk)) != -1) bytes.write(chunk, 0, count);
                        }
                        if (!Arrays.equals(payload, bytes.toByteArray())) throw new IOException("Corrupt TUN payload");
                        result.putString("direct_" + size, "PASS");
                    } catch (Throwable e) {
                        result.putString("direct_" + size, "FAIL " + e);
                        break;
                    } finally { c.disconnect(); }
                }
                byte[] payload = new byte[384 * 1024];
                for (int i = 0; i < payload.length; i++) payload[i] = (byte)(i % 251);
                for (int port : getIntent().getBooleanExtra("echo_only", false) ? new int[]{} : new int[]{18082}) {
                    HttpURLConnection c = (HttpURLConnection)new URL("http://10.0.2.2:" + port + "/echo").openConnection(Proxy.NO_PROXY);
                    try {
                        c.setConnectTimeout(8000); c.setReadTimeout(8000);
                        c.setRequestMethod("POST"); c.setDoOutput(true); c.setFixedLengthStreamingMode(payload.length);
                        try (OutputStream out = c.getOutputStream()) { out.write(payload); }
                        if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
                        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                        try (InputStream in = c.getInputStream()) {
                            byte[] chunk = new byte[16384]; int count;
                            while ((count = in.read(chunk)) != -1) bytes.write(chunk, 0, count);
                        }
                        if (!Arrays.equals(payload, bytes.toByteArray())) throw new IOException("Corrupt TUN payload");
                        result.putString("port_" + port, "PASS 393216 bytes each direction");
                    } finally { c.disconnect(); }
                }
                if (getIntent().getBooleanExtra("remote", false)) {
                    for (String host : new String[]{"weixin.qq.com", "douyin.com", "bilibili.com"}) {
                        HttpURLConnection c = null;
                        try {
                            c = (HttpURLConnection)new URL("https://" + host + "/").openConnection(Proxy.NO_PROXY);
                            c.setConnectTimeout(10000); c.setReadTimeout(10000); c.setInstanceFollowRedirects(false);
                            result.putString(host, "TLS/HTTP " + c.getResponseCode());
                        } catch (Exception e) { result.putString(host, "FAIL " + e); }
                        finally { if (c != null) c.disconnect(); }
                    }
                }
            } catch (Throwable e) { result.putString("failure", e.toString()); }
            // Also observable when checking a killed/restarted release process,
            // where instrumentation cannot stay attached to the target UID.
            android.util.Log.i("MikuNetworkProbe", result.toString());
            sendBroadcast(new Intent("com.mikubox.mihomo.QA_NETWORK_RESULT").setPackage("com.mikubox.mihomo").putExtras(result));
            runOnUiThread(this::finish);
        }, "external-uid-probe").start();
    }
}
