package com.github.glassmc.kiln;

import com.github.glassmc.kiln.task.ClearMappings;
import com.github.glassmc.kiln.task.GenerateRunConfiguration;
import com.github.glassmc.kiln.mappings.IMappingsProvider;
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.gradle.api.*;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.file.RegularFile;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskDependency;
import org.objectweb.asm.*;
import com.github.glassmc.kiln.internalremapper.ClassRemapper;
import com.github.glassmc.kiln.internalremapper.Remapper;
import org.objectweb.asm.tree.ClassNode;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;

public class KilnPlugin implements Plugin<Project> {

    protected static KilnPlugin mainInstance;
    protected static KilnPlugin instance;

    public static KilnPlugin getInstance() {
        return instance;
    }

    public static KilnPlugin getMainInstance() {
        return mainInstance;
    }

    protected Project project;
    private KilnExtension extension;

    // only stored for the plugin in root project
    private final List<Pair<IMappingsProvider, Boolean>> allMappingsProviders = new ArrayList<>();
    private final List<Pair<IMappingsProvider, Boolean>> mappingsProviders = new ArrayList<>();

    @Override
    public void apply(Project project) {
        instance = this;
        this.project = project;

        this.extension = project.getExtensions().create("kiln", KilnExtension.class);

        this.mappingsProviders.clear();

        project.getPlugins().apply("java-library");
        project.getTasks().getByName("compileJava").dependsOn(project.getTasks().getByName("processResources"));
        project.getPlugins().apply("com.github.johnrengelman.shadow");

        JavaPluginExtension javaPluginExtension = project.getExtensions().getByType(JavaPluginExtension.class);
        SourceSet classesObf = javaPluginExtension.getSourceSets().create("classesObf");
        classesObf.getOutput().dir(new File(project.getBuildDir(), "classesObf/java/main").getAbsolutePath());

        this.setupShadowPlugin();

        project.getRepositories().maven(action -> action.setUrl(new File(this.getCache(), "minecraft/localMaven")));

        project.afterEvaluate(p -> {
            for (Configuration configuration : project.getConfigurations()) {
                for (Dependency dependency : configuration.getDependencies()) {
                    if (dependency.getGroup() != null && dependency.getGroup().equals("net.minecraft") &&
                            (dependency.getName().startsWith("client-") || dependency.getName().startsWith("server-")) &&
                            dependency.getVersion() != null) {
                        int splitPoint = dependency.getName().indexOf("-");
                        String environment = dependency.getName().substring(0, splitPoint);
                        String version = dependency.getName().substring(splitPoint + 1);

                        String mappings = dependency.getVersion();
                        String mappingsVersion = null;
                        if (dependency.getVersion().contains("-")) {
                            String[] versionSplit = dependency.getVersion().split("-");
                            mappings = versionSplit[0];
                            mappingsVersion = versionSplit[1];
                        }

                        Util.minecraft(environment, version, mappings, mappingsVersion, false);
                    }
                }
            }

            p.getTasks().getByName("classes").doLast(new ReobfuscateAction2());
            p.getTasks().getByName("jar").doLast(new MapJar());

            Task shadowJar = p.getTasks().findByName("shadowJar");
            if (shadowJar != null) {
                shadowJar.doLast(new MapShadowJar());
            }
        });

        if (!project.getRootProject().equals(project)) {
            project.getRootProject().getTasks().getByName("classes").dependsOn(project.getTasks().getByName("classes"));

            // why this fixes a bug? who knows
            TaskDependency taskDependency = project.getTasks().getByName("compileJava").getTaskDependencies();
            taskDependency.getDependencies(project.getTasks().getByName("compileJava"));

            project.getRootProject().getTasks().getByName("shadowJar").dependsOn(project.getTasks().getByName("shadowJar"));
        }

        project.afterEvaluate(project1 -> {
            PublishingExtension publishing = (PublishingExtension) project.getExtensions().findByName("publishing");

            if (publishing != null) {
                publishing.getPublications().create("MavenPublication", MavenPublication.class, publication -> {
                    publication.from(project.getComponents().getByName("java"));

                    Provider<RegularFile> file = project.getLayout().getBuildDirectory().file("libs/" + project.getName() + "-" + project.getVersion() + "-mapped.jar");
                    {
                        PublishArtifact artifact = project.getArtifacts().add("archives", file.get().getAsFile());
                        publication.artifact(artifact);
                    }

                    file = project.getLayout().getBuildDirectory().file("libs/" + project.getName() + "-" + project.getVersion() + "-all-mapped.jar");
                    if (project.getPlugins().hasPlugin("com.github.johnrengelman.shadow")) {
                        PublishArtifact artifact = project.getArtifacts().add("archives", file.get().getAsFile());
                        publication.artifact(artifact);
                    }
                });
            }
        });

        if (project.getRootProject() == project) {
            mainInstance = this;
            project.getTasks().register("genRunConfiguration", GenerateRunConfiguration.class);
            project.getTasks().register("clearMappings", ClearMappings.class);

            Configuration shadowRuntime = project.getConfigurations().create("shadowRuntime");
            Configuration runtimeOnly = project.getConfigurations().getByName("runtimeOnly");
            runtimeOnly.extendsFrom(shadowRuntime);

            ShadowJar shadowJar = (ShadowJar) project.getTasks().getByName("shadowJar");
            shadowJar.getConfigurations().add(project.getConfigurations().getByName("shadowRuntime"));

            shadowJar.doLast(new FullBuiltJar());
        }
    }

    public class FullBuiltJar implements Action<Task> {

        @Override
        public void execute(Task task) {
            Project project = task.getProject();
            File libs = new File(task.getProject().getBuildDir(), "libs");

            File allMapped = new File(libs + "/" + project.getName() + "-" + project.getVersion() + "-all-mapped.jar");
            if (!allMapped.exists()) {
                allMapped = new File(libs + "/" + project.getName() + "-all-mapped.jar");
            }

            List<Project> projects = new ArrayList<>();
            appendAllProjects(project, projects);

            List<File> filesToAppend = new ArrayList<>();
            for (Project project1 : projects) {
                File libs1 = new File(project1.getBuildDir(), "libs");
                File allMapped1 = new File(libs1 + "/" + project1.getName() + "-" + project1.getVersion() + "-all-mapped.jar");
                if (!allMapped1.exists()) {
                    allMapped1 = new File(libs1 + "/" + project1.getName() + "-all-mapped.jar");
                }

                filesToAppend.add(allMapped1);
            }

            List<String> alreadyAdded = new ArrayList<>();
            try {
                JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(new File(allMapped.getAbsolutePath().replace("-all-mapped.jar", "-all-all-mapped.jar")).toPath()));

                for (File file : filesToAppend) {
                    JarFile jarFile = new JarFile(file);
                    Enumeration<JarEntry> entries = jarFile.entries();

                    while (entries.hasMoreElements()) {
                        JarEntry jarEntry = entries.nextElement();

                        if (!alreadyAdded.contains(jarEntry.getName())) {
                            jarOutputStream.putNextEntry(new JarEntry(jarEntry.getName()));
                            jarOutputStream.write(IOUtils.toByteArray(jarFile.getInputStream(jarEntry)));
                            jarOutputStream.closeEntry();

                            alreadyAdded.add(jarEntry.getName());
                        }
                    }
                }

                jarOutputStream.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

    }

    public void appendAllProjects(Project parent, List<Project> projects) {
        if (parent.getBuildFile().exists() && !parent.getDisplayName().contains("launch")) {
            projects.add(parent);
        }

        for (Project project1 : parent.getChildProjects().values()) {
            appendAllProjects(project1, projects);
        }
    }

    public static class MapJar implements Action<Task> {

        @Override
        public void execute(Task task) {
            Project p = task.getProject();
            File file = new File(p.getBuildDir(), "libs/" + p.getName() + "-" + p.getVersion() + ".jar");
            if (!file.exists()) {
                file = new File(p.getBuildDir(), "libs/" + p.getName() + ".jar");
            }

            File file2 = new File(file.getAbsolutePath().replace(".jar", "-mapped.jar"));

            try {
                JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(file2.toPath()));
                JarInputStream jarInputStream = new JarInputStream(Files.newInputStream(file.toPath()));
                JarEntry jarEntry = jarInputStream.getNextJarEntry();
                while (jarEntry != null) {
                    File obfFile = new File(p.getBuildDir(), "THIS SHOULD NEVER EXIST");
                    for (String language : Objects.requireNonNull(new File(p.getBuildDir(), "classesObf").list())) {
                        if (!obfFile.exists()) {
                            obfFile = new File(p.getBuildDir(), "classesObf/" + language + "/main/" + jarEntry.getName());
                        }
                    }

                    jarOutputStream.putNextEntry(new JarEntry(jarEntry.getName()));
                    if (obfFile.exists() && jarEntry.getName().endsWith(".class")) {
                        jarOutputStream.write(FileUtils.readFileToByteArray(obfFile));
                    } else {
                        jarOutputStream.write(IOUtils.toByteArray(jarInputStream));
                    }
                    jarOutputStream.closeEntry();

                    jarEntry = jarInputStream.getNextJarEntry();
                }
                jarOutputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    public static class MapShadowJar implements Action<Task> {
        @Override
        public void execute(Task task) {
            Project p = task.getProject();
            File file = new File(p.getBuildDir(), "libs/" + p.getName() + "-" + p.getVersion() + "-all.jar");
            if (!file.exists()) {
                file = new File(p.getBuildDir(), "libs/" + p.getName() + "-all.jar");
            }

            File file2 = new File(file.getAbsolutePath().replace(".jar", "-mapped.jar"));

            try {
                JarOutputStream jarOutputStream = new JarOutputStream(new FileOutputStream(file2));
                JarInputStream jarInputStream = new JarInputStream(new FileInputStream(file));
                JarEntry jarEntry = jarInputStream.getNextJarEntry();
                while (jarEntry != null) {
                    File obfFile = new File(p.getBuildDir(), "THIS SHOULD NEVER EXIST");
                    for (String language : Objects.requireNonNull(new File(p.getBuildDir(), "classesObf").list())) {
                        if (!obfFile.exists()) {
                            obfFile = new File(p.getBuildDir(), "classesObf/" + language + "/main/" + jarEntry.getName());
                        }
                    }

                    jarOutputStream.putNextEntry(new JarEntry(jarEntry.getName()));
                    if (obfFile.exists() && jarEntry.getName().endsWith(".class")) {
                        jarOutputStream.write(FileUtils.readFileToByteArray(obfFile));
                    } else {
                        jarOutputStream.write(IOUtils.toByteArray(jarInputStream));
                    }
                    jarOutputStream.closeEntry();

                    jarEntry = jarInputStream.getNextJarEntry();
                }
                jarOutputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void setupShadowPlugin() {
        project.getPlugins().apply("com.github.johnrengelman.shadow");

        Configuration shadowImplementation = project.getConfigurations().create("shadowImplementation");
        project.getConfigurations().getByName("implementation").extendsFrom(shadowImplementation);

        Configuration shadowApi = project.getConfigurations().create("shadowApi");
        project.getConfigurations().getByName("api").extendsFrom(shadowApi);

        project.getConfigurations().create("shadowOnly");

        ShadowJar shadowJar = (ShadowJar) project.getTasks().getByName("shadowJar");
        shadowJar.getConfigurations().clear();
        shadowJar.getConfigurations().add(project.getConfigurations().getByName("shadowImplementation"));

        shadowJar.getConfigurations().add(project.getConfigurations().getByName("shadowApi"));

        shadowJar.getConfigurations().add(project.getConfigurations().getByName("shadowOnly"));
    }

    public File getCache() {
        File cache = new File(this.project.getGradle().getGradleUserHomeDir() + File.separator + "caches" + File.separator + "kiln");
        cache.mkdirs();
        return cache;
    }

    public Project getProject() {
        return project;
    }

    public void addMappingsProvider(IMappingsProvider mappingsProvider, boolean prefix) {
        this.mappingsProviders.add(new Pair<>(mappingsProvider, prefix));

        KilnPlugin.getMainInstance().addAllMappingsProvider(mappingsProvider, prefix);
    }

    public List<Pair<IMappingsProvider, Boolean>> getMappingsProviders() {
        return mappingsProviders;
    }

    private void addAllMappingsProviders(Project project, List<Pair<IMappingsProvider, Boolean>> mappingsProviders) {
        KilnPlugin plugin = project.getPlugins().findPlugin(KilnPlugin.class);
        if (plugin != null) {
            mappingsProviders.addAll(plugin.getMappingsProviders());
        }

        for (Project project1 : project.getChildProjects().values()) {
            this.addAllMappingsProviders(project1, mappingsProviders);
        }
    }

    private void addAllTransformers(Project project, List<Pair<Project, CustomTransformer>> customTransformers) {
        KilnExtension extension = project.getExtensions().findByType(KilnExtension.class);
        if (extension != null) {
            for (CustomTransformer transformer : extension.transformers) {
                customTransformers.add(new Pair<>(project, transformer));
            }
        }

        for (Project project1 : project.getChildProjects().values()) {
            this.addAllTransformers(project1, customTransformers);
        }
    }

    private static final Map<String, byte[]> cachedClasses = new HashMap<>();

    @NonNullApi
    private class ReobfuscateAction2 implements Action<Task> {

        public List<String> getAllParents(Map<String, List<String>> classesMap, String clazz) {
            List<String> parents = new ArrayList<>();

            for (String classThing : classesMap.getOrDefault(clazz, new ArrayList<>())) {
                parents.addAll(this.getAllParents(classesMap, classThing));
            }

            parents.add(clazz);
            return parents;
        }

        @Override
        public void execute(Task task) {
            long startTime = System.currentTimeMillis();

            List<Pair<IMappingsProvider, Boolean>> mappingsProviders = new ArrayList<>();
            addAllMappingsProviders(project, mappingsProviders);

            Map<String, ClassNode> classNodes = new HashMap<>();
            Map<String, ClassNode> classNodesModified = new HashMap<>();
            Map<String, File> classPaths = new HashMap<>();

            File classes = new File(project.getBuildDir(), "classes");

            for(File file : project.fileTree(classes)) {
                if(!file.getName().endsWith(".class")) {
                    continue;
                }
                try {
                    InputStream inputStream = new FileInputStream(file);
                    byte[] data = IOUtils.readFully(inputStream, inputStream.available());
                    ClassReader classReader = new ClassReader(data);
                    ClassNode classNode = new ClassNode();
                    classReader.accept(classNode, 0);
                    String language = file.getAbsolutePath();
                    language = language.substring(language.indexOf("classes" + File.separator) + 8);
                    language = language.substring(0, language.indexOf(File.separator));
                    String className = file.getAbsolutePath().replace(new File(classes, language + File.separator + "main").getAbsolutePath() + File.separator, "").replace(".class", "").replace("\\", "/");
                    classNodes.put(className, classNode);

                    if (cachedClasses.get(className) == null || !Arrays.equals(data, cachedClasses.get(className)) || !new File(file.getAbsolutePath().replace("\\", "/").replace("/classes/", "/classesObf/")).exists()) {
                        cachedClasses.put(className, data);
                        classNodesModified.put(className, classNode);
                    }

                    classPaths.put(className, file);
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            List<Pair<IMappingsProvider, Pair<Boolean, Remapper>>> remappers = mappingsProviders.stream()
                    .map(provider -> new Pair<>(provider.getLeft(), new Pair<>(provider.getRight(), provider.getLeft().getRemapper(IMappingsProvider.Direction.TO_OBFUSCATED))))
                    .collect(Collectors.toList());

            System.out.println("Finished setup in " + (System.currentTimeMillis() - startTime) + " milliseconds.");

            Map<String, List<String>> classesMap = new HashMap<>();

            Remapper collectiveRemapper = new Remapper() {

                private final Map<String, String> cache = new HashMap<>();

                @Override
                public String map(String name) {
                    String newName = cache.get(name);

                    if (newName == null) {
                        String nameVersion;
                        if (name.startsWith("v")) {
                            newName = name.substring(name.indexOf("/") + 1);
                            nameVersion = name.substring(1, name.indexOf("/")).replace("_", ".");
                        } else {
                            newName = name;
                            nameVersion = null;
                        }
                        for (Pair<IMappingsProvider, Pair<Boolean, Remapper>> remapper : remappers) {
                            if (!remapper.getRight().getLeft() || remapper.getLeft().getVersion().equals(nameVersion)) {
                                newName = remapper.getRight().getRight().map(newName);
                            }
                        }
                        cache.put(name, newName);
                    }

                    return newName;
                }

                @Override
                public String mapFieldName(String owner, String name, String descriptor) {
                    String newName = cache.get(owner + name + descriptor);

                    if (newName == null) {
                        List<String> parents = getAllParents(classesMap, owner);

                        String nameVersion = null;
                        for (String classString : new ArrayList<>(parents)) {
                            if (classString.startsWith("v")) {
                                String newClassString = classString.substring(classString.indexOf("/") + 1);
                                parents.replaceAll(string -> {
                                    if (string.equals(classString)) {
                                        return newClassString;
                                    }
                                    return classString;
                                });
                                nameVersion = classString.substring(1, classString.indexOf("/")).replace("_", ".");
                            }
                        }

                        newName = name;

                        boolean done = false;

                        for (String classString : parents) {
                            for (Pair<IMappingsProvider, Pair<Boolean, Remapper>> remapper : remappers) {
                                if (!remapper.getRight().getLeft() || remapper.getLeft().getVersion().equals(nameVersion)) {
                                    newName = remapper.getRight().getRight().mapFieldName(classString, newName, descriptor);
                                    done = true;
                                }

                                if (done) {
                                    break;
                                }
                            }

                            if (done) {
                                break;
                            }
                        }

                        cache.put(owner + name + descriptor, newName);
                    }

                    return newName;
                }

                @Override
                public String mapMethodName(String owner, String name, String descriptor) {
                    String newName = cache.get(owner + name + descriptor);

                    if (newName == null) {
                        List<String> parents = getAllParents(classesMap, owner);
                        String nameVersion = null;
                        for (String classString : new ArrayList<>(parents)) {
                            if (classString.startsWith("v")) {
                                String newClassString = classString.substring(classString.indexOf("/") + 1);
                                parents.replaceAll(string -> {
                                    if (string.equals(classString)) {
                                        return newClassString;
                                    }
                                    return classString;
                                });
                                nameVersion = classString.substring(1, classString.indexOf("/")).replace("_", ".");
                            }
                        }

                        newName = name;

                        boolean done = false;

                        for (String classString : parents) {
                            for (Pair<IMappingsProvider, Pair<Boolean, Remapper>> remapper : remappers) {
                                if (!remapper.getRight().getLeft() || remapper.getLeft().getVersion().equals(nameVersion)) {
                                    newName = remapper.getRight().getRight().mapMethodName(classString, newName, descriptor);
                                    done = true;
                                }

                                if (done) {
                                    break;
                                }
                            }

                            if (done) {
                                break;
                            }
                        }

                        cache.put(owner + name + descriptor, newName);
                    }

                    return newName;
                }

                @Override
                public String mapVariableName(String owner, String methodOwner, String methodDesc, String name, int index) {
                    String newOwner;
                    String nameVersion;
                    if (owner.startsWith("v")) {
                        newOwner = owner.substring(owner.indexOf("/") + 1);
                        nameVersion = owner.substring(1, owner.indexOf("/")).replace("_", ".");
                    } else {
                        newOwner = owner;
                        nameVersion = null;
                    }

                    String newName = name;
                    for (Pair<IMappingsProvider, Pair<Boolean, Remapper>> remapper : remappers) {
                        if (!remapper.getRight().getLeft() || remapper.getLeft().getVersion().equals(nameVersion)) {
                            newName = remapper.getRight().getRight().mapVariableName(newOwner, methodOwner, methodDesc, newName, index);
                        }
                    }
                    return newName;
                }
            };

            Remapper realRemapper = new Remapper() {

                @Override
                public String map(String name) {
                    return collectiveRemapper.map(name);
                }

                @Override
                public String mapFieldName(String owner, String name, String descriptor) {
                    return collectiveRemapper.mapFieldName(owner, name, descriptor);
                }

                @Override
                public String mapMethodName(String owner, String name, String descriptor) {
                    return collectiveRemapper.mapMethodName(owner, name, descriptor);
                }

                @Override
                public Object mapValue(Object value) {
                    Object newValue = value;
                    try {
                        if(newValue instanceof String && ((String) newValue).chars().allMatch(letter -> Character.isLetterOrDigit(letter) || "#_/();$[".contains(String.valueOf((char) letter)))) {
                            String valueString = (String) newValue;
                            if (valueString.contains("#")) {
                                String[] classElementSplit = (valueString).split("#");
                                String newName = this.map(classElementSplit[0]);
                                if(classElementSplit[1].contains("(")) {
                                    String[] methodDescriptorSplit = classElementSplit[1].split("\\(");
                                    methodDescriptorSplit[1] = "(" + methodDescriptorSplit[1];

                                    String newMethodName = this.mapMethodName(classElementSplit[0], methodDescriptorSplit[0], methodDescriptorSplit[1]);
                                    String newMethodDescription = this.mapMethodDesc(methodDescriptorSplit[1]);
                                    newValue =  newName + "#" + newMethodName + newMethodDescription;
                                } else {
                                    String newFieldName = this.mapFieldName(classElementSplit[0], classElementSplit[1], "");
                                    newValue =  newName + "#" + newFieldName;
                                }
                            } else if (valueString.startsWith("(")) {
                                newValue = this.mapDesc(valueString);
                            } else if (valueString.contains("(")) {
                                String[] methodDescriptorSplit = valueString.split("\\(");
                                methodDescriptorSplit[1] = "(" + methodDescriptorSplit[1];

                                String newMethodName = methodDescriptorSplit[0];
                                String newMethodDescription = this.mapMethodDesc(methodDescriptorSplit[1]);

                                newValue = newMethodName + newMethodDescription;
                            } else {
                                newValue = this.map(valueString);
                            }
                        }
                    } catch (Exception ignored) {
                        //e.printStackTrace();
                    }

                    try {
                        if (value instanceof Type) {
                            newValue = Type.getType(this.mapDesc(((Type) value).getDescriptor()));
                        }

                        if (value instanceof Handle) {
                            Handle handle = (Handle) value;
                            newValue = new Handle(handle.getTag(), handle.getOwner(), handle.getName(), this.mapDesc(handle.getDesc()), handle.isInterface());
                        }
                    } catch(Exception ignored) {

                    }

                    return newValue;
                }

                @Override
                public String mapVariableName(String owner, String methodOwner, String methodDesc, String name, int index) {
                    return collectiveRemapper.mapVariableName(owner, methodOwner, methodDesc, name, index);
                }

            };

            System.out.println("Actually beginning remapping!");

            List<Pair<Project, CustomTransformer>> transformers = new ArrayList<>();
            addAllTransformers(project, transformers);

            for(Map.Entry<String, ClassNode> entry : classNodes.entrySet()) {
                ClassNode classNode = entry.getValue();

                List<String> parents = new ArrayList<>();
                if (!classNode.superName.equals("java/lang/Object")) {
                    parents.add(classNode.superName);
                }
                parents.addAll(classNode.interfaces);

                classesMap.put(classNode.name, parents);
            }

            Map<String, Pair<Map<String, String>, List<String>>> context = new HashMap<>();
            for (Pair<IMappingsProvider, Boolean> mappingsProvider : mappingsProviders) {
                context.putAll(mappingsProvider.getLeft().getContext(IMappingsProvider.Side.NAMED, mappingsProvider.getRight()));
            }

            for(Pair<Project, CustomTransformer> customTransformer : transformers) {
                long remapTime = System.currentTimeMillis();
                customTransformer.getRight().setRemapper(collectiveRemapper);

                customTransformer.getRight().setContext(context);

                customTransformer.getRight().map(new ArrayList<>(classNodes.values()), new HashMap<>(classNodesModified));
                System.out.println(customTransformer.getRight().getClass().getSimpleName() + " done in " + (System.currentTimeMillis() - remapTime) + " milliseconds.");
            }

            System.out.println("Remapping and outputting.");
            long remapTime = System.currentTimeMillis();

            for(Map.Entry<String, ClassNode> entry : classNodesModified.entrySet()) {
                File file = classPaths.get(entry.getKey());

                ClassNode classNode = entry.getValue();
                ClassWriter writer = new ClassWriter(0);
                ClassVisitor visitor = new ClassRemapper(writer, realRemapper);
                classNode.accept(visitor);
                try {
                    File file2 = new File(file.getAbsolutePath().replace("\\", "/").replace("/classes/", "/classesObf/"));
                    file2.getParentFile().mkdirs();
                    OutputStream outputStream = new FileOutputStream(file2);
                    outputStream.write(writer.toByteArray());
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            System.out.println("Done remapping in " + (System.currentTimeMillis() - remapTime) + " milliseconds! (" + (System.currentTimeMillis() - startTime) + " milliseconds total)");
        }

    }

    public void addAllMappingsProvider(IMappingsProvider mappingsProvider, boolean prefix) {
        this.allMappingsProviders.add(new Pair<>(mappingsProvider, prefix));
    }

    public List<Pair<IMappingsProvider, Boolean>> getAllMappingsProviders() {
        return allMappingsProviders;
    }

}
