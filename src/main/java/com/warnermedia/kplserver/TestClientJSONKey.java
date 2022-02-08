package com.warnermedia.kplserver;

import com.google.gson.Gson;

import java.io.*;
import java.net.Socket;


public class TestClientJSONKey {
  public static class TestWithKey {
    String kdsHashKey;
    String testa;
  }

  public static void main(String[] args) throws Exception, IOException, ClassNotFoundException, InterruptedException {

    System.out.println("starting client");

    // establish socket connection to server
    Socket socket = new Socket("127.0.0.1", 3000);
    OutputStreamWriter out = new OutputStreamWriter(socket.getOutputStream(), "UTF-8");

    System.out.println("Sending request to Socket Server");

    TestWithKey tst = new TestWithKey();
    tst.kdsHashKey = "mykey";
    tst.testa ="hello";

    Gson gson = new Gson();
    String jsonResult = gson.toJson(tst);
    String jsonFinal = jsonResult + "\n";

    out.write(jsonFinal);
    out.flush();
    Thread.sleep(100);

    socket.close();
  }
}
