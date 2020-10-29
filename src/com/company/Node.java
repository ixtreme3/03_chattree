package com.company;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class Node { // creates needed structures, initializes them, sends connection request(optionally), creates writing thread, handles income connection requests, receives and prints messages

    byte[] buffer = new byte[1024];
    private final HashMap<String, Integer> nodeNeighbours = new HashMap<>(); // pairs IP / port

    private final String myNodeName;
    private final int myPort;
    private final int myPercentageLoss;

    private String connectIp = null;
    private int connectPort;

    Node(String name, int percentage, int port, String connectIp, int connectPort) throws IOException {
        this.myNodeName = name;
        this.myPercentageLoss = percentage;
        this.myPort = port;

        this.connectIp = connectIp;
        this.connectPort = connectPort;
    }

    Node(String name, int percentage, int port) throws SocketException {
        this.myNodeName = name;
        this.myPercentageLoss = percentage;
        this.myPort = port;
    }

    private void handleIncomingMessages(DatagramSocket socket) throws IOException {
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        while (true) {
            socket.receive(packet);
            String receivedMessage = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
            if (isConnectionRequest(receivedMessage)) {
                handleConnectionRequest(socket, packet, receivedMessage);
            } else System.out.println(receivedMessage); // replace GUID with "Username: "
        }
    }

    private void establishConnection(DatagramSocket socket, String connectToIp, int connectToPort) throws IOException {
        String request = "0-0-0-0-0 " + myNodeName;
        byte[] buffer = request.getBytes(StandardCharsets.UTF_8);
        DatagramPacket requestPacket = new DatagramPacket(buffer, buffer.length, InetAddress.getByName(connectToIp), connectToPort);
        byte[] confirmationBuffer = new byte[128];
        DatagramPacket confirmationPacket = new DatagramPacket(confirmationBuffer, confirmationBuffer.length);

        socket.send(requestPacket);
        socket.setSoTimeout(25);
        int i = 0;
        while (i < 3){
            socket.receive(confirmationPacket);
            if (confirmationPacket.getLength() != 0){
                break;
            }
            socket.send(requestPacket);
            i++;
        }
        socket.setSoTimeout(0);
        if (confirmationPacket.getLength() == 0){
            System.out.println("Unable to establish connection");
        } else{
            String confirmationMessage = new String(confirmationPacket.getData(), 0, confirmationPacket.getLength(), StandardCharsets.UTF_8);
            nodeNeighbours.put(connectIp, connectPort);
            System.out.println("Connection " + confirmationMessage.split(" ")[1] + " is established");
        }
    }

    private boolean isConnectionRequest(String message){
        String[] str = message.split(" ");
        return str[0].equals("0-0-0-0-0") && str.length == 2;
    }

    private void handleConnectionRequest(DatagramSocket socket, DatagramPacket packet, String receivedMessage) throws IOException {
        String confirmationMessage = "1-1-1-1-1 " + myNodeName;
        byte[] buffer = confirmationMessage.getBytes(StandardCharsets.UTF_8);
        DatagramPacket confirmationPacket = new DatagramPacket(buffer, buffer.length, packet.getAddress(), packet.getPort());
        socket.send(confirmationPacket);

        synchronized (nodeNeighbours){
            String address = packet.getAddress().toString();
            if (!nodeNeighbours.containsKey(address)) {
                nodeNeighbours.put(address, packet.getPort());
            }
        }
        System.out.println("Connection with " + receivedMessage.split(" ")[1] + " is established");
    }

    public void start() {
        try (DatagramSocket socket = new DatagramSocket(myPort)) {
            if (connectIp != null) {
                establishConnection(socket, connectIp, connectPort);
            }

            SendMessageHandler sendMessageHandler = new SendMessageHandler(socket, nodeNeighbours);
            new Thread(sendMessageHandler).start();

            handleIncomingMessages(socket);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

class SendMessageHandler implements Runnable {
    private final DatagramSocket socket;
    final HashMap<String, Integer> nodeNeighbours;

    SendMessageHandler(DatagramSocket socket, HashMap<String, Integer> nodeNeighbours) {
        this.socket = socket;
        this.nodeNeighbours = nodeNeighbours;
    }

    private void sendMessages() throws IOException {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            String message = scanner.nextLine();
            if (!message.isEmpty() && (nodeNeighbours.size() > 0)) {
                UUID uuid = UUID.randomUUID();
                message = uuid.toString() + " " + message;
                byte[] buf = message.getBytes(StandardCharsets.UTF_8);
                synchronized (nodeNeighbours) {
                    for (Map.Entry<String, Integer> entry : nodeNeighbours.entrySet()) {
                        InetAddress inetAddress = InetAddress.getByName(entry.getKey().replace("/", ""));
                        int port = entry.getValue();
                        DatagramPacket packet = new DatagramPacket(buf, buf.length, inetAddress, port);
                        socket.send(packet);
                    }
                }
            } else System.out.println("Input is empty or there are no neighbours to send message to");
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