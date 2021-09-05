package com.github.glassmc.kiln.standard;

import com.github.glassmc.kiln.standard.mappings.IMappingsProvider;
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar;
import org.apache.commons.io.IOUtils;
import org.gradle.api.*;
import org.gradle.api.artifacts.Configuration;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.ClassNode;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class KilnStandardPlugin implements Plugin<Project> {

    protected static KilnStandardPlugin instance;

    public static KilnStandardPlugin getInstance() {
        return instance;
    }

    protected Project project;
    private KilnStandardExtension extension;

    private final List<IMappingsProvider> mappingsProviders = new ArrayList<>();

    @Override
    public void apply(Project project) {
        instance = this;
        this.project = project;

        this.extension = project.getExtensions().create("kiln", KilnStandardExtension.class);

        this.mappingsProviders.clear();

        project.getPlugins().apply("java-library");
        project.getTasks().getByName("compileJava").dependsOn(project.getTasks().getByName("processResources"));

        this.setupShadowPlugin();

        project.afterEvaluate(p -> p.getTasks().forEach(task -> {
            if (task.getName().startsWith("compile")) {
                task.doLast(new ReobfuscateAction());
            }
        }));
    }

    private void setupShadowPlugin() {
        project.getPlugins().apply("com.github.johnrengelman.shadow");

        Configuration shadowImplementation = project.getConfigurations().create("shadowImplementation");
        project.getConfigurations().getByName("implementation").extendsFrom(shadowImplementation);

        Configuration shadowApi = project.getConfigurations().create("shadowApi");
        project.getConfigurations().getByName("api").extendsFrom(shadowApi);

        ShadowJar shadowJar = (ShadowJar) project.getTasks().getByName("shadowJar");
        shadowJar.getConfigurations().clear();
        shadowJar.getConfigurations().add(project.getConfigurations().getByName("shadowImplementation"));

        shadowJar.getConfigurations().add(project.getConfigurations().getByName("shadowApi"));
    }

    public File getCache() {
        File cache = new File(this.project.getGradle().getGradleUserHomeDir() + File.separator + "caches" + File.separator + "kiln");
        cache.mkdirs();
        return cache;
    }

    public Project getProject() {
        return project;
    }

    public void addMappingsProvider(IMappingsProvider mappingsProvider) {
        this.mappingsProviders.add(mappingsProvider);
    }

    public List<IMappingsProvider> getMappingsProviders() {
        return mappingsProviders;
    }

    private class ReobfuscateAction implements Action<Task> {

        @Override
        public void execute(Task task) {
            File classes = new File(project.getBuildDir(), "classes");

            List<IMappingsProvider> mappingsProviders = getMappingsProviders();

            Map<String, ClassNode> classNodes = new HashMap<>();
            Map<String, File> classPaths = new HashMap<>();

            for(File file : project.fileTree(classes)) {
                if(!file.getName().endsWith(".class")) {
                    continue;
                }

                try {
                    InputStream inputStream = new FileInputStream(file);
                    ClassReader classReader = new ClassReader(IOUtils.readFully(inputStream, inputStream.available()));
                    ClassNode classNode = new ClassNode();
                    classReader.accept(classNode, 0);

                    String language = file.getAbsolutePath();
                    language = language.substring(language.indexOf("classes" + File.separator) + 8);
                    language = language.substring(0, language.indexOf(File.separator));

                    String className = file.getAbsolutePath().replace(new File(classes, language + File.separator + "main").getAbsolutePath() + File.separator, "").replace(".class", "");

                    classNodes.put(className, classNode);
                    classPaths.put(className, file);
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            List<Remapper> remappers = mappingsProviders.stream()
                    .map(provider -> provider.getRemapper(IMappingsProvider.Direction.TO_OBFUSCATED))
                    .collect(Collectors.toList());

            Remapper collectiveRemapper = new Remapper() {

                @Override
                public String map(String name) {
                    String newName = name;
                    for (Remapper remapper : remappers) {
                        newName = remapper.map(newName);
                    }
                    return newName;
                }

                @Override
                public String mapFieldName(String owner, String name, String descriptor) {
                    String newName = name;
                    for (Remapper remapper : remappers) {
                        newName = remapper.mapFieldName(owner, newName, descriptor);
                    }
                    return newName;
                }

                @Override
                public String mapMethodName(String owner, String name, String descriptor) {
                    String newName = name;
                    for (Remapper remapper : remappers) {
                        newName = remapper.mapMethodName(owner, newName, descriptor);
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
                public String mapModuleName(String name) {
                    return collectiveRemapper.mapModuleName(name);
                }

                @Override
                public Object mapValue(Object value) {
                    try {
                        Object newValue = value;
                        if(newValue instanceof String) {
                            String valueString = (String) newValue;
                            if ((valueString).contains("#") && (valueString).length() >= 6) {
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
                            } else {
                                newValue = this.map(valueString);
                            }
                        }

                        return newValue;
                    } catch (Exception e) {
                        System.out.println("err: " + value);
                        e.printStackTrace();
                        return value;
                    }
                }
            };

            for(CustomRemapper customRemapper : extension.remappers) {
                customRemapper.setParent(collectiveRemapper);
                customRemapper.map(classNodes);
            }

            for(Map.Entry<String, ClassNode> entry : classNodes.entrySet()) {
                ClassNode classNode = entry.getValue();
                ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                ClassVisitor visitor = new ClassRemapper(writer, realRemapper);
                classNode.accept(visitor);
                try {
                    OutputStream outputStream = new FileOutputStream(classPaths.get(entry.getKey()));
                    outputStream.write(writer.toByteArray());
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

    }

}
