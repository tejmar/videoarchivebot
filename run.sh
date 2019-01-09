#!/bin/sh

cd "$(dirname "$0")"
./gradlew build
java -jar build/libs/videoarchivebot-1.0.0.jar
