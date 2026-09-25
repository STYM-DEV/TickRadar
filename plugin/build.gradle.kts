plugins {
    alias(libs.plugins.shadow)
}

dependencies {
    compileOnly(libs.folia.api)
    compileOnly(libs.placeholderapi)
    implementation(libs.bstats.bukkit)
    testImplementation(libs.folia.api)
    testImplementation(libs.placeholderapi)
}

tasks.processResources {
    val props = mapOf("version" to project.version.toString())
    inputs.properties(props)
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.jar {
    enabled = false
}

val unusedBStats = listOf("charts/AdvancedBarChart", "charts/AdvancedPie", "charts/DrilldownPie", "charts/MultiLineChart",
    "charts/SimpleBarChart", "charts/SingleLineChart", "config/MetricsConfig")

tasks.shadowJar {
    archiveBaseName = "TickRadar"
    archiveClassifier = ""
    exclude("META-INF/maven/**", "META-INF/versions/*/module-info.class", "module-info.class")
    relocate("org.bstats", "dev.stym.tickradar.lib.bstats")
    exclude(unusedBStats.map { "org/bstats/$it.class" })
}

val maxJarBytes = 300L * 1024L

val checkJarSize = tasks.register("checkJarSize") {
    description = "Fails when the plugin jar is larger than ${maxJarBytes / 1024} KB."
    group = "verification"
    val jar = tasks.shadowJar.flatMap { it.archiveFile }
    inputs.file(jar).withPropertyName("jar")
    doLast {
        val file = jar.get().asFile
        val size = file.length()
        if (size > maxJarBytes) {
            throw GradleException("${file.name} is $size bytes, more than the ${maxJarBytes / 1024} KB budget")
        }
        logger.lifecycle("${file.name}: ${size / 1024} KB (budget ${maxJarBytes / 1024} KB)")
    }
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}

val coldStartTests = "**/*ColdStartTest.class"

val coldStartTest = tasks.register<Test>("coldStartTest") {
    description = "Times the first sample of a player, each test class in a fresh JVM."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    include(coldStartTests)
    forkEvery = 1
    shouldRunAfter(tasks.test)
}

tasks.check {
    dependsOn(checkJarSize, coldStartTest)
}

tasks.test {
    exclude(coldStartTests)
    dependsOn(tasks.shadowJar)
    val shadedJar = tasks.shadowJar.flatMap { it.archiveFile }
    inputs.file(shadedJar).withPropertyName("shadedJar")
    doFirst {
        systemProperty("tickradar.shadedJar", shadedJar.get().asFile.absolutePath)
    }
}
