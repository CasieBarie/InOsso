FROM eclipse-temurin:17
LABEL authors="CasieBarie"
WORKDIR /app
COPY target/inosso-bot.jar bot.jar
CMD ["java", "-jar", "bot.jar"]