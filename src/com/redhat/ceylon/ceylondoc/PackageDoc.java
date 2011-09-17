package com.redhat.ceylon.ceylondoc;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.redhat.ceylon.compiler.typechecker.model.Class;
import com.redhat.ceylon.compiler.typechecker.model.ClassOrInterface;
import com.redhat.ceylon.compiler.typechecker.model.Declaration;
import com.redhat.ceylon.compiler.typechecker.model.Interface;
import com.redhat.ceylon.compiler.typechecker.model.Method;
import com.redhat.ceylon.compiler.typechecker.model.Package;
import com.redhat.ceylon.compiler.typechecker.model.Value;

public class PackageDoc extends ClassOrPackageDoc {

	final private Package pkg;
    private List<Class> classes;
    private List<Interface> interfaces;
    private List<Value> attributes;
    private List<Method> methods;

	public PackageDoc(final String destDir, final Package pkg) throws IOException {
		super(destDir);
		this.pkg = pkg;
		loadMembers();
	}

	private void loadMembers() {
	    classes = new ArrayList<Class>();
        interfaces = new ArrayList<Interface>();
        attributes = new ArrayList<Value>();
        methods = new ArrayList<Method>();
        for (Declaration members : pkg.getMembers()) {
            if (members instanceof Interface) {
                interfaces.add((Interface) members);
            } else if (members instanceof Class) {
                classes.add((Class) members);
            } else if (members instanceof Value) {
                attributes.add((Value) members);
            } else if (members instanceof Method) {
                methods.add((Method) members);
            }
        }
        Comparator<Declaration> comparator = new Comparator<Declaration>(){
            @Override
            public int compare(final Declaration a, final Declaration b) {
                return a.getName().compareTo(b.getName());
            }
        };
        Collections.sort(classes, comparator);
        Collections.sort(interfaces, comparator);
        Collections.sort(attributes, comparator);
        Collections.sort(methods, comparator);
    }

    public void generate() throws IOException {
	    setupWriter();
		open("html");
		open("head");
		around("title", "Package " + pkg.getName());
		if (pkg.getNameAsString().isEmpty()) {
		    tag("link href='style.css' rel='stylesheet' type='text/css'");
		} else {
		    tag("link href='" + getPathToBase(pkg) + "/style.css' rel='stylesheet' type='text/css'");
		}
		close("head");
		open("body");
		summary();
		attributes();
        methods();
        interfaces();
		classes();
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
        open("div class='selected'");
        write("Package");
        close("div");
        open("div");
        write("Class");
        close("div");
        close("div");

        open("div class='head'");
		around("h1", "Package ", pkg.getNameAsString());
		close("div");
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
	    openTable("Attributes", "Modifier and Type", "Name and Description");
	    for (Value v : attributes) {
	        doc(v);
	    }
	    close("table");
	}

	private void interfaces() throws IOException {
	    openTable("Interfaces", "Modifier and Type", "Description");
		for (Interface i : interfaces) {
		    doc(i);
		}
		close("table");
	}

	private void classes() throws IOException {
        openTable("Classes", "Modifier and Type", "Description");
		for (Class c : classes) {
		    doc(c);
		}
		close("table");
	}

	private void doc(final ClassOrInterface classOrInterface) throws IOException {
        open("tr class='TableRowColor'");
		open("td");
		around("span class='modifiers'", getModifiers(classOrInterface));
		write(" ");
		link(classOrInterface.getType());
		close("td");
		open("td");
		write(getDoc(classOrInterface));
		close("td");
		close("tr");
	}

	@Override
	protected String getPathToBase() {
		return getPathToBase(pkg);
	}

    @Override
    protected File getOutputFile() {
        return new File(getFolder(pkg), "index.html");
    }
}
