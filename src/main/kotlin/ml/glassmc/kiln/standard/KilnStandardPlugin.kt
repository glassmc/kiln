package ml.glassmc.kiln.standard

import org.gradle.api.Plugin
import org.gradle.api.Project
import java.io.File

class KilnStandardPlugin: Plugin<Project> {

    lateinit var project: Project

    val cache: File
        get() {
            val cache = File("${this.project.gradle.gradleUserHomeDir}/caches/glass")
            cache.mkdirs()
            return cache
        }

    val localRepository: File
        get() {
            return File(cache, "repository")
        }

    override fun apply(project: Project) {
        instance = this
        this.project = project
    }

    companion object {
        lateinit var instance: KilnStandardPlugin
    }

}
