plugins {
    java
}

subprojects {
    apply(plugin = "java-library")

    group = "io.archivist"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}
