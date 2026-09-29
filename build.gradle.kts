import org.gradle.jvm.application.tasks.CreateStartScripts
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.0.21"
    application
}

repositories { mavenCentral() }

dependencies {
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.apache.commons:commons-csv:1.12.0")
    testImplementation("junit:junit:4.13.2")
}

kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

application {
    applicationName = "restaurante"
    mainClass.set("delivery.restaurante.RestauranteKt")
    applicationDefaultJvmArgs = listOf("-Dfile.encoding=UTF-8")
}

tasks.named<JavaExec>("run") { standardInput = System.`in` }

tasks.register<JavaExec>("runCliente") {
    group = "application"
    description = "Executa o aplicativo do cliente."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("delivery.cliente.ClienteKt")
    standardInput = System.`in`
    systemProperty("file.encoding", "UTF-8")
}

val clienteScripts = tasks.register<CreateStartScripts>("clienteScripts") {
    applicationName = "cliente"
    mainClass.set("delivery.cliente.ClienteKt")
    outputDir = layout.buildDirectory.dir("scriptsCliente").get().asFile
    classpath = files(tasks.jar, configurations.runtimeClasspath)
    defaultJvmOpts = listOf("-Dfile.encoding=UTF-8")
}

distributions {
    main {
        distributionBaseName.set("delivery-kotlin")
        contents {
            from(clienteScripts) { into("bin"); filePermissions { unix("rwxr-xr-x") } }
            from("README.md")
        }
    }
}

tasks.test {
    maxHeapSize = "512m"
    doFirst { systemProperty("delivery.classpath", sourceSets.main.get().runtimeClasspath.asPath) }
}
