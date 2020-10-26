package com.company;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;

public class Node {
    DatagramSocket socket;

    private final String nodeName;
    private final int percentageLoss;
    private final int port;

    private InetAddress neighbourIp = null;
    private int neighbourPort;

    Node(String name, int percentage, int port, InetAddress neighbourIp, int neighbourPort) throws SocketException { // connect to somebody/make a neighbour
        this.nodeName = name;
        this.percentageLoss = percentage;
        this.port = port;

        this.neighbourIp = neighbourIp;
        this.neighbourPort = neighbourPort;

//        socket = new DatagramSocket(port); // Constructs a datagram socket and binds it to the specified port on the local host machine.
//        socket.connect(neighbourIp, neighbourPort);
    }

    Node(String name, int percentage, int port) throws SocketException { // born detached
        this.nodeName = name;
        this.percentageLoss = percentage;
        this.port = port;

//        socket = new DatagramSocket(port); // Constructs a datagram socket and binds it to the specified port on the local host machine.
    }

    private void start(){
        // Сделать коннект здесь и передать готовый сокет в 2 потока?
        try (DatagramSocket socket = new DatagramSocket(port)) {
            if (neighbourIp != null) {
                socket.connect(neighbourIp, neighbourPort);
            }
            Sender sender = new Sender(socket, "");
            Receiver receiver = new Receiver(socket);
            new Thread(receiver).start();
            new Thread(sender).start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}

class Sender implements Runnable {
    public int port = 0;
    private final DatagramSocket socket;
    private String hostname;

    Sender(DatagramSocket s, String h) {
        socket = s;
//        hostname = h;
    }

    private void sendMessage(String s) throws Exception {
        byte[] buf = s.getBytes(StandardCharsets.UTF_8);
//        InetAddress address = InetAddress.getByName(hostname);
        DatagramPacket packet = new DatagramPacket(buf, buf.length, address, port);
        socket.send(packet);
    }

    public void run() {

    }
}

class Receiver implements Runnable {
    DatagramSocket socket;
    byte[] buf;

    Receiver(DatagramSocket s) {
        socket = s;
        buf = new byte[1024];
    }

    public void run() {
        while (true) {
            DatagramPacket packet = new DatagramPacket(buf, buf.length);
            try {
                socket.receive(packet);
            } catch (IOException e) {
                e.printStackTrace();
            }
            String received = new String(packet.getData(), 0, packet.getLength());
            System.out.println(received);
        }
    }
}

//
// 1.
//
// 2. При работе с UDP в Java для отправки и получения пакетов используется класс DatagramPacket:
//  Constructor to send data: DatagramPacket(byte buf[], int length, InetAddress inetAddress, int port)
//  Constructor to receive the data: DatagramPacket(byte buf[], int length)
//
// 3. socket.send(packet) / socket.receive(packet)
//
//



