plugins {
    alias(libs.plugins.spring.boot)
    `java-library`
}

dependencies {
    implementation(project(":application"))
    implementation(project(":infrastructure"))

    implementation(platform(libs.spring.boot.bom))
    implementation(platform(libs.spring.ai.bom))
    implementation(libs.spring.boot.starter)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.ai.starter.mcp.server)

    testImplementation(libs.spring.boot.starter.test)
}

tasks.named<Test>("test") {
    workingDir = rootProject.layout.projectDirectory.asFile
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = true
    // Stable filename so MCP harness configs do not break on version bumps
    archiveFileName.set("transport.jar")
}

tasks.named<Jar>("jar") {
    enabled = false
}
