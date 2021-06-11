package ml.glassmc.kiln.main

import org.gradle.api.Plugin
import org.gradle.api.Project

class KilnMainPlugin: Plugin<Project> {

    override fun apply(target: Project) {
        println("Hello!")
    }

}