package com.redhat.ceylon.ceylondoc;

import static com.redhat.ceylon.ceylondoc.Util.getAncestors;
import static com.redhat.ceylon.ceylondoc.Util.getDoc;
import static com.redhat.ceylon.ceylondoc.Util.getModifiers;
import static com.redhat.ceylon.ceylondoc.Util.getSuperInterfaces;
import static com.redhat.ceylon.ceylondoc.Util.isNullOrEmpty;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

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

	private ClassOrInterface klass;
    private List<Method> methods;
    private List<MethodOrValue> attributes;
    private List<ClassOrInterface> subclasses;
    private List<ClassOrInterface> satisfyingClassesOrInterfaces;
    private List<Class> satisfyingClasses;
    private List<Class> innerClasses;
    private List<Interface> satisfyingInterfaces;
    private List<TypeDeclaration> superInterfaces;
    private List<TypeDeclaration> superClasses;
    
    private Comparator<Declaration> comparator = new Comparator<Declaration>() {
        @Override
        public int compare(Declaration a, Declaration b) {
            return a.getName().compareTo(b.getName());
        }
    };
    
	interface MemberSpecification {
		boolean isSatisfiedBy(Declaration decl);
	}
	
	MemberSpecification atributeSpecification = new MemberSpecification() {		
		@Override
		public boolean isSatisfiedBy(Declaration decl) {			
			return decl instanceof Value || decl  instanceof Getter;
		}
	};
	
	MemberSpecification methodSpecification = new MemberSpecification() {		
		@Override
		public boolean isSatisfiedBy(Declaration decl) {			
			return decl instanceof Method;
		}
	};
    
    
	public ClassDoc(String destDir, boolean showPrivate, ClassOrInterface klass, List<ClassOrInterface> subclasses, List<ClassOrInterface> satisfyingClassesOrInterfaces) throws IOException {
		super(destDir, showPrivate);
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
	    satisfyingClasses = new ArrayList<Class>();
	    satisfyingInterfaces = new ArrayList<Interface>();	        
	    attributes = new ArrayList<MethodOrValue>();
	    innerClasses = new ArrayList<Class>();
	    superClasses = getAncestors(klass);
	    superInterfaces = getSuperInterfaces(klass);
	    for(Declaration m : klass.getMembers()){	        	
	    	if (showPrivate || m.isShared()) {
	            if(m instanceof Value)	            	
	                attributes.add((Value) m);
	            else if(m instanceof Getter)
	                attributes.add((Getter) m);
	            else if(m instanceof Method)
	                methods.add((Method) m);
	            else if(m instanceof Class)
	                innerClasses.add((Class) m);
	    	}
	    }
	
	    for (ClassOrInterface classOrInterface : satisfyingClassesOrInterfaces) {
	    	if (classOrInterface instanceof Class) {
	    		satisfyingClasses.add((Class) classOrInterface);
	    	} else if (classOrInterface instanceof Interface) {
	    		satisfyingInterfaces.add((Interface) classOrInterface);
	    	}
	    }	        
	    
	    Collections.sort(methods, comparator );
	    Collections.sort(attributes, comparator );
	    Collections.sort(subclasses, comparator);	        
	    Collections.sort(satisfyingClasses, comparator);
	    Collections.sort(satisfyingInterfaces, comparator);     
//	    Collections.sort(superInterfaces, comparator);
	    Collections.sort(innerClasses, comparator);
    }

    public void generate() throws IOException {
	    setupWriter();
		open("html");
		open("head");
		around("title", "Class for "+klass.getName());
		tag("link href='"+getPathToBase(klass)+"/style.css' rel='stylesheet' type='text/css'");
		close("head");
		open("body");
		summary();
		innerClasses();
		attributes();
		inheritedMembers(atributeSpecification,"Attributes inherited from class ");
		if(klass instanceof Class)
			constructor((Class)klass);		
		methods();
		inheritedMembers(methodSpecification, "Methods inherited from class ");
		inheritedMethodsFromInterfaces();
		close("body");
		close("html");
		writer.flush();
		writer.close();
	}
    
	public List<Declaration> getConcreteSharedMembers(TypeDeclaration decl, MemberSpecification specification ) {
		List<Declaration> members = new ArrayList<Declaration>();		
		for(Declaration m : decl.getMembers())		
			if ((m.isShared() && !m.isFormal()) && specification.isSatisfiedBy(m))
	                members.add((MethodOrValue) m);		
		return members;
	}	
	  
	private void inheritedMembers(MemberSpecification specification, String title) throws IOException {		
		if  (superClasses.isEmpty())
			return;
		TypeDeclaration subclass = klass;		
		for (TypeDeclaration superClass: superClasses) {
			List<Declaration> methods = getConcreteSharedMembers(superClass, specification);
			if (methods.isEmpty())
				continue;
			List<Declaration> notRefined = new ArrayList<Declaration>();
			// clean already listed methods (refined in subclasses)
			// done in 2 phases to avoid empty tables
 			for (Declaration method: methods) {
				if (subclass.getDirectMember(method.getName()) == null) { 
					notRefined.add(method);
				}
			}
			if (notRefined.isEmpty())
				continue;
			openTable( title + superClass.getQualifiedNameString());
			open("tr class='TableRowColor'");
			open("td");
			boolean first = true;
			for (Declaration method: notRefined) {
					if(!first){
						write(", ");
					}else{
						first = false;
					}
					// generate links to class#method instead of just print the name
					write(method.getName());
			}
			close("td");
			close("tr");
			subclass = superClass;		
		}
		close("table");	
	}
	
	private void inheritedMethodsFromInterfaces() throws IOException {
		Map<String, List<Declaration>> classMethods = new HashMap<String, List<Declaration>>();
		for (TypeDeclaration superInterface: superInterfaces) {
			List<Declaration> methods = getConcreteSharedMembers(superInterface, methodSpecification);
			classMethods.put(superInterface.getQualifiedNameString(), methods);
		}
		for (TypeDeclaration superInterface: superInterfaces) {
			List<Declaration> methods = getConcreteSharedMembers(superInterface, methodSpecification);
			for (Declaration method: methods) {
				Declaration refined = method.getRefinedDeclaration();
				if (refined != null && refined != method) {
					classMethods.get(refined.getContainer().getQualifiedNameString()).remove(refined);
				}
			}
		}
		for (String superIntefaceName: classMethods.keySet()) {
			List<Declaration> methods = classMethods.get(superIntefaceName);
			if (methods.isEmpty())
				continue;
			openTable("Methos inherited from interface: " + superIntefaceName);
			open("tr class='TableRowColor'");
			open("td");
			boolean first = true;
			for (Declaration method: methods) {
					if(!first){
						write(", ");
					}else{
						first = false;
					}
					// generate links to class#method instead of just print the name
					write(method.getName());
			}
			close("td");
			close("tr");
			close("table");	
		}
	
	
	}
	
	private void innerClasses() throws IOException {
		if (innerClasses.isEmpty())
			return;
		openTable("Nested Classes");
		for (Class m : innerClasses) {
			doc(m);
		}
		close("table");
	}
	
	private void doc(Class c) throws IOException {
		open("tr class='TableRowColor'");
		open("td");
		around("span class='modifiers'", getModifiers(c));
		close("td");
		open("td");
		link(c.getType());
		tag("br");
		around("span class='doc'", getDoc(c));
		close("td");
		close("tr");
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
		
		// hierarchy tree - only for classes
		if (klass instanceof Class) {			
			LinkedList<ClassOrInterface> superTypes = new LinkedList<ClassOrInterface>();
			superTypes.add(klass);
			ClassOrInterface type = klass.getExtendedTypeDeclaration(); 
			while(type != null){
				superTypes.add(0, type);
				type = type.getExtendedTypeDeclaration();
			}
			int i=0;
			for(ClassOrInterface superType : superTypes){
				open("ul class='inheritance'", "li");
				link(superType, true);
				i++;
			}
			while(i-- > 0){
				close("li", "ul");
			}
        }
        
		// type parameters
		if (isNullOrEmpty(klass.getTypeParameters()) == false ) {
			open("div class='type-parameters'");
			write("Type parameters:");
			open("ul");
			for(TypeParameter typeParam : klass.getTypeParameters()){
				around("li", typeParam.getName());
			}
			close("ul");
			close("div");
		}
		
		// interfaces
		writeListOnSummary("satisfied", "All Known Satisfied Interfaces: ",superInterfaces);

		// subclasses
		writeListOnSummary("subclasses", "Direct Known Subclasses: ", subclasses);

		// satisfying classes
		writeListOnSummary("satisfyingClasses", "All Known Satisfying Classes: ", satisfyingClasses);

		// satisfying interfaces
		writeListOnSummary("satisfyingClasses", "All Known Satisfying Interfaces: ", satisfyingInterfaces);

		// description
		around("div class='doc'", getDoc(klass));
		
		close("div");
	}

	private void constructor(Class klass) throws IOException {
		openTable("Constructor");
		open("tr", "td");
		write(klass.getName());
		writeParameterList(klass.getParameterLists());
		close("td", "tr", "table");
	}

	private void methods() throws IOException {
        if(methods.isEmpty())
            return;
        openTable("Methods", "Modifier and Type", "Method and Description");
		for(Method m : methods){
		    doc(m);
		}
		close("table");
	}

	private void attributes() throws IOException {
	    if(attributes.isEmpty())
	        return;
	    openTable("Attributes", "Modifier and Type", "Name and Description");
		for(MethodOrValue attribute : attributes){
		    doc(attribute);
		}
		close("table");
	}

	@Override
	protected String getPathToBase() {
		return getPathToBase(klass);
	}

    @Override
    protected File getOutputFile() {
        return new File(getFolder(klass), getFileName(klass));
    }
    
    private void writeListOnSummary(String divClass, String label, List<? extends TypeDeclaration> list) throws IOException {
		if (isNullOrEmpty(list) == false) {
			boolean first = true;
			open("div class='" + divClass + "'");
			write(label);
			for (TypeDeclaration typeDeclaration : list) {
				if (!first) {
					write(", ");
				} else {
					first = false;
				}
				link(typeDeclaration, true);
			}
			close("div");
		}
    }    
}
