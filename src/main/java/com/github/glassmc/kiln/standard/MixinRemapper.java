package com.github.glassmc.kiln.standard;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("unused")
public class MixinRemapper extends CustomTransformer {

    private final Map<String, String> mixinClasses = new HashMap<>();

    @Override
    public void map(List<ClassNode> context, Map<String, ClassNode> classNodes) {
        if (classNodes.isEmpty()) {
            return;
        }

        for (ClassNode classNode : context) {
            if (classNode.invisibleAnnotations != null) {
                for (AnnotationNode annotationNode : classNode.invisibleAnnotations) {
                    if (annotationNode.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")) {
                        if (annotationNode.values != null) {
                            List<Object> values = (List<Object>) annotationNode.values.get(annotationNode.values.indexOf("value") + 1);
                            if (values.get(0) instanceof Type) {
                                mixinClasses.put(classNode.name, ((Type) values.get(0)).getClassName().replace(".", "/"));
                            }
                        }
                    }
                }
            }
        }

        for(String className : classNodes.keySet()) {
            ClassNode classNode = classNodes.get(className);

            if (classNode.invisibleAnnotations != null) {
                for (AnnotationNode annotationNode : classNode.invisibleAnnotations) {
                    if (annotationNode.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")) {
                        if (annotationNode.values != null) {
                            List<Object> values = (List<Object>) annotationNode.values.get(annotationNode.values.indexOf("value") + 1);
                            if (values.get(0) instanceof Type) {
                                List<Type> oldValues = new ArrayList<>((List<Type>) (List<?>) values);
                                values.clear();

                                for (Type type : oldValues) {
                                    values.add(Type.getType("L" + this.getRemapper().map(type.getClassName().replace(".", "/")) + ";"));
                                }
                            }
                        }
                    }
                }
            }

            for(FieldNode fieldNode : classNode.fields) {
                if(this.getMixinClass(className) != null) {
                    for(AnnotationNode annotationNode : fieldNode.visibleAnnotations) {
                        if(annotationNode.desc.equals("Lorg/spongepowered/asm/mixin/Shadow;")) {
                            fieldNode.name = this.mapFieldName(className, fieldNode.name, fieldNode.desc);
                        }
                    }
                }
            }

            for(MethodNode methodNode : classNode.methods) {
                if(this.getMixinClass(className) != null) {
                    if (methodNode.visibleAnnotations != null) {
                        for(AnnotationNode annotationNode : methodNode.visibleAnnotations) {
                            if(annotationNode.desc.equals("Lorg/spongepowered/asm/mixin/gen/Invoker;") ||
                                    annotationNode.desc.equals("Lorg/spongepowered/asm/mixin/gen/Accessor;") ||
                                    annotationNode.desc.equals("Lorg/spongepowered/asm/mixin/Overwrite;") ||
                                    annotationNode.desc.equals("Lorg/spongepowered/asm/mixin/Shadow;")) {
                                methodNode.name = this.mapMethodName(className, methodNode.name, methodNode.desc);
                            }
                        }
                    }

                    if (methodNode.visibleAnnotations != null) {
                        for(AnnotationNode annotationNode : methodNode.visibleAnnotations) {
                            if (annotationNode.desc.equals("Lorg/spongepowered/asm/mixin/gen/Accessor;")) {
                                if (annotationNode.values != null) {
                                    int index = annotationNode.values.indexOf("value");
                                    String string = (String) annotationNode.values.get(index + 1);

                                    string = this.mapFieldName(className, string, "");

                                    annotationNode.values.set(index + 1, string);
                                }
                            } else if (annotationNode.desc.equals("Lorg/spongepowered/asm/mixin/gen/Invoker;")) {
                                if (annotationNode.values != null) {
                                    int index = annotationNode.values.indexOf("value");
                                    String string = (String) annotationNode.values.get(index + 1);
                                    int descIndex = string.indexOf("(");
                                    String name = string.substring(0, descIndex);
                                    String descriptor = string.substring(descIndex);

                                    string = this.mapMethodName(className, name, descriptor) + this.getRemapper().mapDesc(descriptor);

                                    annotationNode.values.set(index + 1, string);
                                }
                            }
                        }
                    }

                    if (methodNode.visibleAnnotations != null) {
                        for(AnnotationNode annotationNode : methodNode.visibleAnnotations) {
                            if(annotationNode.desc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;") ||
                                    annotationNode.desc.equals("Lorg/spongepowered/asm/mixin/injection/Redirect;") ||
                                    annotationNode.desc.equals("Lorg/spongepowered/asm/mixin/injection/ModifyConstant;")) {
                                List<String> targets = (List<String>) annotationNode.values.get(annotationNode.values.indexOf("method") + 1);
                                List<String> newTargets = new ArrayList<>();
                                for(String string : targets) {
                                    int splitIndex = string.indexOf('(');
                                    System.out.println(string);
                                    String name = string.substring(0, splitIndex);
                                    String desc = string.substring(splitIndex);
                                    newTargets.add(this.getRemapper().mapMethodName(this.getMixinClass(className), name, desc) + this.getRemapper().mapMethodDesc(desc));
                                }
                                int index = annotationNode.values.indexOf(targets);
                                annotationNode.values.remove(targets);
                                annotationNode.values.add(index, newTargets);
                            }

                            if(annotationNode.values != null && annotationNode.values.contains("at")) {
                                List<AnnotationNode> atAnnotations = (List<AnnotationNode>) annotationNode.values.get(annotationNode.values.indexOf("at") + 1);

                                for (AnnotationNode atAnnotation : atAnnotations) {
                                    if (atAnnotation.values.get(0) instanceof AnnotationNode) {
                                        atAnnotation = (AnnotationNode) atAnnotation.values.get(0);
                                    }

                                    if(atAnnotation.values.contains("target")) {
                                        String target = (String) atAnnotation.values.get(atAnnotation.values.indexOf("target") + 1);

                                        int index = target.indexOf(";");
                                        String[] classMethodSplit = new String[] {target.substring(0, index), target.substring(index + 1)};
                                        String className1 = classMethodSplit[0].substring(1);
                                        int methodDescIndex = classMethodSplit[1].indexOf('(');
                                        if (methodDescIndex != -1) {
                                            String methodName = classMethodSplit[1].substring(0, methodDescIndex);
                                            String methodDesc = classMethodSplit[1].substring(methodDescIndex);

                                            int targetIndex = atAnnotation.values.indexOf(target);

                                            atAnnotation.values.remove(target);

                                            atAnnotation.values.add(targetIndex, "L" + this.getRemapper().map(className1) + ";" + this.getRemapper().mapMethodName(className1, methodName, methodDesc) + this.getRemapper().mapMethodDesc(methodDesc));
                                        } else {
                                            if (classMethodSplit[1].contains(":")) {
                                                String[] fieldSplit = classMethodSplit[1].split(":");

                                                String fieldName = fieldSplit[0];
                                                int targetIndex = atAnnotation.values.indexOf(target);

                                                atAnnotation.values.remove(target);

                                                atAnnotation.values.add(targetIndex, "L" + this.getRemapper().map(className1) + ";" + this.getRemapper().mapFieldName(className1, fieldName, "") + ":" + this.getRemapper().mapDesc(fieldSplit[1]));
                                            } else {
                                                String fieldName = classMethodSplit[1];
                                                int targetIndex = atAnnotation.values.indexOf(target);

                                                atAnnotation.values.remove(target);

                                                atAnnotation.values.add(targetIndex, "L" + this.getRemapper().map(className1) + ";" + this.getRemapper().mapFieldName(className1, fieldName, ""));
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        for (String className : classNodes.keySet()) {
            for (MethodNode methodNode : classNodes.get(className).methods) {
                for(AbstractInsnNode node : methodNode.instructions.toArray()) {
                    if(node instanceof FieldInsnNode) {
                        FieldInsnNode fieldInsnNode = (FieldInsnNode) node;
                        if(this.getMixinClass(fieldInsnNode.owner) != null) {
                            fieldInsnNode.name = this.mapFieldName(fieldInsnNode.owner, fieldInsnNode.name, fieldInsnNode.desc);
                        }
                    }
                    if(node instanceof MethodInsnNode) {
                        MethodInsnNode methodInsnNode = (MethodInsnNode) node;
                        if(this.getMixinClass(methodInsnNode.owner) != null) {
                            methodInsnNode.name = this.mapMethodName(methodInsnNode.owner, methodInsnNode.name, methodInsnNode.desc);
                        }
                    }
                }
            }
        }
    }

    private String mapMethodName(String className, String methodName, String methodDesc) {
        String mixinClass = this.getMixinClass(className);

        if(methodName.startsWith("invoke") || methodName.startsWith("call")) {
            String prefix = methodName.startsWith("invoke") ? "invoke" : "call";
            String strippedName = methodName.replace(prefix, "");
            strippedName = Character.toLowerCase(strippedName.charAt(0)) + strippedName.substring(1);
            methodName = this.getRemapper().mapMethodName(mixinClass, strippedName, methodDesc);
            methodName = prefix + Character.toUpperCase(methodName.charAt(0)) + methodName.substring(1);
        } else if(methodName.startsWith("get") || methodName.startsWith("set") || methodName.startsWith("is")) {
            String prefix = methodName.startsWith("get") ? "get" : methodName.startsWith("set") ? "set" : "is";
            String strippedName = methodName.replace(prefix, "");
            strippedName = Character.toLowerCase(strippedName.charAt(0)) + strippedName.substring(1);
            methodName = this.getRemapper().mapFieldName(mixinClass, strippedName, methodDesc);
            methodName = prefix + Character.toUpperCase(methodName.charAt(0)) + methodName.substring(1);
        }

        String methodName2 = this.getRemapper().mapMethodName(mixinClass, methodName, methodDesc);

        if (!methodName2.equals(methodName)) {
            return methodName2;
        }

        return methodName;
    }

    private String mapFieldName(String className, String fieldName, String fieldDesc) {
        String mixinClass = this.getMixinClass(className);
        return this.getRemapper().mapFieldName(mixinClass, fieldName, fieldDesc);
    }

    private String getMixinClass(String className) {
        return mixinClasses.get(className);
    }

}