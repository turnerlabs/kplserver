package com.warnermedia.kplserver;

import com.amazonaws.services.kinesis.producer.KinesisProducer;
import com.amazonaws.services.kinesis.producer.KinesisProducerConfiguration;
import com.amazonaws.services.kinesis.producer.UserRecord;
import com.amazonaws.services.kinesis.producer.UserRecordFailedException;
import com.amazonaws.services.kinesis.producer.UserRecordResult;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.BufferedReader;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Random;
import java.util.stream.Collectors;

public class SendEventsToKinesis {
  private final BufferedReader inputQueue;
  private final String stream;
  private static final Log log = LogFactory.getLog(
    SendEventsToKinesis.class);

  private final KinesisProducer kinesis;

  public SendEventsToKinesis(String stream, String region, BufferedReader inputQueue) {
    this.inputQueue = inputQueue;
    this.stream = stream;
    kinesis = new KinesisProducer(new KinesisProducerConfiguration()
      .setRegion(region));
  }

  public void runOnce() throws Exception {
    String line = inputQueue.readLine();
    if (line == null) {
      return;
    }

    // add new line so that downstream systems have an easier time parsing
    String finalLine = line + "\n";

    ByteBuffer data = ByteBuffer.wrap(line.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    while (kinesis.getOutstandingRecordsCount() > 1e4) {
      log.info("Too many outstanding records pending in the queue. Waiting for a second.");
      Thread.sleep(1000);
    }

    UserRecord userRecord = new UserRecord(stream, " ", randomExplicitHashKey(), data);
    ListenableFuture<UserRecordResult> f = kinesis.addUserRecord(userRecord);


    Futures.addCallback(f, new FutureCallback<UserRecordResult>() {

      @Override
      public void onSuccess(UserRecordResult result) {
        // noop
      }

      @Override
      public void onFailure(Throwable t) {
        if (t instanceof UserRecordFailedException) {
          UserRecordFailedException e =
            (UserRecordFailedException) t;
          UserRecordResult result = e.getResult();

          String errorList =
            StringUtils.join(result.getAttempts().stream()
              .map(a -> String.format(
                "Delay after prev attempt: %d ms, "
                  + "Duration: %d ms, Code: %s, "
                  + "Message: %s",
                a.getDelay(), a.getDuration(),
                a.getErrorCode(),
                a.getErrorMessage()))
              .collect(Collectors.toList()), "n");

          log.error(String.format(
            "Record failed to put, partitionKey=%s, "
              + "payload=%s, attempts:n%s",
            " ", finalLine, errorList));
        }
      }
    });
  }

  private String randomExplicitHashKey() {
    return new BigInteger(128, new Random()).toString(10);
  }

  public void stop() {
    kinesis.flushSync();
    kinesis.destroy();
  }
}
