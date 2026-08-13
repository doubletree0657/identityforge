FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY payroll-resource-service/target/payroll-resource-service-*.jar app.jar

EXPOSE 8090

USER 10001

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
