package com.company;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static java.lang.System.exit;
import static java.lang.Thread.sleep;


public class Node {
    private final byte[] buffer = new byte[1024];
    private static final HashMap<String, Integer> nodeNeighbours = new HashMap<>();
    public final static HashMap<String, String> nodeReplacements = new HashMap<>();
    private final List<Message> resendMessageList = new ArrayList<>();

    private final String myNodeName;
    private final int myPort;
    private final int myPercentageLoss;

    private static String connectIp = null;
    private static int connectPort;

    Node(String name, int percentage, int port, String connectIp, int connectPort) {
        this.myNodeName = name;
        this.myPercentageLoss = percentage;
        this.myPort = port;

        Node.connectIp = connectIp;
        Node.connectPort = connectPort;
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

    private void handleIncomingMessages(DatagramSocket socket) throws IOException {
        DatagramPacket receivedPacket = new DatagramPacket(buffer, buffer.length);
        HashMap<String, LocalTime> messageMap = new HashMap<>();

        while (true) {
            socket.receive(receivedPacket);
            if (getRandomNumber() >= myPercentageLoss) {
                String receivedMessage = new String(receivedPacket.getData(), 0, receivedPacket.getLength(), StandardCharsets.UTF_8);
                String[] parsedMessage = receivedMessage.split(" ");

                if (isConnectionRequest(receivedMessage)) {
                    handleConnectionRequest(socket, receivedPacket, receivedMessage);
                }

                if (parsedMessage.length == 1) {
                    String messageUUID = parsedMessage[0];
                    synchronized (resendMessageList) {
                        for (int i = 0; i < resendMessageList.size(); i++) {
                            Message currMessage = resendMessageList.get(i);
                            if (currMessage.uuid.equals(messageUUID)) {
                                resendMessageList.remove(currMessage);
                            }
                        }
                    }
                }

                if (parsedMessage.length > 2) {
                    String receivedMessageUUID = parsedMessage[0];
                    DatagramPacket sendConfirmationPacket = new DatagramPacket(parsedMessage[0].getBytes(StandardCharsets.UTF_8), 0, parsedMessage[0].getBytes(StandardCharsets.UTF_8).length, receivedPacket.getAddress(), receivedPacket.getPort());
                    socket.send(sendConfirmationPacket);
                    if (!messageMap.containsKey(receivedMessageUUID) || Math.abs(ChronoUnit.SECONDS.between(LocalTime.now(), messageMap.get(receivedMessageUUID))) < 5) {
                        String senderIp = receivedPacket.getAddress().toString().replace("/", "");
                        synchronized (nodeNeighbours) {
                            for (Map.Entry<String, Integer> entry : nodeNeighbours.entrySet()) {
                                if (!entry.getKey().equals(senderIp)) {
                                    InetAddress inetAddress = InetAddress.getByName(entry.getKey().replace("/", ""));
                                    int port = entry.getValue();
                                    DatagramPacket sendPacket = new DatagramPacket(receivedMessage.getBytes(StandardCharsets.UTF_8), 0, receivedMessage.getBytes().length, inetAddress, port);
                                    socket.send(sendPacket);
                                    synchronized (resendMessageList) {
                                        resendMessageList.add(new Message(receivedMessageUUID, receivedMessage, inetAddress, port, LocalTime.now()));
                                    }
                                }
                            }
                        }
                    }

                    if (parsedMessage[1].equals("2-2-2-2-2") && parsedMessage.length == 4) {
                        synchronized (nodeReplacements) {
                            String ip = receivedPacket.getAddress().getHostAddress();
                            nodeReplacements.put(ip, parsedMessage[2] + " " + parsedMessage[3]);
                        }
                        continue;
                    }

                    if (messageMap.size() == 0) {
                        messageMap.put(receivedMessageUUID, LocalTime.now());
                        System.out.println(receivedMessage.replace(parsedMessage[0] + " ", ""));
                    }
                    else if (!(messageMap.containsKey(receivedMessageUUID) && Math.abs(ChronoUnit.SECONDS.between(LocalTime.now(), messageMap.get(receivedMessageUUID))) < 5)) {
                        messageMap.entrySet().removeIf(stringLocalTimeEntry -> Math.abs(ChronoUnit.SECONDS.between(LocalTime.now(), stringLocalTimeEntry.getValue())) >= 5);

                        messageMap.put(receivedMessageUUID, LocalTime.now());
                        System.out.println(receivedMessage.replace(parsedMessage[0] + " ", ""));
                    }
                }
            }
        }
    }

    public static void establishConnection(DatagramSocket socket, String connectToIp, int connectToPort) throws IOException {
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

            SendMessageHandler sendMessageHandler = new SendMessageHandler(socket, nodeNeighbours, myNodeName, resendMessageList);
            new Thread(sendMessageHandler).start();

            ReSender resender = new ReSender(socket, resendMessageList);
            new Thread(resender).start();

            FindReplacementHandler findReplacementHandler = new FindReplacementHandler(nodeNeighbours);
            new Thread(findReplacementHandler).start();

            handleIncomingMessages(socket);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

class Message {
    public String uuid;
    public String message;
    public InetAddress inetAddress;
    public int port;
    public LocalTime sendTime;

    Message(String uuid, String message, InetAddress inetAddress, int port, LocalTime sendTime) {
        this.uuid = uuid;
        this.message = message;
        this.inetAddress = inetAddress;
        this.port = port;
        this.sendTime = sendTime;
    }
}

class ReSender implements Runnable {
    private final DatagramSocket socket;
    private final List<Message> resendMessageList;

    ReSender(DatagramSocket socket, List<Message> resendMessageList) {
        this.socket = socket;
        this.resendMessageList = resendMessageList;
    }

    private void rebuildTree(Message message) throws IOException {
        String[] parsedConnectTo;
        synchronized (Node.nodeReplacements){
            String connectTo = Node.nodeReplacements.get(message.inetAddress.getHostAddress());
            parsedConnectTo = connectTo.split( " ");
        }
        Node.establishConnection(socket, parsedConnectTo[0], Integer.parseInt(parsedConnectTo[1]));
    }

    private void resend() throws IOException, InterruptedException {
        while (true) {
            synchronized (resendMessageList) {
                for (Message currMessage : resendMessageList) {
                    String resendMessage = currMessage.message;
                    InetAddress resendAddress = currMessage.inetAddress;
                    int resendPort = currMessage.port;

                    if (Math.abs(ChronoUnit.SECONDS.between(LocalTime.now(), currMessage.sendTime)) >= 5) {
                        System.out.println("Connection with node " + currMessage.inetAddress.toString() + "/" + currMessage.port + " has been lost. Rebuilding the tree.. ");
                        rebuildTree(currMessage);
                    }

                    DatagramPacket packet = new DatagramPacket(resendMessage.getBytes(StandardCharsets.UTF_8), 0, resendMessage.getBytes(StandardCharsets.UTF_8).length, resendAddress, resendPort);
                    socket.send(packet);
                }
            }
            sleep(1000);
        }
    }

    @Override
    public void run() {
        try {
            resend();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}

class SendMessageHandler implements Runnable {
    private final DatagramSocket socket;
    private final HashMap<String, Integer> nodeNeighbours;
    private final String nodeName;
    private final List<Message> resendMessageList;

    SendMessageHandler(DatagramSocket socket, HashMap<String, Integer> nodeNeighbours, String nodeName, List<Message> resendMessageList) {
        this.socket = socket;
        this.nodeNeighbours = nodeNeighbours;
        this.nodeName = nodeName;
        this.resendMessageList = resendMessageList;
    }

    private void sendMessages() throws IOException {
        Scanner scanner = new Scanner(System.in);
        DatagramPacket packet;
        LocalTime time = LocalTime.now();
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
                        packet = new DatagramPacket(buf, 0, buf.length, inetAddress, port);
                        socket.send(packet);

                        synchronized (resendMessageList) {
                            resendMessageList.add(new Message(uuid.toString(), message, inetAddress, port, LocalTime.now()));
                            if (Math.abs(ChronoUnit.SECONDS.between(LocalTime.now(), time)) >= 1) {
                                UUID uuid1 = UUID.randomUUID();
                                String replacementMessage = uuid1.toString() + " " + "2-2-2-2-2" + " " + FindReplacementHandler.message;
                                buf = replacementMessage.getBytes(StandardCharsets.UTF_8);
                                packet = new DatagramPacket(buf, 0, buf.length, inetAddress, port);
                                socket.send(packet);

                                resendMessageList.add(new Message(uuid1.toString(), replacementMessage, inetAddress, port, LocalTime.now()));
                            }
                        }
                    }
                }
            } else System.out.println("Input is empty or there are no neighbours to send message to");
            time = time.plusSeconds(1);
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

class FindReplacementHandler implements Runnable {
    private final HashMap<String, Integer> nodeNeighbours;
    public static String message = null;

    FindReplacementHandler(HashMap<String, Integer> nodeNeighbours) {
        this.nodeNeighbours = nodeNeighbours;
    }

    private int getRandomNumber(int bound) {
        Random random = new Random();
        return random.nextInt(bound);
    }

    private void handle() throws InterruptedException {
        while (true) {
            synchronized (nodeNeighbours) {
                if (nodeNeighbours.size() > 1) {
                    int idx = getRandomNumber(nodeNeighbours.size());
                    List<String> keys = new ArrayList<>(nodeNeighbours.keySet());
                    String randomKey = keys.get(idx);
                    Integer value = nodeNeighbours.get(randomKey);

                    message = randomKey + " " + value.toString();
                } else message = null;
            }
            sleep(1500);
        }
    }

    public void run() {
        try {
            handle();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}