plugins {
    id("java")
    id("application") // 添加application插件用于运行main方法
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // HTML解析 - Jsoup
    implementation("org.jsoup:jsoup:1.17.2")

    // HTTP客户端 - 如果需要更强大的HTTP功能
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // CSV处理 - OpenCSV
    implementation("com.opencsv:opencsv:5.9")

    // JSON处理 - Jackson (如果需要处理JSON数据)
    implementation("com.fasterxml.jackson.core:jackson-databind:2.16.1")

    // 日志框架
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("ch.qos.logback:logback-classic:1.4.14")

    // 工具类库 - Apache Commons
    implementation("org.apache.commons:commons-lang3:3.14.0")
    implementation("commons-io:commons-io:2.15.1")

    // 并发工具 - 如果需要更高级的并发控制
    implementation("com.google.guava:guava:32.1.3-jre")

    // 配置文件处理
    implementation("com.typesafe:config:1.4.3")

    // 测试依赖
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core:5.8.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}


tasks.test {
    useJUnitPlatform()
}

// Java版本配置
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(19))
    }
}

// 编译配置
tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

// 指定主类
application {
    mainClass.set("com.textbook.manager.SpiderManager")
}