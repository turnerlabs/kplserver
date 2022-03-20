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

    port = getErrSocketPort();
    System.out.println("error socket is opening at: " + port);
    ServerSocket errSocket = new ServerSocket(port);
    errSocket.setSoTimeout(100);

    KinesisEventPublisher kinesisEventPublisher = new KinesisEventPublisher(stream, getRegion(), getMetricsLevel(), errSocket);

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

  static int getErrSocketPort() {
    String p = System.getenv("ERROR_SOCKET_PORT");
    if (p == null || p.equals("")) {
      return 3001;
    }
    return Integer.parseInt(p);
  }

  static String getKinesisStream() {
    return System.getenv("KINESIS_STREAM");
  }

  static String getRegion() {
    return System.getenv("AWS_DEFAULT_REGION");
  }

  static String getMetricsLevel() {
    String p = System.getenv("METRICS_LEVEL");
    if (p == null || p.equals("")) {
      return "detailed";
    }
    return p;
  }

}
