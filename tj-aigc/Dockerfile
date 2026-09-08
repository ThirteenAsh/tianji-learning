FROM eclipse-temurin:17-jre

LABEL maintainer="研究院研发组 <research-maint@itcast.cn>"

ENV TZ=Asia/Shanghai \
    JAVA_OPTS=""
hb
WORKDIR /app

COPY target/tj-aigc.jar /app/app.jar

EXPOSE 8094

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
