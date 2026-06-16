val avdName = "openballistics"
val sdkHome: String = System.getenv("ANDROID_HOME")
    ?: rootProject.file("local.properties")
        .takeIf { it.exists() }
        ?.readLines()
        ?.firstOrNull { it.startsWith("sdk.dir=") }
        ?.substringAfter("sdk.dir=")
    ?: error("Set ANDROID_HOME or sdk.dir in local.properties")

val adb = "$sdkHome/platform-tools/adb"

tasks.register("start") {
    group = "emulator"
    description = "Start the Android emulator in background"
    doLast {
        val process = ProcessBuilder("$sdkHome/emulator/emulator", "-avd", avdName, "-no-snapshot-save")
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .start()
        Thread.sleep(3_000)
        if (!process.isAlive) {
            throw GradleException("Emulator crashed on startup (exit code: ${process.exitValue()})")
        }
        println("Emulator running (pid: ${process.pid()}). Use :android:emulator:wait to wait for boot.")
    }
}

tasks.register<Exec>("stop") {
    group = "emulator"
    description = "Stop the running Android emulator"
    commandLine(adb, "emu", "kill")
    isIgnoreExitValue = true
}

tasks.register<Exec>("status") {
    group = "emulator"
    description = "Check if emulator is running"
    commandLine(adb, "devices")
    isIgnoreExitValue = true
}

tasks.register<Exec>("wait") {
    group = "emulator"
    description = "Wait for emulator to finish booting"
    commandLine(
        adb, "wait-for-device", "shell",
        """while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 1; done""",
    )
}
