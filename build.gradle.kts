plugins {
    java
    application
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(23))
}

application {
    mainClass.set("core.DTNSim")  // Pastikan ini sesuai dengan class utama
}



repositories {
    mavenCentral()
}

dependencies {
    implementation(files("lib/ECLA.jar"))
    implementation(files("lib/DTNConsoleConnection.jar"))
    implementation(files("lib/junit-4.8.2.jar"))
    implementation(files("lib/uncommons-maths-1.2.1.jar"))
}

sourceSets.main {
    java.srcDirs("src")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "core.DTNSim"
    }
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}

tasks.named<JavaExec>("run") {
    dependsOn("classes")  // Pastikan semua kelas terkompilasi sebelum dijalankan
    jvmArgs = listOf("-Xmx512M")  // Alokasi memori yang cukup

    classpath = sourceSets.main.get().runtimeClasspath  // Pakai classpath yang benar dari Gradle

//    args = listOf("1", "default_settings.txt")  // Sesuaikan dengan eksekusi GUI
    args = listOf("-b","1", "config/SprayAndWait/SprayAndWaitDecisionEngine_randomwaypoint_settings.txt")  // Sesuaikan dengan eksekusi GUI


}










