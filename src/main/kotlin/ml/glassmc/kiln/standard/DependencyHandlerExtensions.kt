package ml.glassmc.kiln.standard

import org.apache.commons.io.FileUtils
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.file.FileCollection
import org.json.JSONObject
import java.io.File
import java.net.URL

fun DependencyHandler.shard(id: String, version: String): FileCollection {
    val plugin = KilnStandardPlugin.instance

    val virtualDependency = handleVirtualDependency(plugin, id, version)
    if(virtualDependency != null) {
        return virtualDependency
    }

    val file = File("${plugin.project.gradle.gradleUserHomeDir}/caches/kiln/shard/$id/$version/$id-$version.jar")
    println(file)
    if(!file.exists()) {
        println("Not Exist!")
    }
    return plugin.project.files(file.absolutePath)
}

private fun handleVirtualDependency(plugin: KilnStandardPlugin, id: String, version: String): FileCollection? {
    when(id) {
        "client" -> {
            val pluginCache = plugin.cache
            val minecraftFile = File(pluginCache, "minecraft")
            val versionFile = File(minecraftFile, version)
            val specificFile = File(versionFile, "$id.jar")

            if(!specificFile.exists()) {
                val versions = JSONObject(String(URL("https://launchermeta.mojang.com/mc/game/version_manifest.json").openStream().readBytes()))
                val versionInfo = versions.getJSONArray("versions").find {
                    (it as JSONObject).getString("id") == version
                } as JSONObject

                val url = versionInfo.getString("url")
                val version = JSONObject(String(URL(url).openStream().readBytes()))
                val versionJarURL = URL(version.getJSONObject("downloads").getJSONObject("client").getString("url"))
                FileUtils.copyURLToFile(versionJarURL, specificFile)
            }

            return plugin.project.files(specificFile.absolutePath)
        }
        else -> {
            return null
        }
    }
}