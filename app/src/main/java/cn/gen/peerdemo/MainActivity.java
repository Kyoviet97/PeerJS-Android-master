package cn.gen.peerdemo;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;


import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;

import cn.gen.peer.Connection;
import cn.gen.peer.PackData;
import cn.gen.peer.PeerController;

public class MainActivity extends Activity implements Connection.OnConnectionEvent, Connection.OnMessage, SurfaceHolder.Callback {

    android.hardware.Camera camera;
    SurfaceView surfaceView;
    SurfaceHolder surfaceHolder;
    android.hardware.Camera.PictureCallback rawCallback;
    android.hardware.Camera.ShutterCallback shutterCallback;

    FileOutputStream outputStream = null;

    EditText serverEdit;
    Button serverButton;
    EditText peerEdit;
    Button peerButton;
    Button liveCam;
    EditText dataEdit;
    Button sendButton;
    TextView output;
    String serverUrl;

    PeerController peerController;
    Connection connection;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        init();
        setOnClick();
    }

    private void init() {
        serverEdit = findViewById(R.id.server_url);
        serverButton = findViewById(R.id.server_button);
        liveCam = findViewById(R.id.btn_livecam);
        peerEdit = findViewById(R.id.peer_id);
        peerButton = findViewById(R.id.peer_button);
        dataEdit = findViewById(R.id.data_content);
        sendButton = findViewById(R.id.send_button);
        surfaceView = (SurfaceView) findViewById(R.id.cameraView);
        output = findViewById(R.id.output);
        serverUrl = "https://peer.metruelife.com:5443/";
        surfaceView = (SurfaceView)findViewById(R.id.cameraView);
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);
        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

    }


    private void start_camera() {
        try{
            camera = Camera.open(Camera.CameraInfo.CAMERA_FACING_FRONT);
            camera.setDisplayOrientation(90);
        }catch(RuntimeException e){
            System.out.println("=====================> LOI" + e);
            return;
        }
        Camera.Parameters param;
        param = camera.getParameters();
        camera.setParameters(param);
        try {
            camera.setPreviewDisplay(surfaceHolder);
            camera.startPreview();

            //camera.takePicture(shutter, raw, jpeg)
        } catch (Exception e) {
            System.out.println("=====================> LOI" + e);
            return;
        }
    }


    private byte[] getByte(){
        Bitmap bitmap = BitmapFactory.decodeResource(getApplicationContext().getResources(), R.drawable.download);
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        byte[] byteArray = stream.toByteArray();
        bitmap.recycle();
        return byteArray;
    }


    private void setOnClick() {

        liveCam.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                PackData data = new PackData();
                try {
                    data.put("video", getByte());
                } catch (PackData.PackException e) {
                    e.printStackTrace();
                }
            }
        });

        serverButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                System.out.println("-----------------111111");
//                final String serverUrl = serverEdit.getText().toString().trim();
                if (serverUrl.length() > 0) {
                    peerController = new PeerController(MainActivity.this, serverUrl);
                    peerController.start();
                    peerController.setOnStatusChange(new PeerController.OnStatusChange() {
                        @Override
                        public void onPeerStatus(int status) {
                            System.out.println("-----------------22222222: " + status);
                            switch (status) {
                                case PeerController.STATUS_CONNECTED: {
                                    log("-----------Server connected");

                                    break;
                                }
                                case PeerController.STATUS_CONNECTING: {
                                    log("-----------Connecting to " + serverUrl + " ...");
                                    break;
                                }
                                case PeerController.STATUS_DISCONNECTED: {
                                    log("-----------Server disconnected");
                                    break;
                                }
                            }
                        }
                    });
                } else {
                    log("-----------Error: Server is empty!");
                }
            }
        });

        peerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
//                String peerId = peerEdit.getText().toString().trim();
                String peerId = peerEdit.getText().toString();
                if (peerId.length() == 0) {
                    log("Error: Peer ID is empty!");
                } else if (peerController == null) {
                    log("Error: must connect to server first!");
                } else {
                    Connection conn = peerController.connect(peerId);
                    conn.registerConnectionEvent(MainActivity.this);
                    conn.registerOnMessage(MainActivity.this);
                    log("Connecting to " + peerId + " ...");
                }
            }
        });

        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
//                start_camera();
                String text = dataEdit.getText().toString().trim();
                if (text.length() == 0) {
                    System.out.println("================> Error: Data text is empty!" );
                } else if (connection == null) {
                    System.out.println("================> Error: not connect to any peer");
                } else {
                    PackData pd = new PackData();
                    try {
                        pd.put("text", text);
                        pd.put("from", "luong");
                    } catch (PackData.PackException e) {
                        e.printStackTrace();
                    }
                    connection.send(pd);
                    log("Sent to " + connection.getPeer() + " (" + text + ")");
                }
            }
        });
    }

    void log(String text) {
        String o = output.getText().toString();
        output.setText(text + "\n" + o);
    }

    @Override
    public void onOpen(Connection conn) {
        connection = conn;
        log("Connected!");
    }

    @Override
    public void onClose(Connection conn) {
        log(conn.getPeer() + "disconnect!");
        if (connection == conn) {
            connection = null;
        }
    }

    @Override
    public void onMessage(Connection connection, PackData data) {
        System.out.println("==================== CO MESSAGES: "  + data.get("text").toString() + " Từ: " + data.get("from").toString());
        System.out.println("==================== CO MESSAGES: "  + data.get().toString());
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        System.out.println("======================22222222>");
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {

    }
}
