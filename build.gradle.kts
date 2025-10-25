import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import org.gradle.kotlin.dsl.*
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.file.Files

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
    pb.redirectErrorStream(true)
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

tasks.register("installPanamaGenerator") {
    val os = DefaultNativePlatform.getCurrentOperatingSystem()
    val mvn = if(os.isLinux){"mvn"} else {"mvn.cmd"};

    val url = "https://github.com/dreamlike-ocean/PanamaUring"
    val hash = "0613da385d6cfa79dd94f5e1765f228f812bf140"

    val dir = layout.projectDirectory.dir("code/PanamaUring")

    if (!dir.asFile.exists()||dir.asFile.listFiles().isEmpty()) {
        dir.asFile.mkdirs()
        if (execCommand( "git", "clone", url,dir = dir.dir("..").asFile) != 0 ){
            throw RuntimeException("Failed to clone PanamaUring")
        }
    }
    execCommand("git", "checkout", "-f", hash,dir = dir.asFile)

    val pom = dir.file("panama-generator/pom.xml")
    val lines = pom.asFile.readLines(Charsets.UTF_8).toMutableList()
    if (lines[39].contains("panama-generator-test-native")){
        //skip the test-native build
        for (idx in 37..40) {
            lines.removeAt(37)
        }
        pom.asFile.writeText(lines.joinToString("\n"))
    }



    execCommand(mvn,"install","-DskipTests","-Dgpg.skip=true","-pl", ":panama-generator","-am",dir = dir.asFile)
}

tasks.register("installBoringtun"){

    if (execCommand("cargo","--version")!=0){
        throw RuntimeException("Rust installation required")
    }

    val url = "https://github.com/cloudflare/boringtun.git"
    val hash = "08bc5ed19b797d8a741bb4f3bea1d627d8301735"

    val dir = layout.projectDirectory.dir("code/boringtun")
    val target = layout.projectDirectory.dir("src/main/resources/META-INF/native")
    val lib = System.mapLibraryName("boringtun")

    if (!dir.asFile.exists()||dir.asFile.listFiles().isEmpty()) {
        dir.asFile.mkdirs()
        if (execCommand( "git", "clone", url,dir = dir.dir("..").asFile) != 0 ){
            throw RuntimeException("Failed to clone boringtun")
        }
    }
    execCommand("git", "checkout", "-f", hash,dir = dir.asFile)

    if (execCommand("cargo","rustc","-p","boringtun","--lib","--release","--features=ffi-bindings","--crate-type","cdylib",dir = dir.asFile)!=0){
        throw RuntimeException("Failed to build boringtun shareLibrary")
    }

    val libFile = dir.file("target/release/$lib").asFile
    if (!libFile.exists()){
        throw RuntimeException("Failed to build boringtun shareLibrary")
    }
    libFile.copyTo(target.file(lib).asFile,true)
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
    implementation("io.github.dreamlike-ocean:panama-generator:4.2.0-SNAPSHOT")
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
