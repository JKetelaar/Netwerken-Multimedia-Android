package avans.edu.netmul.android.task.three_four;

import android.os.StrictMode;
import avans.edu.netmul.android.task.a.TcpServer;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class Server implements Runnable {

	// Variables RTP:
	// ----------------
	private DatagramSocket RTPsocket; // Socket for sending and recieving UDP packets
	private DatagramPacket senddp; // UDP packet containing video

	private InetAddress ClientIPAddr; // Client IP
	private int RTP_dest_port = 0; // Destination port

	// Variablen video:
	// ----------------
	private int imagenb = 0; // Image nr send
	private VideoStream video; // VideoStream object object used to access video frames
	private static int MJPEG_TYPE = 26; // RTP payload type for MJPEG video
	private static int FRAME_PERIOD = 50; //100 // Frame period of the video to stream, in ms
	private static int VIDEO_LENGTH = 500; // length of the video in frames

	private byte[] buf; // buffer used to store the images to send to the client

	private final static int INIT = 0;
	private final static int READY = 1;
	private final static int PLAYING = 2;

	private final static int SETUP = 3;
	private final static int PLAY = 4;
	private final static int PAUSE = 5;
	private final static int TEARDOWN = 6;

	private static int state; // RTSP Server state == INIT or READY or PLAY
	private Socket RTSPsocket; // socket used to send/receive RTSP messages

	private static BufferedReader RTSPBufferedReader;
	private static BufferedWriter RTSPBufferedWriter;
	private static String VideoFileName; // video file requested from the client
	private static int RTSP_ID = 123456; // ID of the RTSP session
	private int RTSPSeqNb = 0; // Sequence number of RTSP messages within the session

	private final static String CRLF = "\r\n";

	// --------------------------------
	// Constructor
	// --------------------------------
	public Server() {
		buf = new byte[15000];
	}

	// ------------------------
	// Handler for timer
	// ------------------------
	public void run()
    {
		if (imagenb < VIDEO_LENGTH) {
			imagenb++;

			try {
				// Get next frame and size
				int image_length = video.getnextframe(buf);

				// Builds an RTPpacket object containing the frame
				RTPpacket rtp_packet = new RTPpacket(MJPEG_TYPE, imagenb,
						imagenb * FRAME_PERIOD, buf, image_length);

				// get to total length of the full rtp packet to send
				int packet_length = rtp_packet.getlength();

				// retrieve the packet bitstream and store it in an array of
				// bytes
				byte[] packet_bits = new byte[packet_length];
				rtp_packet.getpacket(packet_bits);

				// send the packet as a DatagramPacket over the UDP socket
				senddp = new DatagramPacket(packet_bits, packet_length,
						ClientIPAddr, RTP_dest_port);
				RTPsocket.send(senddp);

				// print the header bitstream
				rtp_packet.printheader();

				System.out.println("Send frame #" + imagenb);
			} catch (Exception ex) {
				System.out.println("Exception Detected: " + ex);
				System.exit(0);
			}
		} else {
			//Thread.currentThread().stop();
		}
	}

	// ------------------------------------
	// Parse RTSP Request
	// ------------------------------------
	private int parse_RTSP_request() {
		int request_type = -1;
		try {
			// parse request line and extract the request_type:
			String RequestLine = RTSPBufferedReader.readLine();
			// System.out.println("RTSP Server - Received from Client:");
			System.out.println(RequestLine);

			StringTokenizer tokens = new StringTokenizer(RequestLine);
			String request_type_string = tokens.nextToken();

			// convert to request_type structure:
			if ((request_type_string).compareTo("SETUP") == 0)
				request_type = SETUP;
			else if ((request_type_string).compareTo("PLAY") == 0)
				request_type = PLAY;
			else if ((request_type_string).compareTo("PAUSE") == 0)
				request_type = PAUSE;
			else if ((request_type_string).compareTo("TEARDOWN") == 0)
				request_type = TEARDOWN;

			if (request_type == SETUP) {
				// extract VideoFileName from RequestLine
				VideoFileName = tokens.nextToken();
			}

			// parse the SeqNumLine and extract CSeq field
			String SeqNumLine = RTSPBufferedReader.readLine();
			System.out.println(SeqNumLine);
			tokens = new StringTokenizer(SeqNumLine);
			tokens.nextToken();
			RTSPSeqNb = Integer.parseInt(tokens.nextToken());

			// get LastLine
			String LastLine = RTSPBufferedReader.readLine();
			System.out.println(LastLine);

			if (request_type == SETUP) {
				// extract RTP_dest_port from LastLine
				tokens = new StringTokenizer(LastLine);
				for (int i = 0; i < 3; i++)
					tokens.nextToken(); // skip unused stuff
				RTP_dest_port = Integer.parseInt(tokens.nextToken());
			}
			// else LastLine will be the SessionId line ... do not check for
			// now.
		} catch (Exception ex) {
			System.out.println("Exception caught: " + ex);
			System.exit(0);
		}
		return (request_type);
	}

	// ------------------------------------
	// Send RTSP Response
	// ------------------------------------
	private void send_RTSP_response() {
		try {
			RTSPBufferedWriter.write("RTSP/1.0 200 OK" + CRLF);
			RTSPBufferedWriter.write("CSeq: " + RTSPSeqNb + CRLF);
			RTSPBufferedWriter.write("Session: " + RTSP_ID + CRLF);
			RTSPBufferedWriter.flush();
			// System.out.println("RTSP Server - Sent response to Client.");
		} catch (Exception ex) {
			System.out.println("Exception caught: " + ex);
			System.exit(0);
		}
	}

	public static void start() throws Exception {
		// Creates Server
		final Server theServer = new Server();

		StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
		StrictMode.setThreadPolicy(policy);

		// User port
		int RTSPport = TcpServer.DEFAULT_SOCKET_PORT;

		// Initializes a TCP connection to the client using RTSP session
		ServerSocket listenSocket = new ServerSocket(RTSPport);
		theServer.RTSPsocket = listenSocket.accept();
		listenSocket.close();

		// Takes client IP
		theServer.ClientIPAddr = theServer.RTSPsocket.getInetAddress();

		// Initializes the state of the RTP
		state = INIT;

		// return;

		// Sets of input and output streams
		RTSPBufferedReader = new BufferedReader(new InputStreamReader(
				theServer.RTSPsocket.getInputStream()));
		RTSPBufferedWriter = new BufferedWriter(new OutputStreamWriter(
				theServer.RTSPsocket.getOutputStream()));

		// Waits for a customer SETUP message
		int request_type;
		boolean done = false;
		while (!done) {
			request_type = theServer.parse_RTSP_request(); // block the port

			if (request_type == SETUP) {
				done = true;
				// Update state of RTSP
				state = READY;
				System.out.println("New state RTSP: READY");

				// Sends a reply
				theServer.send_RTSP_response();

				// Initializes a VideoStream object
				try {
					theServer.video = new VideoStream(VideoFileName);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

				// Initializes an RTP socket
				try {
					theServer.RTPsocket = new DatagramSocket();
				} catch (SocketException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}


		}

		final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

		new Thread(new Runnable() {

			private ScheduledFuture sf;

			@Override
			public void run() {
				// TODO Auto-generated method stub

				while (true) {

					int request_type;

					// Recognizes the request type
					request_type = theServer.parse_RTSP_request(); // blocked

					if ((request_type == PLAY) && (state == READY)) {

						theServer.send_RTSP_response();

						sf = scheduler.scheduleAtFixedRate(theServer, 0,
								FRAME_PERIOD, TimeUnit.MILLISECONDS);

						state = PLAYING;
						System.out.println("New state RTSP: PLAYING");
					} else if ((request_type == PAUSE) && (state == PLAYING)) {
						// Sends a response back
						theServer.send_RTSP_response();
						try {
							sf.cancel(false);
						} catch(Exception e) {
                            e.printStackTrace();
						}
						// Update the RTSP state
						state = READY;
						System.out.println("New state RTSP: READY");
					} else if (request_type == TEARDOWN) {
						// Sends response
						theServer.send_RTSP_response();
						scheduler.shutdownNow();

						// Close
						try {
							theServer.RTSPsocket.close();
						} catch (IOException e) {
							 e.printStackTrace();
						}
						theServer.RTPsocket.close();

						// Close application
						System.exit(0);
					}

					try {
						Thread.sleep(30);
					} catch (Exception e) {

					}

				}

			}

		}).start();

	}

}