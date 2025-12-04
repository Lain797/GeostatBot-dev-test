val springAiVersion = "1.0.0-M5"

plugins {
    id("org.springframework.boot") version "3.3.1"
    id("io.spring.dependency-management") version "1.1.5"
    id("java")
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven { url = uri("https://repo.spring.io/milestone") }
}

extra["spring-ai.version"] = springAiVersion

dependencies {
    implementation(platform("org.springframework.ai:spring-ai-bom:${property("spring-ai.version")}"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("com.google.cloud:google-cloud-speech:4.3.0")

    implementation("me.paulschwarz:spring-dotenv:4.0.0")

    implementation("org.springframework.ai:spring-ai-anthropic-spring-boot-starter:1.0.0-M3")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
