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
}
