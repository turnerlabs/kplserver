package com.warnermedia.kplserver;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.math.BigInteger;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

import com.amazonaws.services.kinesis.producer.UserRecordFailedException;
import com.amazonaws.services.kinesis.producer.UserRecordResult;
import com.amazonaws.services.kinesis.producer.KinesisProducer;
import com.amazonaws.services.kinesis.producer.KinesisProducerConfiguration;
import com.amazonaws.services.kinesis.producer.UserRecord;
import com.amazonaws.services.kinesis.producer.Attempt;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

public class App {

  public static void main(String[] args) throws Exception {

    final ExecutorService callbackThreadPool = Executors.newCachedThreadPool();

    final FutureCallback<UserRecordResult> callback = new FutureCallback<UserRecordResult>() {
      @Override
      public void onFailure(Throwable t) {
        System.out.println(t.toString());
        if (t instanceof UserRecordFailedException) {
          int attempts = ((UserRecordFailedException) t).getResult().getAttempts().size() - 1;
          Attempt last = ((UserRecordFailedException) t).getResult().getAttempts().get(attempts);
          if (attempts > 1) {
            Attempt previous = ((UserRecordFailedException) t).getResult().getAttempts().get(attempts - 1);
            System.out.println(String.format("Record failed to put - %s : %s. Previous failure - %s : %s",
                last.getErrorCode(), last.getErrorMessage(), previous.getErrorCode(), previous.getErrorMessage()));
          } else {
            System.out
                .println(String.format("Record failed to put - %s : %s.", last.getErrorCode(), last.getErrorMessage()));
          }
        }
      }

      @Override
      public void onSuccess(UserRecordResult result) {
        // noop
      }
    };

    int port = getPort();
    System.out.println("starting kplserver: " + port);
    ServerSocket server = new ServerSocket(port);

    // graceful shutdowns
    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        System.out.println("closing socket");
        if (!server.isClosed()) {
          try {
            server.close();
          } catch (IOException e) {
          }
        }
      }
    });

    Socket client = server.accept();
    String clientAddress = client.getInetAddress().getHostAddress();
    System.out.println("connection from " + clientAddress);

    BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
    String line = null;
    String stream = getKinesisStream();

    KinesisProducerConfiguration config = new KinesisProducerConfiguration().setRegion(getRegion());
    final KinesisProducer producer = new KinesisProducer(config);

    while (true) {
      line = in.readLine();
      if (line != null) {

        // add new line so that downstream systems have an easier time parsing
        line += "\n";

        // write to kinesis
        ByteBuffer data = ByteBuffer.wrap(line.getBytes("UTF-8"));
        UserRecord userRecord = new UserRecord(stream, " ", randomExplicitHashKey(), data);
        try {
          ListenableFuture<UserRecordResult> f = producer.addUserRecord(userRecord);
          Futures.addCallback(f, callback, callbackThreadPool);
        } catch (Exception e) {
          System.out.println(e);
        }
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

  public static String randomExplicitHashKey() {
    return new BigInteger(128, new Random()).toString(10);
  }

}
