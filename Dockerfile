# =============================================================================
# 蜀韵·智游 后端镜像（多阶段构建）
#
# 用途：Render / Koyeb 等容器平台部署；本地也可用以下命令验证：
#   docker build -t shuyun-web .
#   docker run -p 8080:8080 -e MYSQL_URL=... -e REDIS_HOST=... shuyun-web
#
# 说明：只构建 shuyun-web 及其依赖模块（common/pojo/mapper/service/ai），
#       跳过测试；前端 shuyun-vue 由 EdgeOne Pages 单独托管，不参与本镜像。
# =============================================================================

# ---- 阶段 1：Maven 编译打包 ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# 复制后端 Maven 工程（.dockerignore 已排除 node_modules / docs 等无关内容）
COPY . .

# -pl shuyun-web -am：仅构建 shuyun-web 与其上游依赖模块，跳过空壳 shuyun-admin
RUN mvn -B clean package -DskipTests -pl shuyun-web -am

# ---- 阶段 2：JRE 运行（镜像精简，启动快） ----
FROM eclipse-temurin:17-jre
WORKDIR /app

# 可执行 jar（由 spring-boot-maven-plugin 生成；若版本号变更请同步修改）
COPY --from=build /build/shuyun-web/target/shuyun-web-1.0.0.jar app.jar

EXPOSE 8080

# 说明：
#   MaxRAMPercentage=65 适配平台免费实例 512MB 内存
#   UseSerialGC 在低内存小实例下更省资源
#   user.timezone 固定东八区，避免统计/时间显示偏差
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=65", "-XX:+UseSerialGC", "-Dfile.encoding=UTF-8", "-Duser.timezone=Asia/Shanghai", "-jar", "app.jar"]
