plugins {
    alias(libs.plugins.shadow) apply false
}

val junitBom = libs.junit.bom
val junitJupiter = libs.junit.jupiter
val junitLauncher = libs.junit.platform.launcher

subprojects {
    apply(plugin = "java")

    group = "dev.stym.tickradar"
    version = rootProject.version

    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion = JavaLanguageVersion.of(25)
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release = 25
        options.compilerArgs.add("-Xlint:all,-processing,-serial")
    }

    tasks.named<JavaCompile>("compileJava") {
        options.isDebug = true
        options.debugOptions.debugLevel = "source,lines"
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            events("failed")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }

    dependencies {
        "testImplementation"(platform(junitBom))
        "testImplementation"(junitJupiter)
        "testRuntimeOnly"(junitLauncher)
    }
}
