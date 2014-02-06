package com.redhat.ceylon.compiler.java.test.structure.concrete;

interface IntersectionSatisfier_X<T, N> {
    
    public abstract java.lang.Object getX();
}
final class IntersectionSatisfier_X$impl {
    
    private IntersectionSatisfier_X$impl() {
    }
}
interface IntersectionSatisfier_I1<U> extends com.redhat.ceylon.compiler.java.test.structure.concrete.IntersectionSatisfier_X<U, java.lang.Object> {
    
    @java.lang.Override
    public U getX();
}
final class IntersectionSatisfier_I1$impl {
    
    private IntersectionSatisfier_I1$impl() {
    }
    
    static final <U>U getX(com.redhat.ceylon.compiler.java.test.structure.concrete.IntersectionSatisfier_I1<? extends U> $this, final com.redhat.ceylon.compiler.java.runtime.model.TypeDescriptor $reified$U) {
        return null;
    }
}
interface IntersectionSatisfier_I2 extends com.redhat.ceylon.compiler.java.test.structure.concrete.IntersectionSatisfier_I1<java.lang.Object> {
    public static final com.redhat.ceylon.compiler.java.runtime.model.TypeDescriptor $TypeDescriptor$ = com.redhat.ceylon.compiler.java.runtime.model.TypeDescriptor.klass(com.redhat.ceylon.compiler.java.test.structure.concrete.IntersectionSatisfier_I2.class);
}
final class IntersectionSatisfier_I2$impl {
    
    private IntersectionSatisfier_I2$impl() {
    }
}
class IntersectionSatisfier_C<V> implements com.redhat.ceylon.compiler.java.runtime.model.ReifiedType, com.redhat.ceylon.compiler.java.test.structure.concrete.IntersectionSatisfier_I2, com.redhat.ceylon.compiler.java.test.structure.concrete.IntersectionSatisfier_I1<V> {
    
    IntersectionSatisfier_C(final com.redhat.ceylon.compiler.java.runtime.model.TypeDescriptor $reified$V) {
        this.$reified$V = $reified$V;
    }
    private final com.redhat.ceylon.compiler.java.runtime.model.TypeDescriptor $reified$V;
    
    
    public V getX() {
        return com.redhat.ceylon.compiler.java.test.structure.concrete.IntersectionSatisfier_I1$impl.getX(this, $reified$V);
    }
    
    public static void main(java.lang.String[] args) {
        ceylon.language.process_.get_().setupArguments(args);
        new com.redhat.ceylon.compiler.java.test.structure.concrete.IntersectionSatisfier_C(com.redhat.ceylon.compiler.java.runtime.model.TypeDescriptor.NothingType);
    }
    
    @java.lang.Override
    public com.redhat.ceylon.compiler.java.runtime.model.TypeDescriptor $getType$() {
        return com.redhat.ceylon.compiler.java.runtime.model.TypeDescriptor.klass(com.redhat.ceylon.compiler.java.test.structure.concrete.IntersectionSatisfier_C.class, $reified$V);
    }
}