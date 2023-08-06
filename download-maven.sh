#!/bin/bash

# Determines the latest version of maven and downloads it. Integrity is checkes with corresponding checksum file.


# Fetch the HTML content and store it in a variable
html_content=$(curl -sS "https://dlcdn.apache.org/maven/maven-3/")

# Extract the lines containing version information using grep
version_lines=$(echo "$html_content" | grep -Eo '<a href=".+/">[0-9.]*/</a>' | grep -Eo '[0-9.]+/')

# Use awk to find the latest version
latest_version=$(echo "$version_lines" | awk -F'/' '{print $1}' | sort -rV | head -n1)

#echo "Latest version of Maven: $latest_version"

# Build the download urls of both the executable and the expected checksum
download_url=https://dlcdn.apache.org/maven/maven-3/${latest_version}/binaries/apache-maven-${latest_version}-bin.tar.gz
download_url_checksum=https://dlcdn.apache.org/maven/maven-3/${latest_version}/binaries/apache-maven-${latest_version}-bin.tar.gz.sha512

#echo "Download link of latest version ${download_url}"
#echo "Download link of latest version ${download_url_checksum}"

# Download the checksum
checksum=$(curl -sS "${download_url_checksum}")

echo "Checksum of maven $checksum"

# Actually download maven and check the checksums
mkdir -p /usr/share/maven /usr/share/maven/ref 
  
curl -fsSL -o /tmp/apache-maven.tar.gz ${download_url} &&\
  echo "${checksum}  /tmp/apache-maven.tar.gz" | sha512sum -c - &&\ 
  tar -xzf /tmp/apache-maven.tar.gz -C /usr/share/maven --strip-components=1 &&\ 
  rm -f /tmp/apache-maven.tar.gz  &&\
  ln -s /usr/share/maven/bin/mvn /usr/bin/mvn
