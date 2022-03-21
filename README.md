# kplserver

Exposes the [Amazon Kinesis Producer Library](https://github.com/awslabs/amazon-kinesis-producer) via sockets for
multi-language support.

[![Docker Repository on Quay](https://quay.io/repository/turner/kplserver/status "Docker Repository on Quay")](https://quay.io/repository/turner/kplserver)

The KPL is written in Java and designed to be consumed by Java applications. This project may be useful if you're
building a data ingestion app using Kinesis in a language other than Java.

### Usage

- Point the server to your kinesis stream by setting the `AWS_DEFAULT_REGION` and `KINESIS_STREAM` environment variables. Once the server is
up and running, you can send data to Kinesis by opening a socket connection and sending utf-8 data.
- Each record you send should be delimited by a new line.
- If you don't pass in a hash key a random hash key is generated for you.
  - To pass in a hash key, add the hash key as kdshashkey on the root of your JSON object.

When service starts, it exposes two ports:
1. Inlet Port: This port is used to receive the message from your app to be sent to kinesis. The server defaults to port `3000` but can be overridden by setting the `PORT` environment variable.
2. Outlet Port: This port is used to send any message back to your app in case kplserver is not able to send message to kinesis after all retries. This port default to `3001` but can be overwritten by environment variable `ERROR_SOCKET_PORT`. Messages are not persisted if your app is not connected to this port and any such messages will be lost permanently.

### docker image

This server is available as a docker image.

```sh
docker run -it --rm \
  -p 3000:3000 \
  -p 3001:3001 \
  -e AWS_DEFAULT_REGION=us-east-1 \
  -e KINESIS_STREAM=my-stream \
  -e ERROR_SOCKET_PORT=3001 \
  quay.io/turner/kplserver
```

### sidecar

If you're consuming the KPL from an ecs/fargate container, the followning container definitions snippet from a task
definition will configure this server as a sidecar container that you can talk to over sockets via `127.0.0.1:3000`.

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
        },
        {
          "protocol": "tcp",
          "hostPort": 3001,
          "containerPort": 3001
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
          "name": "ERROR_SOCKET_PORT",
          "value": "3001"
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
