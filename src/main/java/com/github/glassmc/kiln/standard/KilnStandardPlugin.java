package com.github.glassmc.kiln.standard;

import com.github.glassmc.kiln.standard.mappings.IMappingsProvider;
import org.apache.commons.io.IOUtils;
import org.gradle.api.*;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import java.io.*;

public class KilnStandardPlugin implements Plugin<Project> {

    private static KilnStandardPlugin instance;

    public static KilnStandardPlugin getInstance() {
        return instance;
    }

    private Project project;
    private KilnStandardExtension extension;

    @Override
    public void apply(Project project) {
        instance = this;
        this.project = project;

        this.extension = project.getExtensions().create("kiln", KilnStandardExtension.class);

        project.afterEvaluate(p -> {
            project.getTasks().findByName("compileJava").doLast(new ReobfuscateAction());
        });
    }

    public File getCache() {
        File cache = new File(this.project.getGradle().getGradleUserHomeDir() + "/caches/kiln");
        cache.mkdirs();
        return cache;
    }

    public File getLocalRepository() {
        return new File(this.getCache(), "repository");
    }

    public Project getProject() {
        return project;
    }

    private class ReobfuscateAction implements Action<Task> {

        @Override
        public void execute(Task task) {
            File classes = new File(project.getBuildDir(), "classes");

            IMappingsProvider mappingsProvider = DependencyHandlerExtension.mappingsProviders.get(getProject());
            if(mappingsProvider == null) {
                return;
            }

            final boolean[] setParents = {false};

            Remapper remapper = mappingsProvider.getRemapper(IMappingsProvider.Direction.TO_OBFUSCATED);
            Remapper realRemapper = new Remapper() {

                @Override
                public String map(String name) {
                    if(!setParents[0]) {
                        for(CustomRemapper customRemapper : extension.remappers) {
                            customRemapper.setParent(this);
                        }
                        setParents[0] = true;
                    }

                    String newName = remapper.map(name);
                    for(Remapper remapper1 : extension.remappers) {
                        newName = remapper1.map(newName);
                    }
                    return newName;
                }

                @Override
                public String mapFieldName(String owner, String name, String descriptor) {
                    String newName = name;
                    for(Remapper remapper1 : extension.remappers) {
                        newName = remapper1.mapFieldName(owner, newName, descriptor);
                    }
                    newName = remapper.mapFieldName(owner, name, descriptor);
                    return newName;
                }

                @Override
                public String mapMethodName(String owner, String name, String descriptor) {
                    String newName = name;
                    for(Remapper remapper1 : extension.remappers) {
                        newName = remapper1.mapMethodName(owner, newName, descriptor);
                    }
                    newName = remapper.mapMethodName(owner, newName, descriptor);

                    System.out.println(owner + " " + name + " " + newName + " " + descriptor);
                    return newName;
                }

                @Override
                public Object mapValue(Object value) {
                    Object newValue = value;
                    for(Remapper remapper1 : extension.remappers) {
                        newValue = remapper1.mapValue(newValue);
                    }
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
                    String newName = name;
                    for(Remapper remapper1 : extension.remappers) {
                        newName = remapper1.mapAnnotationAttributeName(descriptor, newName);
                    }
                    newName = remapper.mapAnnotationAttributeName(descriptor, name);
                    return newName;
                }
            };

            DependencyHandlerExtension.mappingsProviders.remove(getProject());

            for(File file : project.fileTree(classes)) {
                if(!file.getName().endsWith(".class")) {
                    continue;
                }

                try {
                    InputStream inputStream = new FileInputStream(file);
                    ClassReader classReader = new ClassReader(IOUtils.readFully(inputStream, inputStream.available()));
                    ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                    ClassVisitor visitor = new ClassRemapper(writer, realRemapper);
                    classReader.accept(visitor, ClassReader.EXPAND_FRAMES);
                    inputStream.close();
                    OutputStream outputStream = new FileOutputStream(file);
                    outputStream.write(writer.toByteArray());
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

    }

}
