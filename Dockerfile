# ---- 建置階段 ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# 先只複製 pom.xml 抓依賴：只要 pom 沒變，改 Java 檔重建時這一層可以直接用快取，不用再下載一次
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
# 映像檔建置時不跑測試：測試需要 Docker（Testcontainers），而且 CI 已經跑過了
RUN mvn -B clean package -DskipTests

# 把 jar 拆成分層，讓依賴和應用程式碼分開成不同的 image layer。
# 依賴很大但很少變，程式碼很小但常變，分開之後每次改程式只需要重建最後一層。
RUN java -Djarmode=layertools -jar target/short-url-service-*.jar extract --destination extracted

# ---- 執行階段 ----
# 只帶 JRE，不帶 JDK 和 Maven，映像檔小很多，攻擊面也小
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# 不用 root 跑應用程式：萬一應用程式被攻破，攻擊者拿到的也只是一個沒有權限的帳號
RUN addgroup -S spring && adduser -S spring -G spring
USER spring

# 順序要由「最少變動」到「最常變動」，變動的那層以下才需要重建
COPY --from=build /build/extracted/dependencies/ ./
COPY --from=build /build/extracted/spring-boot-loader/ ./
COPY --from=build /build/extracted/snapshot-dependencies/ ./
COPY --from=build /build/extracted/application/ ./

# 容器預設是 UTC，但每日統計是用伺服器時區切分日期的：
# 不設的話，台灣時間 08:00 的點擊會被算進前一天，跨日的那幾個小時統計會錯。
# JRE 自帶時區資料庫，所以 alpine 不用另外裝 tzdata。
ENV TZ=Asia/Taipei

EXPOSE 8080

# 容器的記憶體上限跟主機不同，這個參數讓 JVM 去讀 cgroup 的限制而不是主機的總記憶體
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "org.springframework.boot.loader.launch.JarLauncher"]
