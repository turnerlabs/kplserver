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
    Socket errSocket = new Socket();

    Socket client = server.accept();
    String clientAddress = client.getInetAddress().getHostAddress();
    System.out.println("connection from " + clientAddress);

    BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
    String stream = getKinesisStream();

    KinesisEventPublisher kinesisEventPublisher = new KinesisEventPublisher(stream, getRegion(), getErrSocketPort(), getErrSocketHost());

    // graceful shutdowns
    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        System.out.println("closing sockets");
        if (!server.isClosed()) {
          try {
            server.close();
            kinesisEventPublisher.stop();
          } catch (IOException e) {
            System.out.println(e);
          }
        }
      }
    });

    while (true) {
      try {
        String line = in.readLine();
        if (line == null) {
          continue;
        }
        kinesisEventPublisher.runOnce(line);
      } catch (Exception e) {
        System.out.println(e);
      }
    }
  }

  static int getPort() {
    String p = System.getenv("PORT");
    if (p == null || p.equals("")) {
      return 3000;
    }
    return Integer.parseInt(p);
  }

  static String getErrSocketHost() {
    String h = System.getenv("ERROR_SOCKET_HOST");
    if (h == null || h.equals("")) {
      return "127.0.01";
    }
    return h;
  }

  static Integer getErrSocketPort() {
    String p = System.getenv("ERROR_SOCKET_PORT");
    try {
      return Integer.parseInt(p);
    } catch (
      Exception e) {
      System.out.println("There is no or invalid port set for errors");
      System.out.println(e);
      return null;
    }

  }

  static String getKinesisStream() {
    return System.getenv("KINESIS_STREAM");
  }

  static String getRegion() {
    return System.getenv("AWS_DEFAULT_REGION");
  }
}
