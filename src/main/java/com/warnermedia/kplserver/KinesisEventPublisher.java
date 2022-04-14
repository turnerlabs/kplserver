package com.warnermedia.kplserver;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.kinesis.producer.KinesisProducer;
import com.amazonaws.services.kinesis.producer.KinesisProducerConfiguration;
import com.amazonaws.services.kinesis.producer.UserRecord;
import com.amazonaws.services.kinesis.producer.UserRecordFailedException;
import com.amazonaws.services.kinesis.producer.UserRecordResult;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceAsyncClientBuilder;
import com.amazonaws.services.securitytoken.model.AssumeRoleRequest;
import com.amazonaws.services.securitytoken.model.AssumeRoleResult;
import com.amazonaws.services.securitytoken.model.Credentials;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.security.auth.login.Configuration;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.ServerSocket;
import java.net.Socket;
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
  private DataOutputStream errOutputStream;
  ServerSocket errSocket;
  Socket errClient;

  public KinesisEventPublisher(String stream, String region, String metricsLevel, ServerSocket errSocket) {
    this.stream = stream;
    kinesis = new KinesisProducer(new KinesisProducerConfiguration()
      .setRegion(region)
      .setMetricsLevel(metricsLevel)
      .setCredentialsProvider(loadCredentials(false)));
    this.errSocket = errSocket;
  }

  private static AWSCredentialsProvider loadCredentials(boolean isLocal) {
    final AWSCredentialsProvider credentialsProvider;
    if (isLocal) {
      AWSSecurityTokenService stsClient = AWSSecurityTokenServiceAsyncClientBuilder.standard()
        .withCredentials(new ProfileCredentialsProvider("nonprodjump"))
        .withRegion("us-east-1")
        .build();

      AssumeRoleRequest assumeRoleRequest = new AssumeRoleRequest().withDurationSeconds(3600)
        .withRoleArn("arn:aws:iam::373762790913:role/doppler-video-lcluseast1")
        .withRoleSessionName("Kinesis_Session");

      AssumeRoleResult assumeRoleResult = stsClient.assumeRole(assumeRoleRequest);
      Credentials creds = assumeRoleResult.getCredentials();

      credentialsProvider = new AWSStaticCredentialsProvider(
        new BasicSessionCredentials(creds.getAccessKeyId(),
          creds.getSecretAccessKey(),
          creds.getSessionToken())
      );
    } else {
      credentialsProvider = new DefaultAWSCredentialsProviderChain();
    }

    return credentialsProvider;
  }

  public void runOnce(String line) throws Exception {
    // add new line so that downstream systems have an easier time parsing
    String finalLine = line + "\n";

    ByteBuffer data = ByteBuffer.wrap(finalLine.getBytes(java.nio.charset.StandardCharsets.UTF_8));

    // Need to serialize this to an object to get the key.
    String hashKey;

    Gson gson = new Gson();
    try {
      MinimalKey minimal = gson.fromJson(line, MinimalKey.class);
      if (minimal.kdsHashKey != null) {
        hashKey = minimal.kdsHashKey;
        log.debug("Using passed in hash key");
      } else {
        hashKey = randomExplicitHashKey();
        log.debug("Using random hash key");
      }
    }
    catch (JsonSyntaxException e) {
      hashKey = randomExplicitHashKey();
      log.debug("Using random hash key");
    }

    //This is a measure of the backpressure in the system, which should be checked before putting more records,
    //to avoid exhausting system resources.
    while (kinesis.getOutstandingRecordsCount() > 1e4) {
      log.info("Too many outstanding records pending in the queue. Waiting for a second.");
      Thread.sleep(500);
    }

    UserRecord userRecord = new UserRecord(stream, " ", hashKey, data);
    ListenableFuture<UserRecordResult> f = kinesis.addUserRecord(userRecord);

    Futures.addCallback(f, new FutureCallback<UserRecordResult>() {

      @Override
      public void onSuccess(UserRecordResult result) {
        // noop
      }

      @Override
      public void onFailure(Throwable t) {
        if (t instanceof UserRecordFailedException) {
          try {
            if (errSocket != null && (errClient == null || !errClient.isConnected())) {
              errClient = errSocket.accept();
              errClient.setKeepAlive(true);
              errOutputStream = new DataOutputStream((errClient.getOutputStream()));
              System.out.println("error socket connection from " + errClient.getInetAddress().getHostAddress());
            }
          } catch (Exception e) {
            log.error(String.format(
              "Record dropped. Unable to connect to error socket. payload=%s, attempts:%s",
              finalLine, e));
            return;
          }

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
            "Record failed to put payload=%s, attempts=%s",
            finalLine, errorList));

          if (errOutputStream != null) {
            try {
              errOutputStream.writeBytes(finalLine);
              errOutputStream.flush();
              log.info("Sent the record to output stream");
            } catch (IOException ioException) {
              System.out.println("Unable to send data to err channel");
              ioException.printStackTrace();
            }
          }
        }
      }
    }, callbackThreadPool);
  }

  private String randomExplicitHashKey() {
    return new BigInteger(128, new Random()).toString(10);
  }

  public void stop() throws IOException {
    errOutputStream.close();
    kinesis.flushSync();
    kinesis.destroy();
  }
}
