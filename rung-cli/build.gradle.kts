plugins {
    application
}

application {
    applicationName = "rung"
    mainClass.set("dev.liamnet.rung.cli.MigrationRunner")
}

dependencies {
    implementation(project(":rung-core"))
    implementation("info.picocli:picocli:4.7.6")

    runtimeOnly("org.postgresql:postgresql:42.7.12")
    runtimeOnly("org.slf4j:slf4j-simple:2.0.18")
}
