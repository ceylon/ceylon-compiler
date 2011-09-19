package com.redhat.ceylon.ceylondoc;

import java.io.IOException;
import java.util.List;

import com.redhat.ceylon.compiler.typechecker.model.Annotation;
import com.redhat.ceylon.compiler.typechecker.model.Declaration;
import com.redhat.ceylon.compiler.typechecker.model.Method;
import com.redhat.ceylon.compiler.typechecker.model.MethodOrValue;
import com.redhat.ceylon.compiler.typechecker.model.Parameter;
import com.redhat.ceylon.compiler.typechecker.model.ParameterList;
import com.redhat.ceylon.compiler.typechecker.model.TypeParameter;
import com.redhat.ceylon.compiler.typechecker.model.Value;

public abstract class ClassOrPackageDoc extends CeylonDoc {

	public ClassOrPackageDoc(String destDir) {
		super(destDir);
	}

	protected String getModifiers(Declaration decl) {
		StringBuilder modifiers = new StringBuilder();
		if (decl.isShared()) {
			modifiers.append("shared ");
		}
		if (decl instanceof Value) {
			Value value = (Value) decl;
			if (value.isVariable()) {
				modifiers.append("variable");
			}
		} else if (decl instanceof com.redhat.ceylon.compiler.typechecker.model.Class) {
			com.redhat.ceylon.compiler.typechecker.model.Class klass  = (com.redhat.ceylon.compiler.typechecker.model.Class) decl;
			if (klass.isAbstract()) {
				modifiers.append("abstract");
			}
		}
		return modifiers.toString().trim();
	}

	protected void doc(Method method) throws IOException {
        open("tr class='TableRowColor'");
		open("td");
		around("span class='modifiers'", getModifiers(method));
		write(" ");
		link(method.getType());
		List<TypeParameter> typeParameters = method.getTypeParameters();
		if (!typeParameters.isEmpty()) {
		    write("&lt;");
		    boolean first = true;
		    for (TypeParameter type : typeParameters) {
		        if (first) {
		            first = false;
		        } else {
		            write(", ");
		        }
		        write(type.getName());
		    }
            write("&gt;");
		}
		close("td");
		open("td");
		write(method.getName());
		writeParameterList(method.getParameterLists());
		tag("br");
		around("span class='doc'", getDoc(method));
		close("td");
		close("tr");
	}

	protected void doc(MethodOrValue methodOrValue) throws IOException {
        open("tr class='TableRowColor'");
		open("td");
		around("span class='modifiers'", getModifiers(methodOrValue));
		write(" ");
		link(methodOrValue.getType());
		close("td");
        open("td");
		write(methodOrValue.getName());
        tag("br");
        around("span class='doc'", getDoc(methodOrValue));
        close("td");
		close("tr");
	}

    protected void writeParameterList(List<ParameterList> parameterLists) throws IOException {
		for (ParameterList lists : parameterLists) {
			write("(");
			boolean first = true;
			for (Parameter param : lists.getParameters()) {
				if (!first) {
					write(", ");
				} else {
					first = false;
				}
				link(param.getType());
				write(" ", param.getName());
			}
			write(")");
		}
	}

	protected String getDoc(Declaration decl) {
		String doc = "";
	    for (Annotation annotation : decl.getAnnotations()) {
	        if (annotation.getName().equals("doc")) {
	            doc = unquote(annotation.getPositionalArguments().get(0));
	        }
	    }
        return doc;
    }

}
