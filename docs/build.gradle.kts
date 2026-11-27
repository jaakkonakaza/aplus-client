plugins {
    base
    alias(libs.plugins.dokkaHtml)
}

repositories { mavenCentral() }

dependencies {
    dokka(project(":"))
    dokka(project(":course-config"))
}

val generateOverview = tasks.register("generateOverview") {
    group = "documentation"
    description = "Builds the documentation overview from validated JSON examples."
    val overview = layout.projectDirectory.file("Overview.md")
    val examples = rootProject.layout.projectDirectory.dir("examples/course-config")
    val output = layout.buildDirectory.file("generated/Overview.md")
    inputs.file(overview)
    inputs.dir(examples)
    outputs.file(output)
    doLast {
        val sections = listOf(
            "minimal" to "Template catalog",
            "scala" to "Scala course",
            "python" to "Python course",
        )
        output.get().asFile.apply {
            parentFile.mkdirs()
            writeText(overview.asFile.readText() + sections.joinToString("") { (name, title) ->
                "\n## $title\n\n[Download $name.json](examples/course-config/$name.json)\n\n```json\n" +
                    examples.file("$name.json").asFile.readText() + "```\n"
            })
        }
    }
}

dokka {
    pluginsConfiguration.html {
        templatesDir.set(layout.projectDirectory.dir("templates"))
        customAssets.from(layout.projectDirectory.file("assets/json-highlighting.js"))
    }
    moduleName.set("A+ JVM libraries")
    dokkaPublications.html {
        failOnWarning.set(true)
        includes.from(generateOverview.map { it.outputs.files.singleFile })
    }
}

val site = tasks.register<Sync>("site") {
    group = "documentation"
    description = "Assembles Dokka, Scaladoc and JSON examples for GitHub Pages."
    dependsOn(":test", ":course-config:test", ":course-config-scala:test")
    from(tasks.named("dokkaGeneratePublicationHtml"))
    into(layout.buildDirectory.dir("site"))
    from(project(":course-config-scala").tasks.named("scaladoc")) {
        into("scala")
    }
    from(rootProject.file("examples")) {
        into("examples")
    }
    filesMatching("**/*.html") {
        val siteRoot = "../".repeat(relativePath.segments.size - 1)
        filter { line: String ->
            line.replace("https://jaakkonakaza.github.io/aplus-client/", siteRoot)
        }
    }
    doLast {
        destinationDir.resolve(".nojekyll").writeText("")
    }
}

tasks.check { dependsOn(site) }
