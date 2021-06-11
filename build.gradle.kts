plugins {
    kotlin("jvm") version "1.5.10"
    id("maven-publish")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(gradleApi())
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "ml.glassmc"
            artifactId = "kiln"
            version = "0.0.1"

            from(components["java"])
        }
    }
}
