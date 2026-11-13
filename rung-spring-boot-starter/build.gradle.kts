val springBootVersion = "4.1.1"

dependencies {
    api(project(":rung-core"))

    // Consumers on Spring Boot's dependency management resolve this to their own Boot version.
    api("org.springframework.boot:spring-boot-starter-jdbc:$springBootVersion")

    testImplementation("org.springframework.boot:spring-boot-test:$springBootVersion")
    testRuntimeOnly("com.h2database:h2:2.4.240")
}
