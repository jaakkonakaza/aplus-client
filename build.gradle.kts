import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.SourcesJar

plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.kover)

    `java-library`
    alias(libs.plugins.mavenPublish)
    alias(libs.plugins.dokkaJavadoc)
    alias(libs.plugins.dokkaHtml)
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(25)
    explicitApi()
    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation()
}

dependencies {
    // These types appear in public signatures, so callers need their compile dependencies.
    api(libs.ktorClientCore)
    api(libs.ktorClientResources)
    api(libs.jsonSerialization)

    // The factory exposes OkHttpConfig in its public configure parameter.
    api(libs.ktorClientOkhttp)

    testImplementation(platform(libs.junitBom))
    testImplementation(libs.junit)
    testImplementation(libs.ktorClientMock)
    testImplementation(kotlin("test"))
    testRuntimeOnly(libs.junitPlatformLauncher)
}

mavenPublishing {
    if (providers.gradleProperty("signingInMemoryKey").orNull?.isNotBlank() == true) signAllPublications()
    configure(
        KotlinJvm(
            javadocJar = JavadocJar.Dokka("dokkaGeneratePublicationJavadoc"),
            sourcesJar = SourcesJar.Sources()
        )
    )
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}


kover {
    reports {
        total {
            xml {
                onCheck = true
            }
        }
    }
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
            remoteUrl("https://github.com/jaakkonakaza/aplus-client/blob/${providers.gradleProperty("docsRevision").getOrElse("main")}/src/main/kotlin")
            remoteLineSuffix.set("#L")
        }
    }
}


allprojects {
    tasks.withType<org.gradle.jvm.tasks.Jar>().configureEach {
        from(rootProject.file("LICENSE")) {
            into("META-INF")
        }
    }
}
