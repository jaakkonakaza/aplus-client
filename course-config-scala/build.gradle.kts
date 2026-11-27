import com.vanniktech.maven.publish.JavaLibrary
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.SourcesJar

plugins {
    scala
    `java-library`
    alias(libs.plugins.mavenPublish)
}

repositories { mavenCentral() }
java { toolchain { languageVersion.set(JavaLanguageVersion.of(25)) } }
val scaladocTool = configurations.create("scaladocTool")

dependencies {
    add(scaladocTool.name, libs.scaladoc)
    api(project(":course-config"))
    api(libs.scalaLibrary)
    testImplementation(platform(libs.junitBom))
    testImplementation(libs.junit)
    testRuntimeOnly(libs.junitPlatformLauncher)
}
tasks.test { useJUnitPlatform() }
val scaladocJar = tasks.register<Jar>("scaladocJar") {
    group = "documentation"
    description = "Packages Scala API documentation for publication."
    archiveClassifier.set("javadoc")
    from(tasks.scaladoc)
}
mavenPublishing {
    if (providers.gradleProperty("signingInMemoryKey").orNull?.isNotBlank() == true) signAllPublications()
    configure(JavaLibrary(javadocJar = JavadocJar.None(), sourcesJar = SourcesJar.Sources()))
}
publishing.publications.withType<MavenPublication>().configureEach { artifact(scaladocJar) }


tasks.scaladoc {
    scalaClasspath = scaladocTool
    inputs.dir("docs")
    scalaDocOptions.additionalParameters = listOf("-siteroot", file("docs").absolutePath, "-Ygenerate-inkuire")
}

val compiledScala = layout.buildDirectory.dir("classes/scala/main")
val scalaApi = layout.projectDirectory.file("api/scala.api")
val javap = javaToolchains.launcherFor {
    languageVersion.set(JavaLanguageVersion.of(25))
}.map { it.metadata.installationPath.file("bin/javap") }
for (update in listOf(false, true)) {
    val apiTask = tasks.register<Exec>(if (update) "updateScalaApi" else "checkScalaApi") {
        group = "verification"
        description = if (update) "Updates the reviewed Scala JVM API snapshot." else "Checks Scala public JVM signatures."
        dependsOn(tasks.compileScala)
        inputs.dir(compiledScala)
        inputs.file(rootProject.file("scripts/check-scala-api.py"))
        if (update) outputs.file(scalaApi) else inputs.file(scalaApi)
        doFirst {
            commandLine(listOf(
                "python3", rootProject.file("scripts/check-scala-api.py").absolutePath,
                compiledScala.get().asFile.absolutePath, javap.get().asFile.absolutePath,
                scalaApi.asFile.absolutePath
            ) + if (update) listOf("--update") else emptyList())
        }
    }
    if (!update) tasks.check { dependsOn(apiTask) }
}
