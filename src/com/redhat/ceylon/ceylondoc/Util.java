package com.redhat.ceylon.ceylondoc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.redhat.ceylon.compiler.typechecker.model.Annotation;
import com.redhat.ceylon.compiler.typechecker.model.Class;
import com.redhat.ceylon.compiler.typechecker.model.Declaration;
import com.redhat.ceylon.compiler.typechecker.model.Getter;
import com.redhat.ceylon.compiler.typechecker.model.Method;
import com.redhat.ceylon.compiler.typechecker.model.MethodOrValue;
import com.redhat.ceylon.compiler.typechecker.model.TypeDeclaration;
import com.redhat.ceylon.compiler.typechecker.model.Value;

public class Util {
	
	public static String getDoc(Declaration decl) {
		for (Annotation a : decl.getAnnotations()) {
			if (a.getName().equals("doc"))
				return unquote(a.getPositionalArguments().get(0));
		}
		return "";
	}

	private static String unquote(String string) {
		return string.substring(1, string.length() - 1);
	}
	
	public static String getModifiers(Declaration d) {
		StringBuilder modifiers = new StringBuilder();
		if (d.isShared()) {
			modifiers.append("shared ");
		}
		if (d.isFormal()) {
			modifiers.append("formal ");
		} else {
			if (d.isActual()) {
				modifiers.append("actual ");
			}
			if (d.isDefault()) {
				modifiers.append("default ");
			}
		}	
		if (d instanceof Value) {
			Value v = (Value) d;
			if (v.isVariable()) {
				modifiers.append("variable ");
			}
		} else if (d instanceof Class) {
			Class c = (Class) d;
			if (c.isAbstract()) {
				modifiers.append("abstract ");
			}
		}
		return modifiers.toString().trim();
	}
	
	public static List<MethodOrValue> getConcreteSharedAttributes(TypeDeclaration decl) {
		List<MethodOrValue> attributes = new ArrayList<MethodOrValue>();
		for(Declaration m : decl.getMembers())	 
			if ((m.isShared() && !m.isFormal()) && (m instanceof Value ||m  instanceof Getter))
	                attributes.add((MethodOrValue) m);
		return attributes;
	}
	
	public static List<MethodOrValue> getConcreteSharedMethods(TypeDeclaration decl) {
		List<MethodOrValue> methods = new ArrayList<MethodOrValue>();
		for(Declaration m : decl.getMembers())	 
			if ((m.isShared() && !m.isFormal()) && (m instanceof Method))
	                methods.add((MethodOrValue) m);
		return methods;
	}	
	
	public static List<TypeDeclaration> getAncestors(TypeDeclaration decl) {
		List<TypeDeclaration> ancestors =  new ArrayList<TypeDeclaration>();
		TypeDeclaration ancestor = decl.getExtendedTypeDeclaration();
		while (ancestor != null) {
			ancestors.add(ancestor);
			ancestor = ancestor.getExtendedTypeDeclaration();
		}
		return ancestors;
	}	
	
    public static boolean isNullOrEmpty(Collection<? extends Object> collection ) {
    	return collection == null || collection.isEmpty(); 
    }
    
}
