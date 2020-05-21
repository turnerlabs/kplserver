package com.warnermedia.kplserver;

import java.io.*;
import java.net.Socket;

public class TestClient {
  public static void main(String[] args) throws Exception, IOException, ClassNotFoundException, InterruptedException {

    System.out.println("starting client");

    // establish socket connection to server
    Socket socket = new Socket("127.0.0.1", 3000);
    OutputStreamWriter out = new OutputStreamWriter(socket.getOutputStream(), "UTF-8");

    System.out.println("Sending request to Socket Server");
    out.write("record\n");
    out.flush();
    Thread.sleep(100);

    socket.close();
  }
}
