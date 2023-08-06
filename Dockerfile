# The first image uses graalvm to build a native executable for the webserver
FROM ghcr.io/graalvm/native-image:ol8-java17-22 AS builder

# Install tar and gzip to extract the Maven binaries
RUN microdnf update \
  && microdnf install --nodocs \
  tar \
  gzip \
  && microdnf clean all \
  && rm -rf /var/cache/yum

# Install Maven
# Source:
# 1) https://github.com/carlossg/docker-maven/blob/925e49a1d0986070208e3c06a11c41f8f2cada82/openjdk-17/Dockerfile
# 2) https://maven.apache.org/download.cgi
ARG USER_HOME_DIR="/root"

COPY ./download-maven.sh /tmp/download-maven.sh

RUN /tmp/download-maven.sh

ENV MAVEN_HOME /usr/share/maven
ENV MAVEN_CONFIG "$USER_HOME_DIR/.m2"

# Set the working directory to /home/app
WORKDIR /build

# Copy the source code into the image for building
COPY . /build

# Build
RUN mvn clean install -Dnative

# Build whisperX and copy the server binary created before
FROM ubuntu:latest AS final

RUN apt update && \
  apt install -y git pip python3 ffmpeg && \
  rm -rf /var/lib/apt/lists/* 

RUN git clone https://github.com/m-bain/whisperX.git && \
  cd whisperX && \
  pip install -e .

# Cache for downloaded models
RUN mkdir -p /root/.cache
VOLUME /root/.cache

# Server port
EXPOSE 8080

# Copy the native executable into the containers
COPY --from=builder /build/target/*-runner /whisperX/app
WORKDIR /whisperX
ENTRYPOINT ["/whisperX/app"]