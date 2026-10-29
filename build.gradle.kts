plugins {
	java
	id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.openapi.generator") version "7.10.0"
}

group = "org.corp.meet-reserve"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-data-redis")
	implementation("org.springframework.boot:spring-boot-starter-liquibase")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.0")
	compileOnly("org.projectlombok:lombok")
	developmentOnly("org.springframework.boot:spring-boot-docker-compose")
	runtimeOnly("org.postgresql:postgresql")
	annotationProcessor("org.projectlombok:lombok")
	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	testImplementation("org.springframework.boot:spring-boot-starter-data-redis-test")
	testImplementation("org.springframework.boot:spring-boot-starter-liquibase-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testCompileOnly("org.projectlombok:lombok")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	testAnnotationProcessor("org.projectlombok:lombok")
}

tasks.withType<Test> {
	useJUnitPlatform()
}

openApiGenerate {
    generatorName.set("spring")
    inputSpec.set("$rootDir/openapi/openapi.yaml")
    outputDir.set("$buildDir/generated/openapi")
    apiPackage.set("org.corp.meet_reserve.api")
    modelPackage.set("org.corp.meet_reserve.api.model")
    invokerPackage.set("org.corp.meet_reserve.api.invoker")
    configOptions.set(
        mapOf(
            "interfaceOnly" to "true",
            "skipDefaultInterface" to "true",
            "useTags" to "true",
            "useSpringBoot3" to "true",
            "dateLibrary" to "java8",
            "useJakartaEe" to "true",
            "annotationLibrary" to "none",
            "documentationProvider" to "none",
            "openApiNullable" to "false"
        )
    )
    globalProperties.set(
        mapOf(
            "apiTests" to "false",
            "modelTests" to "false"
        )
    )
}

tasks.named("compileJava") {
    dependsOn("openApiGenerate")
}

sourceSets {
    main {
        java.srcDir("$buildDir/generated/openapi/src/main/java")
    }
}
