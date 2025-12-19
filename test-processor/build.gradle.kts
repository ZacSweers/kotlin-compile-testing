plugins {
    kotlin("jvm")
}

dependencies {
    api(projects.testAnnotations)

    implementation(libs.kotlinpoet)
    implementation(libs.javapoet)
}
