plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":shared"))
    implementation("io.ktor:ktor-server-core:3.6.0")
    implementation("io.ktor:ktor-server-netty:3.6.0")
    implementation("io.ktor:ktor-server-auth:3.6.0")
    implementation("io.ktor:ktor-server-content-negotiation:3.6.0")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.6.0")
    implementation("io.ktor:ktor-client-core:3.6.0")
    implementation("io.ktor:ktor-client-cio:3.6.0")
    runtimeOnly("org.slf4j:slf4j-simple:2.0.19")

    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-test-host:3.6.0")
    testImplementation("io.ktor:ktor-client-mock:3.6.0")
}

application {
    mainClass.set("ar.com.freno.server.ApplicationKt")
}
