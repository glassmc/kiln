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

import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;
import org.objectweb.asm.signature.SignatureWriter;

public abstract class Remapper {

    public String mapDesc(final String descriptor) {
        if (descriptor.isEmpty()) return descriptor;

        return mapType(Type.getType(descriptor)).getDescriptor();
    }

    private Type mapType(final Type type) {
        switch (type.getSort()) {
            case Type.ARRAY:
                StringBuilder remappedDescriptor = new StringBuilder();
                for (int i = 0; i < type.getDimensions(); ++i) {
                    remappedDescriptor.append('[');
                }
                remappedDescriptor.append(mapType(type.getElementType()).getDescriptor());
                return Type.getType(remappedDescriptor.toString());
            case Type.OBJECT:
                String remappedInternalName = map(type.getInternalName());
                return remappedInternalName != null ? Type.getObjectType(remappedInternalName) : type;
            case Type.METHOD:
                return Type.getMethodType(mapMethodDesc(type.getDescriptor()));
            default:
                return type;
        }
    }

    public String mapType(final String internalName) {
        if (internalName == null) {
            return null;
        }
        return mapType(Type.getObjectType(internalName)).getInternalName();
    }

    public String[] mapTypes(final String[] internalNames) {
        String[] remappedInternalNames = null;
        for (int i = 0; i < internalNames.length; ++i) {
            String internalName = internalNames[i];
            String remappedInternalName = mapType(internalName);
            if (remappedInternalName != null) {
                if (remappedInternalNames == null) {
                    remappedInternalNames = internalNames.clone();
                }
                remappedInternalNames[i] = remappedInternalName;
            }
        }
        return remappedInternalNames != null ? remappedInternalNames : internalNames;
    }

    public String mapMethodDesc(final String methodDescriptor) {
        if (methodDescriptor.isEmpty()) return methodDescriptor;

        if ("()V".equals(methodDescriptor)) {
            return methodDescriptor;
        }

        StringBuilder stringBuilder = new StringBuilder("(");
        for (Type argumentType : Type.getArgumentTypes(methodDescriptor)) {
            stringBuilder.append(mapType(argumentType).getDescriptor());
        }
        Type returnType = Type.getReturnType(methodDescriptor);
        if (returnType == Type.VOID_TYPE) {
            stringBuilder.append(")V");
        } else {
            stringBuilder.append(')').append(mapType(returnType).getDescriptor());
        }
        return stringBuilder.toString();
    }

    public Object mapValue(final Object value) {
        if (value instanceof Type) {
            return mapType((Type) value);
        }
        if (value instanceof Handle) {
            Handle handle = (Handle) value;
            return new Handle(
                    handle.getTag(),
                    mapType(handle.getOwner()),
                    handle.getTag() <= Opcodes.H_PUTSTATIC
                            ? mapFieldName(handle.getOwner(), handle.getName(), handle.getDesc())
                            : mapMethodName(handle.getOwner(), handle.getName(), handle.getDesc()),
                    handle.getTag() <= Opcodes.H_PUTSTATIC
                            ? mapDesc(handle.getDesc())
                            : mapMethodDesc(handle.getDesc()),
                    handle.isInterface());
        }
        if (value instanceof ConstantDynamic) {
            ConstantDynamic constantDynamic = (ConstantDynamic) value;
            int bootstrapMethodArgumentCount = constantDynamic.getBootstrapMethodArgumentCount();
            Object[] remappedBootstrapMethodArguments = new Object[bootstrapMethodArgumentCount];
            for (int i = 0; i < bootstrapMethodArgumentCount; ++i) {
                remappedBootstrapMethodArguments[i] =
                        mapValue(constantDynamic.getBootstrapMethodArgument(i));
            }
            String descriptor = constantDynamic.getDescriptor();
            return new ConstantDynamic(
                    mapInvokeDynamicMethodName(constantDynamic.getName(), descriptor),
                    mapDesc(descriptor),
                    (Handle) mapValue(constantDynamic.getBootstrapMethod()),
                    remappedBootstrapMethodArguments);
        }
        return value;
    }

    public String mapSignature(final String signature, final boolean typeSignature) {
        if (signature == null) {
            return null;
        }
        SignatureReader signatureReader = new SignatureReader(signature);
        SignatureWriter signatureWriter = new SignatureWriter();
        SignatureVisitor signatureRemapper = createSignatureRemapper(signatureWriter);
        if (typeSignature) {
            signatureReader.acceptType(signatureRemapper);
        } else {
            signatureReader.accept(signatureRemapper);
        }
        return signatureWriter.toString();
    }

    protected SignatureVisitor createSignatureRemapper(final SignatureVisitor signatureVisitor) {
        return new SignatureRemapper(signatureVisitor, this);
    }

    public String mapAnnotationAttributeName(final String descriptor, final String name) {
        return name;
    }

    public String mapInnerClassName(final String name, final String ownerName, final String innerName) {
        final String remappedInnerName = this.mapType(name);
        if (remappedInnerName.contains("$")) {
            int index = remappedInnerName.lastIndexOf('$') + 1;
            while (index < remappedInnerName.length()
                    && Character.isDigit(remappedInnerName.charAt(index))) {
                index++;
            }
            return remappedInnerName.substring(index);
        } else {
            return innerName;
        }
    }

    public String mapMethodName(final String owner, final String name, final String descriptor) {
        return name;
    }

    public String mapInvokeDynamicMethodName(final String name, final String descriptor) {
        return name;
    }

    public String mapRecordComponentName(final String owner, final String name, final String descriptor) {
        return name;
    }

    public String mapFieldName(final String owner, final String name, final String descriptor) {
        return name;
    }

    public String mapPackageName(final String name) {
        return name;
    }

    public String mapModuleName(final String name) {
        return name;
    }

    public String map(final String internalName) {
        return internalName;
    }

    public String mapVariableName(String clazz, String method, String methodDesc, String name, int index) {
        return name;
    }

}
