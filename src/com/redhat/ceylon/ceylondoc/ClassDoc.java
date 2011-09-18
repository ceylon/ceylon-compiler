package com.redhat.ceylon.ceylondoc;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

import com.redhat.ceylon.compiler.typechecker.model.Class;
import com.redhat.ceylon.compiler.typechecker.model.ClassOrInterface;
import com.redhat.ceylon.compiler.typechecker.model.Declaration;
import com.redhat.ceylon.compiler.typechecker.model.Getter;
import com.redhat.ceylon.compiler.typechecker.model.Interface;
import com.redhat.ceylon.compiler.typechecker.model.Method;
import com.redhat.ceylon.compiler.typechecker.model.MethodOrValue;
import com.redhat.ceylon.compiler.typechecker.model.TypeDeclaration;
import com.redhat.ceylon.compiler.typechecker.model.TypeParameter;
import com.redhat.ceylon.compiler.typechecker.model.Value;

public class ClassDoc extends ClassOrPackageDoc {

	private final ClassOrInterface klass;
    private List<Method> methods;
    private List<MethodOrValue> attributes;
    private List<ClassOrInterface> subclasses;
    private List<ClassOrInterface> satisfyingClassesOrInterfaces;
    private List<Class> satisfyingClasses;
    private List<Interface> satisfyingInterfaces;

    private final Comparator<Declaration> comparator = new Comparator<Declaration>() {
        @Override
        public int compare(final Declaration a, final Declaration b) {
            return a.getName().compareTo(b.getName());
        }
    };

	public ClassDoc(final String destDir, final ClassOrInterface klass, final List<ClassOrInterface> subclasses, final List<ClassOrInterface> satisfyingClassesOrInterfaces) throws IOException {
		super(destDir);
		if (subclasses != null) {
			this.subclasses = subclasses;
		} else {
			this.subclasses = new ArrayList<ClassOrInterface>();
		}
		if (satisfyingClassesOrInterfaces != null) {
			this.satisfyingClassesOrInterfaces = satisfyingClassesOrInterfaces;
		} else {
			this.satisfyingClassesOrInterfaces = new ArrayList<ClassOrInterface>();
		}
		this.klass = klass;
		loadMembers();
	}

	private void loadMembers() {
	        methods = new ArrayList<Method>();
	        attributes = new ArrayList<MethodOrValue>();
	        satisfyingClasses = new ArrayList<Class>();
	        satisfyingInterfaces = new ArrayList<Interface>();
	        for (Declaration m : klass.getMembers()) {
	            if (m instanceof Value) {
                    attributes.add((Value) m);
	            } else if (m instanceof Getter) {
	                attributes.add((Getter) m);
	            } else if (m instanceof Method) {
                    methods.add((Method) m);
	            }
	        }

	        for (ClassOrInterface classOrInterface : satisfyingClassesOrInterfaces) {
	        	if (classOrInterface instanceof Class) {
	        		satisfyingClasses.add((Class) classOrInterface);
	        	} else if (classOrInterface instanceof Interface) {
	        		satisfyingInterfaces.add((Interface) classOrInterface);
	        	}
	        }

	        Collections.sort(methods, comparator);
	        Collections.sort(attributes, comparator);
	        Collections.sort(subclasses, comparator);
	        Collections.sort(satisfyingClasses, comparator);
	        Collections.sort(satisfyingInterfaces, comparator);
    }

    public void generate() throws IOException {
	    setupWriter();
		open("html");
		open("head");
		around("title", "Class for " + klass.getName());
		tag("link href='" + getPathToBase(klass) + "/style.css' rel='stylesheet' type='text/css'");
		close("head");
		open("body");
		summary();
		if (klass instanceof Class) {
			constructor((Class) klass);
		}
		attributes();
		methods();
		close("body");
		close("html");
		writer.flush();
		writer.close();
	}

	private void summary() throws IOException {
        open("div class='nav'");
        open("div");
        around("a href='" + getPathToBase() + "/overview-summary.html'", "Overview");
        close("div");
        open("div");
        around("a href='index.html'", "Package");
        close("div");
        open("div class='selected'");
        write("Class");
        close("div");
        close("div");

        open("div class='head'");

        // name
		around("div class='package'", getPackage(klass).getNameAsString());
		around("div class='type'", klass instanceof Class ? "Class " : "Interface ", klass.getName());

		// hierarchy tree
		final LinkedList<ClassOrInterface> superTypes = new LinkedList<ClassOrInterface>();
		superTypes.add(klass);
		ClassOrInterface type = klass.getExtendedTypeDeclaration();
		while (type != null) {
			superTypes.add(0, type);
			type = type.getExtendedTypeDeclaration();
		}
		int i = 0;
		for (ClassOrInterface superType : superTypes) {
			open("ul class='inheritance'", "li");
			link(superType, true);
			i++;
		}
		while (i-- > 0) {
			close("li", "ul");
		}

		// type parameters
		if (isNullOrEmpty(klass.getTypeParameters()) == false) {
			open("div class='type-parameters'");
			write("Type parameters:");
			open("ul");
			for (TypeParameter typeParam : klass.getTypeParameters()) {
				around("li", typeParam.getName());
			}
			close("ul");
			close("div");
		}

		// interfaces
		if (isNullOrEmpty(klass.getSatisfiedTypeDeclarations()) ==  false) {
			open("div class='implements'");
			write("Satisfied interfaces: ");
			boolean first = true;
			for (TypeDeclaration satisfied : klass.getSatisfiedTypeDeclarations()) {
				if (!first) {
					write(", ");
				} else {
					first = false;
				}
				link(satisfied, true);
			}
			close("div");
		}

		// subclasses
		if (isNullOrEmpty(subclasses) == false) {
			boolean first = true;
			open("div class='sublclases'");
			write("Direct Known Subclasses: ");
			for (TypeDeclaration sublcass : subclasses) {
				if (!first) {
					write(", ");
				} else {
					first = false;
				}
				link(sublcass, true);
			}
			close("div");
		}

		// satisfying classes
		if (isNullOrEmpty(satisfyingClasses) == false) {
			boolean first = true;
			open("div class='satisfyingClasses'");
			write("All Known Satisfying Classes: ");
			for (TypeDeclaration subclass : satisfyingClasses) {
				if (!first) {
					write(", ");
				} else {
					first = false;
				}
				link(subclass, true);
			}
			close("div");
		}

		// satisfying interfaces
		if (isNullOrEmpty(satisfyingInterfaces) == false) {
			boolean first = true;
			open("div class='satisfyingClasses'");
			write("All Known Satisfying Interfaces: ");
			for (TypeDeclaration subclass : satisfyingInterfaces) {
				if (!first) {
					write(", ");
				} else {
					first = false;
				}
				link(subclass, true);
			}
			close("div");
		}

		// description
		around("div class='doc'", getDoc(klass));

		close("div");
	}

	private void constructor(final Class klass) throws IOException {
		openTable("Constructor");
		open("tr", "td");
		write(klass.getName());
		writeParameterList(klass.getParameterLists());
		close("td", "tr", "table");
	}

	private void methods() throws IOException {
        if (methods.isEmpty() == false) {
            openTable("Methods", "Modifier and Type", "Method and Description");
    		for (Method m : methods) {
    		    doc(m);
    		}
    		close("table");
        }
	}

	private void attributes() throws IOException {
	    if (attributes.isEmpty() == false) {
		    openTable("Attributes", "Modifier and Type", "Name and Description");
			for (MethodOrValue attribute : attributes) {
			    doc(attribute);
			}
			close("table");
	    }
	}

	@Override
	protected String getPathToBase() {
		return getPathToBase(klass);
	}

    @Override
    protected File getOutputFile() {
        return new File(getFolder(klass), getFileName(klass));
    }

    private boolean isNullOrEmpty(final Collection<? extends Object> collection) {
    	return collection == null || collection.isEmpty();
    }

}
