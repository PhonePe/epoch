FROM ubuntu:jammy

USER root
RUN apt-get update && apt-get install -y openjdk-17-jre-headless

EXPOSE 8080
EXPOSE 8081

ARG VERSION

ADD configs/docker.yml config.yml
ADD target/epoch-server-$VERSION.jar epoch-server.jar
ADD entrypoint.sh entrypoint.sh

ENTRYPOINT ["bash", "./entrypoint.sh"]

