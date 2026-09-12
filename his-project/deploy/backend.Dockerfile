# 构建后端镜像：请先在宿主机执行
#   cd his-backend && mvn -DskipTests package
# 再在 deploy 目录执行 docker compose build / up -d
# 注意：compose 中 build.context 指向工程根目录（his-project/）
FROM eclipse-temurin:17.0.10_7-jre

WORKDIR /app
COPY his-backend/target/his-backend-0.1.0-SNAPSHOT.jar app.jar

ENV TZ=Asia/Shanghai
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
