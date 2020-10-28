package com.company;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Scanner;

public class Node {
    private final String myNodeName;
    private final int myPercentageLoss;
    private final int myPort;

    private final HashMap<String, Integer> nodeNeighbours = new HashMap<>(); // pairs IP / port

    Node(String name, int percentage, int port, String neighbourIp, int neighbourPort) throws SocketException { // connect to somebody / make a neighbour
        this.myNodeName = name;
        this.myPercentageLoss = percentage;
        this.myPort = port;

        nodeNeighbours.put(neighbourIp, neighbourPort);
    }

    Node(String name, int percentage, int port) throws SocketException { // born detached
        this.myNodeName = name;
        this.myPercentageLoss = percentage;
        this.myPort = port;
    }

    private void start(){
        try (DatagramSocket socket = new DatagramSocket(myPort)) {
            Sender sender = new Sender(socket, nodeNeighbours);
            Receiver receiver = new Receiver(socket, nodeNeighbours);
            new Thread(receiver).start();
            new Thread(sender).start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

class Sender implements Runnable {
    private final DatagramSocket socket;
    final HashMap<String, Integer> nodeNeighbours;

    Sender(DatagramSocket s, HashMap<String, Integer> nodeNeighbours) {
        socket = s;
        this.nodeNeighbours = nodeNeighbours;
    }

    private void sendMessages() throws IOException {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            String message = scanner.nextLine();
            byte[] buf = message.getBytes(StandardCharsets.UTF_8);
            synchronized (nodeNeighbours) { // ??
                Iterator<Map.Entry<String, Integer>> iterator = nodeNeighbours.entrySet().iterator(); // to change
                while (iterator.hasNext()) {
                    Map.Entry<String, Integer> entry = iterator.next();
                    InetAddress inetAddress = InetAddress.getByName(entry.getKey());
                    int port = entry.getValue();
                    DatagramPacket packet = new DatagramPacket(buf, buf.length, inetAddress, port);
                    socket.send(packet);
                }
            }
        }
    }

    public void run() {
        try {
            sendMessages();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}


class Receiver implements Runnable {
    DatagramSocket socket;
    final HashMap<String, Integer> nodeNeighbours;
    byte[] buf;

    Receiver(DatagramSocket s, HashMap<String, Integer> nodeNeighbours) {
        socket = s;
        buf = new byte[1024];
        this.nodeNeighbours = nodeNeighbours;
    }

    private boolean isConnectionRequest(String message){
        // some logic
        return false;
    }

    private void handleRequest(String str){
        synchronized (nodeNeighbours){
            // some logic
        }
    }

    private void receiveMessage() throws IOException {
        while(true) {
            DatagramPacket packet = new DatagramPacket(buf, buf.length);
            socket.receive(packet);
            String receivedMessage = new String(packet.getData(), 0, packet.getLength());
            if (isConnectionRequest(receivedMessage)){
                handleRequest("");
            } else System.out.println(receivedMessage);
        }
    }

    public void run() {
        try {
            receiveMessage();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}




