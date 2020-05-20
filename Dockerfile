FROM maven:3.6.3-jdk-8 AS build 
WORKDIR /usr/src/app
COPY . .
RUN mvn clean install

# copy app, deps, and native binaries into lighter image
FROM openjdk:8-jre-slim
WORKDIR /usr/src/app
COPY --from=build /usr/src/app/target/app.jar /usr/src/app/
COPY --from=build /usr/src/app/target/deps/ /usr/src/app/deps/
CMD ["java","-jar","app.jar"]