FROM clojure:temurin-17-lein-bullseye

RUN apt-get update \
 && DEBIAN_FRONTEND=noninteractive \
    apt-get install --assume-yes \
      python3 \
      curl \
      netbase \
      unzip \
      zip \
 && rm -rf /var/lib/apt/lists/*
