plugins {
    `java-library`
}

dependencies {
    api(project(":domain"))
    api(project(":application"))

    implementation(platform(libs.spring.boot.bom))
    implementation(platform(libs.spring.ai.bom))
    implementation(libs.spring.boot.starter)
    implementation(libs.spring.ai.commons)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.snakeyaml)
    implementation(libs.lucene.core)
    implementation(libs.lucene.analysis.common)
    implementation(libs.lucene.queryparser)

    testImplementation(libs.spring.boot.starter.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.named<Test>("test") {
    workingDir = rootProject.layout.projectDirectory.asFile
}
