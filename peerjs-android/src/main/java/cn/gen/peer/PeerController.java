package cn.gen.peer;

import android.content.Context;
import android.os.Handler;
import android.test.UiThreadTest;
import android.util.Log;

import com.codebutler.android_websockets.WebSocketClient;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Created by mac on 2018/3/12.
 */

public class PeerController {

    public interface OnStatusChange {
        void onPeerStatus(int status);
    }

    public interface OnConnectListener {
        void onPeerConnect(Connection connection);
    }

    public static final int STATUS_DISCONNECTED = 0;
    public static final int STATUS_CONNECTING = 1;
    public static final int STATUS_CONNECTED = 2;

    private WebSocketClient client;
    private int status = STATUS_DISCONNECTED;

    Context context;
    Handler handler;

    OnStatusChange onStatusChange;
    OnConnectListener onConnectListener;
    BandwidthLimiter bandwidthLimiter;

    String peerId;
    ArrayList<JSONObject> messageQueue = new ArrayList<>();
    HashMap<String, ArrayList<Connection>> connections = new HashMap<>();

    String trackerHost;

    public void setOnStatusChange(OnStatusChange onStatusChange) {
        this.onStatusChange = onStatusChange;
    }

    public void setOnConnectListener(OnConnectListener onConnectListener) {
        this.onConnectListener = onConnectListener;
    }

    public int getStatus() {
        return status;
    }

    private void setStatus(int status) {
        if (this.status != status) {
            if (onStatusChange != null) {
                onStatusChange.onPeerStatus(status);
            }
            this.status = status;
        }
    }

    public String getPeerId() {
        return peerId;
    }

    TrustManager[] trustManagers = null;

    public PeerController(Context context, String trackerHost) {
        handler = new Handler();
        bandwidthLimiter = new BandwidthLimiter();
        this.context = context;
        this.trackerHost = trackerHost;
        Connection.setUp(context);
        bandwidthLimiter.start(handler);
    }

    public BandwidthLimiter getBandwidthLimiter() {
        return bandwidthLimiter;
    }

    public Handler getHandler() {
        return handler;
    }

    public Context getContext() {
        return context;
    }

    public void send(JSONObject json) {
        if (status == STATUS_CONNECTED) {
            client.send(json.toString());
        } else {
            messageQueue.add(json);
        }
    }


    private void loadId() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    URL url = new URL(trackerHost + "peerjs/peerjs/id");
                    System.out.println("-----------URL: " + url);
                    getAPI(url.toString());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    int count = 0;

    private void getAPI(String url) {
        if (count > 0)
            return;
        System.out.println("---------url2: " + url);
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(url)
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                System.out.println("--------------Lôi: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                count++;
                String string = response.body().string();
                System.out.println("--------------OKKK: " + string);
                gotId(string);
            }
        });
    }

    public void start() {
        if (status != STATUS_DISCONNECTED) return;
        setStatus(STATUS_CONNECTING);
        loadId();
    }


    private void gotId(String id) {
        String url = trackerHost;
        if (url.indexOf("https://") == 0) {
            url = url.replace("https://", "wss://");
        } else if (url.indexOf("http://") == 0) {
            url = url.replace("http://", "ws://");
        }
        url += "peerjs/peerjs?key=peerjs&id=" + id + "&token=" + Math.random();
        System.out.println("--------------url 333: " + url);
        peerId = id;
        setStatus(STATUS_CONNECTING);
        client = new WebSocketClient(URI.create(url), new WebSocketClient.Listener() {
            @Override
            public void onConnect() {
                System.out.println("------------Connect 111: " + id);
            }

            @Override
            public void onMessage(final String message) {
                System.out.println("--------------Message Connect>>>" + message);
                getHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            processMessage(new JSONObject(message));
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                });
            }


            @Override
            public void onMessage(byte[] data) {
                System.out.println("--------------Connect 222 DATA: " + data.length);
            }

            @Override
            public void onDisconnect(int code, String reason) {
                System.out.println("------------DisConnect 111: " + code + "__" + reason);
                status = STATUS_DISCONNECTED;
                getHandler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        loadId();
                    }
                }, 5000);
            }

            @Override
            public void onError(Exception error) {
                System.out.println("------------Error 111: " + error.getMessage());
                status = STATUS_DISCONNECTED;
                getHandler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        loadId();
                    }
                }, 5000);
            }
        }, null);
        client.connect();

    }

    private Connection getConnection(String peer, String clientId) {
        if (connections.containsKey(peer)) {
            ArrayList<Connection> list = connections.get(peer);
            for (Connection client : list) {
                if (clientId.equals(client.getClientId())) {
                    return client;
                }
            }
        }
        return null;
    }

    private void addConnection(Connection conn) {
        ArrayList<Connection> list = null;
        String peer = conn.getPeer(), clientId = conn.getClientId();
        if (connections.containsKey(peer)) {
            list = connections.get(peer);
        }
        if (list == null) {
            list = new ArrayList<>();
            connections.put(peer, list);
        }
        for (Connection client : list) {
            if (client.getClientId().equals(clientId)) {
                return;
            }
        }
        list.add(conn);
    }

    private void removeConnection(Connection conn) {
        String peer = conn.getPeer(), clientId = conn.getClientId();
        if (connections.containsKey(peer)) {
            ArrayList<Connection> list = connections.get(peer);
            list.remove(conn);
        }
    }

    public Connection connect(String peer) {
        Connection connection = new Connection(peer, this);
        addConnection(connection);
        connection.connect();
        return connection;
    }

    private void processMessage(JSONObject json) {
        String type = null;
        try {
            type = json.getString("type");
        } catch (JSONException e) {
        }
        JSONObject payload = null;
        try {
            payload = json.getJSONObject("payload");
        } catch (JSONException e) {
        }
        String peer = null;
        try {
            peer = json.getString("src");
        } catch (JSONException e) {
        }
        if ("OPEN".equals(type)) {
            setStatus(STATUS_CONNECTED);
            for (JSONObject msg : messageQueue) {
                send(msg);
            }
            messageQueue.clear();
        } else if ("ERROR".equals(type) || "ID-TAKEN".equals(type) || "INVALID-KEY".equals(type)) {
            status = STATUS_DISCONNECTED;
            client.disconnect();
            client = null;
        } else if ("LEAVE".equals(type)) {

        } else if ("EXPIRE".equals(type)) {

        } else if ("BEAT".equals(type)) { client.send("{\"type\": \"ECHO\"}");
        } else if ("OFFER".equals(type)) {
            try {
                String connectionId = payload.getString("connectionId");
                Connection connection = getConnection(peer, connectionId);
                if (connection != null) {
                } else {
                    connection = new Connection(peer, this, payload);
                    addConnection(connection);

                    String payloadType = payload.getString("type");
                    /*if ("media".equals(payloadType)) {

                    }else */
                    if ("data".equals(payloadType)) {
                        connection.onOffer(payload);
                    } else {
                    }
                    processConnection(connection);
                    if (onConnectListener != null) {
                        onConnectListener.onPeerConnect(connection);
                    }
                }
            } catch (JSONException e) {

            }
        } else {
            if (payload != null) {
                try {
                    String connectionId = payload.getString("connectionId");
                    Connection connection = getConnection(peer, connectionId);
                    if (connection != null) {
                        connection.onMessage(json);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private class OnCloseProcess implements Connection.OnConnectionEvent {

        private Connection connection;

        public OnCloseProcess(Connection connection) {
            this.connection = connection;
        }

        @Override
        public void onOpen(Connection conn) {

        }

        @Override
        public void onClose(Connection conn) {
            removeConnection(connection);
            connection.removeConnectionEvent(this);
        }
    }

    private void processConnection(Connection connection) {
        connection.registerConnectionEvent(new OnCloseProcess(connection));
    }

    public void destroy() {
        if (status == STATUS_CONNECTED) {
            client.disconnect();
        }
    }
}
