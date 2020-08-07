package com.android.tvremoteime.server;


import android.content.Context;
import android.net.wifi.WifiManager;
import android.util.Log;

import com.android.tvremoteime.Environment;
import com.android.tvremoteime.IMEService;
import com.android.tvremoteime.R;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import fi.iki.elonen.*;

/**
 * Created by kingt on 2018/1/7.
 */

public class RemoteServer extends NanoHTTPD
{
    public interface DataReceiver{
        /**
         *
         * @param keyCode
         * @param keyAction : 0 = keypressed, 1 = keydown, 2 = keyup
         */
        void onKeyEventReceived(String keyCode, int keyAction);

        /**
         *
         * @param text
         */
        void onTextReceived(String text);
    }

    public static int serverPort = 9978;
    private boolean isStarted = false;
    private DataReceiver mDataReceiver = null;
    private Context mContext;
    private final RemoteServerFileManager.Factory fileManagerFactory = new RemoteServerFileManager.Factory();
    private final ArrayList<RequestProcessor> getRequestProcessors = new ArrayList<>();
    private final ArrayList<RequestProcessor> postRequestProcessors = new ArrayList<>();

    public void setDataReceiver(DataReceiver receiver){
        mDataReceiver = receiver;
    }
    public DataReceiver getDataReceiver(){
        return mDataReceiver;
    }
    public boolean isStarting(){
        return isStarted;
    }

    public RemoteServer(int port, Context context) {
        super(port);
        mContext = context;
        this.addGetRequestProcessers();
        this.addPostRequestProcessers();
    }

    @Override
    public void start(int timeout, boolean daemon) throws IOException {
        isStarted = true;
        setTempFileManagerFactory(fileManagerFactory);
        super.start(timeout, daemon);
    }

    @Override
    public void stop() {
        super.stop();
        isStarted = false;
    }

    public static String getLocalIPAddress(Context context){
        WifiManager wifiManager = (WifiManager)context.getSystemService(Context.WIFI_SERVICE);
        int ipAddress = wifiManager.getConnectionInfo().getIpAddress();
        if(ipAddress == 0){
            try {
                Enumeration<NetworkInterface> enumerationNi = NetworkInterface.getNetworkInterfaces();
                while (enumerationNi.hasMoreElements()) {
                    NetworkInterface networkInterface = enumerationNi.nextElement();
                    String interfaceName = networkInterface.getDisplayName();
                    if (interfaceName.equals("eth0") || interfaceName.equals("wlan0")) {
                        Enumeration<InetAddress> enumIpAddr = networkInterface.getInetAddresses();

                        while (enumIpAddr.hasMoreElements()) {
                            InetAddress inetAddress = enumIpAddr.nextElement();
                            if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                                return inetAddress.getHostAddress();
                            }
                        }
                    }
                }
            } catch (SocketException e) {
                Log.e(IMEService.TAG, "获取本地IP出错", e);
            }
        }else {
            return String.format("%d.%d.%d.%d", (ipAddress & 0xff), (ipAddress >> 8 & 0xff), (ipAddress >> 16 & 0xff), (ipAddress >> 24 & 0xff));
        }
        return "0.0.0.0";
    }

    public String getServerAddress() {
        return getServerAddress(mContext);
    }
    public static String getServerAddress(Context context){
        String ipAddress = getLocalIPAddress(context);
        if(Environment.needDebug) {
            Environment.debug(IMEService.TAG, "ip-address:" + ipAddress);
        }
        return "http://" + ipAddress + ":" + RemoteServer.serverPort + "/";
    }

    public static Response createPlainTextResponse(Response.IStatus status, String text){
        return newFixedLengthResponse(status, NanoHTTPD.MIME_PLAINTEXT, text);
    }

    public static Response createJSONResponse(Response.IStatus status, String text){
        return newFixedLengthResponse(status, "application/json", text);
    }

    private void addGetRequestProcessers(){
        this.getRequestProcessors.add(new RawRequestProcessor(this.mContext, "/index.html", R.raw.index, NanoHTTPD.MIME_HTML));
        this.getRequestProcessors.add(new RawRequestProcessor(this.mContext, "/style.css", R.raw.style, "text/css"));
        this.getRequestProcessors.add(new RawRequestProcessor(this.mContext, "/jquery_min.js", R.raw.jquery_min, "application/x-javascript"));
        this.getRequestProcessors.add(new RawRequestProcessor(this.mContext, "/ime_core.js", R.raw.ime_core, "application/x-javascript"));
        this.getRequestProcessors.add(new RawRequestProcessor(this.mContext, "/keys.png", R.raw.keys, "image/png"));
        this.getRequestProcessors.add(new RawRequestProcessor(this.mContext, "/ic_dl_folder.png", R.raw.ic_dl_folder, "image/png"));
        this.getRequestProcessors.add(new RawRequestProcessor(this.mContext, "/ic_dl_other.png", R.raw.ic_dl_other, "image/png"));
        this.getRequestProcessors.add(new RawRequestProcessor(this.mContext, "/ic_dl_video.png", R.raw.ic_dl_video, "image/png"));
        this.getRequestProcessors.add(new RawRequestProcessor(this.mContext, "/favicon.ico", R.drawable.ic_launcher, "image/x-icon"));
        this.getRequestProcessors.add(new FileRequestProcessor(this.mContext));
        this.getRequestProcessors.add(new AppIconRequestProcessor(this.mContext));
        this.getRequestProcessors.add(new TVRequestProcessor(this.mContext));
        this.getRequestProcessors.add(new OtherGetRequestProcessor(this.mContext));
    }
    private void addPostRequestProcessers(){
        this.postRequestProcessors.add(new InputRequestProcessor(this.mContext, this));
        this.postRequestProcessors.add(new UploadRequestProcessor(this.mContext));
        this.postRequestProcessors.add(new AppRequestProcessor(this.mContext));
        this.postRequestProcessors.add(new PlayRequestProcessor(this.mContext));
        this.postRequestProcessors.add(new FileRequestProcessor(this.mContext));
        this.postRequestProcessors.add(new TVRequestProcessor(this.mContext));
        this.postRequestProcessors.add(new TorrentRequestProcessor(this.mContext));
        this.postRequestProcessors.add(new OtherPostRequestProcessor(this.mContext));
    }


    @Override
    public Response serve(IHTTPSession session) {
        Log.i(IMEService.TAG, "接收到HTTP请求：" + session.getMethod() + " " + session.getUri());
        if(!session.getUri().isEmpty()) {
            String fileName = session.getUri().trim();
            if (fileName.indexOf('?') >= 0) {
                fileName = fileName.substring(0, fileName.indexOf('?'));
            }
            if (session.getMethod() == Method.GET) {
                for(RequestProcessor processer : this.getRequestProcessors){
                    if(processer.isRequest(session, fileName)){
                        return processer.doResponse(session, fileName, session.getParms(), null);
                    }
                }
            } else if (session.getMethod() == Method.POST) {
                Map<String, String> files = new HashMap<>();
                try {
                    session.parseBody(files);
                } catch (IOException ioex) {
                    return createPlainTextResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR,  "SERVER INTERNAL ERROR: IOException: " + ioex.getMessage());
                } catch (NanoHTTPD.ResponseException rex) {
                    return createPlainTextResponse(rex.getStatus(),  rex.getMessage());
                }
                for(RequestProcessor processer : this.postRequestProcessors){
                    if(processer.isRequest(session, fileName)){
                        return processer.doResponse(session, fileName, session.getParms(), files);
                    }
                }
            }
        }
        //default page: index.html
        return this.getRequestProcessors.get(0).doResponse(session, "", null, null);
    }
}
