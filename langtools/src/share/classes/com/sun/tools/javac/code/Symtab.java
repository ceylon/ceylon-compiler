/*
 * Copyright (c) 1999, 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.sun.tools.javac.code;

import java.util.*;
import javax.lang.model.type.TypeVisitor;
import javax.lang.model.element.ElementVisitor;

import com.sun.tools.javac.util.*;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.code.Symbol.*;
import com.sun.tools.javac.code.Type.*;
import com.sun.tools.javac.jvm.*;
import com.sun.tools.javac.main.OptionName;

import static com.sun.tools.javac.jvm.ByteCodes.*;
import static com.sun.tools.javac.code.Flags.*;

/** A class that defines all predefined constants and operators
 *  as well as special classes such as java.lang.Object, which need
 *  to be known to the compiler. All symbols are held in instance
 *  fields. This makes it possible to work in multiple concurrent
 *  projects, which might use different class files for library classes.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class Symtab {
    /** The context key for the symbol table. */
    protected static final Context.Key<Symtab> symtabKey =
        new Context.Key<Symtab>();

    /** Get the symbol table instance. */
    public static Symtab instance(Context context) {
        Symtab instance = context.get(symtabKey);
        if (instance == null)
            instance = new Symtab(context);
        return instance;
    }

    /** Builtin types.
     */
    public final Type byteType = new Type(TypeTags.BYTE, null);
    public final Type charType = new Type(TypeTags.CHAR, null);
    public final Type shortType = new Type(TypeTags.SHORT, null);
    public final Type intType = new Type(TypeTags.INT, null);
    public final Type longType = new Type(TypeTags.LONG, null);
    public final Type floatType = new Type(TypeTags.FLOAT, null);
    public final Type doubleType = new Type(TypeTags.DOUBLE, null);
    public final Type booleanType = new Type(TypeTags.BOOLEAN, null);
    public final Type botType = new BottomType();
    public final JCNoType voidType = new JCNoType(TypeTags.VOID);

    private final Names names;
    private final ClassReader reader;
    private final Target target;

    /** A symbol for the root package.
     */
    public final PackageSymbol rootPackage;

    /** A symbol for the unnamed package.
     */
    public final PackageSymbol unnamedPackage;

    /** A symbol that stands for a missing symbol.
     */
    public final TypeSymbol noSymbol;

    /** The error symbol.
     */
    public final ClassSymbol errSymbol;

    /** The unknown symbol.
     */
    public final ClassSymbol unknownSymbol;

    /** A value for the errType, with a originalType of noType */
    public final Type errType;

    /** A value for the unknown type. */
    public final Type unknownType;

    /** The builtin type of all arrays. */
    public final ClassSymbol arrayClass;
    public final MethodSymbol arrayCloneMethod;

    /** VGJ: The (singleton) type of all bound types. */
    public final ClassSymbol boundClass;

    /** The builtin type of all methods. */
    public final ClassSymbol methodClass;

    /** Predefined types.
     */
    public final Type objectType;
    public final Type classType;
    public final Type classLoaderType;
    public final Type stringType;
    public final Type stringBufferType;
    public final Type stringBuilderType;
    public final Type booleanObjectType;    
    public final Type integerObjectType;
    public final Type longObjectType;
    public final Type floatObjectType;
    public final Type doubleObjectType;
    public final Type characterObjectType;
    public final Type cloneableType;
    public final Type serializableType;
    // Backported by Ceylon from JDK8
    public final Type methodHandleLookupType;
    public final Type methodHandlesType;
    // Backported by Ceylon from JDK8
    public final Type methodTypeType;
    public final Type methodHandleType;
    public final Type polymorphicSignatureType;
    public final Type throwableType;
    public final Type errorType;
    public final Type interruptedExceptionType;
    public final Type illegalArgumentExceptionType;
    public final Type exceptionType;
    public final Type runtimeExceptionType;
    public final Type classNotFoundExceptionType;
    public final Type noClassDefFoundErrorType;
    public final Type noSuchFieldErrorType;
    public final Type assertionErrorType;
    public final Type cloneNotSupportedExceptionType;
    public final Type nullPointerExceptionType;
    public final Type annotationType;
    public final TypeSymbol enumSym;
    public final Type listType;
    public final Type collectionsType;
    public final Type comparableType;
    public final Type arraysType;
    public final Type iterableType;
    public final Type iteratorType;
    public final Type annotationTargetType;
    public final Type overrideType;
    public final Type retentionType;
    public final Type retentionPolicyType;
    public final Type targetType;
    public final Type elementTypeType;
    public final Type deprecatedType;
    public final Type suppressWarningsType;
    public final Type inheritedType;
    public final Type proprietaryType;
    // Ceylon: guess backport from Java 8
    public final Type jdkProfileType;
    public final Type systemType;
    public final Type autoCloseableType;
    public final Type trustMeType;

    public  Type ceylonAnythingType;
    public  Type ceylonObjectType;
    public  Type ceylonIdentifiableType;
    public  Type ceylonFloatType;
    public  Type ceylonIntegerType;
    public  Type ceylonStringType;
    public  Type ceylonTupleType;
    public  Type ceylonArrayType;
    public  Type ceylonArrayIterableType;
    public  Type ceylonAbstractIterableType;
    public  Type ceylonAbstractIteratorType;
    public  Type ceylonLazyIterableType;
    public  Type ceylonLazyInvokingIterableType;
    public  Type ceylonCharacterType;
    public  Type ceylonByteType;
    public  Type ceylonBooleanType;
    public  Type ceylonFinishedType;
    public  Type ceylonExceptionType;
    public  Type ceylonThrowableType;
    public  Type ceylonAssertionErrorType;
    public  Type ceylonInitializationErrorType;
    public  Type ceylonEnumeratedTypeErrorType;
    public  Type ceylonUninvokableErrorType;
    public  Type ceylonUninitializedMethodErrorType;
    public  Type ceylonUnresolvedCompilationErrorType;
    public  Type ceylonAbstractCallableType;
    public  Type ceylonAbstractTypeConstructorType;
    public  Type ceylonSerializationProxyType;
    public  Type ceylonVariableBoxType;
    public  Type ceylonVariableBoxLongType;
    public  Type ceylonVariableBoxIntType;
    public  Type ceylonVariableBoxDoubleType;
    public  Type ceylonVariableBoxByteType;
    public  Type ceylonVariableBoxBooleanType;
    public  Type ceylonGetterType;
    public  Type ceylonGetterLongType;
    public  Type ceylonGetterIntType;
    public  Type ceylonGetterDoubleType;
    public  Type ceylonGetterByteType;
    public  Type ceylonGetterBooleanType;
    
    public final Type ceylonAtCeylonType;
    public final Type ceylonAtDynamicType;
    public final Type ceylonAtModuleType;
    public final Type ceylonAtPackageType;
    public final Type ceylonAtImportType;
    public final Type ceylonAtNameType;
    public final Type ceylonAtEnumeratedType;
    public final Type ceylonAtSequencedType;
    public final Type ceylonAtDefaultedType;
    public final Type ceylonAtTypeInfoType;
    public final Type ceylonAtAttributeType;
    public final Type ceylonAtSetterType;
    public final Type ceylonAtMethodType;
    public final Type ceylonAtFunctionalParameterType;
    public final Type ceylonAtObjectType;
    public final Type ceylonAtClassType;
    public final Type ceylonAtSatisfiedTypes;
    public final Type ceylonAtCaseTypes;
    public final Type ceylonAtIgnore;
    public final Type ceylonAtConstructorName;
    public final Type ceylonAtJpa;
    public final Type ceylonVarianceType;
    public final Type ceylonAtTypeParameters;
    public final Type ceylonAtTypeParameter;
    public final Type ceylonAtAnnotationsType;
    public final Type ceylonAtAnnotationType;
    public final Type ceylonAtAnnotationInstantiationType;
    public final Type ceylonAtAnnotationInstantiationTreeType;
    public final Type ceylonAtDeclarationReferenceType;
    public final Type ceylonAtEnumerationReferenceType;
    public final Type ceylonAtContainerType;
    public final Type ceylonAtLocalContainerType;
    public final Type ceylonAtLocalDeclarationType;
    public final Type ceylonAtLocalDeclarationsType;
    public final Type ceylonAtMemberType;
    public final Type ceylonAtMembersType;
    public final Type ceylonAtNamedArgumentType;
    public final Type ceylonAtValueTypeType;
    public final Type ceylonAtAliasType;
    public final Type ceylonAtTypeAliasType;
    public final Type ceylonAtTransientType;
    public final Type ceylonAtCompileTimeErrorType;
    public final Type ceylonAtNoInitCheckType;
    
    public final Type ceylonAtBooleanValueType;
    public final Type ceylonAtBooleanExprsType;
    public final Type ceylonAtCharacterValueType;
    public final Type ceylonAtCharacterExprsType;
    public final Type ceylonAtDeclarationValueType;
    public final Type ceylonAtDeclarationExprsType;
    public final Type ceylonAtFloatValueType;
    public final Type ceylonAtFloatExprsType;
    public final Type ceylonAtIntegerValueType;
    public final Type ceylonAtIntegerExprsType;
    public final Type ceylonAtObjectValueType;
    public final Type ceylonAtObjectExprsType;
    public final Type ceylonAtStringValueType;
    public final Type ceylonAtStringExprsType;
    public final Type ceylonAtParameterValueType;
    
    public final Type ceylonUtilType;
    public final Type ceylonMetamodelType;
    public final Type ceylonTypeDescriptorType;
    public final Type ceylonReifiedTypeType;
    public final Type ceylonSerializationType;
    public final Type ceylonSerializableType;
    public final Type ceylonReachableReferenceType;
    public final Type ceylonMemberImplType;
    public final Type ceylonMemberType;
    public final Type ceylonOuterImplType;
    public final Type ceylonOuterType;
    public final Type ceylonElementImplType;
    public final Type ceylonElementType;
    public final Type ceylonUninitializedLateValueType;
    
    /** The symbol representing the length field of an array.
     */
    public final VarSymbol lengthVar;

    /** The null check operator. */
    public final OperatorSymbol nullcheck;

    /** The symbol representing the final finalize method on enums */
    public final MethodSymbol enumFinalFinalize;

    /** The symbol representing the close method on TWR AutoCloseable type */
    public final MethodSymbol autoCloseableClose;

    /** The predefined type that belongs to a tag.
     */
    public final Type[] typeOfTag = new Type[TypeTags.TypeTagCount];

    /** The name of the class that belongs to a basix type tag.
     */
    public final Name[] boxedName = new Name[TypeTags.TypeTagCount];

    /** A hashtable containing the encountered top-level and member classes,
     *  indexed by flat names. The table does not contain local classes.
     *  It should be updated from the outside to reflect classes defined
     *  by compiled source files.
     */
    public final Map<Name, ClassSymbol> classes = new HashMap<Name, ClassSymbol>();

    /** A hashtable containing the encountered packages.
     *  the table should be updated from outside to reflect packages defined
     *  by compiled source files.
     */
    public final Map<Name, PackageSymbol> packages = new HashMap<Name, PackageSymbol>();

    public void initType(Type type, ClassSymbol c) {
        type.tsym = c;
        typeOfTag[type.tag] = type;
    }

    public void initType(Type type, String name) {
        initType(
            type,
            new ClassSymbol(
                PUBLIC, names.fromString(name), type, rootPackage));
    }

    public void initType(Type type, String name, String bname) {
        initType(type, name);
            boxedName[type.tag] = names.fromString("java.lang." + bname);
    }

    /** The class symbol that owns all predefined symbols.
     */
    public final ClassSymbol predefClass;

    /** Enter a constant into symbol table.
     *  @param name   The constant's name.
     *  @param type   The constant's type.
     */
    private VarSymbol enterConstant(String name, Type type) {
        VarSymbol c = new VarSymbol(
            PUBLIC | STATIC | FINAL,
            names.fromString(name),
            type,
            predefClass);
        c.setData(type.constValue());
        predefClass.members().enter(c);
        return c;
    }

    /** Enter a binary operation into symbol table.
     *  @param name     The name of the operator.
     *  @param left     The type of the left operand.
     *  @param right    The type of the left operand.
     *  @param res      The operation's result type.
     *  @param opcode   The operation's bytecode instruction.
     */
    private void enterBinop(String name,
                            Type left, Type right, Type res,
                            int opcode) {
        predefClass.members().enter(
            new OperatorSymbol(
                names.fromString(name),
                new MethodType(List.of(left, right), res,
                               List.<Type>nil(), methodClass),
                opcode,
                predefClass));
    }

    /** Enter a binary operation, as above but with two opcodes,
     *  which get encoded as (opcode1 << ByteCodeTags.preShift) + opcode2.
     *  @param opcode1     First opcode.
     *  @param opcode2     Second opcode.
     */
    private void enterBinop(String name,
                            Type left, Type right, Type res,
                            int opcode1, int opcode2) {
        enterBinop(
            name, left, right, res, (opcode1 << ByteCodes.preShift) | opcode2);
    }

    /** Enter a unary operation into symbol table.
     *  @param name     The name of the operator.
     *  @param arg      The type of the operand.
     *  @param res      The operation's result type.
     *  @param opcode   The operation's bytecode instruction.
     */
    private OperatorSymbol enterUnop(String name,
                                     Type arg,
                                     Type res,
                                     int opcode) {
        OperatorSymbol sym =
            new OperatorSymbol(names.fromString(name),
                               new MethodType(List.of(arg),
                                              res,
                                              List.<Type>nil(),
                                              methodClass),
                               opcode,
                               predefClass);
        predefClass.members().enter(sym);
        return sym;
    }

    /** Enter a class into symbol table.
     *  @param    The name of the class.
     */
    private Type enterClass(String s) {
        return reader.enterClass(names.fromString(s)).type;
    }

    public void synthesizeEmptyInterfaceIfMissing(final Type type) {
        final Completer completer = type.tsym.completer;
        if (completer != null) {
            type.tsym.completer = new Completer() {
                public void complete(Symbol sym) throws CompletionFailure {
                    try {
                        completer.complete(sym);
                    } catch (CompletionFailure e) {
                        sym.flags_field |= (PUBLIC | INTERFACE);
                        ((ClassType) sym.type).supertype_field = objectType;
                    }
                }
            };
        }
    }

    public void synthesizeBoxTypeIfMissing(final Type type) {
        ClassSymbol sym = reader.enterClass(boxedName[type.tag]);
        final Completer completer = sym.completer;
        if (completer != null) {
            sym.completer = new Completer() {
                public void complete(Symbol sym) throws CompletionFailure {
                    try {
                        completer.complete(sym);
                    } catch (CompletionFailure e) {
                        sym.flags_field |= PUBLIC;
                        ((ClassType) sym.type).supertype_field = objectType;
                        Name n = target.boxWithConstructors() ? names.init : names.valueOf;
                        MethodSymbol boxMethod =
                            new MethodSymbol(PUBLIC | STATIC,
                                n,
                                new MethodType(List.of(type), sym.type,
                                    List.<Type>nil(), methodClass),
                                sym);
                        sym.members().enter(boxMethod);
                        MethodSymbol unboxMethod =
                            new MethodSymbol(PUBLIC,
                                type.tsym.name.append(names.Value), // x.intValue()
                                new MethodType(List.<Type>nil(), type,
                                    List.<Type>nil(), methodClass),
                                sym);
                        sym.members().enter(unboxMethod);
                    }
                }
            };
        }

    }

    /** Constructor; enters all predefined identifiers and operators
     *  into symbol table.
     */
    protected Symtab(Context context) throws CompletionFailure {
        context.put(symtabKey, this);

        names = Names.instance(context);
        target = Target.instance(context);

        // Create the unknown type
        unknownType = new Type(TypeTags.UNKNOWN, null) {
            @Override
            public <R, P> R accept(TypeVisitor<R, P> v, P p) {
                return v.visitUnknown(this, p);
            }
        };

        // create the basic builtin symbols
        rootPackage = new PackageSymbol(names.empty, null);
        final JavacMessages messages = JavacMessages.instance(context);
        unnamedPackage = new PackageSymbol(names.empty, rootPackage) {
                public String toString() {
                    return messages.getLocalizedString("compiler.misc.unnamed.package");
                }
            };
        noSymbol = new TypeSymbol(0, names.empty, Type.noType, rootPackage) {
            public <R, P> R accept(ElementVisitor<R, P> v, P p) {
                return v.visitUnknown(this, p);
            }
        };
        noSymbol.kind = Kinds.NIL;

        // create the error symbols
        errSymbol = new ClassSymbol(PUBLIC|STATIC|ACYCLIC, names.any, null, rootPackage);
        errType = new ErrorType(errSymbol, Type.noType);

        unknownSymbol = new ClassSymbol(PUBLIC|STATIC|ACYCLIC, names.fromString("<any?>"), null, rootPackage);
        unknownSymbol.members_field = new Scope.ErrorScope(unknownSymbol);
        unknownSymbol.type = unknownType;

        // initialize builtin types
        initType(byteType, "byte", "Byte");
        initType(shortType, "short", "Short");
        initType(charType, "char", "Character");
        initType(intType, "int", "Integer");
        initType(longType, "long", "Long");
        initType(floatType, "float", "Float");
        initType(doubleType, "double", "Double");
        initType(booleanType, "boolean", "Boolean");
        initType(voidType, "void", "Void");
        initType(botType, "<nulltype>");
        initType(errType, errSymbol);
        initType(unknownType, unknownSymbol);

        // the builtin class of all arrays
        arrayClass = new ClassSymbol(PUBLIC|ACYCLIC, names.Array, noSymbol);

        // VGJ
        boundClass = new ClassSymbol(PUBLIC|ACYCLIC, names.Bound, noSymbol);
        boundClass.members_field = new Scope.ErrorScope(boundClass);

        // the builtin class of all methods
        methodClass = new ClassSymbol(PUBLIC|ACYCLIC, names.Method, noSymbol);
        methodClass.members_field = new Scope.ErrorScope(boundClass);

        // Create class to hold all predefined constants and operations.
        predefClass = new ClassSymbol(PUBLIC|ACYCLIC, names.empty, rootPackage);
        Scope scope = new Scope(predefClass);
        predefClass.members_field = scope;

        // Enter symbols for basic types.
        scope.enter(byteType.tsym);
        scope.enter(shortType.tsym);
        scope.enter(charType.tsym);
        scope.enter(intType.tsym);
        scope.enter(longType.tsym);
        scope.enter(floatType.tsym);
        scope.enter(doubleType.tsym);
        scope.enter(booleanType.tsym);
        scope.enter(errType.tsym);

        // Enter symbol for the errSymbol
        scope.enter(errSymbol);

        classes.put(predefClass.fullname, predefClass);

        reader = ClassReader.instance(context);
        reader.init(this);

        // Enter predefined classes.
        objectType = enterClass("java.lang.Object");
        classType = enterClass("java.lang.Class");
        stringType = enterClass("java.lang.String");
        stringBufferType = enterClass("java.lang.StringBuffer");
        stringBuilderType = enterClass("java.lang.StringBuilder");
        booleanObjectType = enterClass("java.lang.Boolean");
        integerObjectType = enterClass("java.lang.Integer");
        longObjectType = enterClass("java.lang.Long");
        floatObjectType = enterClass("java.lang.Float");
        doubleObjectType = enterClass("java.lang.Double");
        characterObjectType = enterClass("java.lang.Character");
        cloneableType = enterClass("java.lang.Cloneable");
        throwableType = enterClass("java.lang.Throwable");
        serializableType = enterClass("java.io.Serializable");
        methodHandleType = enterClass("java.lang.invoke.MethodHandle");
        // Backported by Ceylon from JDK8
        methodHandleLookupType = enterClass("java.lang.invoke.MethodHandles$Lookup");
        methodHandlesType = enterClass("java.lang.invoke.MethodHandles");
        // Backported by Ceylon from JDK8
        methodTypeType = enterClass("java.lang.invoke.MethodType");
        polymorphicSignatureType = enterClass("java.lang.invoke.MethodHandle$PolymorphicSignature");
        errorType = enterClass("java.lang.Error");
        illegalArgumentExceptionType = enterClass("java.lang.IllegalArgumentException");
        interruptedExceptionType = enterClass("java.lang.InterruptedException");
        exceptionType = enterClass("java.lang.Exception");
        runtimeExceptionType = enterClass("java.lang.RuntimeException");
        classNotFoundExceptionType = enterClass("java.lang.ClassNotFoundException");
        noClassDefFoundErrorType = enterClass("java.lang.NoClassDefFoundError");
        noSuchFieldErrorType = enterClass("java.lang.NoSuchFieldError");
        assertionErrorType = enterClass("java.lang.AssertionError");
        cloneNotSupportedExceptionType = enterClass("java.lang.CloneNotSupportedException");
        nullPointerExceptionType = enterClass("java.lang.NullPointerException");
        annotationType = enterClass("java.lang.annotation.Annotation");
        classLoaderType = enterClass("java.lang.ClassLoader");
        enumSym = reader.enterClass(names.java_lang_Enum);
        enumFinalFinalize =
            new MethodSymbol(PROTECTED|FINAL|HYPOTHETICAL,
                             names.finalize,
                             new MethodType(List.<Type>nil(), voidType,
                                            List.<Type>nil(), methodClass),
                             enumSym);
        listType = enterClass("java.util.List");
        collectionsType = enterClass("java.util.Collections");
        comparableType = enterClass("java.lang.Comparable");
        arraysType = enterClass("java.util.Arrays");
        iterableType = target.hasIterable()
            ? enterClass("java.lang.Iterable")
            : enterClass("java.util.Collection");
        iteratorType = enterClass("java.util.Iterator");
        annotationTargetType = enterClass("java.lang.annotation.Target");
        overrideType = enterClass("java.lang.Override");
        retentionType = enterClass("java.lang.annotation.Retention");
        retentionPolicyType = enterClass("java.lang.annotation.RetentionPolicy");
        targetType = enterClass("java.lang.annotation.Target");
        elementTypeType = enterClass("java.lang.annotation.ElementType");
        deprecatedType = enterClass("java.lang.Deprecated");
        suppressWarningsType = enterClass("java.lang.SuppressWarnings");
        inheritedType = enterClass("java.lang.annotation.Inherited");
        systemType = enterClass("java.lang.System");
        autoCloseableType = enterClass("java.lang.AutoCloseable");
        autoCloseableClose = new MethodSymbol(PUBLIC,
                             names.close,
                             new MethodType(List.<Type>nil(), voidType,
                                            List.of(exceptionType), methodClass),
                             autoCloseableType.tsym);
        trustMeType = enterClass("java.lang.SafeVarargs");
        
        // Only load the ceylon symbols from class files if we're not boostrapping it
        if(Options.instance(context).get(OptionName.BOOTSTRAPCEYLON) == null){
            loadCeylonSymbols();
        }
        
        ceylonAtCeylonType = enterClass("com.redhat.ceylon.compiler.java.metadata.Ceylon");
        ceylonAtDynamicType = enterClass("com.redhat.ceylon.compiler.java.metadata.Dynamic");
        ceylonAtImportType = enterClass("com.redhat.ceylon.compiler.java.metadata.Import");
        ceylonAtModuleType = enterClass("com.redhat.ceylon.compiler.java.metadata.Module");
        ceylonAtPackageType = enterClass("com.redhat.ceylon.compiler.java.metadata.Package");
        ceylonAtNameType = enterClass("com.redhat.ceylon.compiler.java.metadata.Name");
        ceylonAtEnumeratedType = enterClass("com.redhat.ceylon.compiler.java.metadata.Enumerated");
        ceylonAtSequencedType = enterClass("com.redhat.ceylon.compiler.java.metadata.Sequenced");
        ceylonAtDefaultedType = enterClass("com.redhat.ceylon.compiler.java.metadata.Defaulted");
        ceylonAtTypeInfoType = enterClass("com.redhat.ceylon.compiler.java.metadata.TypeInfo");
        ceylonAtAttributeType = enterClass("com.redhat.ceylon.compiler.java.metadata.Attribute");
        ceylonAtMethodType = enterClass("com.redhat.ceylon.compiler.java.metadata.Method");
        ceylonAtFunctionalParameterType = enterClass("com.redhat.ceylon.compiler.java.metadata.FunctionalParameter");
        ceylonAtObjectType = enterClass("com.redhat.ceylon.compiler.java.metadata.Object");
        ceylonAtClassType = enterClass("com.redhat.ceylon.compiler.java.metadata.Class");
        ceylonAtSatisfiedTypes = enterClass("com.redhat.ceylon.compiler.java.metadata.SatisfiedTypes");
        ceylonAtCaseTypes = enterClass("com.redhat.ceylon.compiler.java.metadata.CaseTypes");
        ceylonAtIgnore = enterClass("com.redhat.ceylon.compiler.java.metadata.Ignore");
        ceylonAtConstructorName = enterClass("com.redhat.ceylon.compiler.java.metadata.ConstructorName");
        ceylonAtJpa = enterClass("com.redhat.ceylon.compiler.java.metadata.Jpa");
        ceylonVarianceType = enterClass("com.redhat.ceylon.compiler.java.metadata.Variance");
        ceylonAtTypeParameter = enterClass("com.redhat.ceylon.compiler.java.metadata.TypeParameter");
        ceylonAtTypeParameters = enterClass("com.redhat.ceylon.compiler.java.metadata.TypeParameters");
        ceylonAtAnnotationsType = enterClass("com.redhat.ceylon.compiler.java.metadata.Annotations");
        ceylonAtAnnotationType = enterClass("com.redhat.ceylon.compiler.java.metadata.Annotation");
        ceylonAtAnnotationInstantiationType = enterClass("com.redhat.ceylon.compiler.java.metadata.AnnotationInstantiation");
        ceylonAtAnnotationInstantiationTreeType = enterClass("com.redhat.ceylon.compiler.java.metadata.AnnotationInstantiationTree");
        ceylonAtDeclarationReferenceType  = enterClass("com.redhat.ceylon.compiler.java.metadata.DeclarationReference");
        ceylonAtEnumerationReferenceType  = enterClass("com.redhat.ceylon.compiler.java.metadata.EnumerationReference");
        ceylonAtContainerType = enterClass("com.redhat.ceylon.compiler.java.metadata.Container");
        ceylonAtLocalContainerType = enterClass("com.redhat.ceylon.compiler.java.metadata.LocalContainer");
        ceylonAtLocalDeclarationType = enterClass("com.redhat.ceylon.compiler.java.metadata.LocalDeclaration");
        ceylonAtLocalDeclarationsType = enterClass("com.redhat.ceylon.compiler.java.metadata.LocalDeclarations");
        ceylonAtMemberType = enterClass("com.redhat.ceylon.compiler.java.metadata.Member");
        ceylonAtMembersType = enterClass("com.redhat.ceylon.compiler.java.metadata.Members");
        ceylonAtNamedArgumentType = enterClass("com.redhat.ceylon.compiler.java.metadata.NamedArgument");
        ceylonAtValueTypeType = enterClass("com.redhat.ceylon.compiler.java.metadata.ValueType");
        ceylonAtAliasType = enterClass("com.redhat.ceylon.compiler.java.metadata.Alias");
        ceylonAtTypeAliasType = enterClass("com.redhat.ceylon.compiler.java.metadata.TypeAlias");
        ceylonAtTransientType = enterClass("com.redhat.ceylon.compiler.java.metadata.Transient");
        ceylonAtCompileTimeErrorType = enterClass("com.redhat.ceylon.compiler.java.metadata.CompileTimeError");
        ceylonAtSetterType = enterClass("com.redhat.ceylon.compiler.java.metadata.Setter");
        
        ceylonAtBooleanValueType = enterClass("com.redhat.ceylon.compiler.java.metadata.BooleanValue");
        ceylonAtBooleanExprsType = enterClass("com.redhat.ceylon.compiler.java.metadata.BooleanExprs");
        ceylonAtCharacterValueType = enterClass("com.redhat.ceylon.compiler.java.metadata.CharacterValue");
        ceylonAtCharacterExprsType = enterClass("com.redhat.ceylon.compiler.java.metadata.CharacterExprs");
        ceylonAtDeclarationValueType = enterClass("com.redhat.ceylon.compiler.java.metadata.DeclarationValue");
        ceylonAtDeclarationExprsType = enterClass("com.redhat.ceylon.compiler.java.metadata.DeclarationExprs");
        ceylonAtFloatValueType = enterClass("com.redhat.ceylon.compiler.java.metadata.FloatValue");
        ceylonAtFloatExprsType = enterClass("com.redhat.ceylon.compiler.java.metadata.FloatExprs");
        ceylonAtIntegerValueType = enterClass("com.redhat.ceylon.compiler.java.metadata.IntegerValue");
        ceylonAtIntegerExprsType = enterClass("com.redhat.ceylon.compiler.java.metadata.IntegerExprs");
        ceylonAtObjectValueType = enterClass("com.redhat.ceylon.compiler.java.metadata.ObjectValue");
        ceylonAtObjectExprsType = enterClass("com.redhat.ceylon.compiler.java.metadata.ObjectExprs");
        ceylonAtStringValueType = enterClass("com.redhat.ceylon.compiler.java.metadata.StringValue");
        ceylonAtStringExprsType = enterClass("com.redhat.ceylon.compiler.java.metadata.StringExprs");
        ceylonAtParameterValueType = enterClass("com.redhat.ceylon.compiler.java.metadata.ParameterValue");
        
        ceylonUtilType = enterClass("com.redhat.ceylon.compiler.java.Util");
        ceylonMetamodelType = enterClass("com.redhat.ceylon.compiler.java.runtime.metamodel.Metamodel");
        ceylonTypeDescriptorType = enterClass("com.redhat.ceylon.compiler.java.runtime.model.TypeDescriptor");
        ceylonReifiedTypeType = enterClass("com.redhat.ceylon.compiler.java.runtime.model.ReifiedType");
        ceylonSerializationType = enterClass("com.redhat.ceylon.compiler.java.runtime.serialization.$Serialization$");
        ceylonSerializableType = enterClass("com.redhat.ceylon.compiler.java.runtime.serialization.Serializable");
        ceylonReachableReferenceType = enterClass("ceylon.language.serialization.ReachableReference");
        ceylonMemberType = enterClass("ceylon.language.serialization.Member");
        ceylonMemberImplType = enterClass("ceylon.language.impl.MemberImpl");
        ceylonOuterType = enterClass("ceylon.language.serialization.Outer");
        ceylonOuterImplType = enterClass("ceylon.language.impl.outerImpl_");
        ceylonElementType = enterClass("ceylon.language.serialization.Element");
        ceylonElementImplType = enterClass("ceylon.language.impl.ElementImpl");
        ceylonUninitializedLateValueType = enterClass("ceylon.language.serialization.UninitializedLateValue");
        
        
        ceylonAtNoInitCheckType = enterClass("com.redhat.ceylon.compiler.java.metadata.NoInitCheck");
        
        synthesizeEmptyInterfaceIfMissing(autoCloseableType);
        synthesizeEmptyInterfaceIfMissing(cloneableType);
        synthesizeEmptyInterfaceIfMissing(serializableType);
        synthesizeEmptyInterfaceIfMissing(polymorphicSignatureType);
        synthesizeBoxTypeIfMissing(doubleType);
        synthesizeBoxTypeIfMissing(floatType);
        synthesizeBoxTypeIfMissing(voidType);

        // Enter a synthetic class that is used to mark internal
        // proprietary classes in ct.sym.  This class does not have a
        // class file.
        ClassType proprietaryType = (ClassType)enterClass("sun.Proprietary+Annotation");
        this.proprietaryType = proprietaryType;
        ClassSymbol proprietarySymbol = (ClassSymbol)proprietaryType.tsym;
        proprietarySymbol.completer = null;
        proprietarySymbol.flags_field = PUBLIC|ACYCLIC|ANNOTATION|INTERFACE;
        proprietarySymbol.erasure_field = proprietaryType;
        proprietarySymbol.members_field = new Scope(proprietarySymbol);
        proprietaryType.typarams_field = List.nil();
        proprietaryType.allparams_field = List.nil();
        proprietaryType.supertype_field = annotationType;
        proprietaryType.interfaces_field = List.nil();

        // Ceylon: guess backport from Java 8
        // Enter a synthetic class that is used to mark JDK prfiles classes in ct.sym.  This class does not have a
        // class file.
        ClassType jdkProfileType = (ClassType)enterClass("jdk.Profile+Annotation");
        this.jdkProfileType = jdkProfileType;
        ClassSymbol jdkProfileSymbol = (ClassSymbol)jdkProfileType.tsym;
        jdkProfileSymbol.completer = null;
        jdkProfileSymbol.flags_field = PUBLIC|ACYCLIC|ANNOTATION|INTERFACE;
        jdkProfileSymbol.erasure_field = jdkProfileType;
        jdkProfileSymbol.members_field = new Scope(jdkProfileSymbol);
        jdkProfileType.typarams_field = List.nil();
        jdkProfileType.allparams_field = List.nil();
        jdkProfileType.supertype_field = annotationType;
        jdkProfileType.interfaces_field = List.nil();

        // Enter a class for arrays.
        // The class implements java.lang.Cloneable and java.io.Serializable.
        // It has a final length field and a clone method.
        ClassType arrayClassType = (ClassType)arrayClass.type;
        arrayClassType.supertype_field = objectType;
        arrayClassType.interfaces_field = List.of(cloneableType, serializableType);
        arrayClass.members_field = new Scope(arrayClass);
        lengthVar = new VarSymbol(
            PUBLIC | FINAL,
            names.length,
            intType,
            arrayClass);
        arrayClass.members().enter(lengthVar);
        arrayCloneMethod = new MethodSymbol(
            PUBLIC,
            names.clone,
            new MethodType(List.<Type>nil(), objectType,
                           List.<Type>nil(), methodClass),
            arrayClass);
        arrayClass.members().enter(arrayCloneMethod);

        // Enter operators.
        enterUnop("+", doubleType, doubleType, nop);
        enterUnop("+", floatType, floatType, nop);
        enterUnop("+", longType, longType, nop);
        enterUnop("+", intType, intType, nop);

        enterUnop("-", doubleType, doubleType, dneg);
        enterUnop("-", floatType, floatType, fneg);
        enterUnop("-", longType, longType, lneg);
        enterUnop("-", intType, intType, ineg);

        enterUnop("~", longType, longType, lxor);
        enterUnop("~", intType, intType, ixor);

        enterUnop("++", doubleType, doubleType, dadd);
        enterUnop("++", floatType, floatType, fadd);
        enterUnop("++", longType, longType, ladd);
        enterUnop("++", intType, intType, iadd);
        enterUnop("++", charType, charType, iadd);
        enterUnop("++", shortType, shortType, iadd);
        enterUnop("++", byteType, byteType, iadd);

        enterUnop("--", doubleType, doubleType, dsub);
        enterUnop("--", floatType, floatType, fsub);
        enterUnop("--", longType, longType, lsub);
        enterUnop("--", intType, intType, isub);
        enterUnop("--", charType, charType, isub);
        enterUnop("--", shortType, shortType, isub);
        enterUnop("--", byteType, byteType, isub);

        enterUnop("!", booleanType, booleanType, bool_not);
        nullcheck = enterUnop("<*nullchk*>", objectType, objectType, nullchk);

        // string concatenation
        enterBinop("+", stringType, objectType, stringType, string_add);
        enterBinop("+", objectType, stringType, stringType, string_add);
        enterBinop("+", stringType, stringType, stringType, string_add);
        enterBinop("+", stringType, intType, stringType, string_add);
        enterBinop("+", stringType, longType, stringType, string_add);
        enterBinop("+", stringType, floatType, stringType, string_add);
        enterBinop("+", stringType, doubleType, stringType, string_add);
        enterBinop("+", stringType, booleanType, stringType, string_add);
        enterBinop("+", stringType, botType, stringType, string_add);
        enterBinop("+", intType, stringType, stringType, string_add);
        enterBinop("+", longType, stringType, stringType, string_add);
        enterBinop("+", floatType, stringType, stringType, string_add);
        enterBinop("+", doubleType, stringType, stringType, string_add);
        enterBinop("+", booleanType, stringType, stringType, string_add);
        enterBinop("+", botType, stringType, stringType, string_add);

        // these errors would otherwise be matched as string concatenation
        enterBinop("+", botType, botType, botType, error);
        enterBinop("+", botType, intType, botType, error);
        enterBinop("+", botType, longType, botType, error);
        enterBinop("+", botType, floatType, botType, error);
        enterBinop("+", botType, doubleType, botType, error);
        enterBinop("+", botType, booleanType, botType, error);
        enterBinop("+", botType, objectType, botType, error);
        enterBinop("+", intType, botType, botType, error);
        enterBinop("+", longType, botType, botType, error);
        enterBinop("+", floatType, botType, botType, error);
        enterBinop("+", doubleType, botType, botType, error);
        enterBinop("+", booleanType, botType, botType, error);
        enterBinop("+", objectType, botType, botType, error);

        enterBinop("+", doubleType, doubleType, doubleType, dadd);
        enterBinop("+", floatType, floatType, floatType, fadd);
        enterBinop("+", longType, longType, longType, ladd);
        enterBinop("+", intType, intType, intType, iadd);

        enterBinop("-", doubleType, doubleType, doubleType, dsub);
        enterBinop("-", floatType, floatType, floatType, fsub);
        enterBinop("-", longType, longType, longType, lsub);
        enterBinop("-", intType, intType, intType, isub);

        enterBinop("*", doubleType, doubleType, doubleType, dmul);
        enterBinop("*", floatType, floatType, floatType, fmul);
        enterBinop("*", longType, longType, longType, lmul);
        enterBinop("*", intType, intType, intType, imul);

        enterBinop("/", doubleType, doubleType, doubleType, ddiv);
        enterBinop("/", floatType, floatType, floatType, fdiv);
        enterBinop("/", longType, longType, longType, ldiv);
        enterBinop("/", intType, intType, intType, idiv);

        enterBinop("%", doubleType, doubleType, doubleType, dmod);
        enterBinop("%", floatType, floatType, floatType, fmod);
        enterBinop("%", longType, longType, longType, lmod);
        enterBinop("%", intType, intType, intType, imod);

        enterBinop("&", booleanType, booleanType, booleanType, iand);
        enterBinop("&", longType, longType, longType, land);
        enterBinop("&", intType, intType, intType, iand);

        enterBinop("|", booleanType, booleanType, booleanType, ior);
        enterBinop("|", longType, longType, longType, lor);
        enterBinop("|", intType, intType, intType, ior);

        enterBinop("^", booleanType, booleanType, booleanType, ixor);
        enterBinop("^", longType, longType, longType, lxor);
        enterBinop("^", intType, intType, intType, ixor);

        enterBinop("<<", longType, longType, longType, lshll);
        enterBinop("<<", intType, longType, intType, ishll);
        enterBinop("<<", longType, intType, longType, lshl);
        enterBinop("<<", intType, intType, intType, ishl);

        enterBinop(">>", longType, longType, longType, lshrl);
        enterBinop(">>", intType, longType, intType, ishrl);
        enterBinop(">>", longType, intType, longType, lshr);
        enterBinop(">>", intType, intType, intType, ishr);

        enterBinop(">>>", longType, longType, longType, lushrl);
        enterBinop(">>>", intType, longType, intType, iushrl);
        enterBinop(">>>", longType, intType, longType, lushr);
        enterBinop(">>>", intType, intType, intType, iushr);

        enterBinop("<", doubleType, doubleType, booleanType, dcmpg, iflt);
        enterBinop("<", floatType, floatType, booleanType, fcmpg, iflt);
        enterBinop("<", longType, longType, booleanType, lcmp, iflt);
        enterBinop("<", intType, intType, booleanType, if_icmplt);

        enterBinop(">", doubleType, doubleType, booleanType, dcmpl, ifgt);
        enterBinop(">", floatType, floatType, booleanType, fcmpl, ifgt);
        enterBinop(">", longType, longType, booleanType, lcmp, ifgt);
        enterBinop(">", intType, intType, booleanType, if_icmpgt);

        enterBinop("<=", doubleType, doubleType, booleanType, dcmpg, ifle);
        enterBinop("<=", floatType, floatType, booleanType, fcmpg, ifle);
        enterBinop("<=", longType, longType, booleanType, lcmp, ifle);
        enterBinop("<=", intType, intType, booleanType, if_icmple);

        enterBinop(">=", doubleType, doubleType, booleanType, dcmpl, ifge);
        enterBinop(">=", floatType, floatType, booleanType, fcmpl, ifge);
        enterBinop(">=", longType, longType, booleanType, lcmp, ifge);
        enterBinop(">=", intType, intType, booleanType, if_icmpge);

        enterBinop("==", objectType, objectType, booleanType, if_acmpeq);
        enterBinop("==", booleanType, booleanType, booleanType, if_icmpeq);
        enterBinop("==", doubleType, doubleType, booleanType, dcmpl, ifeq);
        enterBinop("==", floatType, floatType, booleanType, fcmpl, ifeq);
        enterBinop("==", longType, longType, booleanType, lcmp, ifeq);
        enterBinop("==", intType, intType, booleanType, if_icmpeq);

        enterBinop("!=", objectType, objectType, booleanType, if_acmpne);
        enterBinop("!=", booleanType, booleanType, booleanType, if_icmpne);
        enterBinop("!=", doubleType, doubleType, booleanType, dcmpl, ifne);
        enterBinop("!=", floatType, floatType, booleanType, fcmpl, ifne);
        enterBinop("!=", longType, longType, booleanType, lcmp, ifne);
        enterBinop("!=", intType, intType, booleanType, if_icmpne);

        enterBinop("&&", booleanType, booleanType, booleanType, bool_and);
        enterBinop("||", booleanType, booleanType, booleanType, bool_or);
    }

    public void loadCeylonSymbols() {
        ceylonAnythingType = enterClass("ceylon.language.Anything");
        ceylonObjectType = enterClass("ceylon.language.Object");
        ceylonIdentifiableType = enterClass("ceylon.language.Identifiable");
        ceylonFloatType = enterClass("ceylon.language.Float");
        ceylonIntegerType = enterClass("ceylon.language.Integer");
        ceylonStringType = enterClass("ceylon.language.String");
        ceylonTupleType = enterClass("ceylon.language.Tuple");
        ceylonArrayType = enterClass("ceylon.language.Array");
        ceylonArrayIterableType = enterClass("com.redhat.ceylon.compiler.java.language.ArrayIterable");
        ceylonAbstractIterableType = enterClass("com.redhat.ceylon.compiler.java.language.AbstractIterable");
        ceylonAbstractIteratorType = enterClass("com.redhat.ceylon.compiler.java.language.AbstractIterator");
        ceylonLazyIterableType = enterClass("com.redhat.ceylon.compiler.java.language.LazyIterable");
        ceylonLazyInvokingIterableType = enterClass("com.redhat.ceylon.compiler.java.language.LazyInvokingIterable");
        ceylonCharacterType = enterClass("ceylon.language.Character");
        ceylonByteType = enterClass("ceylon.language.Byte");
        ceylonBooleanType = enterClass("ceylon.language.Boolean");
        ceylonFinishedType = enterClass("ceylon.language.Finished");
        ceylonExceptionType = enterClass("ceylon.language.Exception");
        ceylonThrowableType = enterClass("ceylon.language.Throwable");
        ceylonAssertionErrorType = enterClass("ceylon.language.AssertionError");
        ceylonInitializationErrorType = enterClass("ceylon.language.InitializationError");
        ceylonEnumeratedTypeErrorType = enterClass("com.redhat.ceylon.compiler.java.language.EnumeratedTypeError");
        ceylonUninvokableErrorType = enterClass("com.redhat.ceylon.compiler.java.language.UninvokableError");
        ceylonUninitializedMethodErrorType = enterClass("com.redhat.ceylon.compiler.java.language.UninitializedMethodError");
        ceylonUnresolvedCompilationErrorType = enterClass("com.redhat.ceylon.compiler.java.language.UnresolvedCompilationError");
        ceylonAbstractCallableType = enterClass("com.redhat.ceylon.compiler.java.language.AbstractCallable");
        ceylonAbstractTypeConstructorType = enterClass("com.redhat.ceylon.compiler.java.language.AbstractTypeConstructor");
        ceylonSerializationProxyType = enterClass("com.redhat.ceylon.compiler.java.language.SerializationProxy");
        ceylonVariableBoxType = enterClass("com.redhat.ceylon.compiler.java.language.VariableBox");
        ceylonVariableBoxLongType = enterClass("com.redhat.ceylon.compiler.java.language.VariableBoxLong");
        ceylonVariableBoxIntType = enterClass("com.redhat.ceylon.compiler.java.language.VariableBoxInt");
        ceylonVariableBoxDoubleType = enterClass("com.redhat.ceylon.compiler.java.language.VariableBoxDouble");
        ceylonVariableBoxByteType = enterClass("com.redhat.ceylon.compiler.java.language.VariableBoxByte");
        ceylonVariableBoxBooleanType = enterClass("com.redhat.ceylon.compiler.java.language.VariableBoxBoolean");
        ceylonGetterType = enterClass("com.redhat.ceylon.compiler.java.language.Getter");
        ceylonGetterLongType = enterClass("com.redhat.ceylon.compiler.java.language.GetterLong");
        ceylonGetterIntType = enterClass("com.redhat.ceylon.compiler.java.language.GetterInt");
        ceylonGetterDoubleType = enterClass("com.redhat.ceylon.compiler.java.language.GetterDouble");
        ceylonGetterByteType = enterClass("com.redhat.ceylon.compiler.java.language.GetterByte");
        ceylonGetterBooleanType = enterClass("com.redhat.ceylon.compiler.java.language.GetterBoolean");
        
    }
}
