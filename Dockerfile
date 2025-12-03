FROM eclipse-temurin:21-jre
WORKDIR /app
LABEL authors="ischi & leyre"

COPY Documents/Message/HELP.txt /app/Documents/Message/HELP.txt
COPY Documents/Message/RULES.txt /app/Documents/Message/RULES.txt
COPY target/card-jitsus-1.0-SNAPSHOT.jar card-jitsus.jar

EXPOSE 6433

# ENTRYPOINT runs the Java app
ENTRYPOINT ["java", "-jar", "/app/card-jitsus.jar"]

# Default argument (can be overridden)
CMD ["server"]
