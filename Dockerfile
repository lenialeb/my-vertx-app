# Use an official OpenJDK runtime as a parent image
FROM openjdk:11-jre-slim

# Set the working directory in the container
WORKDIR /app

# Copy the JAR file from your target directory to the container
COPY target/my-vertx-app-1.0-SNAPSHOT.jar app.jar
COPY .env ./
# Command to run the application
ENTRYPOINT ["java", "-jar", "app.jar"]