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

import java.util.Collections;
import java.util.List;

import com.redhat.ceylon.cmr.api.ArtifactContext;
import com.redhat.ceylon.cmr.api.RepositoryManager;
import com.redhat.ceylon.common.Backend;
import com.redhat.ceylon.common.log.Logger;
import com.redhat.ceylon.common.tools.ModuleSpec;
import com.redhat.ceylon.compiler.java.util.Util;
import com.redhat.ceylon.compiler.typechecker.context.Context;
import com.redhat.ceylon.model.cmr.ArtifactResult;
import com.redhat.ceylon.model.loader.AbstractModelLoader;
import com.redhat.ceylon.model.loader.impl.reflect.model.ReflectionModule;
import com.redhat.ceylon.model.loader.impl.reflect.model.ReflectionModuleManager;
import com.redhat.ceylon.model.loader.mirror.ClassMirror;
import com.redhat.ceylon.model.loader.model.LazyModule;
import com.redhat.ceylon.model.loader.model.LazyPackage;
import com.redhat.ceylon.model.typechecker.model.Module;
import com.redhat.ceylon.model.typechecker.model.Modules;
import com.redhat.ceylon.model.typechecker.model.Package;

public class CeylonDocModuleManager extends ReflectionModuleManager {

    private List<ModuleSpec> modulesSpecs;
    private CeylonDocTool tool;
    private RepositoryManager outputRepositoryManager;
    private boolean bootstrapCeylon;

    public CeylonDocModuleManager(CeylonDocTool tool, Context context, List<ModuleSpec> modules, RepositoryManager outputRepositoryManager, boolean bootstrapCeylon, Logger log) {
        super();
        this.outputRepositoryManager = outputRepositoryManager;
        this.modulesSpecs = modules;
        this.tool = tool;
        this.bootstrapCeylon = bootstrapCeylon;
    }

    @Override
    public boolean isModuleLoadedFromSource(String moduleName) {
        for(ModuleSpec spec : modulesSpecs){
            if(spec.getName().equals(moduleName))
                return true;
        }
        return false;
    }
    
    @Override
    public boolean supportsBackend(Backend backend) {
        return backend == Backend.None || backend == Backend.Java;
    }

    @Override
    protected AbstractModelLoader createModelLoader(Modules modules) {
        return new CeylonDocModelLoader(this, modules, tool, bootstrapCeylon){
            @Override
            protected boolean isLoadedFromSource(String className) {
                return tool.getCompiledClasses().contains(className);
            }
            
            @Override
            public ClassMirror lookupNewClassMirror(Module module, String name) {
                // don't load it from class if we are compiling it
                if(tool.getCompiledClasses().contains(name)){
                    logVerbose("Not loading "+name+" from class because we are typechecking them");
                    return null;
                }
                return super.lookupNewClassMirror(module, name);
            }
            @Override
            protected void logError(String message) {
                log.error(message);
            }
            @Override
            protected void logVerbose(String message) {
                log.debug(message);
            }
            @Override
            protected void logWarning(String message) {
                log.warning(message);
            }
        };
    }

    @Override
    public Package createPackage(String pkgName, Module module) {
        // never create a lazy package for ceylon.language when we're documenting it
        if((pkgName.equals(AbstractModelLoader.CEYLON_LANGUAGE)
                || pkgName.startsWith(AbstractModelLoader.CEYLON_LANGUAGE+"."))
            && isModuleLoadedFromSource(AbstractModelLoader.CEYLON_LANGUAGE))
            return super.createPackage(pkgName, module);
        final Package pkg = new LazyPackage(getModelLoader());
        List<String> name = pkgName.isEmpty() ? Collections.<String>emptyList() : splitModuleName(pkgName); 
        pkg.setName(name);
        if (module != null) {
            module.getPackages().add(pkg);
            pkg.setModule(module);
        }
        return pkg;
    }

    @Override
    protected Module createModule(List<String> moduleName, String version) {
        String name = Util.getName(moduleName);
        // never create a reflection module for ceylon.language when we're documenting it
        Module module;
        if(name.equals(AbstractModelLoader.CEYLON_LANGUAGE) 
                && isModuleLoadedFromSource(AbstractModelLoader.CEYLON_LANGUAGE))
            module = new Module();
        else
            module = new ReflectionModule(this);
        module.setName(moduleName);
        module.setVersion(version);
        if(module instanceof ReflectionModule)
            setupIfJDKModule((LazyModule) module);
        return module;
    }
    
    @Override
    public void modulesVisited() {
        // this is very important!
        super.modulesVisited();
        for(Module module : getModules().getListOfModules()){
            if(isModuleLoadedFromSource(module.getNameAsString())){
                addOutputModuleToClassPath(module);
            }
        }
    }

    private void addOutputModuleToClassPath(Module module) {
        ArtifactContext ctx = new ArtifactContext(module.getNameAsString(), module.getVersion(), ArtifactContext.CAR);
        ArtifactResult result = outputRepositoryManager.getArtifactResult(ctx);
        if(result != null)
            getModelLoader().addModuleToClassPath(module, result);
    }
}
