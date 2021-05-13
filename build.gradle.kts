import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

apply(plugin = "java")
plugins {
    kotlin("jvm") version "1.5.0"
    idea
}
group = "com.github.evgenijmelnikov"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}
dependencies {
    // https://mvnrepository.com/artifact/io.projectreactor/reactor-core
    implementation(group = "io.projectreactor", name = "reactor-core", version = "3.4.5")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.0-RC")
    testImplementation(platform("org.junit:junit-bom:5.7.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

tasks.withType<KotlinCompile>() {
    kotlinOptions.jvmTarget = "1.8"
}

