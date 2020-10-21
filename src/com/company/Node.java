package com.company;

import java.net.InetAddress;

public class Node {
    private final String nodeName;
    private final int percentageLoss;
    private final int port;

    private InetAddress neighbourIp;
    private int neighbourPort;

    Node(String name, int percentage, int port, InetAddress neighbourIp, int neighbourPort){ // connect to somebody/make a neighbour
        this.nodeName = name;
        this.percentageLoss = percentage;
        this.port = port;

        this.neighbourIp = neighbourIp;
        this.neighbourPort = neighbourPort;
    }

    Node(String name, int percentage, int port){ // born detached
        this.nodeName = name;
        this.percentageLoss = percentage;
        this.port = port;
    }







}
