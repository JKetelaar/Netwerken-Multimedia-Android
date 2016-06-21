package avans.edu.netmul.android.task.a;

import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 * @author JKetelaar
 */
public class UdpClient {

    public UdpClient(int port, String message) {
        try {
            this.start(port, message);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public UdpClient(String message) {
        try {
            this.start(UdpServer.DEFAULT_SOCKET_PORT, message);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void start(int port, String message) throws IOException {
        DatagramSocket datagramSocket = new DatagramSocket();
        InetAddress inetAddress = InetAddress.getByName("localhost");

        Log.i("Client", "Going to send: " + message);

        byte[] sendBytes = message.getBytes();

        DatagramPacket sendPacket = new DatagramPacket(sendBytes, sendBytes.length, inetAddress, port);
        datagramSocket.send(sendPacket);

        datagramSocket.close();
    }
}
