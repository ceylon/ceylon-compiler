import ceylon.language.model{SequencedAnnotation, OptionalAnnotation}
import ceylon.language.model.declaration { ClassOrInterfaceDeclaration }

@nomodel
annotation class AnnotationClassMetamodelReference(Anything mmr) satisfies SequencedAnnotation<AnnotationClassMetamodelReference, ClassOrInterfaceDeclaration>{}
@nomodel
annotation class AnnotationClassMetamodelReferenceDefaulted(Anything mmr=AnnotationClassMetamodelReference) satisfies SequencedAnnotation<AnnotationClassMetamodelReferenceDefaulted, ClassOrInterfaceDeclaration>{}
@nomodel
annotation class AnnotationClassMetamodelReferenceVariadic(Anything* mmrs) satisfies SequencedAnnotation<AnnotationClassMetamodelReferenceVariadic, ClassOrInterfaceDeclaration>{}
@nomodel
annotation AnnotationClassMetamodelReference annotationClassMetamodelReference(Anything mmr) => AnnotationClassMetamodelReference(mmr);
@nomodel
annotationClassMetamodelReference(`Anything`)
annotationClassMetamodelReference(`process`)
class AnnotationClassMetamodelReference_callsite() {}