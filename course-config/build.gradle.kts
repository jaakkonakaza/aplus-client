import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.SourcesJar

plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.mavenPublish)
    `java-library`
    alias(libs.plugins.dokkaJavadoc)
    alias(libs.plugins.dokkaHtml)
}

repositories { mavenCentral() }
kotlin {
    jvmToolchain(25)
    explicitApi()
    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation()
    compilerOptions { javaParameters.set(true) }
}
dependencies {
    api(libs.jsonSerialization)
    testImplementation(platform(libs.junitBom))
    testImplementation(libs.junit)
    testImplementation(kotlin("test"))
    testRuntimeOnly(libs.junitPlatformLauncher)
}
tasks.test { useJUnitPlatform() }

mavenPublishing {
    if (providers.gradleProperty("signingInMemoryKey").orNull?.isNotBlank() == true) signAllPublications()
    configure(
        KotlinJvm(
            javadocJar = JavadocJar.Dokka("dokkaGeneratePublicationJavadoc"),
            sourcesJar = SourcesJar.Sources()
        )
    )
}


dokka {
    pluginsConfiguration.html {
        templatesDir.set(rootProject.layout.projectDirectory.dir("docs/templates"))
    }
    dokkaPublications.configureEach {
        failOnWarning.set(true)
    }
    dokkaSourceSets.named("main") {
        jdkVersion.set(25)
        includes.from("Module.md")
        sourceLink {
            localDirectory.set(file("src/main/kotlin"))
            remoteUrl("https://github.com/jaakkonakaza/aplus-client/blob/${providers.gradleProperty("docsRevision").getOrElse("main")}/course-config/src/main/kotlin")
            remoteLineSuffix.set("#L")
        }
    }
}

sourceSets.test {
    resources.srcDir(rootProject.file("examples/course-config"))
}
