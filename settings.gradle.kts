rootProject.name = "aplus-client"

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(":course-config", ":course-config-scala", ":docs")
