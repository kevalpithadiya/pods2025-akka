#! /bin/sh

# Build the JAR
cd /tmp/build
mvn clean install

# Copy compiled JAR to root
cp ./target/*.jar /users.jar
# Copy production override properties to root
cp ./src/main/resources/application-prod.properties /application.properties
