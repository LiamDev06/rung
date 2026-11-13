plugins {
    id("com.vanniktech.maven.publish")
}

dependencies {
    implementation("org.slf4j:slf4j-api:2.0.18")

    testRuntimeOnly("com.h2database:h2:2.4.240")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.18")
}

mavenPublishing {
    pom {
        name.set("Rung Core")
        description.set("SQL migration engine for Java where every module owns its own migrations and history.")
    }
}
