# kplserver

Exposes the [Amazon Kinesis Producer Library](https://github.com/awslabs/amazon-kinesis-producer) via sockets for multi-language support.

[![Docker Repository on Quay](https://quay.io/repository/turner/kplserver/status "Docker Repository on Quay")](https://quay.io/repository/turner/kplserver)

The KPL is written in Java and designed to be consumed by Java applications.  This project may be useful if you're building a data ingestion app using Kinesis in a language other than Java.

### usage

The server defaults to port `3000` but can be overridden by setting the `PORT` environment variable.  Point the server to your kinesis stream by setting the `AWS_DEFAULT_REGION` and `KINESIS_STREAM` environment variables.  Once the server is up and running, you can send data to Kinesis by opening a socket connection and sending utf-8 data.  Each record you send should be delimited by a new line. 

Optionally, you can provide an AWS SQS URL with env var `DLQ_URL` where any message which cannot be put in kinesis stream for any reason will be posted.


### docker image

This server is available as a docker image.

```sh
docker run -it --rm \
  -p 3000:3000 \
  -e AWS_DEFAULT_REGION=us-east-1 \
  -e KINESIS_STREAM=my-stream \
  -e DLQ_URL=my-sql-url \
  quay.io/turner/kplserver
```

### sidecar

If you're consuming the KPL from an ecs/fargate container, the followning container definitions snippet from a task definition will configure this server as a sidecar container that you can talk to over sockets via `127.0.0.1:3000`.

<details><summary>task definition example</summary>

```json
{
  "containerDefinitions": [
    {
      "name": "app",
      "image": "1234567890.dkr.ecr.us-east-1.amazonaws.com/my-service:0.1.0",
      "dependsOn": [
        {
          "containerName": "kpl",
          "condition": "START"
        }
      ]
    },
    {
      "name": "kpl",
      "image": "quay.io/turner/kplserver:0.1.0",
      "portMappings": [
        {
          "protocol": "tcp",
          "hostPort": 3000,
          "containerPort": 3000
        }
      ],
      "environment": [
        {
          "name": "KINESIS_STREAM",
          "value": "my-stream"
        },
        {
          "name": "PORT",
          "value": "3000"
        },
        {
          "name": "DLQ_URL",
          "value": "https://sqs.us-east-1.amazonaws.com/651850529327/kinesis-dlq"
        }
      ]
    }
  ]
}
```

</details>


### client libraries

- [Go](https://github.com/turnerlabs/kplclientgo)


### development

```
 Choose a make command to run

  build   build jar and deps
  start   build and start local server
```
