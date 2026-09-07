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
    doLast {
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

}

tasks.register("installBoringtun"){
    doLast {
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

}

/*
 * Find the real (non-symlink) shared library file starting with prefix in dir
 * and copy it to toDir as destName
 */
fun copySharedLib(dir:File, prefix:String, toDir:File, destName:String){
    val lib = dir.listFiles { f -> f.isFile && f.name.startsWith(prefix) && f.name.contains(".so.") }
        ?.maxByOrNull { it.name } ?: throw RuntimeException("No $prefix shared library found in ${dir.absolutePath}")
    lib.copyTo(File(toDir, destName),true)
}

/*
 * One-shot pipeline: clone xdp-tools (pinned hash) -> build libbpf/libxdp shared libs ->
 * copy libbpf.so/libxdp.so to META-INF/native -> compile the eBPF program standalone ->
 * copy xdp_redirect_prog.o to META-INF/ebpf
 *
 * Note: the eBPF headers shipped with xdp-tools (xdp/xdp_helpers.h, xdp/parsing_helpers.h)
 * only exist at specific commits and change between releases (e.g. parse_arphdr was
 * added later). Pin the hash instead of following master or a tag.
 */
tasks.register("installLibxdp"){
    doLast {
        println("Required tools: git, make, gcc, clang, pkg-config, m4, readelf, objcopy, linux-libc-dev")
        println("Install (Debian/Ubuntu): apt install -y git make gcc clang pkg-config m4 binutils linux-libc-dev")

        val url = "https://github.com/xdp-project/xdp-tools"
        val hash = "883a9b36e5624d0c201099f60f24ea36848b6b48"
        val dir = layout.projectDirectory.dir("code/xdp-tools")
        val projectDir = layout.projectDirectory.asFile

        if (!dir.asFile.exists()||dir.asFile.listFiles().isEmpty()) {
            dir.asFile.mkdirs()
            if (execCommand( "git", "clone", "--recurse-submodules", url, dir = dir.dir("..").asFile) != 0 ){
                throw RuntimeException("Failed to clone xdp-tools")
            }
        }
        execCommand("git", "checkout", "-f", hash, dir = dir.asFile)
        execCommand("git", "submodule", "update", "--init", "--recursive", "-f", dir = dir.asFile)

        /*
         * Only build the lib layer (libbpf.a+libbpf.so, libxdp.a+libxdp.so), not the tools.
         * FORCE_SUBDIR_LIBBPF=1: always use the bundled libbpf submodule, statically linked
         * into libxdp.so (matching the META-INF/native artifacts). Otherwise configure would
         * detect an installed libbpf-dev (SYSTEM_LIBBPF=y) and make libxdp.so depend on the
         * system libbpf dynamically.
         */
        if (execCommand("make", "libxdp", "-j", Runtime.getRuntime().availableProcessors().toString(),
                env = arrayOf("FORCE_SUBDIR_LIBBPF=1"), dir = dir.asFile) != 0){
            throw RuntimeException("Failed to build libbpf/libxdp")
        }

        val nativeDir = layout.projectDirectory.dir("src/main/resources/META-INF/native").asFile
        nativeDir.mkdirs()
        copySharedLib(dir.file("lib/libbpf/src").asFile, "libbpf", nativeDir, "libbpf.so")
        copySharedLib(dir.file("lib/libxdp").asFile, "libxdp", nativeDir, "libxdp.so")

        /*
         * Compile the eBPF program standalone. Include paths:
         *  - headers/                      : xdp/xdp_helpers.h, xdp/parsing_helpers.h (and vendored linux/bpf.h)
         *  - lib/libbpf/src/root/include   : bpf/bpf_helpers.h, bpf/bpf_endian.h (generated by libbpf install_headers)
         *  - /usr/include/<multiarch>      : <asm/types.h> (indirectly required by linux/bpf.h)
         * Remaining linux headers (if_ether/if_arp/ip/ipv6/icmpv6/tcp/udp/in) come from system /usr/include/linux.
         */
        val arch = DefaultNativePlatform.getCurrentArchitecture()
        val multiarch = if (arch.isAmd64) {"x86_64-linux-gnu"} else {"aarch64-linux-gnu"}
        val incXdp = dir.file("headers").asFile.absolutePath
        val incBpf = dir.file("lib/libbpf/src/root/include").asFile.absolutePath
        val ebpfDir = layout.projectDirectory.dir("src/main/resources/META-INF/ebpf").asFile
        ebpfDir.mkdirs()

        if (execCommand("clang",
                "-O2", "-g", "-Wall", "-Werror", "-target", "bpf", "-std=gnu2x",
                "-Wno-unused-value", "-Wno-pointer-sign", "-Wno-compare-distinct-pointer-types",
                "-Wno-visibility", "-fno-stack-protector",
                "-I$incXdp", "-I$incBpf", "-I/usr/include/$multiarch",
                "-c", "src/main/c/ebpf/xdp_redirect_prog.c",
                "-o", "src/main/resources/META-INF/ebpf/xdp_redirect_prog.o",
                dir = projectDir) != 0){
            throw RuntimeException("Failed to compile xdp_redirect_prog.o")
        }

        println("libbpf.so/libxdp.so -> src/main/resources/META-INF/native")
        println("xdp_redirect_prog.o  -> src/main/resources/META-INF/ebpf")
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
    implementation("com.github.ben-manes.caffeine:caffeine:3.2.4")
    implementation("com.maxmind.geoip2:geoip2:5.0.2")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.84")
    implementation("io.github.dreamlike-ocean:panama-generator:4.2.0-SNAPSHOT")
    implementation("io.vertx:vertx-core:5.0.12")
    implementation("io.vertx:vertx-web:5.0.12")
    implementation("io.vertx:vertx-config:5.0.12")
    implementation("io.vertx:vertx-config-yaml:5.0.12")
    implementation("ch.qos.logback:logback-core:1.5.25")
    implementation("ch.qos.logback:logback-classic:1.5.25")

    implementation("org.apache.sshd:sshd-core:2.15.0")

    implementation("org.stormpx.partialtcp:net-netty:0.0.8")

    implementation("org.drasyl:netty-tun:1.2.5")

    implementation("io.netty:netty-transport-native-epoll:4.2.13.Final:linux-${if(arch.isAmd64) {"x86_64"} else { "aarch_64" }}")


    if (os.isWindows){
        implementation("io.netty:netty-tcnative-boringssl-static:2.0.71.Final:windows-x86_64")
    }else {
        implementation("io.netty:netty-tcnative-boringssl-static:2.0.71.Final:${if(os.isLinux){"linux"}else {"osx"}}-${if(arch.isAmd64){"x86_64"} else {"aarch_64"}}")
    }


    testImplementation(group = "junit", name = "junit", version = "4.13.1")



    testImplementation("io.netty:netty-pkitesting:4.2.12.Final")

    testImplementation("org.testcontainers:testcontainers:1.21.2")
    testImplementation("org.testcontainers:testcontainers-nginx:2.0.1")

    testImplementation("org.openjdk.jol:jol-core:0.16")

    testImplementation("org.openjdk.jmh:jmh-core:1.36")
    testImplementation("org.openjdk.jmh:jmh-generator-annprocess:1.36")
    testAnnotationProcessor("org.openjdk.jmh:jmh-generator-annprocess:1.36")

}
