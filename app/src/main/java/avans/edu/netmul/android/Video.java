package avans.edu.netmul.android;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.StrictMode;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import avans.edu.netmul.android.task.three_four.RTPpacket;

import java.io.*;
import java.net.*;
import java.util.StringTokenizer;
import java.util.Timer;

public class Video extends AppCompatActivity {

    private int currentFrame = 0;
    private Handler refresh;

    private boolean run;

    public final static String CRLF = "\r\n";

    private static int RTP_RCV_PORT = 25000; //port where the client will receive the RTP packets
    private static int state; //RTSP state == INIT or READY or PLAYING
    //input and output stream filters
    private static BufferedReader RTSPBufferedReader;
    private static BufferedWriter RTSPBufferedWriter;
    private static String VideoFileName; //video file to request to the server

    private DatagramPacket rcvdp; //UDP packet received from the server
    private DatagramSocket RTPsocket; //socket to be used to send and receive UDP packets
    private Timer timer; //timer used to receive data from the UDP socket
    private byte[] buf; //buffer used to store data received from the server
    private Socket RTSPsocket; //socket used to send/receive RTSP messages
    private int RTSPSeqNb = 0; //Sequence number of RTSP messages within the session
    private int RTSPid = 0; //ID of the RTSP session (given by the RTSP Server)

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        buf = new byte[15000];

        refresh = new Handler(Looper.getMainLooper());

        new Thread() {
            @Override
            public void run() {
                try {
                    int RTSP_server_port = 1337;

                    InetAddress ServerIPAddr = InetAddress.getByName("192.168.2.31");
                    VideoFileName = "movie.Mjpeg";

                    RTSPsocket = new Socket(ServerIPAddr, RTSP_server_port);

                    RTSPBufferedReader = new BufferedReader(new InputStreamReader(RTSPsocket.getInputStream()));
                    RTSPBufferedWriter = new BufferedWriter(new OutputStreamWriter(RTSPsocket.getOutputStream()));

                    try {
                        RTPsocket = new DatagramSocket(RTP_RCV_PORT);
                        RTPsocket.setSoTimeout(250); // 5 milliseconds

                    } catch (SocketException ex) {
                        ex.printStackTrace();
                    }

                    RTSPSeqNb = 1;

                    //Send SETUP message to the server
                    send_RTSP_request("SETUP");

                    //Wait for the response
                    if (parse_server_response() != 200)
                        Log.i("Stream", "Invalid Server Response");
                    else {
                        Log.i("Stream", "New RTSP state: READY");
                    }

                    perform();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }

    private int parse_server_response() {
        int reply_code = 0;

        try {
            //parse status line and extract the reply_code:
            String StatusLine = RTSPBufferedReader.readLine();
            //System.out.println("RTSP Client - Received from Server:");
            System.out.println(StatusLine);

            StringTokenizer tokens = new StringTokenizer(StatusLine);
            tokens.nextToken(); //skip over the RTSP version
            reply_code = Integer.parseInt(tokens.nextToken());

            //if reply code is OK get and print the 2 other lines
            if (reply_code == 200) {
                String SeqNumLine = RTSPBufferedReader.readLine();
                Log.i("Streaming", SeqNumLine);

                String SessionLine = RTSPBufferedReader.readLine();
                Log.i("Streaming", SessionLine);

                //if state == INIT gets the Session Id from the SessionLine
                tokens = new StringTokenizer(SessionLine);
                tokens.nextToken(); //skip over the Session:
                RTSPid = Integer.parseInt(tokens.nextToken());
            }
        } catch (Exception ex) {
            System.out.println("Exception caught: " + ex);
            System.exit(0);
        }

        return (reply_code);
    }

    private void send_RTSP_request(String request_type) {
        try {
            //Use the RTSPBufferedWriter to write to the RTSP socket

            //write the request line:
            RTSPBufferedWriter.write(request_type + " " + VideoFileName + " RTSP/1.0" + CRLF);

            //write the CSeq line:
            RTSPBufferedWriter.write("CSeq: " + RTSPSeqNb + CRLF);

            //check if request_type is equal to "SETUP" and in this case write the Transport: line advertising to the server the port used to receive the RTP packets RTP_RCV_PORT
            if (request_type.equals("SETUP")) {
                RTSPBufferedWriter.write("Transport: RTP/UDP; client_port= " + RTP_RCV_PORT + CRLF);
                //otherwise, write the Session line from the RTSPid field
            } else {
                RTSPBufferedWriter.write("Session: " + RTSPid + CRLF);
            }

            RTSPBufferedWriter.flush();
        } catch (Exception ex) {
            System.out.println("Exception caught: " + ex);
            System.exit(0);
        }
    }

    private void perform() {
        RTSPSeqNb++;

        send_RTSP_request("PLAY");

        //Wait for the response
        if (parse_server_response() != 200)
            Log.i("Stream", "Invalid Server Response");
        else {
            Log.i("Stream", "New RTSP state: PLAYING");
            run = true;
        }

        final ImageView image = (ImageView) findViewById(R.id.video_image);
        image.setVisibility(View.VISIBLE);

        new Thread() {
            @Override
            public void run() {
                while (run) {
                    // Construct a DatagramPacket to receive data from the UDP socket
                    rcvdp = new DatagramPacket(buf, buf.length);

                    try {
                        RTPsocket.receive(rcvdp);

                        RTPpacket rtp_packet = new RTPpacket(rcvdp.getData(),
                                rcvdp.getLength());

                        rtp_packet.printheader();

                        int payload_length = rtp_packet.getpayload_length();
                        byte[] payload = new byte[payload_length];
                        rtp_packet.getpayload(payload);

                        final Bitmap bitmap = BitmapFactory.decodeByteArray(payload, 0, payload_length);
                        Log.i("Here", "I am");

                        refresh.post(new Runnable() {
                            public void run() {
                                image.setImageBitmap(bitmap);
                            }
                        });

                        Thread.sleep(10);

                    } catch (SocketTimeoutException ignored) {

                    } catch (Exception ignored) {
                        ignored.printStackTrace();
                    }
                }
            }
        }.start();
    }

}
