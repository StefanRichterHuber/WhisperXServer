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
ARG SHA=400fc5b6d000c158d5ee7937543faa06b6bda8408caa2444a9c947c21472fde0f0b64ac452b8cec8855d528c0335522ed5b6c8f77085811c7e29e1bedbb5daa2
ARG MAVEN_DOWNLOAD_URL=https://dlcdn.apache.org/maven/maven-3/3.9.3/binaries/apache-maven-3.9.3-bin.tar.gz

RUN mkdir -p /usr/share/maven /usr/share/maven/ref \
  && curl -fsSL -o /tmp/apache-maven.tar.gz ${MAVEN_DOWNLOAD_URL} \
  && echo "${SHA}  /tmp/apache-maven.tar.gz" | sha512sum -c - \
  && tar -xzf /tmp/apache-maven.tar.gz -C /usr/share/maven --strip-components=1 \
  && rm -f /tmp/apache-maven.tar.gz \
  && ln -s /usr/share/maven/bin/mvn /usr/bin/mvn

ENV MAVEN_HOME /usr/share/maven
ENV MAVEN_CONFIG "$USER_HOME_DIR/.m2"

# Set the working directory to /home/app
WORKDIR /build

# Copy the source code into the image for building
COPY . /build

# Build
RUN mvn clean install -Dnative

# Build whisperX and copy the server binary
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
COPY --from=builder /build/target/whisperX-server-1.0.0-SNAPSHOT-runner .
ENTRYPOINT ["/whisperX-server-1.0.0-SNAPSHOT-runner"]