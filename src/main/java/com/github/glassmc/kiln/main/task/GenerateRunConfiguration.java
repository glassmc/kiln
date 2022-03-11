package com.github.glassmc.kiln.main.task;

import com.github.glassmc.kiln.main.KilnMainPlugin;
import com.github.glassmc.kiln.common.Util;
import com.github.glassmc.kiln.standard.KilnStandardExtension;
import com.github.glassmc.kiln.standard.environment.Environment;
import com.github.glassmc.kiln.standard.mappings.ObfuscatedMappingsProvider;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

public abstract class GenerateRunConfiguration extends DefaultTask {

    @TaskAction
    public void run() {
        this.setGroup("kiln");
        String arguments = (String) this.getProject().getProperties().get("configuration");
        String[] argumentsSplit = arguments.split(",");

        String ide = argumentsSplit[0];
        String environment = argumentsSplit[1];
        String version = argumentsSplit[2];

        switch (ide) {
            case "idea":
                generateIntelliJRunConfiguration(environment, version);
                break;
        }
    }

    private void generateIntelliJRunConfiguration(String environment, String version) {
        File runConfigurationsFile = new File(".idea/runConfigurations");
        if (!runConfigurationsFile.exists()) {
            runConfigurationsFile.mkdirs();
        }

        File pluginCache = KilnMainPlugin.getInstance().getCache();
        File jar = Util.setupMinecraft(environment, version, pluginCache, new ObfuscatedMappingsProvider());
        File dependencies = new File(jar.getParentFile(), "libraries");
        File natives = new File(jar.getParentFile(), "natives");

        StringBuilder vmArgsBuilder = new StringBuilder();
        vmArgsBuilder.append("-cp ").append(jar.getAbsolutePath()).append(File.pathSeparator);
        for(File dependency : Objects.requireNonNull(dependencies.listFiles())) {
            vmArgsBuilder.append(dependency.getAbsolutePath()).append(File.pathSeparator);
        }

        KilnStandardExtension extension = (KilnStandardExtension) this.getProject().getExtensions().getByName("kiln");
        Environment environment1 = extension.environment;
        vmArgsBuilder.append(String.join(File.pathSeparator, environment1.getRuntimeDependencies(KilnMainPlugin.getInstance().getCache()))).append(File.pathSeparator);

        File shadedJar = new File(this.getProject().getBuildDir(), "libs/" + this.getProject().getName() + "-all-mapped.jar");
        if (!shadedJar.exists()) {
            shadedJar = new File(this.getProject().getBuildDir(), "libs/" + this.getProject().getName() + "-" + this.getProject().getVersion() + "-all-mapped.jar");
        }

        vmArgsBuilder.append(shadedJar.getAbsolutePath()).append(File.pathSeparator);
        vmArgsBuilder.append("$Classpath$");

        vmArgsBuilder.append(" -Djava.library.path=").append(natives.getAbsolutePath());

        String name = environment.substring(0, 1).toUpperCase(Locale.ROOT) + environment.substring(1) + " " + version;

        String mainClass = environment1.getMainClass();
        String module = getProject().getRootProject().getName() + ".main";
        String programArguments = "--accessToken 0 --version " + version + " --userProperties {} --assetsDir " + new File(pluginCache, "minecraft/" + version + "/assets/");

        try {
            JSONObject versionManifest = Util.getVersionManifest(version);
            String id = versionManifest.getJSONObject("assetIndex").getString("id");
            programArguments = programArguments + " --assetIndex " + id;
        } catch (IOException e) {
            e.printStackTrace();
        }

        programArguments += " " + String.join(" ", environment1.getProgramArguments(environment));

        String vmArguments = vmArgsBuilder.toString();

        String runConfigurationTemplate = "<component name=\"ProjectRunConfigurationManager\">\n" +
                "  <configuration default=\"false\" name=\"%s\" type=\"Application\" factoryName=\"Application\">\n" +
                "    <option name=\"MAIN_CLASS_NAME\" value=\"%s\" />\n" +
                "    <module name=\"%s\" />\n" +
                "    <option name=\"PROGRAM_PARAMETERS\" value=\"%s\" />\n" +
                "    <option name=\"VM_PARAMETERS\" value=\"%s\" />\n" +
                "    <option name=\"WORKING_DIRECTORY\" value=\"run\" />\n" +
                "    <method v=\"2\">\n" +
                "      <option name=\"Gradle.BeforeRunTask\" enabled=\"true\" tasks=\"shadowJar\" externalProjectPath=\"$PROJECT_DIR$\" vmOptions=\"\" scriptParameters=\"\" />\n" +
                "    </method>\n" +
                "  </configuration>\n" +
                "</component>";

        String runConfigurationData = String.format(runConfigurationTemplate, name, mainClass, module, programArguments, vmArguments);

        try {
            FileWriter fileWriter = new FileWriter(new File(runConfigurationsFile, name.replace(" ", "_").replace(".", "_") + ".xml"));
            fileWriter.write(runConfigurationData);
            fileWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        File run = new File("run");
        run.mkdirs();
    }

}
