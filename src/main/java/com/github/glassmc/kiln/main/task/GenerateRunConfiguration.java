package com.github.glassmc.kiln.main.task;

import com.github.glassmc.kiln.main.KilnMainPlugin;
import com.github.glassmc.kiln.common.Util;
import com.github.glassmc.kiln.standard.KilnStandardExtension;
import com.github.glassmc.kiln.standard.environment.Environment;
import com.github.glassmc.kiln.standard.mappings.ObfuscatedMappingsProvider;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskAction;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

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
            case "eclipse":
                generateEclipseRunConfiguration(environment, version);
        }
    }

    private List<Project> getAllProjects(Project project) {
        List<Project> projects = new ArrayList<>();
        if (project.getBuildDir().exists()) {
            projects.add(project);
        }

        for (Project project1 : project.getChildProjects().values()) {
            projects.addAll(this.getAllProjects(project1));
        }

        return projects;
    }

    private void generateIntelliJRunConfiguration(String environment, String version) {
        File runConfigurationsFile = new File(".idea/runConfigurations");
        if (!runConfigurationsFile.exists()) {
            runConfigurationsFile.mkdirs();
        }

        File pluginCache = KilnMainPlugin.getInstance().getCache();
        Util.setupMinecraft(environment, version, pluginCache, new ObfuscatedMappingsProvider());
        File versionFile = new File(pluginCache, "minecraft/" + version);
        File dependencies = new File(versionFile, "libraries");
        File natives = new File(versionFile, "natives");

        StringBuilder vmArgsBuilder = new StringBuilder();
        vmArgsBuilder.append("-cp ").append(new File(versionFile, environment + "-" + version + ".jar")).append(File.pathSeparator);
        for(File dependency : Objects.requireNonNull(dependencies.listFiles())) {
            vmArgsBuilder.append(dependency.getAbsolutePath()).append(File.pathSeparator);
        }

        KilnStandardExtension extension = (KilnStandardExtension) this.getProject().getExtensions().getByName("kiln");
        Environment environment1 = extension.environment;
        vmArgsBuilder.append(String.join(File.pathSeparator, environment1.getRuntimeDependencies(KilnMainPlugin.getInstance().getCache()))).append(File.pathSeparator);

        for (Project project : this.getAllProjects(getProject())) {
            vmArgsBuilder.append(new File(project.getBuildDir(), "classesObf/java/main")).append(File.pathSeparator);
            vmArgsBuilder.append(new File(project.getBuildDir(), "classesObf/kotlin/main")).append(File.pathSeparator);
        }

        vmArgsBuilder.append("$Classpath$");

        vmArgsBuilder.append(" -Djava.library.path=").append(natives.getAbsolutePath());

        String name = environment.substring(0, 1).toUpperCase(Locale.ROOT) + environment.substring(1) + " " + version;

        String mainClass = environment1.getMainClass();
        String module = getProject().getRootProject().getName() + ".main";
        String programArguments = "--accessToken 0 --version Glass-" + version + " --glassVersion " + version + " --userProperties {} --assetsDir " + new File(pluginCache, "minecraft/" + version + "/assets/");

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
                "      <option name=\"Make\" enabled=\"true\" />\n" +
                "    </method>" +
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

    private void generateEclipseRunConfiguration(String environment, String version) {
        File pluginCache = KilnMainPlugin.getInstance().getCache();
        Util.setupMinecraft(environment, version, pluginCache, new ObfuscatedMappingsProvider());
        File versionFile = new File(pluginCache, "minecraft/" + version);
        File dependencies = new File(versionFile, "libraries");
        File natives = new File(versionFile, "natives");

        StringBuilder vmArgsBuilder = new StringBuilder();
        vmArgsBuilder.append("-cp ").append(new File(versionFile, environment + "-" + version + ".jar")).append(File.pathSeparator);
        for(File dependency : Objects.requireNonNull(dependencies.listFiles())) {
            vmArgsBuilder.append(dependency.getAbsolutePath()).append(File.pathSeparator);
        }

        KilnStandardExtension extension = (KilnStandardExtension) this.getProject().getExtensions().getByName("kiln");
        Environment environment1 = extension.environment;
        vmArgsBuilder.append(String.join(File.pathSeparator, environment1.getRuntimeDependencies(KilnMainPlugin.getInstance().getCache()))).append(File.pathSeparator);

        for (Project project : this.getAllProjects(getProject())) {
            vmArgsBuilder.append(new File(project.getBuildDir(), "classesObf/java/main")).append(File.pathSeparator);
            vmArgsBuilder.append(new File(project.getBuildDir(), "classesObf/kotlin/main")).append(File.pathSeparator);
        }

        vmArgsBuilder.append("${project_classpath}");

        vmArgsBuilder.append(" -Djava.library.path=").append(natives.getAbsolutePath());

        String name = environment.substring(0, 1).toUpperCase(Locale.ROOT) + environment.substring(1) + " " + version;

        String mainClass = environment1.getMainClass();
        String module = getProject().getRootProject().getName();
        String programArguments = "--accessToken 0 --version Glass-" + version + " --glassVersion " + version + " --userProperties {} --assetsDir " + new File(pluginCache, "minecraft/" + version + "/assets/");

        try {
            JSONObject versionManifest = Util.getVersionManifest(version);
            String id = versionManifest.getJSONObject("assetIndex").getString("id");
            programArguments = programArguments + " --assetIndex " + id;
        } catch (IOException e) {
            e.printStackTrace();
        }

        programArguments += " " + String.join(" ", environment1.getProgramArguments(environment));

        String vmArguments = vmArgsBuilder.toString();

        String runConfigurationTemplate = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n" +
                "<launchConfiguration type=\"org.eclipse.jdt.launching.localJavaApplication\">\n" +
                "    <listAttribute key=\"org.eclipse.debug.core.MAPPED_RESOURCE_PATHS\">\n" +
                "        <listEntry value=\"/%s\"/>\n" +
                "    </listAttribute>\n" +
                "    <listAttribute key=\"org.eclipse.debug.core.MAPPED_RESOURCE_TYPES\">\n" +
                "        <listEntry value=\"4\"/>\n" +
                "    </listAttribute>\n" +
                "    <booleanAttribute key=\"org.eclipse.jdt.launching.ATTR_ATTR_USE_ARGFILE\" value=\"false\"/>\n" +
                "    <booleanAttribute key=\"org.eclipse.jdt.launching.ATTR_SHOW_CODEDETAILS_IN_EXCEPTION_MESSAGES\" value=\"true\"/>\n" +
                "    <booleanAttribute key=\"org.eclipse.jdt.launching.ATTR_USE_CLASSPATH_ONLY_JAR\" value=\"false\"/>\n" +
                "    <booleanAttribute key=\"org.eclipse.jdt.launching.ATTR_USE_START_ON_FIRST_THREAD\" value=\"true\"/>\n" +
                "    <booleanAttribute key=\"org.eclipse.jdt.launching.DEFAULT_CLASSPATH\" value=\"false\"/>\n" +
                "    <stringAttribute key=\"org.eclipse.jdt.launching.MAIN_TYPE\" value=\"%s\"/>\n" +
                "    <stringAttribute key=\"org.eclipse.jdt.launching.MODULE_NAME\" value=\"%s\"/>\n" +
                "    <stringAttribute key=\"org.eclipse.jdt.launching.PROGRAM_ARGUMENTS\" value=\"%s\"/>\n" +
                "    <stringAttribute key=\"org.eclipse.jdt.launching.VM_ARGUMENTS\" value=\"%s\"/>\n" +
                "    <stringAttribute key=\"org.eclipse.jdt.launching.PROJECT_ATTR\" value=\"%s\"/>\n" +
                "</launchConfiguration>";

        String runConfigurationData = String.format(runConfigurationTemplate, module, mainClass, module, programArguments, vmArguments, module);

        try {
            FileWriter fileWriter = new FileWriter(name.replace(" ", "_").replace(".", "_") + ".launch");
            fileWriter.write(runConfigurationData);
            fileWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        File run = new File("run");
        run.mkdirs();
    }

}
