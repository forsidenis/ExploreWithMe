FROM amazoncorretto:21
RUN yum install -y curl && yum clean all
COPY target/*.jar app.jar
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar /app.jar"]