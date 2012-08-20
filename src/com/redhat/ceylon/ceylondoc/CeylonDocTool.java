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

import static com.redhat.ceylon.ceylondoc.Util.join;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.redhat.ceylon.cmr.api.ArtifactContext;
import com.redhat.ceylon.cmr.api.RepositoryManager;
import com.redhat.ceylon.cmr.ceylon.CeylonUtils;
import com.redhat.ceylon.cmr.impl.CMRException;
import com.redhat.ceylon.compiler.loader.SourceDeclarationVisitor;
import com.redhat.ceylon.compiler.typechecker.TypeChecker;
import com.redhat.ceylon.compiler.typechecker.TypeCheckerBuilder;
import com.redhat.ceylon.compiler.typechecker.analyzer.ModuleManager;
import com.redhat.ceylon.compiler.typechecker.context.Context;
import com.redhat.ceylon.compiler.typechecker.context.PhasedUnit;
import com.redhat.ceylon.compiler.typechecker.model.Class;
import com.redhat.ceylon.compiler.typechecker.model.ClassOrInterface;
import com.redhat.ceylon.compiler.typechecker.model.Declaration;
import com.redhat.ceylon.compiler.typechecker.model.Element;
import com.redhat.ceylon.compiler.typechecker.model.Getter;
import com.redhat.ceylon.compiler.typechecker.model.Interface;
import com.redhat.ceylon.compiler.typechecker.model.Method;
import com.redhat.ceylon.compiler.typechecker.model.Module;
import com.redhat.ceylon.compiler.typechecker.model.Modules;
import com.redhat.ceylon.compiler.typechecker.model.Package;
import com.redhat.ceylon.compiler.typechecker.model.Scope;
import com.redhat.ceylon.compiler.typechecker.model.TypeDeclaration;
import com.redhat.ceylon.compiler.typechecker.model.Unit;
import com.redhat.ceylon.compiler.typechecker.model.Value;
import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.AttributeDeclaration;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.CompilationUnit;
import com.redhat.ceylon.compiler.typechecker.tree.Visitor;
import com.redhat.ceylon.compiler.typechecker.tree.Walker;
import com.redhat.ceylon.compiler.typechecker.util.ModuleManagerFactory;

public class CeylonDocTool {

    private List<PhasedUnit> phasedUnits;
    private List<Module> modules;
    private String outputRepository;
    private String user,pass;
    /**
     * The {@linkplain #shouldInclude(Declaration) visible} subclasses of the key
     */
    private Map<ClassOrInterface, List<ClassOrInterface>> subclasses = new HashMap<ClassOrInterface, List<ClassOrInterface>>();
    /**
     * The {@linkplain #shouldInclude(Declaration) visible} class/interfaces 
     * that satisfy the key
     */
    private Map<TypeDeclaration, List<ClassOrInterface>> satisfyingClassesOrInterfaces = new HashMap<TypeDeclaration, List<ClassOrInterface>>();    
    private boolean includeNonShared;
    private boolean includeSourceCode;
    private Map<Declaration, Node> sourceLocations = new HashMap<Declaration, Node>();
    private File tempDestDir;
    private CeylondLogger log;
    private List<String> compiledClasses = new LinkedList<String>();
    private Module currentModule;

    public CeylonDocTool(List<File> sourceFolders, List<String> repositories, List<String> moduleSpecs,
            boolean haltOnError) {
        TypeCheckerBuilder builder = new TypeCheckerBuilder();
        for(File src : sourceFolders){
            builder.addSrcDirectory(src);
        }
        this.log = new CeylondLogger();
        
        // set up the artifact repository
        RepositoryManager repository = CeylonUtils.makeRepositoryManager(repositories, null, log );
        builder.setRepositoryManager(repository);
        
        // we need to plug in the module manager which can load from .cars
        final List<ModuleSpec> modules = ModuleSpec.parse(moduleSpecs);
        builder.moduleManagerFactory(new ModuleManagerFactory(){
            @Override
            public ModuleManager createModuleManager(Context context) {
                return new CeylonDocModuleManager(CeylonDocTool.this, context, modules, log);
            }
        });
        
        // only parse what we asked for
        List<String> moduleFilters = new LinkedList<String>();
        for(ModuleSpec spec : modules){
            moduleFilters.add(spec.name);
        }
        builder.setModuleFilters(moduleFilters);
        
        TypeChecker typeChecker = builder.getTypeChecker();
        // collect all units we are typechecking
        collectTypeCheckedUnits(typeChecker);
        typeChecker.process();
        if(haltOnError && typeChecker.getErrors() > 0)
            throw new RuntimeException(CeylondMessages.msg("error.failedParsing", typeChecker.getErrors()));
        
        this.modules = getModules(modules, typeChecker.getContext().getModules());
        // only for source code mapping
        this.phasedUnits = getPhasedUnits(typeChecker.getPhasedUnits().getPhasedUnits());

        // make a temp dest folder
        try {
            this.tempDestDir = File.createTempFile("ceylond", "");
        } catch (IOException e) {
            e.printStackTrace();
        }
        tempDestDir.delete();
        tempDestDir.mkdirs();
    }

    private void collectTypeCheckedUnits(TypeChecker typeChecker) {
        for(PhasedUnit unit : typeChecker.getPhasedUnits().getPhasedUnits()){
            // obtain the unit container path
            final String pkgName = Util.getUnitPackageName(unit); 
            unit.getCompilationUnit().visit(new SourceDeclarationVisitor(){
                @Override
                public void loadFromSource(com.redhat.ceylon.compiler.typechecker.tree.Tree.Declaration decl) {
                    compiledClasses.add(Util.getQuotedFQN(pkgName, decl));
                }
            });
        }
    }

    private List<Module> getModules(List<ModuleSpec> moduleSpecs, Modules modules){
        // find the required modules
        List<Module> documentedModules = new LinkedList<Module>();
        for(ModuleSpec moduleSpec : moduleSpecs){
            Module foundModule = null;
            for(Module module : modules.getListOfModules()){
                if(module.getNameAsString().equals(moduleSpec.name)){
                    if(moduleSpec.version == null || moduleSpec.version.equals(module.getVersion()))
                        foundModule = module;
                }
            }
            if(foundModule != null)
                documentedModules.add(foundModule);
            else if(moduleSpec.version != null)
                throw new RuntimeException(CeylondMessages.msg("error.cantFindModule", moduleSpec.name, moduleSpec.version));
            else
                throw new RuntimeException(CeylondMessages.msg("error.cantFindModuleNoVersion", moduleSpec.name));
        }
        return documentedModules;
    }
    
    private List<PhasedUnit> getPhasedUnits(List<PhasedUnit> phasedUnits) {
        List<PhasedUnit> documentedPhasedUnit = new LinkedList<PhasedUnit>();
        for(PhasedUnit pu : phasedUnits){
            if(modules.contains(pu.getUnit().getPackage().getModule()))
                documentedPhasedUnit.add(pu);
        }
        return documentedPhasedUnit;
    }

    public void setOutputRepository(String outputRepository, String user, String pass) {
        this.outputRepository = outputRepository;
        this.user = user;
        this.pass = pass;
    }

    public List<String> getCompiledClasses(){
        return compiledClasses;
    }
    
    public String getOutputRepository() {
        return outputRepository;
    }

    public void setIncludeNonShared(boolean includeNonShared) {
        this.includeNonShared = includeNonShared;
    }

    public boolean isIncludeNonShared() {
        return includeNonShared;
    }

    public void setIncludeSourceCode(boolean includeSourceCode) {
        this.includeSourceCode = includeSourceCode;
    }
    
    public boolean isIncludeSourceCode() {
        return includeSourceCode;
    }

    private String getFileName(Scope klass) {
        List<String> name = new LinkedList<String>();
        while(klass instanceof Declaration){
            name.add(0, ((Declaration)klass).getName());
            klass = klass.getContainer();
        }
        return join(".", name);
    }

    private File getFolder(Package pkg) {
        Module module = pkg.getModule();
        List<String> unprefixedName;
        if(module.isDefault())
            unprefixedName = pkg.getName();
        else{
            // remove the leading module name part
            unprefixedName = pkg.getName().subList(module.getName().size(), pkg.getName().size());
        }
        File dir = new File(getOutputFolder(module), join("/", unprefixedName));
        if(shouldInclude(module))
            dir.mkdirs();
        return dir;
    }
    
    public File getOutputFolder(Module module) {
        File folder = new File(com.redhat.ceylon.compiler.java.util.Util.getModulePath(tempDestDir, module),
                "module-doc");
        if(shouldInclude(module))
            folder.mkdirs();
        return folder;
    }

    private File getFolder(ClassOrInterface klass) {
        return getFolder(getPackage(klass));
    }

    public String kind(Object obj) {
        if (obj instanceof Class) {
            return Character.isUpperCase(((Class)obj).getName().charAt(0)) ? "class" : "object";
        } else if (obj instanceof Interface) {
            return "interface";
        } else if (obj instanceof AttributeDeclaration
                || obj instanceof Getter) {
            return "attribute";
        } else if (obj instanceof Method) {
            return "function";
        } else if (obj instanceof Value) {
            return "value";
        } else if (obj instanceof Package) {
            return "package";
        } else if (obj instanceof Module) {
            return "module";
        }
        throw new RuntimeException(CeylondMessages.msg("error.unexpected", obj));
    }

    File getObjectFile(Object modPgkOrDecl) throws IOException {
        final File file;
        if (modPgkOrDecl instanceof ClassOrInterface) {
            ClassOrInterface klass = (ClassOrInterface)modPgkOrDecl;
            String filename = kind(modPgkOrDecl) + "_" + getFileName(klass) + ".html";
            file = new File(getFolder(klass), filename);
        } else if (modPgkOrDecl instanceof Module) {
            String filename = "index.html";
            file = new File(getOutputFolder((Module)modPgkOrDecl), filename);
        } else if (modPgkOrDecl instanceof Package) {
            String filename = "index.html";
            file = new File(getFolder((Package)modPgkOrDecl), filename);
        } else {
            throw new RuntimeException(CeylondMessages.msg("error.unexpected", modPgkOrDecl));
        }
        return file.getCanonicalFile();
    }

    public void makeDoc() throws IOException{
        
        if (includeSourceCode) {
            buildSourceLocations();
            copySourceFiles();
        }

        collectSubclasses();

        // make a destination repo
        RepositoryManager outputRepository = CeylonUtils.makeOutputRepositoryManager(this.outputRepository, log, user, pass);

        try{
            // document every module
            boolean documentedOne = false;
            for(Module module : modules){
                if(isEmpty(module))
                    log.warning(CeylondMessages.msg("warn.moduleHasNoDeclaration", module.getNameAsString()));
                else
                    documentedOne = true;
                documentModule(module);
                ArtifactContext context = new ArtifactContext(module.getNameAsString(), module.getVersion(), ArtifactContext.DOCS);
                try{
                    outputRepository.removeArtifact(context);
                }catch(CMRException x){
                    throw new CeylondException("error.failedRemoveArtifact", new Object[]{context, x.getLocalizedMessage()}, x);
                }catch(Exception x){
                    // FIXME: remove when the whole CMR is using CMRException
                    throw new CeylondException("error.failedRemoveArtifact", new Object[]{context, x.getLocalizedMessage()}, x);
                }
                try{
                    outputRepository.putArtifact(context, getOutputFolder(module));
                }catch(CMRException x){
                    throw new CeylondException("error.failedWriteArtifact", new Object[]{context, x.getLocalizedMessage()}, x);
                }catch(Exception x){
                    // FIXME: remove when the whole CMR is using CMRException
                    throw new CeylondException("error.failedWriteArtifact", new Object[]{context, x.getLocalizedMessage()}, x);
                }
            }
            if(!documentedOne)
                log.warning(CeylondMessages.msg("warn.couldNotFindAnyDeclaration"));
        }finally{
            Util.delete(tempDestDir);
        }
    }

    private boolean isEmpty(Module module) {
        for(Package pkg : module.getPackages())
            if(!pkg.getMembers().isEmpty())
                return false;
        return true;
    }

    private void documentModule(Module module) throws IOException {
        try {
            currentModule = module;
            
            doc(module);
            makeIndex(module);
            makeSearch(module);
            
            File resourcesDir = getResourcesDir(module);
            copyResource("resources/style.css", new File(resourcesDir, "style.css"));
            copyResource("resources/shCore.css", new File(resourcesDir, "shCore.css"));
            copyResource("resources/shThemeDefault.css", new File(resourcesDir, "shThemeDefault.css"));
            copyResource("resources/jquery-1.7.min.js", new File(resourcesDir, "jquery-1.7.min.js"));
            copyResource("resources/ceylond.js", new File(resourcesDir, "ceylond.js"));
            copyResource("resources/shCore.js", new File(resourcesDir, "shCore.js"));
            copyResource("resources/shBrushCeylon.js", new File(resourcesDir, "shBrushCeylon.js"));
            copyResource("resources/icons.png", new File(resourcesDir, "icons.png"));
            copyResource("resources/NOTICE.txt", new File(getOutputFolder(module), "NOTICE.txt"));
        }
        finally {
            currentModule = null;
        }
    }

    private void collectSubclasses() throws IOException {
        for (Module module : modules) {
            for (Package pkg : module.getPackages()) {
                for (Declaration decl : pkg.getMembers()) {
                    if(!shouldInclude(decl)) {
                        continue;
                    }
                    if (decl instanceof ClassOrInterface) {
                        // FIXME: why this call?
                        getObjectFile(decl);
                        ClassOrInterface c = (ClassOrInterface) decl;                    
                        // subclasses map
                        if (c instanceof Class) {
                            ClassOrInterface superclass = c.getExtendedTypeDeclaration();                    
                            if (superclass != null) {
                                if (subclasses.get(superclass) ==  null) {
                                    subclasses.put(superclass, new ArrayList<ClassOrInterface>());
                                }
                                subclasses.get(superclass).add(c);
                            }
                        }

                        List<TypeDeclaration> satisfiedTypes = new ArrayList<TypeDeclaration>(c.getSatisfiedTypeDeclarations());                     
                        if (satisfiedTypes != null && satisfiedTypes.isEmpty() == false) {
                            // satisfying classes or interfaces map
                            for (TypeDeclaration satisfiedType : satisfiedTypes) {
                                if (satisfyingClassesOrInterfaces.get(satisfiedType) ==  null) {
                                    satisfyingClassesOrInterfaces.put(satisfiedType, new ArrayList<ClassOrInterface>());
                                }
                                satisfyingClassesOrInterfaces.get(satisfiedType).add(c);
                            }
                        }
                    }
                }
            }
        }
    }
    private Writer openWriter(File file) throws IOException {
        return new OutputStreamWriter(new FileOutputStream(file), "UTF-8"); 
    }
    
    private void makeSearch(Module module) throws IOException {
        Writer writer = openWriter(new File(getOutputFolder(module), "search.html"));
        try {
            new Search(module, this, writer).generate();
        } finally {
            writer.close();
        }
    }

    private void buildSourceLocations() {
        for (PhasedUnit pu : phasedUnits) {
            CompilationUnit cu = pu.getCompilationUnit();
            Walker.walkCompilationUnit(new Visitor() {
                public void visit(Tree.Declaration decl) {
                    sourceLocations.put(decl.getDeclarationModel(), decl);
                    super.visit(decl);
                }
                public void visit(Tree.MethodDeclaration decl) {
                    sourceLocations.put(decl.getDeclarationModel(), decl);
                    super.visit(decl);
                }
                public void visit(Tree.AttributeDeclaration decl) {
                    sourceLocations.put(decl.getDeclarationModel(), decl);
                    super.visit(decl);
                }
            }, cu);
        }
    }

    private void copySourceFiles() throws FileNotFoundException, IOException {
        for (PhasedUnit pu : phasedUnits) {
            File file = new File(getFolder(pu.getPackage()), pu.getUnitFile().getName()+".html");
            File dir = file.getParentFile();
            if (!dir.exists() && !dir.mkdirs()) {
                throw new IOException(CeylondMessages.msg("error.couldNotCreateDirectory", file));
            }
            Writer writer = openWriter(file);
            try {
            Markup markup = new Markup(writer);
                markup.write("<!DOCTYPE html>");
                markup.open("html xmlns='http://www.w3.org/1999/xhtml'");
                markup.open("head");
                markup.tag("meta charset='UTF-8'");
                markup.around("title", pu.getUnit().getFilename());
                Package decl = pu.getUnit().getPackage();
                markup.tag("link href='" + getResourceUrl(decl, "shCore.css") + "' rel='stylesheet' type='text/css'");
                markup.tag("link href='" + getResourceUrl(decl, "shThemeDefault.css") + "' rel='stylesheet' type='text/css'");
                markup.around("script type='text/javascript' src='"+getResourceUrl(decl, "jquery-1.7.min.js")+"'");
                markup.around("script type='text/javascript' src='"+getResourceUrl(decl, "ceylond.js")+"'");
                markup.around("script src='" + getResourceUrl(decl, "shCore.js") + "' type='text/javascript'");
                markup.around("script src='" + getResourceUrl(decl, "shBrushCeylon.js") + "' type='text/javascript'");
                markup.close("head");
                markup.open("body", "pre class='brush: ceylon'");
                // XXX source char encoding
                BufferedReader input = new BufferedReader(new InputStreamReader(pu.getUnitFile().getInputStream()));
                try{
                    String line = input.readLine();
                    while (line != null) {
                        markup.text(line, "\n");
                        line = input.readLine();
                    }
                } finally {
                    input.close();
                }
                markup.close("pre", "body", "html");
            } finally {
                writer.close();
            }
        }
    }

    private void doc(Module module) throws IOException {
        Writer rootWriter = openWriter(getObjectFile(module));
        try {
            ModuleDoc moduleDoc = new ModuleDoc(this, rootWriter, module);
            moduleDoc.generate();
            for (Package pkg : module.getPackages()) {
                if(pkg.getMembers().isEmpty()){
                    continue;
                }
                // document the package
                if (isRootPackage(module, pkg)) {
                    new PackageDoc(this, rootWriter, pkg).generate();
                } else {
                    Writer packageWriter = openWriter(getObjectFile(pkg));
                    try {
                        new PackageDoc(this, packageWriter, pkg).generate();
                    } finally {
                        packageWriter.close();
                    }
                }
                // document its members
                for (Declaration decl : pkg.getMembers()) {
                    doc(decl);
                }
            }
            moduleDoc.complete();
        } finally {
            rootWriter.close();
        }
        
    }

    private void makeIndex(Module module) throws IOException {
        File dir = getResourcesDir(module);
        Writer writer = openWriter(new File(dir, "index.js"));
        try {
            new IndexDoc(this, writer, module).generate();
        } finally {
            writer.close();
        }
    }

    private File getResourcesDir(Module module) throws IOException {
        File dir = new File(getOutputFolder(module), ".resources");
        if (!dir.exists()
                && !dir.mkdirs()) {
            throw new IOException();
        }
        return dir;
    }
    
    /**
     * Determines whether the given package is the 'root package' (i.e. has the 
     * same fully qualified name as) of the given module.
     * @param module
     * @param pkg
     * @return
     */
    boolean isRootPackage(Module module, Package pkg) {
        if(module.isDefault())
            return pkg.getNameAsString().isEmpty();
        return pkg.getNameAsString().equals(module.getNameAsString());
    }

    private void copyResource(String path, File file) throws IOException {
        File dir = file.getParentFile();
        if (!dir.exists()
                && !dir.mkdirs()) {
            throw new IOException();
        }
        InputStream resource = getClass().getResourceAsStream(path);
        copy(resource, file);
    }

    private void copy(InputStream resource, File file)
            throws FileNotFoundException, IOException {
        OutputStream os = new FileOutputStream(file);
        byte[] buf = new byte[1024];
        int read;
        while ((read = resource.read(buf)) > -1) {
            os.write(buf, 0, read);
        }
        os.flush();
        os.close();
    }

    public void doc(Declaration decl) throws IOException {
        if (decl instanceof ClassOrInterface) {
            if (shouldInclude(decl)) {
                Writer writer = openWriter(getObjectFile(decl));
                try {
                    new ClassDoc(this, writer,
                            (ClassOrInterface) decl,
                            subclasses.get(decl),
                            satisfyingClassesOrInterfaces.get(decl)).generate();
                } finally {
                    writer.close();
                }
            }
        }
    }

    Package getPackage(Declaration decl) {
        Scope scope = decl.getContainer();
        while (!(scope instanceof Package)) {
            scope = scope.getContainer();
        }
        return (Package)scope;
    }

    Module getModule(Object modPkgOrDecl) {
        if (modPkgOrDecl instanceof Module) {
            return (Module)modPkgOrDecl;
        } else if (modPkgOrDecl instanceof Package) {
            return ((Package)modPkgOrDecl).getModule();
        } else if (modPkgOrDecl instanceof Declaration) {
            return getPackage((Declaration)modPkgOrDecl).getModule();
        }
        throw new RuntimeException();
    }
    
    List<Package> getPackages(Module module) {
        List<Package> packages = new ArrayList<Package>();
        for (Package pkg : module.getPackages()) {
            if (pkg.getMembers().size() > 0
                    && shouldInclude(pkg))
                packages.add(pkg);
        }
        Collections.sort(packages, new Comparator<Package>() {
            @Override
            public int compare(Package a, Package b) {
                return a.getNameAsString().compareTo(b.getNameAsString());
            }

        });
        return packages;
    }


    protected boolean shouldInclude(Declaration decl){
        return includeNonShared || decl.isShared();
    }
    
    protected boolean shouldInclude(Package pkg){
        return true; // TODO includeNonShared || pkg.isShared();
    }
    
    protected boolean shouldInclude(Module module){
        return modules.contains(module);
    }

    /**
     * Returns the absolute URI of the page for the given thing
     * @param obj (Module, Package, Declaration etc)
     * @throws IOException 
     */
    private URI getAbsoluteObjectUrl(Object obj) throws IOException {
        File f = getObjectFile(obj);
        if (f == null) {
            throw new RuntimeException(CeylondMessages.msg("error.noPage", obj));
        }
        return f.toURI();
    }
    
    /**
     * Gets the base URL
     * @return Gets the base URL
     */
    private URI getBaseUrl(Module module) throws IOException {
        return getOutputFolder(module).getCanonicalFile().toURI();
    }
    
    /**
     * Generates a relative URL such that:
     * <pre>
     *   uri1.resolve(relativize(url1, url2)).equals(uri2);
     * </pre>
     * @param uri
     * @param uri2
     * @return A URL suitable for a link from a page at uri to a page at uri2
     * @throws IOException 
     */
    private URI relativize(Module module, URI uri, URI uri2) throws IOException {
        if (!uri.isAbsolute()) {
            throw new IllegalArgumentException(CeylondMessages.msg("error.expectedUriToBeAbsolute", uri));
        }
        if (!uri2.isAbsolute()) {
            throw new IllegalArgumentException(CeylondMessages.msg("error.expectedUriToBeAbsolute", uri2));
        }
        URI baseUrl = getBaseUrl(module);
        StringBuilder sb = new StringBuilder();
        URI r = uri;
        if (!r.equals(baseUrl)) {
            r = uri.resolve(URI.create(sb.toString()));
            if (!r.equals(baseUrl)) {
                r = uri;
            }
        }
        while (!r.equals(baseUrl)) {
            sb.append("../");
            r = uri.resolve(URI.create(sb.toString()));
        }
        URI result = URI.create(sb.toString() + baseUrl.relativize(uri2));
        if (result.isAbsolute()) {
            // FIXME: this throws in some cases even for absolute URIs, not sure why
            //throw new RuntimeException("Result not absolute: "+result);
        }
        if (!uri.resolve(result).equals(uri2)) {
            throw new RuntimeException(CeylondMessages.msg("error.failedUriRelativize", uri, uri2, result));
        }
        return result;
    }
    
    protected String getObjectUrl(Object from, Object to) throws IOException {
        return getObjectUrl(from, to, true);
    }
    
    protected String getObjectUrl(Object from, Object to, boolean withFragment) throws IOException {
        Module module = getModule(from);
        URI fromUrl = getAbsoluteObjectUrl(from);
        URI toUrl = getAbsoluteObjectUrl(to);
        String result = relativize(module, fromUrl, toUrl).toString();
        if (withFragment
                && to instanceof Package 
                && isRootPackage(module, (Package)to)) {
            result += "#section-package";
        }
        return result;
    }
    
    protected String getResourceUrl(Object from, String to) throws IOException {
        Module module = getModule(from);
        URI fromUrl = getAbsoluteObjectUrl(from);
        URI toUrl = getBaseUrl(module).resolve(".resources/" + to);
        String result = relativize(module, fromUrl, toUrl).toString();
        return result;
    }
    
    /**
     * Gets a URL for the source file containing the given thing
     * @param from Where the link is relative to
     * @param modPkgOrDecl e.g. Module, Package or Declaration
     * @return A (relative) URL, or null if no source file exists (e.g. for a
     * package or a module without a descriptor)
     * @throws IOException 
     */
    protected String getSrcUrl(Object from, Object modPkgOrDecl) throws IOException {
        URI fromUrl = getAbsoluteObjectUrl(from);
        Module module = getModule(from);
        Package pkg;
        String filename;
        if (modPkgOrDecl instanceof Element) {
            Unit unit = ((Element)modPkgOrDecl).getUnit();
            pkg = unit.getPackage();
            filename = unit.getFilename();
        } else if (modPkgOrDecl instanceof Package) {
            pkg = (Package)modPkgOrDecl;
            filename = "package.ceylon";
        } else if (modPkgOrDecl instanceof Module) {
            Module moduleDecl = (Module)modPkgOrDecl;
            String pkgName;
            if(moduleDecl.isDefault())
                pkgName = "";
            else
                pkgName = moduleDecl.getNameAsString();
            pkg = moduleDecl.getPackage(pkgName);
            filename = "module.ceylon";
        } else {
            throw new RuntimeException(CeylondMessages.msg("error.unexpected", modPkgOrDecl));
        }

        File srcFile = new File(getFolder(pkg), filename + ".html").getCanonicalFile();
        String result;
        if (srcFile.exists()) {
            URI url = srcFile.toURI();
            result = relativize(module, fromUrl, url).toString();
        } else {
            result = null;
        }
        return result;
    }
    
    /**
     * Returns the starting and ending line number of the given declaration
     * @param decl The declaration
     * @return [start, end]
     */
    int[] getDeclarationSrcLocation(Declaration decl) {
        Node node = this.sourceLocations.get(decl);
        if(node == null)
            return null;
        return new int[]{node.getToken().getLine(), node.getEndToken().getLine()};
    }
    
    protected Module getCurrentModule() {
        return currentModule;
    }
    
}