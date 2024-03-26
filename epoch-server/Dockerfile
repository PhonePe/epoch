FROM registry.access.redhat.com/ubi9/openjdk-17-runtime

EXPOSE 8080
EXPOSE 8081
EXPOSE 8082
EXPOSE 8083

ARG VERSION

ADD configs/docker.yml config.yml
ADD target/epoch-server-$VERSION.jar epoch-server.jar
ADD entrypoint.sh entrypoint.sh

ENTRYPOINT ["bash", "./entrypoint.sh"]

