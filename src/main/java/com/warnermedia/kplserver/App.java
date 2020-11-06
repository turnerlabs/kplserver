package com.warnermedia.kplserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

public class App {

  public static void main(String[] args) throws Exception {
    int port = getPort();
    System.out.println("starting kplserver: " + port);
    ServerSocket server = new ServerSocket(port);

    Socket client = server.accept();
    String clientAddress = client.getInetAddress().getHostAddress();
    System.out.println("connection from " + clientAddress);

    BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
    String stream = getKinesisStream();

    SendEventsToKinesis sendEventsToKinesis = new SendEventsToKinesis(stream, getRegion(), in);

    // graceful shutdowns
    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        System.out.println("closing socket");
        if (!server.isClosed()) {
          try {
            server.close();
            sendEventsToKinesis.stop();
          } catch (IOException e) {
            System.out.println(e);
          }
        }
      }
    });


    while (true) {
      try {
        sendEventsToKinesis.runOnce();
      } catch (Exception e) {
        System.out.println(e);
      }
    }
  }

  static int getPort() {
    String p = System.getenv("PORT");
    if (p == null || p == "") {
      return 3000;
    }
    return Integer.parseInt(p);
  }

  static String getKinesisStream() {
    return System.getenv("KINESIS_STREAM");
  }

  static String getRegion() {
    return System.getenv("AWS_DEFAULT_REGION");
  }
}
