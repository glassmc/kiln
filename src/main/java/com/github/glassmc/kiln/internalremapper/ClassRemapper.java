// ASM: a very small and fast Java bytecode manipulation framework
// Copyright (c) 2000-2011 INRIA, France Telecom
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions
// are met:
// 1. Redistributions of source code must retain the above copyright
//    notice, this list of conditions and the following disclaimer.
// 2. Redistributions in binary form must reproduce the above copyright
//    notice, this list of conditions and the following disclaimer in the
//    documentation and/or other materials provided with the distribution.
// 3. Neither the name of the copyright holders nor the names of its
//    contributors may be used to endorse or promote products derived from
//    this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
// AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
// IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
// ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
// LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
// CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
// SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
// INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
// CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
// ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
// THE POSSIBILITY OF SUCH DAMAGE.

package com.github.glassmc.kiln.internalremapper;

import java.util.List;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.ModuleVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.RecordComponentVisitor;
import org.objectweb.asm.TypePath;
import org.objectweb.asm.commons.*;

public class ClassRemapper extends ClassVisitor {

    protected final Remapper remapper;

    protected String className;

    public ClassRemapper(final ClassVisitor classVisitor, final Remapper remapper) {
        super(Opcodes.ASM9, classVisitor);
        this.remapper = remapper;
    }

    @Override
    public void visit(final int version, final int access, final String name, final String signature, final String superName, final String[] interfaces) {
        this.className = name;
        super.visit(
                version,
                access,
                remapper.mapType(name),
                remapper.mapSignature(signature, false),
                remapper.mapType(superName),
                interfaces == null ? null : remapper.mapTypes(interfaces));
    }

    @Override
    public ModuleVisitor visitModule(final String name, final int flags, final String version) {
        ModuleVisitor moduleVisitor = super.visitModule(remapper.mapModuleName(name), flags, version);
        return moduleVisitor == null ? null : createModuleRemapper(moduleVisitor);
    }

    @Override
    public AnnotationVisitor visitAnnotation(final String descriptor, final boolean visible) {
        AnnotationVisitor annotationVisitor =
                super.visitAnnotation(remapper.mapDesc(descriptor), visible);
        return annotationVisitor == null
                ? null
                : createAnnotationRemapper(descriptor, annotationVisitor);
    }

    @Override
    public AnnotationVisitor visitTypeAnnotation(final int typeRef, final TypePath typePath, final String descriptor, final boolean visible) {
        AnnotationVisitor annotationVisitor =
                super.visitTypeAnnotation(typeRef, typePath, remapper.mapDesc(descriptor), visible);
        return annotationVisitor == null
                ? null
                : createAnnotationRemapper(descriptor, annotationVisitor);
    }

    @Override
    public void visitAttribute(final Attribute attribute) {
        if (attribute instanceof ModuleHashesAttribute) {
            ModuleHashesAttribute moduleHashesAttribute = (ModuleHashesAttribute) attribute;
            List<String> modules = moduleHashesAttribute.modules;
            for (int i = 0; i < modules.size(); ++i) {
                modules.set(i, remapper.mapModuleName(modules.get(i)));
            }
        }
        super.visitAttribute(attribute);
    }

    @Override
    public RecordComponentVisitor visitRecordComponent(
            final String name, final String descriptor, final String signature) {
        RecordComponentVisitor recordComponentVisitor =
                super.visitRecordComponent(
                        remapper.mapRecordComponentName(className, name, descriptor),
                        remapper.mapDesc(descriptor),
                        remapper.mapSignature(signature, true));
        return recordComponentVisitor == null
                ? null
                : createRecordComponentRemapper(recordComponentVisitor);
    }

    @Override
    public FieldVisitor visitField(final int access, final String name, final String descriptor, final String signature, final Object value) {
        FieldVisitor fieldVisitor =
                super.visitField(
                        access,
                        remapper.mapFieldName(className, name, descriptor),
                        remapper.mapDesc(descriptor),
                        remapper.mapSignature(signature, true),
                        (value == null) ? null : remapper.mapValue(value));
        return fieldVisitor == null ? null : createFieldRemapper(fieldVisitor);
    }

    @Override
    public MethodVisitor visitMethod(final int access, final String name, final String descriptor, final String signature, final String[] exceptions) {
        String remappedDescriptor = remapper.mapMethodDesc(descriptor);

        String methodName = remapper.mapMethodName(className, name, descriptor);

        MethodVisitor methodVisitor =
                super.visitMethod(
                        access,
                        methodName,
                        remappedDescriptor,
                        remapper.mapSignature(signature, false),
                        exceptions == null ? null : remapper.mapTypes(exceptions));
        return methodVisitor == null ? null : createMethodRemapper(methodVisitor, name, descriptor);
    }

    @Override
    public void visitInnerClass(final String name, final String outerName, final String innerName, final int access) {
        super.visitInnerClass(
                remapper.mapType(name),
                outerName == null ? null : remapper.mapType(outerName),
                innerName == null ? null : remapper.mapInnerClassName(name, outerName, innerName),
                access);
    }

    @Override
    public void visitOuterClass(final String owner, final String name, final String descriptor) {
        super.visitOuterClass(
                remapper.mapType(owner),
                name == null ? null : remapper.mapMethodName(owner, name, descriptor),
                descriptor == null ? null : remapper.mapMethodDesc(descriptor));
    }

    @Override
    public void visitNestHost(final String nestHost) {
        super.visitNestHost(remapper.mapType(nestHost));
    }

    @Override
    public void visitNestMember(final String nestMember) {
        super.visitNestMember(remapper.mapType(nestMember));
    }

    @Override
    public void visitPermittedSubclass(final String permittedSubclass) {
        super.visitPermittedSubclass(remapper.mapType(permittedSubclass));
    }

    protected FieldVisitor createFieldRemapper(final FieldVisitor fieldVisitor) {
        return new FieldRemapper(fieldVisitor, remapper);
    }

    protected MethodVisitor createMethodRemapper(final MethodVisitor methodVisitor, String methodName, String methodDesc) {
        return new MethodRemapper(methodVisitor, remapper, className, methodName, methodDesc);
    }

    protected AnnotationVisitor createAnnotationRemapper(final String descriptor, final AnnotationVisitor annotationVisitor) {
        return new AnnotationRemapper(descriptor, annotationVisitor, remapper);
    }

    protected ModuleVisitor createModuleRemapper(final ModuleVisitor moduleVisitor) {
        return new ModuleRemapper(moduleVisitor, remapper);
    }

    protected RecordComponentVisitor createRecordComponentRemapper(final RecordComponentVisitor recordComponentVisitor) {
        return new RecordComponentRemapper(recordComponentVisitor, remapper);
    }
}