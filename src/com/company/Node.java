package com.company;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static java.lang.System.exit;

public class Node {

    private final byte[] buffer = new byte[1024];
    private final HashMap<String, Integer> nodeNeighbours = new HashMap<>();

    private final String myNodeName;
    private final int myPort;
    private final int myPercentageLoss;

    private String connectIp = null;
    private int connectPort;

    Node(String name, int percentage, int port, String connectIp, int connectPort) {
        this.myNodeName = name;
        this.myPercentageLoss = percentage;
        this.myPort = port;

        this.connectIp = connectIp;
        this.connectPort = connectPort;
    }

    Node(String name, int percentage, int port) {
        this.myNodeName = name;
        this.myPercentageLoss = percentage;
        this.myPort = port;
    }

    private int getRandomNumber() {
        Random random = new Random();
        return random.nextInt(99);
    }

    private void deliveryConfirmation(DatagramSocket socket, DatagramPacket packet, String uuid) throws IOException {
        socket.setSoTimeout(100);
        byte[] confirmationBuffer = new byte[36];
        DatagramPacket confirmationPacket = new DatagramPacket(confirmationBuffer, confirmationBuffer.length);
        int i = 0;
        while (i < 10){
            try {
                socket.receive(confirmationPacket);
                String receivedUUID = (new String(confirmationPacket.getData(),0, confirmationPacket.getData().length, StandardCharsets.UTF_8)).split(" ")[0];
                if (receivedUUID.equals(uuid)){
                    break;
                }
            } catch (SocketTimeoutException ignored){ }
            socket.send(packet);
            i++;
        }
        socket.setSoTimeout(0);
    }

    private void handleIncomingMessages(DatagramSocket socket) throws IOException {
        DatagramPacket receivedPacket = new DatagramPacket(buffer, buffer.length);
        HashMap<String, LocalTime> messageMap = new HashMap<>();

        while (true) {
            synchronized (socket) {
                socket.receive(receivedPacket);
            }
            int randomNumber = getRandomNumber();
            if (randomNumber >= myPercentageLoss) {
                String receivedMessage = new String(receivedPacket.getData(), 0, receivedPacket.getLength(), StandardCharsets.UTF_8);
                String[] parsedMessage = receivedMessage.split(" ");
                if (isConnectionRequest(receivedMessage)) {
                    handleConnectionRequest(socket, receivedPacket, receivedMessage);
                }
                if (parsedMessage.length > 2) {
                    String currentUUID = parsedMessage[0];
                    if (messageMap.size() == 0){
                        messageMap.put(currentUUID, LocalTime.now());
                        System.out.println(receivedMessage.replace(parsedMessage[0] + " ", ""));
                    }
                    LocalTime localTime = LocalTime.now();
                    for (Map.Entry<String, LocalTime> entry : messageMap.entrySet()) {
                        if (messageMap.containsKey(currentUUID) && Math.abs(ChronoUnit.SECONDS.between(localTime, messageMap.get(currentUUID))) < 5){
                            break;
                        } else if (messageMap.size() <= 25) {
                            messageMap.put(currentUUID, LocalTime.now());
                            System.out.println(receivedMessage.replace(parsedMessage[0] + " ", ""));
                            break;
                        } if (Math.abs(ChronoUnit.SECONDS.between(localTime, entry.getValue())) >= 5) {
                            messageMap.remove(entry.getKey());
                            messageMap.put(currentUUID, LocalTime.now());
                            System.out.println(receivedMessage.replace(parsedMessage[0] + " ", ""));
                        }
                    }

                    DatagramPacket sendConfirmationPacket = new DatagramPacket(parsedMessage[0].getBytes(StandardCharsets.UTF_8), 0, parsedMessage[0].getBytes(StandardCharsets.UTF_8).length, receivedPacket.getAddress(), receivedPacket.getPort());
                    socket.send(sendConfirmationPacket);

                    if (!(messageMap.containsKey(currentUUID) && Math.abs(ChronoUnit.SECONDS.between(localTime, messageMap.get(currentUUID))) < 5)){
                        String senderIp = receivedPacket.getAddress().toString().replace("/", "");
                        synchronized (nodeNeighbours) {
                            for (Map.Entry<String, Integer> entry : nodeNeighbours.entrySet()) {
                                if (!entry.getKey().equals(senderIp)) {
                                    InetAddress inetAddress = InetAddress.getByName(entry.getKey().replace("/", ""));
                                    int port = entry.getValue();
                                    DatagramPacket sendPacket = new DatagramPacket(receivedMessage.getBytes(StandardCharsets.UTF_8), 0, receivedMessage.getBytes().length, inetAddress, port);
                                    socket.send(sendPacket);

                                    deliveryConfirmation(socket, sendPacket, parsedMessage[0]);
                                }
                            }
                        }

                    }
                }
            }
        }

    }

    private void establishConnection(DatagramSocket socket, String connectToIp, int connectToPort) throws IOException {
        String requestMessage = "0-0-0-0-0 " + InetAddress.getLocalHost();
        byte[] requestBuffer = requestMessage.getBytes(StandardCharsets.UTF_8);
        DatagramPacket requestPacket = new DatagramPacket(requestBuffer, requestBuffer.length, InetAddress.getByName(connectToIp), connectToPort);

        byte[] confirmationBuffer = new byte[64];
        DatagramPacket confirmationPacket = new DatagramPacket(confirmationBuffer, confirmationBuffer.length);

        socket.send(requestPacket);

        socket.setSoTimeout(100);
        String receivedMessage = null;
        int i = 0; boolean success = false;
        while (i < 50) {
            System.out.println(i);
            try {
                socket.receive(confirmationPacket);
                receivedMessage = new String(confirmationPacket.getData(), 0 , confirmationPacket.getLength(), StandardCharsets.UTF_8);
                if (receivedMessage.split(" ")[0].equals("1-1-1-1-1") && receivedMessage.split(" ").length == 2) {
                    success = true;
                    break;
                }
            } catch (SocketTimeoutException ignored){ }
            socket.send(requestPacket);
            i++;
        }
        socket.setSoTimeout(0);

        if (success) {
            nodeNeighbours.put(connectIp, connectPort);
            System.out.println("Connection with " + receivedMessage.split(" ")[1] + " is established");
        } else {
            System.out.println("Unable to establish connection");
            exit(-1);
        }
    }

    private boolean isConnectionRequest(String message){
        String[] str = message.split(" ");
        return str[0].equals("0-0-0-0-0") && str.length == 2;
    }

    private void handleConnectionRequest(DatagramSocket socket, DatagramPacket packet, String receivedMessage) throws IOException {
        String confirmationMessage = "1-1-1-1-1 " + InetAddress.getLocalHost();
        byte[] buffer = confirmationMessage.getBytes(StandardCharsets.UTF_8);
        DatagramPacket confirmationPacket = new DatagramPacket(buffer, buffer.length, packet.getAddress(), packet.getPort());
        socket.send(confirmationPacket);

        synchronized (nodeNeighbours){
            String address = packet.getAddress().toString().replace("/", "");
            if (!nodeNeighbours.containsKey(address)) {
                nodeNeighbours.put(address, packet.getPort());
                System.out.println("Connection with " + receivedMessage.split(" ")[1] + " is established");
            }
        }
    }

    public void start() {
        try (DatagramSocket socket = new DatagramSocket(myPort)) {
            if (connectIp != null) {
                establishConnection(socket, connectIp, connectPort);
            }

            SendMessageHandler sendMessageHandler = new SendMessageHandler(socket, nodeNeighbours, myNodeName);
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
    private final String nodeName;

    SendMessageHandler(DatagramSocket socket, HashMap<String, Integer> nodeNeighbours, String nodeName) {
        this.socket = socket;
        this.nodeNeighbours = nodeNeighbours;
        this.nodeName = nodeName;
    }

    private void confirmDelivery(DatagramPacket packet, UUID uuid) throws IOException {
        socket.setSoTimeout(100);
        byte[] confirmationBuffer = new byte[36];
        DatagramPacket confirmationPacket = new DatagramPacket(confirmationBuffer, confirmationBuffer.length);
        int i = 0;
        while (i < 10){
            try {
                socket.receive(confirmationPacket);
                String receivedUUID = (new String(confirmationPacket.getData(),0, confirmationPacket.getData().length, StandardCharsets.UTF_8)).split(" ")[0];
                if (receivedUUID.equals(uuid.toString())){
                    break;
                }
            } catch (SocketTimeoutException ignored){ }
            socket.send(packet);
            i++;
        }
        socket.setSoTimeout(0);
    }

    private void sendMessages() throws IOException {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            String message = scanner.nextLine();
            if (!message.isEmpty() && (nodeNeighbours.size() > 0)) {
                UUID uuid = UUID.randomUUID();
                message = uuid.toString() + " " + nodeName + ": " + message;
                byte[] buf = message.getBytes(StandardCharsets.UTF_8);
                synchronized (nodeNeighbours) {
                    for (Map.Entry<String, Integer> entry : nodeNeighbours.entrySet()) {
                        InetAddress inetAddress = InetAddress.getByName(entry.getKey().replace("/", ""));
                        int port = entry.getValue();
                        DatagramPacket packet = new DatagramPacket(buf, 0, buf.length, inetAddress, port);
                        socket.send(packet);
                        synchronized (socket) {
                            confirmDelivery(packet, uuid);
                        }
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