package com.company;

import java.net.SocketException;

public class Main {
    public static void main(String[] args) throws SocketException {
        Node node = new Node("test",0,777);
        node.start();
    }
}
