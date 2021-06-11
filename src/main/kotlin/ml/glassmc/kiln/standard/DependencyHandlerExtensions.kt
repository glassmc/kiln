package ml.glassmc.kiln.standard

import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.file.FileCollection
import java.io.File

fun DependencyHandler.shard(id: String, version: String): FileCollection {
    val project = KilnStandardPlugin.instance.project

    val file = File("${project.gradle.gradleUserHomeDir}/caches/kiln/shard/$id/$version/$id-$version.jar")
    println(file)
    if(!file.exists()) {
        println("Not Exist!")
    }
    return project.files(file.absolutePath)
}

private fun handleVirtualDependency(id: String, version: String): FileCollection? {
    when(id) {
        else -> {
            return null
        }
    }
}