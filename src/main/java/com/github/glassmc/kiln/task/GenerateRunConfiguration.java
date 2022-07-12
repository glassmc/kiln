package com.github.glassmc.kiln.task;

import com.github.glassmc.kiln.Util;
import com.github.glassmc.kiln.KilnPlugin;
import com.github.glassmc.kiln.KilnExtension;
import com.github.glassmc.kiln.environment.Environment;
import com.github.glassmc.kiln.mappings.ObfuscatedMappingsProvider;
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
        projects.add(project);

        for (Project project1 : project.getChildProjects().values()) {
            projects.addAll(this.getAllProjects(project1));
        }

        return projects;
    }

    private void generateBuildScript(String version, String environment) {
        File newProjectRoot = new File(this.getProject().getRootDir(), "launch");
        File newProject = new File(newProjectRoot, environment + "-" + version.replace(".", "_"));
        newProject.mkdirs();

        File newProjectBuildScript = new File(newProject, "build.gradle");
        try {
            newProjectBuildScript.createNewFile();
            FileWriter fileWriter = new FileWriter(newProjectBuildScript);

            fileWriter.write("apply plugin: 'kiln-launch'\n" +
                    "\n" +
                    "kiln_launch {\n" +
                    "    version = \"" + version + "\"\n" +
                    "    environment = \"" + environment + "\"\n" +
                    "}");

            fileWriter.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void generateIntelliJRunConfiguration(String environment, String version) {
        File runConfigurationsFile = new File(this.getProject().getRootDir(), ".idea/runConfigurations");
        if (!runConfigurationsFile.exists()) {
            runConfigurationsFile.mkdirs();
        }

        File pluginCache = KilnPlugin.getMainInstance().getCache();
        Util.setupMinecraft(environment, version, pluginCache, new ObfuscatedMappingsProvider(), true);
        File versionFile = new File(pluginCache, "minecraft/" + version);
        File natives = new File(versionFile, "natives");

        StringBuilder vmArgsBuilder = new StringBuilder();

        KilnExtension extension = (KilnExtension) this.getProject().getExtensions().getByName("kiln");
        Environment environment1 = extension.environment;
        if (environment1 == null) {
            throw new RuntimeException("Environment not set via kiln extension.");
        }

        generateBuildScript(version, environment);

        vmArgsBuilder.append(" -Djava.library.path=").append(natives.getAbsolutePath());

        String name = environment.substring(0, 1).toUpperCase(Locale.ROOT) + environment.substring(1) + " " + version;

        String mainClass = environment1.getMainClass();
        String module = getProject().getRootProject().getName() + ".launch." + environment + "-" + version.replace(".", "_") + ".main";
        String programArguments = "--accessToken 0 --version " + environment1.getVersion(version) + " --userProperties {} --assetsDir " + new File(pluginCache, "minecraft/" + version + "/assets/");

        try {
            JSONObject versionManifest = Util.getVersionManifest(version);
            String id = versionManifest.getJSONObject("assetIndex").getString("id");
            programArguments = programArguments + " --assetIndex " + id;
        } catch (IOException e) {
            e.printStackTrace();
        }

        programArguments += " " + String.join(" ", environment1.getProgramArguments(environment, version));

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
        File pluginCache = KilnPlugin.getMainInstance().getCache();
        Util.setupMinecraft(environment, version, pluginCache, new ObfuscatedMappingsProvider(), true);
        File versionFile = new File(pluginCache, "minecraft/" + version);
        File natives = new File(versionFile, "natives");

        StringBuilder vmArgsBuilder = new StringBuilder();

        KilnExtension extension = (KilnExtension) this.getProject().getExtensions().getByName("kiln");
        Environment environment1 = extension.environment;
        if (environment1 == null) {
            throw new RuntimeException("Environment not set via kiln extension.");
        }

        generateBuildScript(version, environment);

        vmArgsBuilder.append(" -Djava.library.path=").append(natives.getAbsolutePath());

        String name = environment.substring(0, 1).toUpperCase(Locale.ROOT) + environment.substring(1) + " " + version;

        String mainClass = environment1.getMainClass();
        String module = getProject().getRootProject().getName();
        String programArguments = "--accessToken 0 --version " + environment1.getVersion(version) + " --userProperties {} --assetsDir " + new File(pluginCache, "minecraft/" + version + "/assets/");

        try {
            JSONObject versionManifest = Util.getVersionManifest(version);
            String id = versionManifest.getJSONObject("assetIndex").getString("id");
            programArguments = programArguments + " --assetIndex " + id;
        } catch (IOException e) {
            e.printStackTrace();
        }

        programArguments += " " + String.join(" ", environment1.getProgramArguments(environment, version));

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
                "    <listAttribute key=\"org.eclipse.jdt.launching.CLASSPATH\">\n" +
                "%s" +
                "    </listAttribute>\n" +
                "    <booleanAttribute key=\"org.eclipse.jdt.launching.DEFAULT_CLASSPATH\" value=\"false\"/>\n" +
                "    <stringAttribute key=\"org.eclipse.jdt.launching.MAIN_TYPE\" value=\"%s\"/>\n" +
                "    <stringAttribute key=\"org.eclipse.jdt.launching.MODULE_NAME\" value=\"%s\"/>\n" +
                "    <stringAttribute key=\"org.eclipse.jdt.launching.PROGRAM_ARGUMENTS\" value=\"%s\"/>\n" +
                "    <stringAttribute key=\"org.eclipse.jdt.launching.VM_ARGUMENTS\" value=\"%s\"/>\n" +
                "    <stringAttribute key=\"org.eclipse.jdt.launching.PROJECT_ATTR\" value=\"%s\"/>\n" +
                "    <listAttribute key=\"org.eclipse.jdt.launching.MODULEPATH\"/>\n" +
                "    <stringAttribute key=\"org.eclipse.jdt.launching.WORKING_DIRECTORY\" value=\"${workspace_loc:%s}/run\"/>\n" +
                "</launchConfiguration>";

        StringBuilder classpathString = new StringBuilder();
        List<String> projects = new ArrayList<>();
        projects.add(environment + "-" + version.replace(".", "_"));
        projects.add(this.getProject().getName());
        for (Project project : getAllProjects(this.getProject())) {
            if (project.getParent() != null && !project.getParent().getName().equals("launch") && !project.getName().equals("launch") && project.getBuildFile().exists()) {
                projects.add(project.getName().substring(project.getName().lastIndexOf(":") + 1));
            }
        }
        for (String project : projects) {
            classpathString.append(String.format(
                            "        <listEntry value=\"&lt;?xml version=&quot;1.0&quot; encoding=&quot;UTF-8&quot; standalone=&quot;no&quot;?&gt;&#10;&lt;runtimeClasspathEntry path=&quot;5&quot; projectName=&quot;%s&quot; type=&quot;1&quot;/&gt;&#10;\"/>\n" +
                            "        <listEntry value=\"&lt;?xml version=&quot;1.0&quot; encoding=&quot;UTF-8&quot; standalone=&quot;no&quot;?&gt;&#10;&lt;runtimeClasspathEntry containerPath=&quot;org.eclipse.buildship.core.gradleclasspathcontainer&quot; javaProject=&quot;%s&quot; path=&quot;5&quot; type=&quot;4&quot;/&gt;&#10;\"/>\n", project, project));
        }

        String runConfigurationData = String.format(runConfigurationTemplate, module, classpathString.toString(), mainClass, module, programArguments, vmArguments, module, module);

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
