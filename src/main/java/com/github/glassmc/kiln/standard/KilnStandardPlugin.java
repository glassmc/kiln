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
import java.lang.reflect.Field;
import java.util.*;

public class KilnStandardPlugin implements Plugin<Project> {

    private static KilnStandardPlugin instance;

    public static KilnStandardPlugin getInstance() {
        return instance;
    }

    private Project project;
    private KilnStandardExtension extension;

    private IMappingsProvider mappingsProvider;

    @Override
    public void apply(Project project) {
        instance = this;
        this.project = project;

        this.extension = project.getExtensions().create("kiln", KilnStandardExtension.class);

        project.getPlugins().apply("java-library");

        project.getTasks().getByName("compileJava").dependsOn(project.getTasks().getByName("processResources"));

        project.getPlugins().apply("com.github.johnrengelman.shadow");

        Configuration shadowImplementation = project.getConfigurations().create("shadowImplementation");
        project.getConfigurations().getByName("implementation").extendsFrom(shadowImplementation);

        Configuration shadowApi = project.getConfigurations().create("shadowApi");
        project.getConfigurations().getByName("api").extendsFrom(shadowApi);

        ShadowJar shadowJar = (ShadowJar) project.getTasks().getByName("shadowJar");
        shadowJar.getConfigurations().clear();
        shadowJar.getConfigurations().add(project.getConfigurations().getByName("shadowImplementation"));
        shadowJar.getConfigurations().add(project.getConfigurations().getByName("shadowApi"));

        project.afterEvaluate(p -> {
            project.getTasks().findByName("compileJava").doLast(new ReobfuscateAction());
        });
    }

    public File getCache() {
        File cache = new File(this.project.getGradle().getGradleUserHomeDir() + "/caches/kiln");
        cache.mkdirs();
        return cache;
    }

    public Project getProject() {
        return project;
    }

    public void setMappingsProvider(IMappingsProvider mappingsProvider) {
        this.mappingsProvider = mappingsProvider;
    }

    public IMappingsProvider getMappingsProvider() {
        return mappingsProvider;
    }

    private class ReobfuscateAction implements Action<Task> {

        @Override
        public void execute(Task task) {
            File classes = new File(project.getBuildDir(), "classes");

            IMappingsProvider mappingsProvider = getMappingsProvider();
            if(mappingsProvider == null) {
                return;
            }

            Remapper remapper = mappingsProvider.getRemapper(IMappingsProvider.Direction.TO_OBFUSCATED);
            Remapper realRemapper = new Remapper() {

                @Override
                public String map(String name) {
                    return remapper.map(name);
                }

                @Override
                public String mapFieldName(String owner, String name, String descriptor) {
                    return remapper.mapFieldName(owner, name, descriptor);
                }

                @Override
                public String mapMethodName(String owner, String name, String descriptor) {
                    return remapper.mapMethodName(owner, name, descriptor);
                }

                @Override
                public Object mapValue(Object value) {
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
                                String newFieldName = this.mapFieldName(classElementSplit[0], newName, "");
                                newValue =  newName + "#" + newFieldName;
                            }
                        } else {
                            newValue = this.map(valueString);
                        }
                    }

                    newValue = remapper.mapValue(newValue);

                    return newValue;
                }

                @Override
                public String mapAnnotationAttributeName(String descriptor, String name) {
                    return remapper.mapAnnotationAttributeName(descriptor, name);
                }
            };

            Map<String, ClassNode> classNodes = new HashMap<>();

            for(File file : project.fileTree(classes)) {
                if(!file.getName().endsWith(".class")) {
                    continue;
                }

                try {
                    InputStream inputStream = new FileInputStream(file);
                    ClassReader classReader = new ClassReader(IOUtils.readFully(inputStream, inputStream.available()));
                    ClassNode classNode = new ClassNode();
                    classReader.accept(classNode, 0);

                    classNodes.put(file.getAbsolutePath().replace(new File(classes, "java/main").getAbsolutePath() + "/", "").replace(".class", ""), classNode);
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            for(CustomRemapper customRemapper : extension.remappers) {
                customRemapper.setParent(remapper);
                customRemapper.map(classNodes);
            }

            for(Map.Entry<String, ClassNode> classNode : classNodes.entrySet()) {
                ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                ClassVisitor visitor = new ClassRemapper(writer, realRemapper);
                classNode.getValue().accept(visitor);
                try {
                    OutputStream outputStream = new FileOutputStream(new File(new File(classes, "java/main"), classNode.getKey() + ".class"));
                    outputStream.write(writer.toByteArray());
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

    }

}
