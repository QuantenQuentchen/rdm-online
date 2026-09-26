val ktorVersion: String = project.property("ktorVersion") as String
val exposedVersion: String = project.property("exposedVersion") as String

plugins {
    application
    kotlin("jvm") version "2.4.20"
    kotlin("plugin.serialization") version "2.4.20"
    id("io.ktor.plugin") version "3.5.0"
}

group = "org.rdm.online"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))

    // kotlinx.serialization runtime
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

    /*
    * Ktor
    */
    // Status pages plugin (provides StatusPages)
    implementation("io.ktor:ktor-server-status-pages:${ktorVersion}")
    // Plugin for integration for kotlinx.serialization
    implementation("io.ktor:ktor-server-content-negotiation:${ktorVersion}")
    implementation("io.ktor:ktor-serialization-kotlinx-json:${ktorVersion}")
    implementation("io.ktor:ktor-server-core:${ktorVersion}")
    implementation("io.ktor:ktor-server-netty:${ktorVersion}")
    implementation("io.ktor:ktor-server-sse:${ktorVersion}")
    implementation("io.ktor:ktor-server-openapi:${ktorVersion}")
    implementation("io.ktor:ktor-server-routing-openapi:${ktorVersion}")
    implementation("io.ktor:ktor-server-swagger:${ktorVersion}")
    implementation("io.ktor:ktor-server-cors:${ktorVersion}")
    testImplementation("io.ktor:ktor-server-test-host:${ktorVersion}")
    testImplementation("io.ktor:ktor-client-core:${ktorVersion}")

    /*
    * Exposed (for the crime of writing JVM code)
    */
    implementation("org.jetbrains.exposed:exposed-core:${exposedVersion}")
    implementation("org.jetbrains.exposed:exposed-jdbc:${exposedVersion}")
    implementation("org.jetbrains.exposed:exposed-dao:${exposedVersion}")
    implementation("org.xerial:sqlite-jdbc:3.46.1.3")

    implementation("org.jetbrains.kotlinx:kotlinx-cli-jvm:0.3.6")

    //implementation("ch.qos.logback:logback-classic:1.5.13")
}

application {
    mainClass.set("${group}.MainKt")
}

kotlin {
    jvmToolchain(25)
}

ktor {
    openApi {
        enabled = true
        codeInferenceEnabled = true
        onlyCommented = false
    }
}
//Why do this ? Because windows
/*val npm = if (System.getProperty("os.name").lowercase().contains("win")) {
    "npm.cmd"
} else {
    "npm"
}

tasks.register<Exec>("frontendNpmCi") {
    description = "Run Frontend ci/cd (we might not have it but I found the ci command and this seems professional)"
    workingDir("../")
    commandLine(npm, "ci")
}

tasks.register<Exec>("frontendBuild") {
    description = "Build the frontend using npm"
    dependsOn("frontendNpmCi")
    workingDir("../")
    commandLine(npm, "run", "build")
}

tasks.named<ProcessResources>("processResources") {
    dependsOn("frontendBuild")

    from(layout.projectDirectory.dir("../dist")) {
        into("generated/dist")
    }
}

tasks.named<JavaExec>("run") {
    description = "Run the backend (it will serve the frontend)"
    group = "custom-run"
    dependsOn("processResources")


    val appArgs = (System.getenv("APP_ARGS")
        ?.split("|")
        ?: emptyList()).toMutableList()
    args(appArgs)
}

tasks.register<JavaExec>("runHeadless") {
    description = "Run the backend raw (it will not serve the frontend, but it will serve the API)"
    group = "custom-run"
    dependsOn("classes")

    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set(application.mainClass)


    val appArgs = (System.getenv("APP_ARGS")
        ?.split("|")
        ?: emptyList()).toMutableList()
    appArgs += "--headless"

    args(appArgs)
}


tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    //You can be fucking glad I didn't call it obese.
    archiveFileName.set("FlowHub-$version-NoDep.jar")
    dependsOn("processResources")
}

tasks.register("packageRelease") {
    description = "Package the backend into a fat jar for release"
    group = "custom-deploy"
    dependsOn("shadowJar")
}*/

tasks.test {
    useJUnitPlatform()
}