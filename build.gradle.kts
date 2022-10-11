import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.7.20"
    id("com.github.johnrengelman.shadow") version "7.1.2"
    application
}

group = "dev.moru3"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    mavenLocal()
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    testImplementation(kotlin("test"))
    compileOnly("org.spigotmc:spigot:1.16.5-R0.1-SNAPSHOT")
    implementation("com.github.moruch4nn:MinePie:d5a75463ed")
    implementation("com.google.code.gson:gson:2.9.1")
}



tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

application {
    mainClass.set("MainKt")
}

tasks.named("build") {
    dependsOn("shadowJar")
}