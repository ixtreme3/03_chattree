package com.company;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Arrays;

public class Main {
    public static void main(String[] args) throws IOException {
        Node node = new Node("Ivan-Laptop",0,888, "192.168.0.151", 777);
        node.start();

//        System.out.println(InetAddress.getLocalHost().toString().split("/")[1]);
//        System.out.println(Arrays.toString(InetAddress.getLocalHost().getAddress()));
    }
}
