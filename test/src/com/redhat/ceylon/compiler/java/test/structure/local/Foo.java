package com.redhat.ceylon.compiler.java.test.structure.local;

interface ToplevelInterface<T> {
    
    public void m();
}
final class ToplevelInterface$impl {
    
    private ToplevelInterface$impl() {
    }
    
    public static <T>void m(final com.redhat.ceylon.compiler.java.test.structure.local.ToplevelInterface<T> $this, final com.redhat.ceylon.compiler.java.runtime.model.TypeDescriptor $reified$T) {
    }
}
class ToplevelClass<T> implements com.redhat.ceylon.compiler.java.runtime.model.ReifiedType {
    
    public ToplevelClass(final com.redhat.ceylon.compiler.java.runtime.model.TypeDescriptor $reified$T) {
        this.$reified$T = $reified$T;
    }
    private final com.redhat.ceylon.compiler.java.runtime.model.TypeDescriptor $reified$T;
    
    public final void m() {
    }
    
    public static void main(java.lang.String[] args) {
        ceylon.language.process_.get_().setupArguments(args);
        new com.redhat.ceylon.compiler.java.test.structure.local.ToplevelClass(com.redhat.ceylon.compiler.java.runtime.model.TypeDescriptor.NothingType);
    }
    
    @java.lang.Override
    public com.redhat.ceylon.compiler.java.runtime.model.TypeDescriptor $getType$() {
        return com.redhat.ceylon.compiler.java.runtime.model.TypeDescriptor.klass(com.redhat.ceylon.compiler.java.test.structure.local.ToplevelClass.class, $reified$T);
    }
}
class OuterInterface$impl$MemberClass<T, U> implements com.redhat.ceylon.compiler.java.runtime.model.ReifiedType {
    
    public OuterInterface$impl$MemberClass(final com.redhat.ceylon.compiler.java.test.structure.local.OuterInterface<T> $outer$OuterInterface, final com.redhat.ceylon.compiler.java.runtime.model.TypeDescriptor $reified$T, final com.redhat.ceylon.compiler.java.runtime.model.TypeDescriptor $reified$U) {
        this.$outer$OuterInterface = $outer$OuterInterface;
        this.$reified$T = $reified$T;
        this.$reified$U = $reified$U;
    }
    private final com.redhat.ceylon.compiler.java.test.structure.local.OuterInterface<T> $outer$OuterInterface;
    private final com.redhat.ceylon.compiler.java.runtime.model.TypeDescriptor $reified$T;
    private final com.redhat.ceylon.compiler.java.runtime.model.TypeDescriptor $reified$U;
    
    public final void m() {
    }
    
    @java.lang.Override
    public com.redhat.ceylon.compiler.java.runtime.model.TypeDescriptor $getType$() {
        return com.redhat.ceylon.compiler.java.runtime.model.TypeDescriptor.member(com.redhat.ceylon.compiler.java.runtime.model.TypeDescriptor.klass(com.redhat.ceylon.compiler.java.test.structure.local.OuterInterface.class, $reified$T), com.redhat.ceylon.compiler.java.runtime.model.TypeDescriptor.klass(com.redhat.ceylon.compiler.java.test.structure.local.OuterInterface$impl$MemberClass.class, $reified$U));
    }
}
interface OuterInterface$MemberInterface<T, U> {
    
    public void m();
}
final class OuterInterface$MemberInterface$impl {
    
    private OuterInterface$MemberInterface$impl() {
    }
    
    public static <T, U>void m(final com.redhat.ceylon.compiler.java.test.structure.local.OuterInterface$MemberInterface<T, U> $this, final com.redhat.ceylon.compiler.java.test.structure.local.OuterInterface<T> $outer$OuterInterface, final com.redhat.ceylon.compiler.java.runtime.model.TypeDescriptor $reified$T, final com.redhat.ceylon.compiler.java.runtime.model.TypeDescriptor $reified$U) {
    }
}
interface OuterInterface<T> {
    
    public void m();
}
final class OuterInterface$impl {
    
    private OuterInterface$impl() {
    }
    
    public static <T>void m(final com.redhat.ceylon.compiler.java.test.structure.local.OuterInterface<T> $this, final com.redhat.ceylon.compiler.java.runtime.model.TypeDescriptor $reified$T) {
    }
}
class OuterClass$MemberClass<T, U> implements com.redhat.ceylon.compiler.java.runtime.model.ReifiedType {
    
    public OuterClass$MemberClass(final com.redhat.ceylon.compiler.java.test.structure.local.OuterClass<T> $outer$OuterClass, final com.redhat.ceylon.compiler.java.runtime.model.TypeDescriptor $reified$T, final com.redhat.ceylon.compiler.java.runtime.model.TypeDescriptor $reified$U) {
        this.$outer$OuterClass = $outer$OuterClass;
        this.$reified$T = $reified$T;
        this.$reified$U = $reified$U;
    }
    private final com.redhat.ceylon.compiler.java.test.structure.local.OuterClass<T> $outer$OuterClass;
    private final com.redhat.ceylon.compiler.java.runtime.model.TypeDescriptor $reified$T;
    private final com.redhat.ceylon.compiler.java.runtime.model.TypeDescriptor $reified$U;
    
    public final void m() {
    }
    
    @java.lang.Override
    public com.redhat.ceylon.compiler.java.runtime.model.TypeDescriptor $getType$() {
        return com.redhat.ceylon.compiler.java.runtime.model.TypeDescriptor.member(com.redhat.ceylon.compiler.java.runtime.model.TypeDescriptor.klass(com.redhat.ceylon.compiler.java.test.structure.local.OuterClass.class, $reified$T), com.redhat.ceylon.compiler.java.runtime.model.TypeDescriptor.klass(com.redhat.ceylon.compiler.java.test.structure.local.OuterClass$MemberClass.class, $reified$U));
    }
}
interface OuterClass$MemberInterface<T, U> {
    
    public void m();
}
final class OuterClass$MemberInterface$impl {
    
    private OuterClass$MemberInterface$impl() {
    }
    
    public static <T, U>void m(final com.redhat.ceylon.compiler.java.test.structure.local.OuterClass$MemberInterface<T, U> $this, final com.redhat.ceylon.compiler.java.test.structure.local.OuterClass<T> $outer$OuterClass, final com.redhat.ceylon.compiler.java.runtime.model.TypeDescriptor $reified$T, final com.redhat.ceylon.compiler.java.runtime.model.TypeDescriptor $reified$U) {
    }
}
class OuterClass<T> implements com.redhat.ceylon.compiler.java.runtime.model.ReifiedType {
    
    public OuterClass(final com.redhat.ceylon.compiler.java.runtime.model.TypeDescriptor $reified$T) {
        this.$reified$T = $reified$T;
    }
    private final com.redhat.ceylon.compiler.java.runtime.model.TypeDescriptor $reified$T;
    
    public final void m() {
    }
    
    public static void main(java.lang.String[] args) {
        ceylon.language.process_.get_().setupArguments(args);
        new com.redhat.ceylon.compiler.java.test.structure.local.OuterClass(com.redhat.ceylon.compiler.java.runtime.model.TypeDescriptor.NothingType);
    }
    
    @java.lang.Override
    public com.redhat.ceylon.compiler.java.runtime.model.TypeDescriptor $getType$() {
        return com.redhat.ceylon.compiler.java.runtime.model.TypeDescriptor.klass(com.redhat.ceylon.compiler.java.test.structure.local.OuterClass.class, $reified$T);
    }
}