package ml.glassmc.kiln

import org.gradle.api.Plugin
import org.gradle.api.Project

class KilnPlugin: Plugin<Project> {

    override fun apply(target: Project) {
        println("Hello!")
    }

}