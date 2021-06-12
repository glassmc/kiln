plugins {
    kotlin("jvm") version "1.5.10"
    id("maven-publish")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(gradleApi())

    implementation("commons-io:commons-io:2.9.0")
    implementation("org.json:json:20210307")
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
