package avans.edu.netmul.android.task.a;

import android.annotation.TargetApi;
import android.util.Log;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

/**
 * @author JKetelaar
 */
public class TcpServer {

    public static final int DEFAULT_SOCKET_PORT = 1337;

    private InetSocketAddress listener;
    private Selector selector;

    private boolean run;

    public TcpServer(int port) {
        try {
            this.createConnection(port);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public TcpServer() {
        try {
            this.createConnection(DEFAULT_SOCKET_PORT);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void createConnection(int port) throws IOException {
        this.listener = new InetSocketAddress("localhost", port);
        this.selector = Selector.open();

        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.configureBlocking(false);

        serverSocketChannel.socket().bind(listener);
        serverSocketChannel.register(this.selector, SelectionKey.OP_ACCEPT);
    }

    public void startListening() throws IOException {
        while (run) {
            this.selector.select();

            Iterator keys = this.selector.selectedKeys().iterator();
            while (keys.hasNext()) {
                SelectionKey key = (SelectionKey) keys.next();

                keys.remove();

                if (!key.isValid()) {
                    continue;
                }

                if (key.isAcceptable()) {
                    this.open(key);
                } else if (key.isReadable()) {
                    this.read(key);
                }
            }
        }
    }

    @TargetApi(24)
    private void read(SelectionKey key) throws IOException {
        SocketChannel socketChannel = (SocketChannel) key.channel();
        ByteBuffer byteBuffer = ByteBuffer.allocate(2048);

        int read;
        read = socketChannel.read(byteBuffer);

        if (read == -1) {
            socketChannel.close();
            key.cancel();
            return;
        }

        byte[] data = new byte[read];
        System.arraycopy(byteBuffer.array(), 0, data, 0, read);
        Log.i("Server", "TcpClient with port " + socketChannel.getRemoteAddress().toString().split(":")[1] + " sent: " + new String(data));
    }

    private void open(SelectionKey key) throws IOException {
        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();

        SocketChannel socketChannel = serverSocketChannel.accept();
        socketChannel.configureBlocking(false);

        Socket socket = socketChannel.socket();
        SocketAddress socketAddress = socket.getRemoteSocketAddress(); // Can be useful, I guess?

        socketChannel.register(this.selector, SelectionKey.OP_READ);
    }

    public boolean isRun() {
        return run;
    }

    public void setRun(boolean run) {
        this.run = run;
    }
}
