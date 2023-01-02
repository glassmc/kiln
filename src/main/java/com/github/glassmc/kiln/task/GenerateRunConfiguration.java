package com.github.glassmc.kiln.task;

import com.github.glassmc.kiln.Pair;
import com.github.glassmc.kiln.Util;
import com.github.glassmc.kiln.KilnPlugin;
import com.github.glassmc.kiln.KilnExtension;
import com.github.glassmc.kiln.environment.Environment;
import com.github.glassmc.kiln.mappings.IMappingsProvider;
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

        IMappingsProvider mappingsProvider = null;
        for (Pair<IMappingsProvider, Boolean> mappingsProviderPair : KilnPlugin.getMainInstance().getAllMappingsProviders()) {
            if (mappingsProviderPair.getLeft().getVersion().equals(version)) {
                mappingsProvider = mappingsProviderPair.getLeft();
            }
        }

        switch (ide) {
            case "idea":
                generateIntelliJRunConfiguration(environment, version, mappingsProvider.getMappingsVersion());
                break;
            case "eclipse":
                generateEclipseRunConfiguration(environment, version, mappingsProvider.getMappingsVersion());
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

    private void generateIntelliJRunConfiguration(String environment, String version, String mappingsVersion) {
        File runConfigurationsFile = new File(this.getProject().getRootDir(), ".idea/runConfigurations");
        if (!runConfigurationsFile.exists()) {
            runConfigurationsFile.mkdirs();
        }

        File pluginCache = KilnPlugin.getMainInstance().getCache();
        Util.setupMinecraft(environment, version, mappingsVersion, pluginCache, new ObfuscatedMappingsProvider(), true);
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

    private void generateEclipseRunConfiguration(String environment, String version, String mappingsVersion) {
        File pluginCache = KilnPlugin.getMainInstance().getCache();
        Util.setupMinecraft(environment, version, mappingsVersion, pluginCache, new ObfuscatedMappingsProvider(), true);
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
                "    <booleanAttribute key=\"org.eclipse.debug.ui.ATTR_LAUNCH_IN_BACKGROUND\" value=\"false\"/>\n" +
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
        List<Project> projects2 = new ArrayList<>();
        projects.add(environment + "-" + version.replace(".", "_"));
        projects.add(this.getProject().getName());
        for (Project project : getAllProjects(this.getProject())) {
            if (project.getParent() != null && !project.getParent().getName().equals("launch") && !project.getName().equals("launch") && project.getBuildFile().exists()) {
                projects.add(project.getName().substring(project.getName().lastIndexOf(":") + 1));
                projects2.add(project);
            }
        }

        for (Project project : projects2) {
            File classesObfDir = new File(project.getBuildDir(), "classesObf");
            for (String file : Objects.requireNonNull(classesObfDir.list())) {
                classpathString.append(
                        String.format("        <listEntry value=\"&lt;?xml version=&quot;1.0&quot; encoding=&quot;UTF-8&quot; standalone=&quot;no&quot;?&gt;&#10;&lt;runtimeClasspathEntry externalArchive=&quot;%s&quot; path=&quot;5&quot; type=&quot;2&quot;/&gt;&#10;\"/>\n", new File(classesObfDir, file + "/main"))
                );
            }
        }

        for (String project : projects) {
            classpathString.append(String.format(
                    "        <listEntry value=\"&lt;?xml version=&quot;1.0&quot; encoding=&quot;UTF-8&quot; standalone=&quot;no&quot;?&gt;&#10;&lt;runtimeClasspathEntry containerPath=&quot;org.eclipse.buildship.core.gradleclasspathcontainer&quot; javaProject=&quot;%s&quot; path=&quot;5&quot; type=&quot;4&quot;/&gt;&#10;\"/>\n", /*project, */project));
        }

        String runConfigurationData = String.format(runConfigurationTemplate, module, classpathString, mainClass, module, programArguments, vmArguments, module, module);

        String runConfigurationGradleClassesTemplate = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n" +
                "<launchConfiguration type=\"org.eclipse.buildship.core.launch.runconfiguration\">\n" +
                "    <listAttribute key=\"arguments\"/>\n" +
                "    <booleanAttribute key=\"build_scans_enabled\" value=\"false\"/>\n" +
                "    <stringAttribute key=\"gradle_distribution\" value=\"GRADLE_DISTRIBUTION(WRAPPER)\"/>\n" +
                "    <stringAttribute key=\"gradle_user_home\" value=\"\"/>\n" +
                "    <stringAttribute key=\"java_home\" value=\"\"/>\n" +
                "    <listAttribute key=\"jvm_arguments\"/>\n" +
                "    <booleanAttribute key=\"offline_mode\" value=\"false\"/>\n" +
                "    <booleanAttribute key=\"override_workspace_settings\" value=\"false\"/>\n" +
                "    <booleanAttribute key=\"show_console_view\" value=\"true\"/>\n" +
                "    <booleanAttribute key=\"show_execution_view\" value=\"true\"/>\n" +
                "    <listAttribute key=\"tasks\">\n" +
                "        <listEntry value=\"classes\"/>\n" +
                "    </listAttribute>\n" +
                "    <stringAttribute key=\"working_dir\" value=\"${workspace_loc:/%s}\"/>\n" +
                "</launchConfiguration>\n";

        String runConfigurationGradleClassesData = String.format(runConfigurationGradleClassesTemplate, module);

        String runConfigurationGroupTemplate = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n" +
                "<launchConfiguration type=\"org.eclipse.debug.core.groups.GroupLaunchConfigurationType\">\n" +
                "    <stringAttribute key=\"org.eclipse.debug.core.launchGroup.0.action\" value=\"NONE\"/>\n" +
                "    <booleanAttribute key=\"org.eclipse.debug.core.launchGroup.0.adoptIfRunning\" value=\"false\"/>\n" +
                "    <booleanAttribute key=\"org.eclipse.debug.core.launchGroup.0.enabled\" value=\"true\"/>\n" +
                "    <stringAttribute key=\"org.eclipse.debug.core.launchGroup.0.mode\" value=\"run\"/>\n" +
                "    <stringAttribute key=\"org.eclipse.debug.core.launchGroup.0.name\" value=\"%s\"/>\n" +
                "    <stringAttribute key=\"org.eclipse.debug.core.launchGroup.1.action\" value=\"NONE\"/>\n" +
                "    <booleanAttribute key=\"org.eclipse.debug.core.launchGroup.1.adoptIfRunning\" value=\"false\"/>\n" +
                "    <booleanAttribute key=\"org.eclipse.debug.core.launchGroup.1.enabled\" value=\"true\"/>\n" +
                "    <stringAttribute key=\"org.eclipse.debug.core.launchGroup.1.mode\" value=\"run\"/>\n" +
                "    <stringAttribute key=\"org.eclipse.debug.core.launchGroup.1.name\" value=\"%s\"/>\n" +
                "</launchConfiguration>\n";

        String runConfigurationGroupData = String.format(runConfigurationGroupTemplate, "classes", name.replace(" ", "_").replace(".", "_"));

        try {
            FileWriter fileWriter = new FileWriter(name.replace(" ", "_").replace(".", "_") + ".launch");
            fileWriter.write(runConfigurationData);
            fileWriter.close();

            FileWriter fileWriter1 = new FileWriter("classes.launch");
            fileWriter1.write(runConfigurationGradleClassesData);
            fileWriter1.close();

            FileWriter fileWriter2 = new FileWriter(name.replace(" ", "_").replace(".", "_") + "+.launch");
            fileWriter2.write(runConfigurationGroupData);
            fileWriter2.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        File run = new File("run");
        run.mkdirs();
    }

}
