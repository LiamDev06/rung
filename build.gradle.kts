import com.diffplug.gradle.spotless.SpotlessExtension
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
    id("com.diffplug.spotless") version "8.8.0" apply false
    id("com.vanniktech.maven.publish") version "0.37.0" apply false
}

allprojects {
    group = "dev.liamnet"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "checkstyle")
    apply(plugin = "pmd")
    apply(plugin = "com.diffplug.spotless")

    the<JavaPluginExtension>().toolchain.languageVersion.set(JavaLanguageVersion.of(25))

    tasks.withType<JavaCompile>().configureEach {
        options.release.set(17)
        options.compilerArgs.add("-parameters")
    }

    the<CheckstyleExtension>().apply {
        toolVersion = "13.7.0"
        configFile = rootProject.file("config/checkstyle/checkstyle.xml")
    }

    the<PmdExtension>().apply {
        toolVersion = "7.26.0"
        isConsoleOutput = true
        ruleSetFiles = files(rootProject.file("config/pmd/pmd-ruleset.xml"))
        ruleSets = emptyList()
    }

    // Test sources use a ruleset that relaxes rules which misfire on integration tests; main keeps all rules.
    tasks.named<Pmd>("pmdTest") {
        ruleSetFiles = rootProject.files("config/pmd/pmd-test-ruleset.xml")
    }

    the<SpotlessExtension>().java {
        palantirJavaFormat("2.94.0")
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }

    tasks.register("lint") {
        dependsOn("spotlessCheck", "checkstyleMain", "pmdMain")
    }

    dependencies {
        "testImplementation"(platform("org.junit:junit-bom:6.0.3"))
        "testImplementation"("org.junit.jupiter:junit-jupiter")
        "testImplementation"("org.assertj:assertj-core:3.27.7")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    tasks.withType<Javadoc>().configureEach {
        exclude("**/internal/**")
        (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:all,-missing", "-quiet")
    }

    plugins.withId("com.vanniktech.maven.publish") {
        configure<MavenPublishBaseExtension> {
            publishToMavenCentral()
            if (providers.gradleProperty("signingInMemoryKey").isPresent) {
                signAllPublications()
            }

            pom {
                url.set("https://github.com/LiamDev06/rung")
                inceptionYear.set("2026")
                licenses {
                    license {
                        name.set("Apache-2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        distribution.set("repo")
                    }
                }
                developers {
                    developer {
                        id.set("LiamDev06")
                        name.set("Liam Hillefors")
                        url.set("https://github.com/LiamDev06")
                    }
                }
                scm {
                    url.set("https://github.com/LiamDev06/rung")
                    connection.set("scm:git:git://github.com/LiamDev06/rung.git")
                    developerConnection.set("scm:git:ssh://git@github.com/LiamDev06/rung.git")
                }
            }
        }
    }
}
