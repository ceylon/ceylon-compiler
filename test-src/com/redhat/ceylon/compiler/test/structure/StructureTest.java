package com.redhat.ceylon.compiler.test.structure;

import org.junit.Ignore;
import org.junit.Test;

import com.redhat.ceylon.compiler.test.CompilerTest;

public class StructureTest extends CompilerTest {
    
    //
    // Packages
    
    @Test
    public void testPkgPackage(){
        compareWithJavaSource("pkg/pkg");
    }
    
    //
    // Classes
    
    @Test
    public void testKlsClass(){
        compareWithJavaSource("klass/Klass");
    }
    @Test
    public void testKlsPublicClass(){
        compareWithJavaSource("klass/PublicKlass");
    }
    @Test
    public void testKlsInterface(){
        compareWithJavaSource("klass/Interface");
    }
    @Test
    public void testKlsInterfaceWithMembers(){
        compareWithJavaSource("klass/InterfaceWithMembers");
    }
    @Test
    public void testKlsInterfaceWithConcreteMembers(){
        compareWithJavaSource("klass/InterfaceWithConcreteMembers");
    }
    @Test
    public void testKlsInitializerParameter(){
        compareWithJavaSource("klass/InitializerParameter");
    }
    @Test
    public void testKlsExtends(){
        compareWithJavaSource("klass/Extends");
    }
    @Test
    public void testKlsSatisfiesErasure(){
        compareWithJavaSource("klass/SatisfiesErasure");
    }
    @Test
    public void testKlsSatisfies(){
        compareWithJavaSource("klass/Satisfies");
    }
    @Test
    public void testKlsSatisfiesWithMembers(){
        compareWithJavaSource("klass/SatisfiesWithMembers");
    }
    @Test
    public void testKlsAbstractFormal(){
        compareWithJavaSource("klass/AbstractFormal");
    }
    @Test
    public void testKlsKlassMethodTypeParams(){
        compareWithJavaSource("klass/KlassMethodTypeParams");
    }
    @Test
    public void testKlsKlassTypeParams(){
        compareWithJavaSource("klass/KlassTypeParams");
    }
    @Test
    public void testKlsKlassTypeParamsSatisfies(){
        compareWithJavaSource("klass/KlassTypeParamsSatisfies");
    }
    @Test
    public void testKlsInnerClass(){
        compareWithJavaSource("klass/InnerClass");
    }
    @Test
    public void testKlsLocalClass(){
        compareWithJavaSource("klass/LocalClass");
    }
    @Test
    public void testKlsInitializerVarargs(){
        compareWithJavaSource("klass/InitializerVarargs");
    }
    @Ignore("M2")
    @Test
    public void testKlsCaseTypes(){
        compareWithJavaSource("klass/CaseTypes");
    }
    
    //
    // Methods
    
    @Test
    public void testMthMethod(){
        compareWithJavaSource("method/Method");
    }
    @Test
    public void testMthMethodWithParam(){
        compareWithJavaSource("method/MethodWithParam");
    }
    @Test
    public void testMthPublicMethod(){
        compareWithJavaSource("method/PublicMethod");
    }
    @Test
    public void testMthLocalMethod(){
        compareWithJavaSource("method/LocalMethod");
    }
    @Test
    public void testMthMethodWithVarargs(){
        compareWithJavaSource("method/MethodWithVarargs");
    }
    @Test
    public void testMthMethodErasure(){
        compareWithJavaSource("method/MethodErasure");
    }
    @Test
    public void testMthMethodTypeParams(){
        compareWithJavaSource("method/MethodTypeParams");
    }

    //
    // Attributes
    
    @Test
    public void testAtrClassVariable(){
        compareWithJavaSource("attribute/ClassVariable");
    }
    @Test
    public void testAtrClassVariableWithInitializer(){
        compareWithJavaSource("attribute/ClassVariableWithInitializer");
    }
    @Test
    public void testAtrClassAttribute(){
        // FIXME: this one should fail and we should make sure it fails for the right reason
        compareWithJavaSource("attribute/ClassAttribute");
    }
    @Test
    public void testAtrClassAttributeWithInitializer(){
        compareWithJavaSource("attribute/ClassAttributeWithInitializer");
    }
    @Test
    public void testAtrClassAttributeGetter(){
        compareWithJavaSource("attribute/ClassAttributeGetter");
    }
    @Test
    public void testAtrClassAttributeGetterSetter(){
        compareWithJavaSource("attribute/ClassAttributeGetterSetter");
    }
    @Test
    public void testAtrInnerAttributeGetter(){
        compareWithJavaSource("attribute/InnerAttributeGetter");
    }
    @Test
    public void testAtrInnerAttributeGetterSetter(){
        compareWithJavaSource("attribute/InnerAttributeGetterSetter");
    }
    
    //
    // Toplevel
    
    @Test
    public void testTopToplevelAttribute(){
        compareWithJavaSource("toplevel/ToplevelAttribute");
    }
    @Test
    public void testTopToplevelAttributeShared(){
        compareWithJavaSource("toplevel/ToplevelAttributeShared");
    }
    @Test
    public void testTopToplevelVariable(){
        compareWithJavaSource("toplevel/ToplevelVariable");
    }
    @Test
    public void testTopToplevelVariableShared(){
        compareWithJavaSource("toplevel/ToplevelVariableShared");
    }
    @Test
    public void testTopToplevelObject(){
        compareWithJavaSource("toplevel/ToplevelObject");
    }
    @Test
    public void testTopToplevelObjectShared(){
        compareWithJavaSource("toplevel/ToplevelObjectShared");
    }
    @Test
    public void testTopToplevelObjectWithMembers(){
        compareWithJavaSource("toplevel/ToplevelObjectWithMembers");
    }
    @Test
    public void testTopToplevelObjectWithSupertypes(){
        compareWithJavaSource("toplevel/ToplevelObjectWithSupertypes");
    }
    @Test
    public void testTopToplevelMethods(){
        compareWithJavaSource("toplevel/ToplevelMethods");
    }
    
    //
    // Type
    
    @Test @Ignore
    public void testTypBasicTypes(){
        compareWithJavaSource("type/BasicTypes");
    }
    @Test
    public void testTypConversions(){
        compareWithJavaSource("type/Conversions");
    }
    @Test
    public void testTypOptionalType(){
        compareWithJavaSource("type/OptionalType");
    }
    @Test
    public void testTypSequenceType(){
        compareWithJavaSource("type/SequenceType");
    }
    
    //
    // import
    
    @Test
    public void testImpImportAttrSingle(){
        compareWithJavaSource("import_/ImportAttrSingle");
    }
    
    @Test
    public void testImpImportMethodSingle(){
        compareWithJavaSource("import_/ImportMethodSingle");
    }
    
    @Test
    public void testImpImportTypeSingle(){
        compareWithJavaSource("import_/ImportTypeSingle");
    }
    
    @Test
    public void testImpImportTypeMultiple(){
        compareWithJavaSource("import_/ImportTypeMultiple");
    }
    
    @Test
    public void testImpImportTypeAlias(){
        compareWithJavaSource("import_/ImportTypeAlias");
    }
    
    @Test
    public void testImpImportWildcard(){
        compareWithJavaSource("import_/ImportWildcard");
    }
    
    @Test
    public void testImpImportJavaRuntimeTypeSingle(){
        compareWithJavaSource("import_/ImportJavaRuntimeTypeSingle");
    }
}
