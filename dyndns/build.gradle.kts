val pluginVersion = project.version.toString()
val apiVersion = providers.gradleProperty("apiVersion").get()

plugins {
  java
}

repositories {
  // Use Maven Central for resolving dependencies.
  mavenCentral()
  maven {
    name = "papermc"
    url = uri("https://repo.papermc.io/repository/maven-public/")
  }
}

dependencies {
  // Use JUnit Jupiter for testing.
  // testImplementation(libs.junit.jupiter)

  // testRuntimeOnly("org.junit.platform:junit-platform-launcher")

  compileOnly("io.papermc.paper:paper-api:${apiVersion}.build.+")
  implementation(fileTree("src") { include("*.jar") })
}

// Apply a specific Java toolchain to ease working on different environments.
java {
  toolchain {
    languageVersion = JavaLanguageVersion.of(26)
  }
}

tasks.processResources {
  inputs.property("version", pluginVersion)

  filesMatching("plugin.yml") {
    expand(
      "version" to pluginVersion,
      "apiVersion" to apiVersion
    )
  }
}

tasks.jar {
  destinationDirectory.set(file("$rootDir/dist"))
  archiveFileName.set("DynDNS-${pluginVersion}.jar")
}
