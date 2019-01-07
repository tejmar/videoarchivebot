import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  kotlin("jvm") version "1.3.11"
}

group = "therealfarfetchd.videoarchivebot"
version = "1.0.0"

tasks.withType<KotlinCompile> {
  kotlinOptions.freeCompilerArgs += "-Xuse-experimental=kotlin.ExperimentalUnsignedTypes"
}

repositories {
  mavenCentral()
  jcenter()
}

dependencies {
  implementation(kotlin("stdlib-jdk8"))
  implementation(group = "net.dean.jraw", name = "JRAW", version = "1.1.0")
}

tasks.withType<KotlinCompile> {
  kotlinOptions.jvmTarget = "1.8"
}