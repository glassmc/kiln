package com.github.glassmc.kiln.main.task;

import com.github.glassmc.kiln.common.Pair;
import com.github.glassmc.kiln.main.KilnMainPlugin;
import com.github.glassmc.kiln.common.Util;
import com.github.glassmc.kiln.standard.KilnStandardExtension;
import com.github.glassmc.kiln.standard.environment.Environment;
import com.github.glassmc.kiln.standard.mappings.IMappingsProvider;
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
        projects.add(project);

        for (Project project1 : project.getChildProjects().values()) {
            projects.addAll(this.getAllProjects(project1));
        }

        return projects;
    }

    private void generateBuildScript(String version, String environment, File pluginCache, File dependencies, Environment environment1) {
        File newProjectRoot = new File(this.getProject().getRootDir(), "launch");
        File newProject = new File(newProjectRoot, environment + "-" + version.replace(".", "_"));
        newProject.mkdirs();

        File newProjectBuildScript = new File(newProject, "build.gradle");
        try {
            newProjectBuildScript.createNewFile();
            FileWriter fileWriter = new FileWriter(newProjectBuildScript);

            fileWriter.write("apply plugin: 'java'\n");

            fileWriter.write("repositories {\n" +
                    "    var main = project.rootProject\n" +
                    "\n" +
                    "    for (repository in main.getRepositories()) {\n" +
                    "        maven {\n" +
                    "            url = repository.url\n" +
                    "        }\n" +
                    "    }\n" +
                    "}\n");

            fileWriter.write("dependencies {\n");

            fileWriter.write("implementation project(':')\n");

            JSONObject versionManifest = Util.getVersionManifest(version);
            Map<String, String> libraries = new HashMap<>();

            for (Object library : versionManifest.getJSONArray("libraries")) {
                JSONObject libraryJSON = (JSONObject) library;
                JSONObject downloads = libraryJSON.getJSONObject("downloads");
                if (downloads.has("artifact")) {
                    String url = downloads.getJSONObject("artifact").getString("url");
                    url = url.substring(url.lastIndexOf("/") + 1);

                    String id = libraryJSON.getString("name");

                    libraries.put(url, id);
                }
            }

            File minecraftFile = new File(pluginCache, "minecraft");
            File localMaven = new File(minecraftFile, "localMaven");

            //boolean prefix = false;
            IMappingsProvider mappingsProvider = null;
            for (Pair<IMappingsProvider, Boolean> mappingsProviderPair : KilnMainPlugin.getInstance().getAllMappingsProviders()) {
                if (mappingsProviderPair.getLeft().getVersion().equals(version)) {
                    mappingsProvider = mappingsProviderPair.getLeft();
                    //prefix = mappingsProviderPair.getRight();
                }
            }

            if (mappingsProvider == null) {
                mappingsProvider = new ObfuscatedMappingsProvider();
            }

            Util.setupMinecraft(environment, version, pluginCache, mappingsProvider, true);

            File versionMappedJARFile = new File(localMaven, "net/minecraft/" + environment + "-" + version + "/" + mappingsProvider.getID() + "/" + environment + "-" + version + "-" + mappingsProvider.getID() + ".jar");
            fileWriter.write("runtimeOnly files('" + versionMappedJARFile.getAbsolutePath() + "')\n");

            for(File dependency : Objects.requireNonNull(dependencies.listFiles())) {
                if (!dependency.getName().endsWith(".jar")) continue;
                String library = libraries.get(dependency.getName());
                String[] id = library.split(":");
                File file = new File(localMaven, id[0].replace(".", "/") + "/" + id[1] + "-" + version + "/" + id[2] + "/" + id[1] + "-" + version + "-" + id[2] + ".jar");

                fileWriter.write("runtimeOnly files('" + file.getAbsolutePath() + "')\n");
            }

            for (String runtimeDependency : environment1.getRuntimeDependencies(pluginCache)) {
                fileWriter.write("runtimeOnly files('" + runtimeDependency + "')\n");
            }

            fileWriter.write("}\n");

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

        File pluginCache = KilnMainPlugin.getInstance().getCache();
        Util.setupMinecraft(environment, version, pluginCache, new ObfuscatedMappingsProvider(), true);
        File versionFile = new File(pluginCache, "minecraft/" + version);
        File dependencies = new File(versionFile, "libraries");
        File natives = new File(versionFile, "natives");

        StringBuilder vmArgsBuilder = new StringBuilder();

        KilnStandardExtension extension = (KilnStandardExtension) this.getProject().getExtensions().getByName("kiln");
        Environment environment1 = extension.environment;
        if (environment1 == null) {
            throw new RuntimeException("Environment not set via kiln extension.");
        }

        generateBuildScript(version, environment, pluginCache, dependencies, environment1);

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
        File pluginCache = KilnMainPlugin.getInstance().getCache();
        Util.setupMinecraft(environment, version, pluginCache, new ObfuscatedMappingsProvider(), true);
        File versionFile = new File(pluginCache, "minecraft/" + version);
        File dependencies = new File(versionFile, "libraries");
        File natives = new File(versionFile, "natives");

        StringBuilder vmArgsBuilder = new StringBuilder();

        KilnStandardExtension extension = (KilnStandardExtension) this.getProject().getExtensions().getByName("kiln");
        Environment environment1 = extension.environment;
        if (environment1 == null) {
            throw new RuntimeException("Environment not set via kiln extension.");
        }

        generateBuildScript(version, environment, pluginCache, dependencies, environment1);

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
