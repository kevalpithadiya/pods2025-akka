# # Base Image with Java 21 and Maven
# FROM maven:3.9.9-eclipse-temurin-21-alpine

# # Set the working directory
# WORKDIR /app


# # Copy src and pom.xml files to the container
# COPY pom.xml .
# COPY src ./src


# # # Phase 1 || we use application.conf for phase 2
# # # Delete existing application.conf in resource directory
# # RUN rm -rf src/main/resources/application.conf
# # # Copy the application-docker.conf file to the resources directory
# # COPY src/main/resources/application-docker.conf src/main/resources/application.conf


# # compile
# RUN mvn clean install
# # run java -jar target/marketplace-akka-1.0-allinone.jar 
# # ENTRYPOINT ["java", "-jar", "target/marketplace-akka-1.0-allinone.jar"]
# # ENTRYPOINT ["java", "-Dconfig.file=src/main/resources/application.conf", "-jar", "target/marketplace-akka-1.0-allinone.jar"]

# # Delete all copied files
# # RUN rm -rf pom.xml src
# # RUN rm -rf src

# # Expose port 8080
# EXPOSE 8080


FROM maven:3.9.9-eclipse-temurin-21-alpine

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn clean install

# Set main class here
ENTRYPOINT ["mvn", "exec:java", "-Dexec.mainClass=com.keval.pods.Main"]
