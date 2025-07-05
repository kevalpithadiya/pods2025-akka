# Base Image with Java 21 and Maven
FROM maven:3.9.9-eclipse-temurin-21-alpine

# Build the project
ADD ./ /tmp/build
RUN chmod +x /tmp/build/docker_build_helper.sh 
RUN /tmp/build/docker_build_helper.sh

ENTRYPOINT [ "java", "-jar", "/users.jar" ]
