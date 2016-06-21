package avans.edu.netmul.android.task.a;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

/**
 * @author JKetelaar
 */
public class TcpClient {

    public TcpClient(int port, String[] messages) {
        try {
            this.startClient(port, messages);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public TcpClient(String[] messages) {
        try {
            this.startClient(Example.TCP ? TcpServer.DEFAULT_SOCKET_PORT : UdpServer.DEFAULT_SOCKET_PORT, messages);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void startClient(int port, String[] messages) throws IOException, InterruptedException {
        InetSocketAddress inetSocketAddress = new InetSocketAddress("localhost", port);
        SocketChannel socketChannel = SocketChannel.open(inetSocketAddress);

        for (String message : messages) {
            byte[] bytes = message.getBytes();
            ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
            socketChannel.write(byteBuffer);
            byteBuffer.clear();
            Thread.sleep(3500);
        }
        socketChannel.close();
    }
}
