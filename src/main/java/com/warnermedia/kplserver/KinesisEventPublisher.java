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

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class KinesisEventPublisher {
  private final String stream;
  private static final Log log = LogFactory.getLog(
    KinesisEventPublisher.class);
  final ExecutorService callbackThreadPool = Executors.newCachedThreadPool();

  private final KinesisProducer kinesis;

  public KinesisEventPublisher(String stream, String region) {
    this.stream = stream;
    kinesis = new KinesisProducer(new KinesisProducerConfiguration()
      .setRegion(region));
  }

  public void runOnce(String line) throws Exception {
    // add new line so that downstream systems have an easier time parsing
    String finalLine = line + "\n";

    ByteBuffer data = ByteBuffer.wrap(line.getBytes(java.nio.charset.StandardCharsets.UTF_8));

    //This is a measure of the backpressure in the system, which should be checked before putting more records,
    //to avoid exhausting system resources.
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
            "Record failed to put payload=%s, attempts:n%s",
            finalLine, errorList));
        }
      }
    }, callbackThreadPool);
  }

  private String randomExplicitHashKey() {
    return new BigInteger(128, new Random()).toString(10);
  }

  public void stop() {
    kinesis.flushSync();
    kinesis.destroy();
  }
}
