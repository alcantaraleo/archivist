plugins {
    alias(libs.plugins.spring.boot)
    `java-library`
}

dependencies {
    implementation(project(":application"))

    implementation(platform(libs.spring.boot.bom))
    implementation(platform(libs.spring.ai.bom))
    implementation(libs.spring.boot.starter)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.ai.starter.mcp.server)
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = true
}

tasks.named<Jar>("jar") {
    enabled = false
}
