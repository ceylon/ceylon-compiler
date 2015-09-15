/*
 * Copyright Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the authors tag. All rights reserved.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU General Public License version 2.
 * 
 * This particular file is subject to the "Classpath" exception as provided in the 
 * LICENSE file that accompanied this code.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License,
 * along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */

package com.redhat.ceylon.ceylondoc;

import static com.redhat.ceylon.ceylondoc.Util.getAncestors;
import static com.redhat.ceylon.ceylondoc.Util.getDoc;
import static com.redhat.ceylon.ceylondoc.Util.getSuperInterfaces;
import static com.redhat.ceylon.ceylondoc.Util.isAbbreviatedType;
import static com.redhat.ceylon.ceylondoc.Util.isEmpty;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.redhat.ceylon.ceylondoc.Util.TypeComparatorByName;
import com.redhat.ceylon.ceylondoc.Util.ReferenceableComparatorByName;
import com.redhat.ceylon.model.typechecker.model.Class;
import com.redhat.ceylon.model.typechecker.model.ClassOrInterface;
import com.redhat.ceylon.model.typechecker.model.Constructor;
import com.redhat.ceylon.model.typechecker.model.Declaration;
import com.redhat.ceylon.model.typechecker.model.Interface;
import com.redhat.ceylon.model.typechecker.model.Function;
import com.redhat.ceylon.model.typechecker.model.FunctionOrValue;
import com.redhat.ceylon.model.typechecker.model.Package;
import com.redhat.ceylon.model.typechecker.model.Type;
import com.redhat.ceylon.model.typechecker.model.TypeAlias;
import com.redhat.ceylon.model.typechecker.model.TypeDeclaration;
import com.redhat.ceylon.model.typechecker.model.TypedDeclaration;
import com.redhat.ceylon.model.typechecker.model.Value;

public class ClassDoc extends ClassOrPackageDoc {

    private TypeDeclaration klass;
    private List<Constructor> constructors;
    private List<Function> methods;
    private List<TypedDeclaration> attributes;
    private List<Interface> innerInterfaces;
    private List<Class> innerClasses;
    private List<Class> innerExceptions;
    private List<TypeAlias> innerAliases;
    private List<Type> superInterfaces;
    private List<TypeDeclaration> superClasses;
    private Map<MemberSpecification, Map<TypeDeclaration, List<Declaration>>> superclassInheritedMembers = new HashMap<ClassDoc.MemberSpecification, Map<TypeDeclaration,List<Declaration>>>(2);
    private Map<MemberSpecification, Map<TypeDeclaration, List<Declaration>>> interfaceInheritedMembers = new HashMap<ClassDoc.MemberSpecification, Map<TypeDeclaration,List<Declaration>>>(2);

    private interface MemberSpecification {
        boolean isSatisfiedBy(Declaration decl);
    }

    private MemberSpecification attributeSpecification = new MemberSpecification() {
        @Override
        public boolean isSatisfiedBy(Declaration decl) {
            return decl instanceof Value;
        }
    };

    private MemberSpecification methodSpecification = new MemberSpecification() {
        @Override
        public boolean isSatisfiedBy(Declaration decl) {
            return decl instanceof Function;
        }
    };

    public ClassDoc(CeylonDocTool tool, Writer writer, TypeDeclaration klass) throws IOException {
        super(tool.getModule(klass), tool, writer);
        this.klass = klass;
        loadMembers();
    }

    private void loadMembers() {
        constructors = new ArrayList<Constructor>();
        methods = new ArrayList<Function>();
        attributes = new ArrayList<TypedDeclaration>();
        innerInterfaces = new ArrayList<Interface>();
        innerClasses = new ArrayList<Class>();
        innerExceptions = new ArrayList<Class>();
        innerAliases = new ArrayList<TypeAlias>();
        superClasses = getAncestors(klass);
        superInterfaces = getSuperInterfaces(klass);
        
        for (Declaration m : klass.getMembers()) {
            if (tool.shouldInclude(m)) {
                if (m instanceof Constructor) {
                    constructors.add((Constructor) m);
                } else if (m instanceof Value) {
                    attributes.add((Value) m);
                } else if (m instanceof Function) {
                    methods.add((Function) m);
                } else if (m instanceof Interface) {
                    innerInterfaces.add((Interface) m);
                } else if (m instanceof Class) {
                    Class c = (Class) m;
                    if (Util.isThrowable(c)) {
                        innerExceptions.add(c);
                    } else {
                        innerClasses.add(c);
                    }
                } else if (m instanceof TypeAlias) {
                    innerAliases.add((TypeAlias) m);
                }
            }
        }

        Collections.sort(constructors, ReferenceableComparatorByName.INSTANCE);
        Collections.sort(methods, ReferenceableComparatorByName.INSTANCE);
        Collections.sort(attributes, ReferenceableComparatorByName.INSTANCE);
        Collections.sort(superInterfaces, TypeComparatorByName.INSTANCE);
        Collections.sort(innerInterfaces, ReferenceableComparatorByName.INSTANCE);
        Collections.sort(innerClasses, ReferenceableComparatorByName.INSTANCE);
        Collections.sort(innerExceptions, ReferenceableComparatorByName.INSTANCE);
        Collections.sort(innerAliases, ReferenceableComparatorByName.INSTANCE);
        
        loadSuperclassInheritedMembers(attributeSpecification);
        loadSuperclassInheritedMembers(methodSpecification);
        loadInterfaceInheritedMembers(attributeSpecification);
        loadInterfaceInheritedMembers(methodSpecification);
    }

    private void loadSuperclassInheritedMembers(MemberSpecification specification) {
        LinkedHashMap<TypeDeclaration, List<Declaration>> inheritedMembers = new LinkedHashMap<TypeDeclaration, List<Declaration>>();
        TypeDeclaration subclass = klass;
        for (TypeDeclaration superClass : superClasses) {
            List<Declaration> methods = getInheritedMembers(superClass, specification);
            if (methods.isEmpty())
                continue;
            List<Declaration> notRefined = new ArrayList<Declaration>();
            // clean already listed methods (refined in subclasses)
            // done in 2 phases to avoid empty tables
            for (Declaration method : methods) {
                if (subclass.getDirectMember(method.getName(), null, false) == null) {
                    notRefined.add(method);
                }
            }
            if (notRefined.isEmpty())
                continue;
            inheritedMembers.put(superClass, notRefined);
        }
        superclassInheritedMembers.put(specification, inheritedMembers);
    }

    private void loadInterfaceInheritedMembers(MemberSpecification memberSpecification) {
        LinkedHashMap<TypeDeclaration, List<Declaration>> result = new LinkedHashMap<TypeDeclaration, List<Declaration>>();
        for (Type superInterface : superInterfaces) {
            TypeDeclaration decl = superInterface.getDeclaration();
            List<Declaration> members = getInheritedMembers(decl, memberSpecification);
            for (Declaration member : members) {
                
                Declaration refined = member.getRefinedDeclaration();
                if (refined == null
                        || refined == member
                        || (refined.getContainer() instanceof Class && refined.getContainer() != klass)) {
                    List<Declaration> r = result.get(member.getContainer());
                    if (r == null) {
                        r = new ArrayList<Declaration>();
                        result.put((TypeDeclaration)member.getContainer(), r);
                    }
                    r.add(member);
                }
            }
        }
        interfaceInheritedMembers.put(memberSpecification, result);
    }

    private List<Declaration> getInheritedMembers(TypeDeclaration decl, MemberSpecification specification) {
        List<Declaration> members = new ArrayList<Declaration>();
        for (Declaration m : decl.getMembers())
            if (tool.shouldInclude(m) && specification.isSatisfiedBy(m)) {
                members.add((FunctionOrValue) m);
            }
        return members;
    }
    
    private boolean isObject() {
        return klass instanceof Class && klass.isAnonymous();
    }

    private boolean hasInitializer() {
        return klass instanceof Class && !isObject() && constructors.isEmpty();
    }

    /**
     * Determines whether the type has any attributes (include any inherited from superclasses or superinterfaces). 
     * @return true if the type has any attributes.
     */
    private boolean hasAnyAttributes() {
        return !(attributes.isEmpty() 
                    && interfaceInheritedMembers.get(attributeSpecification).isEmpty()
                    && superclassInheritedMembers.get(attributeSpecification).isEmpty());
    }

    /**
     * Determines whether the type has any methods (include any inherited from superclasses or superinterfaces). 
     * @return true if the type has any methods.
     */
    private boolean hasAnyMethods() {
        return !(methods.isEmpty() 
                    && interfaceInheritedMembers.get(methodSpecification).isEmpty()
                    && superclassInheritedMembers.get(methodSpecification).isEmpty());
    }
    
    private String getClassLabel() {
        if (klass instanceof Interface) {
            return "interface";
        } else if (klass.isAnnotation()) {
            return "annotation";
        } else if (isObject()) {
            return "object";
        } else {
            return "class";
        }
    }

    public void generate() throws IOException {
        writeHeader(Util.capitalize(getClassLabel()) + " " + klass.getName());
        writeNavBar();
        writeSubNavBar();
        
        open("div class='container-fluid'");
        
        writeDescription();
        
        if (hasInitializer()) {
            writeInitializer((Class) klass);
        }

        if( !constructors.isEmpty() ) {
            writeConstructors();
        }
        
        if (hasAnyAttributes()) {
            open("div id='section-attributes'");
            writeAttributes();
            writeInheritedMembers(attributeSpecification, "Inherited Attributes", "Attributes inherited from: ");
            close("div");
        }

        if (hasAnyMethods()) {
            open("div id='section-methods'");
            writeMethods();
            writeInheritedMembers(methodSpecification, "Inherited Methods", "Methods inherited from: ");
            close("div");
        }

        // Nested types at the end
        writeInnerTypes(innerAliases, "section-nested-aliases", "Nested Aliases");
        writeInnerTypes(innerInterfaces, "section-nested-interfaces", "Nested Interfaces");
        writeInnerTypes(innerClasses, "section-nested-classes", "Nested Classes");
        writeInnerTypes(innerExceptions, "section-nested-exceptions", "Nested Exceptions");

        close("div");
        
        writeFooter();
    }

    private void writeSubNavBar() throws IOException {
        Package pkg = tool.getPackage(klass);
        
        open("div class='sub-navbar'");
        
        writeLinkSourceCode(klass);
        
        open("div class='sub-navbar-inner'");
        
        open("span class='sub-navbar-package'");
        writeIcon(pkg);
        writePackageNavigation(pkg);
        close("span");
        
        write("<br/>");
        writeClassSignature();
        close("div"); // sub-navbar-inner
        
        open("div class='sub-navbar-menu'");
        writeSubNavBarLink(linkRenderer().to(module).getUrl(), "Overview", 'O', "Jump to module documentation");
        writeSubNavBarLink(linkRenderer().to(pkg).getUrl(), "Package", 'P', "Jump to package documentation");
        if (hasInitializer()) {
            writeSubNavBarLink("#section-initializer", "Initializer", 'z', "Jump to initializer");
        }
        if (!constructors.isEmpty()) {
            writeSubNavBarLink("#section-constructors", "Constructors", 't', "Jump to constructors");
        }
        if (hasAnyAttributes()) {
            writeSubNavBarLink("#section-attributes", "Attributes", 'A', "Jump to attributes");
        }
        if (hasAnyMethods()) {
            writeSubNavBarLink("#section-methods", "Methods", 'M', "Jump to methods");
        }
        if (!innerAliases.isEmpty()) {
            writeSubNavBarLink("#section-nested-aliases", "Nested Aliases", 'l', "Jump to nested aliases");
        }
        if (!innerInterfaces.isEmpty()) {
            writeSubNavBarLink("#section-nested-interfaces", "Nested Interfaces", 'I', "Jump to nested interfaces");
        }
        if (!innerClasses.isEmpty()) {
            writeSubNavBarLink("#section-nested-classes", "Nested Classes", 'C', "Jump to nested classes");
        }
        if (!innerExceptions.isEmpty()) {
            writeSubNavBarLink("#section-nested-exceptions", "Nested Exceptions", 'E', "Jump to nested exceptions");
        }
        if( isObject() ) {
            writeSubNavBarLink(linkRenderer().to(klass.getContainer()).useAnchor(klass).getUrl(), "Singleton object declaration", '\0', "Jump to singleton object declaration");
        }
        
        close("div"); // sub-navbar-menu
        
        close("div"); // sub-navbar
    }
    
    private void writeClassSignature() throws IOException {
        open("span class='sub-navbar-label'");
        write(getClassLabel());
        close("span");
        writeIcon(klass);
        open("span class='sub-navbar-name'");
        writeQualifyingType(klass);
        open("span class='type-identifier'");
        write(klass.getName());
        close("span");
        writeTypeParameters(klass.getTypeParameters(), klass);
        close("span");
        writeInheritance(klass);
        writeTypeParametersConstraints(klass.getTypeParameters(), klass);
    }

    private void writeQualifyingType(TypeDeclaration klass) throws IOException {
        if (klass.isClassOrInterfaceMember()) {
            TypeDeclaration container = (TypeDeclaration) klass.getContainer();
            writeQualifyingType(container);
            linkRenderer().to(container).useScope(klass).write();
            write(".");
        }
    }    

    private void writeDescription() throws IOException {
        open("div class='class-description'");
        
        writeTagged(klass);
        writeTabs();
        
        close("div"); // class-description
    }

    private void writeTabs() throws IOException {
        boolean hasTypeHierarchy = klass instanceof Class;
        boolean hasSupertypeHierarchy = klass instanceof Class || !isEmpty(klass.getSatisfiedTypes());
        boolean hasSubtypeHierarchy = !isEmpty(tool.getSubclasses(klass)) || !isEmpty(tool.getSatisfyingClassesOrInterfaces(klass));

        open("div class='type-tabs section'");
        open("div class='tabbable'");

        open("ul class='nav nav-tabs'");
        writeTabNav("tabDocumentation", "Documentation", "icon-documentation", true, true, false);
        writeTabNav("tabTypeHierarchy", "Type Hierarchy", "icon-type-hierarchy", false, hasTypeHierarchy, false);
        writeTabNav("tabSupertypeHierarchy", "Supertype Hierarchy", "icon-supertype-hierarchy", false, hasSupertypeHierarchy, false);
        writeTabNav("tabSubtypeHierarchy", "Subtype Hierarchy", "icon-subtype-hierarchy", false, hasSubtypeHierarchy, Util.isEnumerated(klass));
        close("ul"); // nav-tabs

        open("div class='tab-content'");

        open("div class='tab-pane active' id='tabDocumentation'");
        /*if (!klass.getUnit().getAnythingDeclaration().equals(klass) &&
                klass.getExtendedTypeDeclaration()!=null) {
            writeListOnSummary("extended", "Extended class: ", 
                    singletonList(klass.getExtendedTypeDeclaration()));
        }
        writeListOnSummary("satisfied", "Satisfied Interfaces: ", superInterfaces);
        writeEnclosingType();*/
        writeAnnotationConstructors();

        around("div class='doc'", getDoc(klass, linkRenderer()));
        writeBy(klass);
        writeSee(klass);
        close("div");

        open("div class='tab-pane' id='tabTypeHierarchy'");
        if( hasTypeHierarchy ) {
            writeTypeHierarchy();
        } else {
            write("<p class='muted'><i>no type hierarchy</i></p>");
        }
        close("div");
        
        open("div class='tab-pane' id='tabSupertypeHierarchy'");
        if( hasSupertypeHierarchy ) {
            writeSuperTypeHierarchy(Collections.singletonList(klass), 0);
        } else {
            write("<p class='muted'><i>no supertypes hierarchy</i></p>");
        }
        close("div");

        open("div class='tab-pane' id='tabSubtypeHierarchy'");
        if( hasSubtypeHierarchy ) {
            writeSubtypesHierarchy(Collections.singletonList(klass), 0);
        } else {
            write("<p class='muted'><i>no subtypes hierarchy</i></p>"); 
        }
        close("div");
        
        close("div"); // tab-content
        close("div"); // tabbable
        close("div"); // typeHierarchy
    }
    
    private void writeTabNav(String id, String name, String icon, boolean isActive, boolean isEnabled, boolean isEnumerated) throws IOException {
        write("<li" + (isActive ? " class='active'" : "") + ">");
        write("<a id='" + id + "Nav' href='#" + id + "' data-toggle='tab'>");
        write("<i class='" + icon + (isEnabled ? "" : "-disabled") + "'></i>");
        if(isEnumerated){
            around("span class='label label-info' title='Enumerated type with an &quot;of&quot; clause'", "Enumerated");
            write(" ");
        }
        write("<span" + (isEnabled ? "" : " class='disabled'") + ">" + name + "</span>");
        write("</a>");
        write("</li>");
    }
    
    private void writeTypeHierarchy() throws IOException {
        LinkedList<Type> superTypes = new LinkedList<Type>();
        superTypes.add(klass.getType());
        Type type = klass.getExtendedType();
        while (type != null) {
            superTypes.add(0, type);
            type = type.getExtendedType();
        }
        int level = 0;
        for (Type superType : superTypes) {
            writeTypeHierarchyLevel(superType.getDeclaration(), level < superTypes.size() - 1);
            if (!isEmpty(superType.getSatisfiedTypes())) {
                write("<a class='hint' title='Go to the Supertype Hierarchy' onClick='$(\"#tabSupertypeHierarchyNav\").tab(\"show\");'> ...and other supertypes</a>");
            }
            open("div class='subhierarchy'");
            level++;
        }
        while (level-- > 0) {
            close("div"); // subhierarchy
            close("li", "ul");
        }
    }
    
    private void writeSuperTypeHierarchy(List<TypeDeclaration> types, int level) throws IOException {
        if (types.size() > 1) {
            Collections.sort(types, ReferenceableComparatorByName.INSTANCE);
        }
        for (TypeDeclaration type : types) {
            List<TypeDeclaration> supertypes = collectSupertypes(type);
            writeTypeHierarchyLevel(type, !supertypes.isEmpty());
            open("div class='subhierarchy'");
            writeSuperTypeHierarchy(supertypes, level + 1);
            close("div"); // subhierarchy
            close("li", "ul");
        }
    }

    private void writeSubtypesHierarchy(List<TypeDeclaration> types, int level) throws IOException {
        if (types.size() > 1) {
            Collections.sort(types, ReferenceableComparatorByName.INSTANCE);
        }
        for (TypeDeclaration type : types) {
            List<TypeDeclaration> subtypes = collectSubtypes(type);
            writeTypeHierarchyLevel(type, !subtypes.isEmpty());
            if(level == 0 && Util.isEnumerated(type)){
                around("span class='keyword'", " of");
            }
            open("div class='subhierarchy'");
            writeSubtypesHierarchy(subtypes, level + 1);
            close("div"); // subhierarchy
            close("li", "ul");
        }
    }
    
    private void writeTypeHierarchyLevel(TypeDeclaration type, boolean hasSublevels) throws IOException {
        open("ul class='hierarchy-level'", "li");
        if( hasSublevels ) {
            write("<span class='hierarchy-arrow-container' title='Click for expand/collapse'><span class='hierarchy-arrow-down'></span></span>");
        } else {
            write("<span class='hierarchy-arrow-none'></span>");
        }
        writeIcon(type);
        linkRenderer().to(type).useScope(klass).withinText(true).printTypeParameters(false).printAbbreviated(false).write();
    }
    
    private List<TypeDeclaration> collectSupertypes(TypeDeclaration type) {
        List<TypeDeclaration> supertypes = new ArrayList<TypeDeclaration>();
        if (type instanceof Class && type.getExtendedType() != null) {
            supertypes.add(type.getExtendedType().getDeclaration());
        }
        List<Type> satisfiedTypes = type.getSatisfiedTypes();
        if (satisfiedTypes != null) {
            for (Type satisfiedType: satisfiedTypes) {
                supertypes.add(satisfiedType.getDeclaration());
            }
        }
        return supertypes;
    }

    private List<TypeDeclaration> collectSubtypes(TypeDeclaration type) {
        List<TypeDeclaration> subtypes = new ArrayList<TypeDeclaration>();
        List<Class> subclasses = tool.getSubclasses(type);
        if (subclasses != null) {
            subtypes.addAll(subclasses);
        }
        List<ClassOrInterface> satisfyingClassesOrInterfaces = tool.getSatisfyingClassesOrInterfaces(type);
        if (satisfyingClassesOrInterfaces != null) {
            subtypes.addAll(satisfyingClassesOrInterfaces);
        }
        return subtypes;
    }

    private void writeListOnSummary(String cssClass, String title, List<?> types) throws IOException {
        if (!isEmpty(types)) {
            open("div class='" + cssClass + " section'");
            around("span class='title'", title);
            
            boolean first = true;
            for (Object type : types) {
                if (!first) {
                    write(", ");
                } else {
                    first = false;
                }
                
                if( type instanceof TypedDeclaration ) {
                    TypedDeclaration decl = (TypedDeclaration) type;
                    linkRenderer().to(decl).useScope(klass).write();
                } else if( type instanceof ClassOrInterface ) {
                    ClassOrInterface coi = (ClassOrInterface) type;
                    linkRenderer().to(coi).useScope(klass).printAbbreviated(!isAbbreviatedType(coi)).write();
                } else {
                    Type pt = (Type) type;
                    linkRenderer().to(pt).useScope(klass).printAbbreviated(!isAbbreviatedType(pt.getDeclaration())).write(); 
                }
            }
            close("div");
        }
    }

    /*private void writeEnclosingType() throws IOException {
        if (klass.isMember()) {
            ClassOrInterface enclosingType = (ClassOrInterface) klass.getContainer();
            open("div class='enclosingType section'");
            around("span class='title'", "Enclosing " + (enclosingType instanceof Class ? "class: " : "interface: "));
            writeIcon(enclosingType);
            linkToDeclaration(enclosingType);
            close("div");
        }
    }*/
    
    private void writeAnnotationConstructors() throws IOException {
        if( klass.isAnnotation() ) {
            List<Function> annotationConstructors = tool.getAnnotationConstructors(klass);
            if( annotationConstructors != null ) {
                Collections.sort(annotationConstructors, ReferenceableComparatorByName.INSTANCE);
                writeListOnSummary("annotationConstructors", "Annotation Constructors: ", annotationConstructors);
            }
        }
    }

    private void writeInheritedMembers(MemberSpecification specification, String tableTitle, String rowTitle) throws IOException {
        boolean first = true;
        
        Map<TypeDeclaration, List<Declaration>> superClassInheritedMembersMap = superclassInheritedMembers.get(specification);
        List<TypeDeclaration> superClasses = new ArrayList<TypeDeclaration>(superclassInheritedMembers.get(specification).keySet());
        Collections.sort(superClasses, ReferenceableComparatorByName.INSTANCE);
        
        for (TypeDeclaration superClass : superClasses) {
            List<Declaration> inheritedMembers = superClassInheritedMembersMap.get(superClass);
            Collections.sort(inheritedMembers, ReferenceableComparatorByName.INSTANCE);

            if (first) {
                first = false;
                openTable(null, tableTitle, 1, false);
            }
            writeInheritedMembersRow(rowTitle, superClass, inheritedMembers);
        }

        Map<TypeDeclaration, List<Declaration>> superInterfaceInheritedMembersMap = interfaceInheritedMembers.get(specification);
        List<TypeDeclaration> superInterfaces = new ArrayList<TypeDeclaration>(superInterfaceInheritedMembersMap.keySet());
        Collections.sort(superInterfaces, ReferenceableComparatorByName.INSTANCE);
        
        for (TypeDeclaration superInterface : superInterfaces) {
            List<Declaration> members = superInterfaceInheritedMembersMap.get(superInterface);
            if (members == null || members.isEmpty()) {
                continue;
            }
            Collections.sort(members, ReferenceableComparatorByName.INSTANCE);

            if (first) {
                first = false;
                openTable(null, tableTitle, 1, false);
            }
            writeInheritedMembersRow(rowTitle, superInterface, members);
        }

        if (!first) {
            closeTable();
        }
    }

    private void writeInheritedMembersRow(String title, TypeDeclaration superType, List<Declaration> members) throws IOException {
        open("tr", "td");
        write(title);
        writeIcon(superType);
        linkRenderer().to(superType).useScope(klass).withinText(true).write();
        open("div class='inherited-members'");
        
        boolean first = true;
        for (Declaration member : members) {
            if (!first) {
                write(", ");
            } else {
                first = false;
            }
            linkRenderer().withinText(true).to(member).write();
        }
        
        close("div");
        close("td", "tr");
    }
    
    private void writeInnerTypes(List<? extends TypeDeclaration> innerTypeDeclarations, String id, String title) throws IOException {
        if (!innerTypeDeclarations.isEmpty()) {
            openTable(id, title, 2, true);
            for (TypeDeclaration innerTypeDeclaration : innerTypeDeclarations) {
                if (innerTypeDeclaration instanceof ClassOrInterface) {
                    ClassOrInterface innerClassOrInterface = (ClassOrInterface) innerTypeDeclaration;
                    tool.doc(innerClassOrInterface);
                    doc(innerClassOrInterface);
                }
                if (innerTypeDeclaration instanceof TypeAlias) {
                    TypeAlias innerAlias = (TypeAlias) innerTypeDeclaration;
                    doc(innerAlias);
                }
            }
            closeTable();
        }
    }

    private void writeInitializer(Class klass) throws IOException {
        openTable("section-initializer", "Initializer", 1, true);
        open("tr", "td");
        
        writeParameterLinksIfRequired(klass);
        writeIcon(klass);
        
        open("code class='decl-label'");
        write(klass.getName());
        writeParameterList(klass, klass);
        close("code");
        
        open("div class='description'");
        writeParameters(klass);
        writeThrows(klass);
        close("div");
        
        close("td", "tr");
        closeTable();
    }
    
    private void writeConstructors() throws IOException {
        if (constructors.isEmpty()){
            return;
        }
        openTable("section-constructors", "Constructors", 1, true);
        for (Constructor constructor : constructors) {
            doc(constructor);
        }
        closeTable();
    }
    
    private void writeAttributes() throws IOException {
        if (attributes.isEmpty()){
            return;
        }
        openTable(null, "Attributes", 2, true);
        for (TypedDeclaration attribute : attributes) {
            doc(attribute);
        }
        closeTable();
    }

    private void writeMethods() throws IOException {
        if (methods.isEmpty()){
            return;
        }
        openTable(null, "Methods", 2, true);
        for (Function m : methods) {
            doc(m);
        }
        closeTable();
    }

    @Override
    protected void registerAdditionalKeyboardShortcuts() throws IOException {
        registerKeyboardShortcut('p', "index.html");
        if (hasAnyAttributes()) {
            registerKeyboardShortcut('a', "#section-attributes");
        }
        if (hasInitializer()) {
            registerKeyboardShortcut('z', "#section-initializer");
        }
        if (!constructors.isEmpty()) {
            registerKeyboardShortcut('t', "#section-constructors");
        }
        if (hasAnyMethods()) {
            registerKeyboardShortcut('m', "#section-methods");
        }
        if (!innerAliases.isEmpty()) {
            registerKeyboardShortcut('l', "#section-nested-aliases");
        }
        if (!innerInterfaces.isEmpty()) {
            registerKeyboardShortcut('i', "#section-nested-interfaces");
        }
        if (!innerClasses.isEmpty()) {
            registerKeyboardShortcut('c', "#section-nested-classes");
        }
        if (!innerExceptions.isEmpty()) {
            registerKeyboardShortcut('e', "#section-nested-exceptions");
        }
    }

    @Override
    protected Object getFromObject() {
        return klass;
    }
    
}