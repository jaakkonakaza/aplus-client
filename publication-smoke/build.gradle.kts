plugins {
    alias(libs.plugins.kotlin)
    scala
}
repositories {
    exclusiveContent {
        forRepository { maven { url = uri(providers.gradleProperty("publicationRepository").get()) } }
        filter { includeGroup("io.github.jaakkonakaza") }
    }
    mavenCentral()
}
kotlin { jvmToolchain(25) }
val releaseVersion = providers.gradleProperty("releaseVersion").get()
dependencies {
    implementation("io.github.jaakkonakaza:aplus-client:$releaseVersion")
    implementation("io.github.jaakkonakaza:course-config-scala_3:$releaseVersion")
}
tasks.register<JavaExec>("kotlinSmoke") {
    dependsOn(tasks.classes)
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("KotlinSmokeKt")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(25)) })
}
tasks.register<JavaExec>("scalaSmoke") {
    dependsOn(tasks.classes)
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("scalaSmoke")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(25)) })
}
tasks.check { dependsOn("kotlinSmoke", "scalaSmoke") }
