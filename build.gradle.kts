import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import org.gradle.kotlin.dsl.*

plugins {
//    java
    application
    id("com.gradleup.shadow") version "9.1.0"
    id("me.champeau.jmh") version "0.7.2"
    id("com.google.protobuf") version "0.9.6"
}

group="io.crowds"
version="1.0-SNAPSHOT"

//sourceCompatibility = 24
//targetCompatibility = 24
interface InjectedExecOps {
    @get:Inject val execOps: ExecOperations
}


fun execCommand(vararg command: String, env: Array<String>? = null, dir:File? = null): Int {
    val pb = ProcessBuilder(command.asList()).directory(dir)
    val envMap = pb.environment()
    if (env != null) {
        env.forEach {
            val (key, value) = it.split("=", limit = 2)
            envMap[key] = value
        }
    }

    val process = pb.start()

    process.inputReader().let {
        while (true){
            val line = it.readLine() ?: break
            println(line)
        }
    }
    val exitVal = process.waitFor()
    process.destroy()
    return exitVal
}


protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.22.0"
    }
    generateProtoTasks {
        ofSourceSet("main")
    }
}

repositories {
    mavenLocal()
    mavenCentral()
}

application{
    mainClass = "io.crowds.Main"
    applicationDefaultJvmArgs = listOf("-ea", "--enable-preview")
}

tasks.compileJava{
    options.compilerArgs.add("--enable-preview")
}

tasks.test{
    jvmArgs = listOf("--enable-preview")
}

tasks.compileTestJava {
    options.compilerArgs = listOf("--enable-preview")
}

tasks.shadowJar{
    archiveBaseName.set("ddnsp")
    archiveClassifier.set("")
    archiveVersion.set("")
}




jmh{
    jvmArgs = listOf("--enable-preview")
}


dependencies {

    val os = DefaultNativePlatform.getCurrentOperatingSystem()
    val arch = DefaultNativePlatform.getCurrentArchitecture()
    logger.info("os is ${os.toFamilyName()}")
    logger.info("arch is ${arch.name}")
    implementation("com.maxmind.geoip2:geoip2:3.0.0")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.80")
    implementation("io.vertx:vertx-core:5.0.0")
    implementation("io.vertx:vertx-web:5.0.0")
    implementation("io.vertx:vertx-config:5.0.0")
    implementation("io.vertx:vertx-config-yaml:5.0.0")
    implementation("ch.qos.logback:logback-core:1.5.13")
    implementation("ch.qos.logback:logback-classic:1.5.13")

    implementation("org.apache.sshd:sshd-core:2.15.0")

    implementation("org.stormpx.partialtcp:net-netty:0.0.6")

    implementation("org.drasyl:netty-tun:1.2.5")
    implementation("io.netty:netty-codec-protobuf:4.2.1.Final")

    implementation("io.netty:netty-transport-native-epoll:4.2.1.Final:linux-${if(arch.isAmd64) {"x86_64"} else { "aarch_64" }}")


    if (os.isWindows){
        implementation("io.netty:netty-tcnative-boringssl-static:2.0.71.Final:windows-x86_64")
    }else {
        implementation("io.netty:netty-tcnative-boringssl-static:2.0.71.Final:${if(os.isLinux){"linux"}else {"osx"}}-${if(arch.isAmd64){"x86_64"} else {"aarch_64"}}")
    }


    testImplementation(group = "junit", name = "junit", version = "4.13.1")



    testImplementation("io.netty:netty-pkitesting:4.2.1.Final")

    testImplementation("org.testcontainers:testcontainers:1.21.2")
    testImplementation("org.testcontainers:testcontainers-nginx:2.0.1")

    testImplementation("org.openjdk.jol:jol-core:0.16")

    testImplementation("org.openjdk.jmh:jmh-core:1.36")
    testImplementation("org.openjdk.jmh:jmh-generator-annprocess:1.36")
    testAnnotationProcessor("org.openjdk.jmh:jmh-generator-annprocess:1.36")

}
