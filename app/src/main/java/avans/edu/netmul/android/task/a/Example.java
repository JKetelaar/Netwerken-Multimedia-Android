package avans.edu.netmul.android.task.a;

import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

/**
 * @author JKetelaar
 */
public class Example {

    public static boolean TCP = false;

    public static void main(String[] args) {
        new Thread() {
            @Override
            public void run() {
                startServer();
            }
        }.start();

        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        new Thread() {
            @Override
            public void run() {
                startClient("Hello", "This is client 1");
            }
        }.start();

        if (TCP) {
            new Thread() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    startClient("Hi", "I am client 2");
                }
            }.start();
        }
    }

    public static void startServer() {
        if (TCP) {
            TcpServer server = new TcpServer();
            server.setRun(true);

            try {
                server.startListening();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            new UdpServer();
        }
    }

    public static void startClient(String... messages) {
        if (TCP) {
            new TcpClient(messages);
        } else {
            new UdpClient(messages[0]);
        }
    }

    public static void setTCP(boolean TCP) {
        Example.TCP = TCP;
    }
}
