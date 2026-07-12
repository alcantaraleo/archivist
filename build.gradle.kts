plugins {
    java
}

version = property("version") as String

subprojects {
    apply(plugin = "java-library")

    group = "io.archivist"
    version = rootProject.version

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
