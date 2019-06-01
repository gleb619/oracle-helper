FROM openjdk:8-jdk-alpine
ENV SCRIPT_PATH=/tmp/scripts
COPY build/libs/*.jar /tmp/app.jar
COPY build/resources/main/scripts /tmp/scripts
ENTRYPOINT [ "java", "-Djava.security.egd=file:/dev/./urandom", "-server", "-noverify", "-Xms64m", "-Xmx64m", "-Xss512k", "-XX:MetaspaceSize=32m", "-XX:MaxMetaspaceSize=32m", "-Dfile.encoding=UTF8", "-DuriEncoding=UTF-8", "-XX:+PrintCommandLineFlags", "-XshowSettings:vm", "-XX:+UseSerialGC", "-XX:+ScavengeBeforeFullGC", "-XX:+ExplicitGCInvokesConcurrentAndUnloadsClasses", "-XX:+AlwaysPreTouch", "-XX:+UnlockExperimentalVMOptions", "-XX:+UseCGroupMemoryLimitForHeap", "-jar", "/tmp/app.jar"]