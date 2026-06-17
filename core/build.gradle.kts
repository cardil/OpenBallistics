plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(17)

    jvm()

    @Suppress("UnstableApiUsage")
    android {
        namespace = "org.openballistics.core"
        compileSdk = 35
        minSdk = 26
    }

    sourceSets {
        commonMain.dependencies {
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}

val fixturesDir = rootProject.file("fixtures")
val fixturesVenvDir = fixturesDir.resolve(".venv")
val fixturesOutputFile = file("src/commonTest/resources/fixtures.json")

val setupFixturesVenv by tasks.registering(Exec::class) {
    description = "Set up Python venv for fixture generation"
    group = "verification"
    onlyIf { !fixturesVenvDir.resolve("bin/python3").exists() }
    commandLine("python3", "-m", "venv", fixturesVenvDir.absolutePath)
}

val installFixturesDeps by tasks.registering(Exec::class) {
     description = "Install py-ballisticcalc in fixtures venv"
     group = "verification"
     dependsOn(setupFixturesVenv)
     inputs.file(fixturesDir.resolve("requirements.txt"))
     outputs.dir(fixturesVenvDir.resolve("lib"))
     commandLine(
         fixturesVenvDir.resolve("bin/pip").absolutePath,
         "install", "-q", "-r",
         fixturesDir.resolve("requirements.txt").absolutePath
     )
 }

val generateFixtures by tasks.registering(Exec::class) {
    description = "Generate ballistic test fixtures using py-ballisticcalc"
    group = "verification"
    dependsOn(installFixturesDeps)

    outputs.upToDateWhen { false }

    doFirst { fixturesOutputFile.parentFile.mkdirs() }

    commandLine(
        fixturesVenvDir.resolve("bin/python3").absolutePath,
        fixturesDir.resolve("generate.py").absolutePath
    )

    listOf("FIXTURE_COUNT", "FIXTURE_CHECKPOINTS", "FIXTURE_SEED", "FIXTURE_STEP").forEach { key ->
        System.getenv(key)?.let { environment(key, it) }
    }
}

tasks.named("jvmTest") {
    dependsOn(generateFixtures)
}
