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
package com.redhat.ceylon.compiler.java.test.cmr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticListener;
import javax.tools.FileObject;
import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Test;

import com.redhat.ceylon.common.FileUtil;
import com.redhat.ceylon.common.Versions;
import com.redhat.ceylon.common.config.Repositories;
import com.redhat.ceylon.compiler.java.test.CompilerError;
import com.redhat.ceylon.compiler.java.test.CompilerTests;
import com.redhat.ceylon.compiler.java.test.ErrorCollector;
import com.redhat.ceylon.compiler.java.tools.CeyloncTaskImpl;
import com.redhat.ceylon.compiler.java.tools.JarEntryManifestFileObject.OsgiManifest;
import com.redhat.ceylon.compiler.java.tools.LanguageCompiler;
import com.redhat.ceylon.compiler.java.util.Util;
import com.redhat.ceylon.compiler.typechecker.context.Context;
import com.redhat.ceylon.model.cmr.JDKUtils;
import com.redhat.ceylon.model.typechecker.model.Module;
import com.redhat.ceylon.model.typechecker.model.ModuleImport;
import com.redhat.ceylon.model.typechecker.model.Modules;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;

public class CMRTests extends CompilerTests {
    
    //
    // Modules
    
    @Test
    public void testMdlByName() throws IOException{
        List<String> options = new LinkedList<String>();
        options.add("-src");
        options.add(getPackagePath()+"/modules/byName");
        options.addAll(defaultOptions);
        CeyloncTaskImpl task = getCompilerTask(options, 
                null,
                Arrays.asList("default", "mod"));
        Boolean ret = task.call();
        assertTrue(ret);

        File carFile = getModuleArchive("default", null);
        assertTrue(carFile.exists());

        JarFile car = new JarFile(carFile);

        ZipEntry moduleClass = car.getEntry("def/Foo.class");
        assertNotNull(moduleClass);
        ZipEntry moduleClassDir = car.getEntry("def/");
        assertNotNull(moduleClassDir);
        assertTrue(moduleClassDir.isDirectory());
        
        car.close();

        carFile = getModuleArchive("mod", "1");
        assertTrue(carFile.exists());

        car = new JarFile(carFile);

        moduleClass = car.getEntry("mod/$module_.class");
        assertNotNull(moduleClass);
        moduleClassDir = car.getEntry("mod/");
        assertNotNull(moduleClassDir);
        assertTrue(moduleClassDir.isDirectory());
        
        car.close();
    }

    @Test
    public void testMdlEndsWithJava() throws IOException{
        List<String> options = new LinkedList<String>();
        options.add("-src");
        options.add(dir);
        options.addAll(defaultOptions);
        CeyloncTaskImpl task = getCompilerTask(options, 
                null,
                Arrays.asList("com.redhat.ceylon.compiler.java.test.cmr.modules.java"));
        Boolean ret = task.call();
        assertTrue(ret);
    }

    @Test
    public void testMdlModuleDefault() throws IOException{
        compile("modules/def/CeylonClass.ceylon");
        
        File carFile = getModuleArchive("default", null);
        assertTrue(carFile.exists());

        JarFile car = new JarFile(carFile);

        ZipEntry moduleClass = car.getEntry("com/redhat/ceylon/compiler/java/test/cmr/modules/def/CeylonClass.class");
        assertNotNull(moduleClass);
        car.close();
    }

    @Test
    public void testMdlModuleDefaultJavaFile() throws IOException{
        compile("modules/def/JavaClass.java");
        
        File carFile = getModuleArchive("default", null);
        assertTrue(carFile.exists());

        JarFile car = new JarFile(carFile);

        ZipEntry moduleClass = car.getEntry("com/redhat/ceylon/compiler/java/test/cmr/modules/def/JavaClass.class");
        assertNotNull(moduleClass);
        car.close();
    }

    @Test
    public void testMdlModuleDefaultIncremental() throws IOException{
        compile("modules/def/A.ceylon");
        compile("modules/def/RequiresA.ceylon");
    }

    @Test
    public void testMdlModuleIncremental() throws IOException{
        compile("modules/incremental/A.ceylon", "modules/incremental/BUsesA.ceylon", "modules/incremental/UsesB.ceylon");
        compile("modules/incremental/A.ceylon", "modules/incremental/UsesB.ceylon");
    }

    @Test
    public void testMdlModuleDefaultIncrementalNoPackage() throws IOException{
        List<String> options = new LinkedList<String>();
        options.add("-src");
        options.add(getPackagePath()+"/modules/def");
        options.addAll(defaultOptions);
        CeyloncTaskImpl task = getCompilerTask(options, 
                null,
                Collections.<String>emptyList(),
                "modules/def/A.ceylon");
        Boolean ret = task.call();
        assertTrue(ret);

        task = getCompilerTask(options, 
                null,
                Collections.<String>emptyList(),
                "modules/def/RequiresA.ceylon");
        ret = task.call();
        assertTrue(ret);
}

    @Test
    public void testMdlModuleOnlyInOutputRepo() throws IOException {
        File carFile = getModuleArchive("com.redhat.ceylon.compiler.java.test.cmr.modules.single", "6.6.6");
        assertFalse(carFile.exists());

        File carFileInCache = getModuleArchive("com.redhat.ceylon.compiler.java.test.cmr.modules.single", "6.6.6", cacheDir);
        if(carFileInCache.exists())
            carFileInCache.delete();
        
        compile("modules/single/module.ceylon");

        // make sure it was created in the output repo
        assertTrue(carFile.exists());
        // make sure it wasn't created in the cache repo
        assertFalse(carFileInCache.exists());
    }

    @Test
    public void testMdlModuleNotLoadedFromHomeRepo() throws IOException {
        File carFile = getModuleArchive("a", "1.0");
        assertFalse(carFile.exists());

        // clean up the home repo if required
        String homeRepo = FileUtil.getUserDir().getCanonicalPath();
        File carFileInHomeRepo = getModuleArchive("a", "1.0", homeRepo);
        if(carFileInHomeRepo.exists())
            carFileInHomeRepo.delete();

        // put a broken one in the home repo
        compileModuleFromSourceFolder("a", "home_repo/a_broken", homeRepo);
        assertTrue(carFileInHomeRepo.exists());

        // the good one in the default output repo
        compileModuleFromSourceFolder("a", "home_repo/a_working", null);
        assertTrue(carFile.exists());

        // now compile the dependent module
        compileModuleFromSourceFolder("b", "home_repo/b", null);

        // make sure it was created in the output repo
        assertTrue(carFile.exists());
    }

    @Test
    public void testMdlModuleNotLoadedFromCache() throws IOException {
        File carFile = getModuleArchive("b", "1.0");
        assertFalse(carFile.exists());

        // clean up the cache repo if required
        File carFileInHomeRepo = getModuleArchive("a", "1.0", cacheDir);
        if(carFileInHomeRepo.exists())
            carFileInHomeRepo.delete();

        // clean up the working repo if required
        String workingRepo = destDir + "-working";
        File carFileInWorkingRepo = getModuleArchive("a", "1.0", workingRepo);
        if(carFileInWorkingRepo.exists())
            carFileInWorkingRepo.delete();
        assertFalse(carFileInWorkingRepo.exists());
        
        // put a broken one in the cache repo
        compileModuleFromSourceFolder("a", "home_repo/a_broken", cacheDir);
        assertTrue(carFileInHomeRepo.exists());

        // the good one in a local repo
        compileModuleFromSourceFolder("a", "home_repo/a_working", workingRepo);
        assertTrue(carFileInWorkingRepo.exists());

        // now compile the dependent module by using that repo
        compileModuleFromSourceFolder("b", "home_repo/b", null, workingRepo);

        // make sure it was created in the output repo
        assertTrue(carFile.exists());
    }
    
    private void compileModuleFromSourceFolder(String module, String srcFolder, String outFolder, String... repos) {
        List<String> options = new LinkedList<String>();
        options.add("-src");
        options.add(getPackagePath()+"/modules/"+srcFolder);
        if(outFolder != null){
            options.add("-out");
            options.add(outFolder);
        }else{
            options.addAll(defaultOptions);
        }
        for(String repo : repos){
            options.add("-rep");
            options.add(repo);
        }
        CeyloncTaskImpl task = getCompilerTask(options, 
                null,
                Arrays.asList(module));
        Boolean ret = task.call();
        assertTrue(ret);
    }

    @Test
    public void testMdlWithCeylonImport() throws IOException{
        compile("modules/ceylon_import/module.ceylon", "modules/ceylon_import/ImportCeylonLanguage.ceylon");
    }
    
    @Test
    public void testMdlWithCommonPrefix() throws IOException{
        compile("modules/depend/prefix/module.ceylon");
        // This is heisenbug https://github.com/ceylon/ceylon-compiler/issues/460 and for some
        // reason it only happens _sometimes_, hence the repeats
        compile("modules/depend/prefix_suffix/module.ceylon");
        compile("modules/depend/prefix_suffix/module.ceylon");
        compile("modules/depend/prefix_suffix/module.ceylon");
        compile("modules/depend/prefix_suffix/module.ceylon");
        compile("modules/depend/prefix_suffix/module.ceylon");
    }
    
    @Test
    public void testMdlModuleFromCompiledModule() throws IOException{
        compile("modules/single/module.ceylon");
        
        File carFile = getModuleArchive("com.redhat.ceylon.compiler.java.test.cmr.modules.single", "6.6.6");
        assertTrue(carFile.exists());

        JarFile car = new JarFile(carFile);
        // just to be sure
        ZipEntry bogusEntry = car.getEntry("com/redhat/ceylon/compiler/java/test/cmr/modules/single/BOGUS");
        assertNull(bogusEntry);

        ZipEntry moduleClass = car.getEntry("com/redhat/ceylon/compiler/java/test/cmr/modules/single/$module_.class");
        assertNotNull(moduleClass);
        car.close();

        compile("modules/single/subpackage/Subpackage.ceylon");

        // MUST reopen it
        car = new JarFile(carFile);

        ZipEntry subpackageClass = car.getEntry("com/redhat/ceylon/compiler/java/test/cmr/modules/single/subpackage/Subpackage.class");
        assertNotNull(subpackageClass);

        car.close();
    }

    @Test
    public void testMdlCarWithInvalidSHA1() throws IOException{
        compile("modules/single/module.ceylon");
        
        File carFile = getModuleArchive("com.redhat.ceylon.compiler.java.test.cmr.modules.single", "6.6.6");
        assertTrue(carFile.exists());

        JarFile car = new JarFile(carFile);
        // just to be sure
        ZipEntry moduleClass = car.getEntry("com/redhat/ceylon/compiler/java/test/cmr/modules/single/$module_.class");
        assertNotNull(moduleClass);
        car.close();

        // now let's break the SHA1
        File shaFile = getArchiveName("com.redhat.ceylon.compiler.java.test.cmr.modules.single", "6.6.6", destDir, "car.sha1");
        Writer w = new FileWriter(shaFile);
        w.write("fubar");
        w.flush();
        w.close();
        
        // now try to compile the subpackage with a broken SHA1
        String carName = "/com/redhat/ceylon/compiler/java/test/cmr/modules/single/6.6.6/com.redhat.ceylon.compiler.java.test.cmr.modules.single-6.6.6.car";
        carName = carName.replace('/', File.separatorChar);
        assertErrors("modules/single/subpackage/Subpackage", 
                new CompilerError(-1, "Module car " + carName
                        + " obtained from repository " + (new File(destDir).getAbsolutePath()) 
                        + " has an invalid SHA1 signature: you need to remove it and rebuild the archive, since it may be corrupted."));
    }

    @Test
    public void testMdlCompilerGeneratesModuleForValidUnits() throws IOException{
        CeyloncTaskImpl compilerTask = getCompilerTask("modules/single/module.ceylon", "modules/single/Correct.ceylon", "modules/single/Invalid.ceylon");
        Boolean success = compilerTask.call();
        assertFalse(success);
        
        File carFile = getModuleArchive("com.redhat.ceylon.compiler.java.test.cmr.modules.single", "6.6.6");
        assertTrue(carFile.exists());

        JarFile car = new JarFile(carFile);

        ZipEntry moduleClass = car.getEntry("com/redhat/ceylon/compiler/java/test/cmr/modules/single/$module_.class");
        assertNotNull(moduleClass);

        ZipEntry correctClass = car.getEntry("com/redhat/ceylon/compiler/java/test/cmr/modules/single/Correct.class");
        assertNotNull(correctClass);

        ZipEntry invalidClass = car.getEntry("com/redhat/ceylon/compiler/java/test/cmr/modules/single/Invalid.class");
        assertNull(invalidClass);
        
        car.close();
    }

    @Test
    public void testMdlInterdepModule(){
        // first compile it all from source
        compile("modules/interdep/a/module.ceylon", "modules/interdep/a/package.ceylon", "modules/interdep/a/b.ceylon", "modules/interdep/a/A.ceylon",
                "modules/interdep/b/module.ceylon", "modules/interdep/b/package.ceylon", "modules/interdep/b/a.ceylon", "modules/interdep/b/B.ceylon");
        
        File carFile = getModuleArchive("com.redhat.ceylon.compiler.java.test.cmr.modules.interdep.a", "6.6.6");
        assertTrue(carFile.exists());

        carFile = getModuleArchive("com.redhat.ceylon.compiler.java.test.cmr.modules.interdep.b", "6.6.6");
        assertTrue(carFile.exists());
        
        // then try to compile only one module (the other being loaded from its car) 
        compile("modules/interdep/a/module.ceylon", "modules/interdep/a/b.ceylon", "modules/interdep/a/A.ceylon");
    }

    @Test
    public void testMdlDependentModule(){
        // Compile only the first module 
        compile("modules/depend/a/module.ceylon", "modules/depend/a/package.ceylon", "modules/depend/a/A.ceylon");
        
        File carFile = getModuleArchive("com.redhat.ceylon.compiler.java.test.cmr.modules.depend.a", "6.6.6");
        assertTrue(carFile.exists());

        // then try to compile only one module (the other being loaded from its car) 
        compile("modules/depend/b/module.ceylon", "modules/depend/b/package.ceylon", "modules/depend/b/a.ceylon", "modules/depend/b/aWildcard.ceylon", "modules/depend/b/B.ceylon");

        carFile = getModuleArchive("com.redhat.ceylon.compiler.java.test.cmr.modules.depend.b", "6.6.6");
        assertTrue(carFile.exists());

        // and then the last one (the other 2 being loaded from their cars) that uses the first one transitively
        compile("modules/depend/c/module.ceylon", "modules/depend/c/a.ceylon", "modules/depend/c/b.ceylon");

        carFile = getModuleArchive("com.redhat.ceylon.compiler.java.test.cmr.modules.depend.c", "6.6.6");
        assertTrue(carFile.exists());
    }

    @Test
    public void testMdlImplicitDependentModule(){
        // Compile only the first module 
        compile("modules/implicit/a/module.ceylon", "modules/implicit/a/package.ceylon", "modules/implicit/a/A.ceylon",
                "modules/implicit/b/module.ceylon", "modules/implicit/b/package.ceylon", "modules/implicit/b/B.ceylon", "modules/implicit/b/B2.ceylon",
                "modules/implicit/c/module.ceylon", "modules/implicit/c/package.ceylon", "modules/implicit/c/c.ceylon");
        
        // Dependencies:
        //
        // c.ceylon--> B2.ceylon
        //         |
        //         '-> B.ceylon  --> A.ceylon

        // Successfull tests :
        
        compile("modules/implicit/c/c.ceylon");
        compile("modules/implicit/b/B.ceylon", "modules/implicit/c/c.ceylon");
        compile("modules/implicit/b/B2.ceylon", "modules/implicit/c/c.ceylon");
        
        // Failing tests :
        
        Boolean success1 = getCompilerTask("modules/implicit/c/c.ceylon", "modules/implicit/b/B.ceylon").call();
        // => B.ceylon : package not found in dependent modules: com.redhat.ceylon.compiler.java.test.cmr.module.implicit.a
        Boolean success2 = getCompilerTask("modules/implicit/c/c.ceylon", "modules/implicit/b/B2.ceylon").call();
        // => c.ceylon : TypeVisitor caused an exception visiting Import node: com.sun.tools.javac.code.Symbol$CompletionFailure: class file for com.redhat.ceylon.compiler.test.cmr.module.implicit.a.A not found at unknown

        Assert.assertTrue(success1 && success2);
    }

    private void copy(File source, File dest) throws IOException {
        InputStream inputStream = new FileInputStream(source);
        OutputStream outputStream = new FileOutputStream(dest); 
        byte[] buffer = new byte[4096];
        int read;
        while((read = inputStream.read(buffer)) != -1){
            outputStream.write(buffer, 0, read);
        }
        inputStream.close();
        outputStream.close();
    }
    
    @Test
    public void testMdlSuppressObsoleteClasses() throws IOException{
        File sourceFile = new File(getPackagePath(), "modules/single/SuppressClass.ceylon");

        copy(new File(getPackagePath(), "modules/single/SuppressClass_1.ceylon"), sourceFile);
        CeyloncTaskImpl compilerTask = getCompilerTask("modules/single/module.ceylon", "modules/single/SuppressClass.ceylon");
        Boolean success = compilerTask.call();
        assertTrue(success);

        File carFile = getModuleArchive("com.redhat.ceylon.compiler.java.test.cmr.modules.single", "6.6.6");
        assertTrue(carFile.exists());
        ZipFile car = new ZipFile(carFile);
        ZipEntry oneClass = car.getEntry("com/redhat/ceylon/compiler/java/test/cmr/modules/single/One.class");
        assertNotNull(oneClass);
        ZipEntry twoClass = car.getEntry("com/redhat/ceylon/compiler/java/test/cmr/modules/single/Two.class");
        assertNotNull(twoClass);
        car.close();

        copy(new File(getPackagePath(), "modules/single/SuppressClass_2.ceylon"), sourceFile);
        compilerTask = getCompilerTask("modules/single/module.ceylon", "modules/single/SuppressClass.ceylon");
        success = compilerTask.call();
        assertTrue(success);
        
        carFile = getModuleArchive("com.redhat.ceylon.compiler.java.test.cmr.modules.single", "6.6.6");
        assertTrue(carFile.exists());
        car = new ZipFile(carFile);
        oneClass = car.getEntry("com/redhat/ceylon/compiler/java/test/cmr/modules/single/One.class");
        assertNotNull(oneClass);
        twoClass = car.getEntry("com/redhat/ceylon/compiler/java/test/cmr/modules/single/Two.class");
        assertNull(twoClass);
        car.close();
        
        sourceFile.delete();
    }

    
    @Test
    public void testMdlMultipleRepos(){
        cleanCars("build/ceylon-cars-a");
        cleanCars("build/ceylon-cars-b");
        cleanCars("build/ceylon-cars-c");
        
        // Compile the first module in its own repo 
        File repoA = new File("build/ceylon-cars-a");
        repoA.mkdirs();
        Boolean result = getCompilerTask(Arrays.asList("-out", repoA.getPath()),
                "modules/depend/a/module.ceylon", "modules/depend/a/package.ceylon", "modules/depend/a/A.ceylon").call();
        Assert.assertEquals(Boolean.TRUE, result);
        
        File carFile = getModuleArchive("com.redhat.ceylon.compiler.java.test.cmr.modules.depend.a", "6.6.6", repoA.getPath());
        assertTrue(carFile.exists());

        // make another repo for the second module
        File repoB = new File("build/ceylon-cars-b");
        repoB.mkdirs();

        // then try to compile only one module (the other being loaded from its car) 
        result = getCompilerTask(Arrays.asList("-out", repoB.getPath(), "-rep", repoA.getPath()),
                "modules/depend/b/module.ceylon", "modules/depend/b/package.ceylon", "modules/depend/b/a.ceylon", "modules/depend/b/B.ceylon").call();
        Assert.assertEquals(Boolean.TRUE, result);

        carFile = getModuleArchive("com.redhat.ceylon.compiler.java.test.cmr.modules.depend.b", "6.6.6", repoB.getPath());
        assertTrue(carFile.exists());

        // make another repo for the third module
        File repoC = new File("build/ceylon-cars-c");
        repoC.mkdirs();

        // then try to compile only one module (the others being loaded from their car) 
        result = getCompilerTask(Arrays.asList("-out", repoC.getPath(), 
                "-rep", repoA.getPath(), "-rep", repoB.getPath()),
                "modules/depend/c/module.ceylon", "modules/depend/c/a.ceylon", "modules/depend/c/b.ceylon").call();
        Assert.assertEquals(Boolean.TRUE, result);

        carFile = getModuleArchive("com.redhat.ceylon.compiler.java.test.cmr.modules.depend.c", "6.6.6", repoC.getPath());
        assertTrue(carFile.exists());
    }

    @Test
    public void testMdlJarDependency() throws IOException{
        // compile our java class
        File classesOutputFolder = new File(destDir+"-jar-classes");
        cleanCars(classesOutputFolder.getPath());
        classesOutputFolder.mkdirs();

        File jarOutputFolder = new File(destDir+"-jar");
        cleanCars(jarOutputFolder.getPath());
        jarOutputFolder.mkdirs();

        compileJavaModule(jarOutputFolder, classesOutputFolder, moduleName+".modules.jarDependency.java", "1.0",
                moduleName.replace('.', '/')+"/modules/jarDependency/java/JavaDependency.java");
        
        // Try to compile the ceylon module
        CeyloncTaskImpl ceylonTask = getCompilerTask(Arrays.asList("-out", destDir, "-rep", jarOutputFolder.getPath()), 
                (DiagnosticListener<? super FileObject>)null, 
                "modules/jarDependency/ceylon/module.ceylon", "modules/jarDependency/ceylon/Foo.ceylon");
        assertEquals(Boolean.TRUE, ceylonTask.call());
    }

    private void compileJavaModule(File jarOutputFolder, File classesOutputFolder,
            String moduleName, String moduleVersion, String... sourceFileNames) throws IOException {
        compileJavaModule(jarOutputFolder, classesOutputFolder,
                          moduleName, moduleVersion, new File(dir), new File[0], sourceFileNames);
    }
    
    private void compileJavaModule(File jarOutputFolder, File classesOutputFolder,
            String moduleName, String moduleVersion, 
            File sourceFolder, 
            File[] extraClassPath,
            String... sourceFileNames) throws IOException {
        JavaCompiler javaCompiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull("Missing Java compiler, this test is probably being run with a JRE instead of a JDK!", javaCompiler);
        StandardJavaFileManager fileManager = javaCompiler.getStandardFileManager(null, null, null);
        Set<String> sourceDirectories = new HashSet<String>();
        File[] javaSourceFiles = new File[sourceFileNames.length];
        for (int i = 0; i < javaSourceFiles.length; i++) {
            javaSourceFiles[i] = new File(sourceFolder, sourceFileNames[i]);
            String sfn = sourceFileNames[i].replace(File.separatorChar, '/');
            int p = sfn.lastIndexOf('/');
            String sourceDir = sfn.substring(0, p);
            sourceDirectories.add(sourceDir);
        }
        Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjects(javaSourceFiles);
        StringBuilder cp = new StringBuilder();
        for (int i = 0; i < extraClassPath.length; i++) {
            if(i > 0)
                cp.append(File.pathSeparator);
            cp.append(extraClassPath[i]);
        }
        CompilationTask task = javaCompiler.getTask(null, null, null, Arrays.asList("-d", classesOutputFolder.getPath(), 
                "-cp", cp.toString(),
                "-sourcepath", sourceFolder.getPath()), null, compilationUnits);
        assertEquals(Boolean.TRUE, task.call());
        
        File jarFolder = new File(jarOutputFolder, moduleName.replace('.', File.separatorChar)+File.separatorChar+moduleVersion);
        jarFolder.mkdirs();
        File jarFile = new File(jarFolder, moduleName+"-"+moduleVersion+".jar");
        // now jar it up
        JarOutputStream outputStream = new JarOutputStream(new FileOutputStream(jarFile));
        for(String sourceFileName : sourceFileNames){
            String classFileName = sourceFileName.substring(0, sourceFileName.length()-5)+".class";
            ZipEntry entry = new ZipEntry(classFileName);
            outputStream.putNextEntry(entry);

            File classFile = new File(classesOutputFolder, classFileName);
            FileInputStream inputStream = new FileInputStream(classFile);
            Util.copy(inputStream, outputStream);
            inputStream.close();
            outputStream.flush();
        }
        outputStream.close();
        for(String sourceDir : sourceDirectories){
            File module = null;
            String sourceName = "module.properties";
            File properties = new File(sourceFolder, sourceDir + File.separator + sourceName);
            if(properties.exists()){
                module = properties;
            }else{
                sourceName = "module.xml";
                properties = new File(sourceFolder, sourceDir + File.separator + sourceName);
                if(properties.exists()){
                    module = properties;
                }
            }
            if(module != null){
                File moduleFile = new File(sourceFolder, sourceDir + File.separator + sourceName);
                FileInputStream inputStream = new FileInputStream(moduleFile);
                FileOutputStream moduleOutputStream = new FileOutputStream(new File(jarFolder, sourceName));
                Util.copy(inputStream, moduleOutputStream);
                inputStream.close();
                moduleOutputStream.flush();
                moduleOutputStream.close();
            }
        }
    }
    
    @Test
    public void testMdlAetherDependencyDefault() throws IOException{
        // Try to compile the ceylon module
        CeyloncTaskImpl ceylonTask = getCompilerTask(Arrays.asList("-out", destDir, "-rep", "aether", "-verbose:cmr"), 
                (DiagnosticListener<? super FileObject>)null, 
                "modules/aetherdefault/module.ceylon", "modules/aetherdefault/foo.ceylon");
        assertEquals(Boolean.TRUE, ceylonTask.call());
        // We're assuming a standard Maven configuration here!
        File camelJar = new File(System.getProperty("user.home"), ".m2/repository/org/apache/camel/camel-core/2.9.2/camel-core-2.9.2.jar");
        assertTrue(camelJar.exists());
    }

    @Test
    public void testMdlImplicitAetherDependencyDefault() throws IOException{
        // Try to compile the ceylon module
        CeyloncTaskImpl ceylonTask = getCompilerTask(Arrays.asList("-out", destDir, "-verbose:cmr"), 
                (DiagnosticListener<? super FileObject>)null, 
                "modules/aetherdefault/module.ceylon", "modules/aetherdefault/foo.ceylon");
        assertEquals(Boolean.TRUE, ceylonTask.call());
        // We're assuming a standard Maven configuration here!
        File camelJar = new File(System.getProperty("user.home"), ".m2/repository/org/apache/camel/camel-core/2.9.2/camel-core-2.9.2.jar");
        assertTrue(camelJar.exists());
    }

    @Test
    public void testMdlAetherIgnoreRecursiveDependencies() throws IOException{
        // Try to compile the ceylon module
        CeyloncTaskImpl ceylonTask = getCompilerTask(Arrays.asList("-out", destDir, "-rep", "aether", "-verbose:cmr"), 
                (DiagnosticListener<? super FileObject>)null, 
                "modules/aetherIgnoreDependencies/module.ceylon", "modules/aetherIgnoreDependencies/foo.ceylon");
        assertEquals(Boolean.TRUE, ceylonTask.call());
        // We're assuming a standard Maven configuration here!
        File camelJar = new File(System.getProperty("user.home"), ".m2/repository/org/apache/camel/camel-core/2.9.4/camel-core-2.9.4.jar");
        assertTrue(camelJar.exists());
        File camelJettyJar = new File(System.getProperty("user.home"), ".m2/repository/org/apache/camel/camel-jetty/2.9.4/camel-jetty-2.9.4.jar");
        assertTrue(camelJettyJar.exists());
    }

    @Test
    public void testMdlAetherDependencyCustom() throws IOException{
        // Try to compile the ceylon module
        File settingsFile = new File(getPackagePath(), "modules/aethercustom/settings.xml");
        CeyloncTaskImpl ceylonTask = getCompilerTask(Arrays.asList("-out", destDir, "-rep", "aether:" + settingsFile.getAbsolutePath(), "-verbose:cmr"), 
                (DiagnosticListener<? super FileObject>)null, 
                "modules/aethercustom/module.ceylon", "modules/aethercustom/foo.ceylon");
        assertEquals(Boolean.TRUE, ceylonTask.call());
        File restletJar = new File("build/test-cars/cmr-repository", "org/restlet/org.restlet/1.1.10/org.restlet-1.1.10.jar");
        assertTrue(restletJar.exists());
    }

    @Test
    public void testMdlAetherMissingDependencies() throws IOException{
        CompilerError[] expectedErrors = new CompilerError[]{
        new CompilerError(6, "Error while loading the org.apache.camel:camel-jetty/2.9.4 module:\n"
                +"   Declaration 'org.apache.camel.component.http.HttpComponent' could not be found in module 'org.apache.camel:camel-jetty' or its imported modules"),
        new CompilerError(10, "argument must be assignable to parameter 'arg1' of 'addComponent' in 'DefaultCamelContext': 'JettyHttpComponent' is not assignable to 'Component?': Error while loading the org.apache.camel:camel-jetty/2.9.4 module:\n"+
                "   Declaration 'org.apache.camel.component.http.HttpComponent' could not be found in module 'org.apache.camel:camel-jetty' or its imported modules"),
        };

        ErrorCollector collector = new ErrorCollector();

        // Try to compile the ceylon module
        CeyloncTaskImpl ceylonTask = getCompilerTask(Arrays.asList("-out", destDir, "-rep", "aether"/*, "-verbose:cmr"*/), 
                collector, 
                "modules/bug1100/module.ceylon", "modules/bug1100/test.ceylon");
        assertEquals("Compilation failed", Boolean.FALSE, ceylonTask.call());

        TreeSet<CompilerError> actualErrors = collector.get(Diagnostic.Kind.ERROR);
        compareErrors(actualErrors, expectedErrors);
    }

    @Test
    public void testMdlAetherMissingDependenciesOverride() throws IOException{
        // Try to compile the ceylon module
        CeyloncTaskImpl ceylonTask = getCompilerTask(Arrays.asList("-out", destDir, 
                "-rep", "aether",
                "-overrides", getPackagePath()+"/modules/bug1100/overrides.xml"/*, "-verbose:cmr"*/), 
                "modules/bug1100/module.ceylon", "modules/bug1100/test.ceylon");
        assertEquals("Compilation failed", Boolean.TRUE, ceylonTask.call());
    }

    @Test
    public void testMdlDependenciesNoOverride() throws IOException{
        CeyloncTaskImpl ceylonTask = getCompilerTask(Arrays.asList("-out", destDir/*, "-verbose:cmr"*/), 
                "modules/overrides/module.ceylon", "modules/overrides/test.ceylon");
        assertEquals("Compilation failed", Boolean.TRUE, ceylonTask.call());
    }

    @Test
    public void testMdlDependenciesOverrideRemoveDep() throws IOException{
        CeyloncTaskImpl ceylonTask = getCompilerTask(Arrays.asList("-out", destDir, 
                "-overrides", getPackagePath()+"/modules/overrides/overridesRemoveJavaBase.xml"/*, "-verbose:cmr"*/), 
                "modules/overrides/module.ceylon", "modules/overrides/test.ceylon");
        assertEquals("Compilation failed", Boolean.TRUE, ceylonTask.call());
    }

    @Test
    public void testMdlAetherMissingDependencies2() throws IOException{
        // Try to compile the ceylon module
        CeyloncTaskImpl ceylonTask = getCompilerTask(Arrays.asList("-out", destDir, 
                "-rep", "aether"/*, "-verbose:cmr"*/), 
                "modules/bug1104/module.ceylon", "modules/bug1104/test.ceylon");
        assertEquals("Compilation failed", Boolean.TRUE, ceylonTask.call());
    }

    @Test
    public void testMdlCeylonAetherDuplicateImports() throws IOException{
        // Try to compile the ceylon module
        ErrorCollector collector = new ErrorCollector();
        CeyloncTaskImpl ceylonTask = getCompilerTask(Arrays.asList("-out", destDir, "-verbose:cmr"), 
                collector, 
                "modules/ceylonAetherDuplicateImports/module.ceylon", "modules/ceylonAetherDuplicateImports/foo.ceylon");
        assertEquals(Boolean.FALSE, ceylonTask.call());
        compareErrors(collector.get(Diagnostic.Kind.ERROR), 
                new CompilerError(23, "duplicate module import: 'org.apache.httpcomponents.httpclient'"),
                new CompilerError(25, "duplicate module import: 'org.apache.httpcomponents:httpclient'")
        );
    }

    @Test
    public void testMdlCeylonAetherDependencyConflict() throws IOException{
        // Try to compile the ceylon module
        CeyloncTaskImpl ceylonTask = getCompilerTask(Arrays.asList("-out", destDir, "-verbose:cmr"), 
                (DiagnosticListener<? super FileObject>)null, 
                "modules/ceylonAetherConflict2/module.ceylon");
        assertEquals(Boolean.TRUE, ceylonTask.call());

        ErrorCollector collector = new ErrorCollector();
        ceylonTask = getCompilerTask(Arrays.asList("-out", destDir, "-verbose:cmr"), 
                collector, 
                "modules/ceylonAetherConflict/module.ceylon", "modules/ceylonAetherConflict/foo.ceylon");
        assertEquals(Boolean.TRUE, ceylonTask.call());
        compareErrors(collector.get(Diagnostic.Kind.WARNING), 
                new CompilerError(Diagnostic.Kind.WARNING, null, 21, "source code imports two different versions of similar modules 'org.apache.httpcomponents.httpclient/4.3.2' and 'org.apache.httpcomponents:httpclient/4.3.3'"),
                new CompilerError(Diagnostic.Kind.WARNING, null, 21, "module (transitively) imports conflicting versions of similar dependencies 'org.apache.httpcomponents.httpclient/4.3.2' and 'org.apache.httpcomponents:httpclient/4.3.3'")
        );
    }

    @Ignore("It takes ages to download about 200 jars")
    @Test
    public void testMdlApacheSpark() throws Throwable{
        // initially run both without offline, then it's much faster
        
        CeyloncTaskImpl ceylonTask = getCompilerTask(Arrays.asList("-out", destDir, "-offline"/*, "-verbose:cmr"*/), 
                "modules/apachespark/module.ceylon", "modules/apachespark/test.ceylon");
        assertEquals("Compilation failed", Boolean.TRUE, ceylonTask.call());
        
        runInJBossModules("run", "com.redhat.ceylon.compiler.java.test.cmr.modules.apachespark/1", 
                Arrays.<String>asList("--flat-classpath", "--offline", 
                        "--maven-overrides", getPackagePath()+"/modules/apachespark/overrides.xml"));
    }

    @Test(expected = AssertionError.class)
    public void testMdlDependenciesFromMavenFail() throws Throwable{
        Assume.assumeTrue("Runs on JDK8", JDKUtils.jdk == JDKUtils.JDK.JDK8);
        CeyloncTaskImpl ceylonTask = getCompilerTask(Arrays.asList("-out", destDir/*, "-verbose:cmr"*/), 
                "modules/sparkframework/module.ceylon", "modules/sparkframework/test.ceylon");
        assertEquals("Compilation failed", Boolean.TRUE, ceylonTask.call());
        
        runInJBossModules("run", "com.redhat.ceylon.compiler.java.test.cmr.modules.sparkframework/1");
    }

    @Test
    public void testMdlDependenciesFromMavenAutoExport() throws Throwable{
        Assume.assumeTrue("Runs on JDK8", JDKUtils.jdk == JDKUtils.JDK.JDK8);
        CeyloncTaskImpl ceylonTask = getCompilerTask(Arrays.asList("-out", destDir/*, "-verbose:cmr"*/), 
                "modules/sparkframework/module.ceylon", "modules/sparkframework/test.ceylon");
        assertEquals("Compilation failed", Boolean.TRUE, ceylonTask.call());
        
        runInJBossModules("run", "com.redhat.ceylon.compiler.java.test.cmr.modules.sparkframework/1", 
                Arrays.asList("--auto-export-maven-dependencies"));
    }

    @Test
    public void testMdlDependenciesFromMavenFlatClasspath() throws Throwable{
        Assume.assumeTrue("Runs on JDK8", JDKUtils.jdk == JDKUtils.JDK.JDK8);
        CeyloncTaskImpl ceylonTask = getCompilerTask(Arrays.asList("-out", destDir/*, "-verbose:cmr"*/), 
                "modules/sparkframework/module.ceylon", "modules/sparkframework/test.ceylon");
        assertEquals("Compilation failed", Boolean.TRUE, ceylonTask.call());
        
        runInJBossModules("run", "com.redhat.ceylon.compiler.java.test.cmr.modules.sparkframework/1", 
                Arrays.asList("--flat-classpath", "--overrides", getPackagePath()+"/modules/sparkframework/overrides-log.xml"));
    }

    @Test
    public void testMdlDependenciesFromMavenWithOverrides() throws Throwable{
        Assume.assumeTrue("Runs on JDK8", JDKUtils.jdk == JDKUtils.JDK.JDK8);
        CeyloncTaskImpl ceylonTask = getCompilerTask(Arrays.asList("-out", destDir/*, "-verbose:cmr"*/), 
                "modules/sparkframework/module.ceylon", "modules/sparkframework/test.ceylon");
        assertEquals("Compilation failed", Boolean.TRUE, ceylonTask.call());
        
        runInJBossModules("run", "com.redhat.ceylon.compiler.java.test.cmr.modules.sparkframework/1", 
                Arrays.asList("--overrides", getPackagePath()+"/modules/sparkframework/overrides-fix.xml"));
    }

    @Test
    public void testMdlSourceArchive() throws IOException{
        File sourceArchiveFile = getSourceArchive("com.redhat.ceylon.compiler.java.test.cmr.modules.single", "6.6.6");
        sourceArchiveFile.delete();
        assertFalse(sourceArchiveFile.exists());

        // compile one file
        compile("modules/single/module.ceylon");

        // make sure it was created
        assertTrue(sourceArchiveFile.exists());

        JarFile sourceArchive = new JarFile(sourceArchiveFile);
        assertEquals(2, countEntries(sourceArchive));

        ZipEntry moduleClass = sourceArchive.getEntry("com/redhat/ceylon/compiler/java/test/cmr/modules/single/module.ceylon");
        assertNotNull(moduleClass);

        ZipEntry moduleClassDir = sourceArchive.getEntry("com/redhat/ceylon/compiler/java/test/cmr/modules/single/");
        assertNotNull(moduleClassDir);
        sourceArchive.close();

        // now compile another file
        compile("modules/single/subpackage/Subpackage.ceylon");

        // MUST reopen it
        sourceArchive = new JarFile(sourceArchiveFile);
        assertEquals(4, countEntries(sourceArchive));

        ZipEntry subpackageClass = sourceArchive.getEntry("com/redhat/ceylon/compiler/java/test/cmr/modules/single/subpackage/Subpackage.ceylon");
        assertNotNull(subpackageClass);
        ZipEntry subpackageClassDir = sourceArchive.getEntry("com/redhat/ceylon/compiler/java/test/cmr/modules/single/subpackage/");
        assertNotNull(subpackageClassDir);

        sourceArchive.close();
    }

    @Test
    public void testMdlMultipleVersionsOnSameCompilation(){
        // Compile module A/1
        Boolean result = getCompilerTask(Arrays.asList("-src", getPackagePath()+"/modules/multiversion/a1"),
                "modules/multiversion/a1/a/module.ceylon", "modules/multiversion/a1/a/package.ceylon", "modules/multiversion/a1/a/A.ceylon").call();
        Assert.assertEquals(Boolean.TRUE, result);
        
        ErrorCollector collector = new ErrorCollector();
        // Compile module A/2 with B importing A/1
        result = getCompilerTask(Arrays.asList("-src", getPackagePath()+"/modules/multiversion/a2"+File.pathSeparator+getPackagePath()+"/modules/multiversion/b"),
                collector,
                "modules/multiversion/a2/a/module.ceylon", "modules/multiversion/a2/a/package.ceylon", "modules/multiversion/a2/a/A.ceylon",
                "modules/multiversion/b/b/module.ceylon", "modules/multiversion/b/b/B.ceylon").call();
        Assert.assertEquals(Boolean.FALSE, result);
        
        compareErrors(collector.get(Diagnostic.Kind.ERROR), 
                new CompilerError(20, "source code imports two different versions of module 'a': version '1' and version '2'"));
    }

    @Test
    public void testMdlMultipleVersionsDuringImport(){
        // Compile module A/1
        Boolean result = getCompilerTask(Arrays.asList("-src", getPackagePath()+"/modules/multiversion/a1"),
                "modules/multiversion/a1/a/module.ceylon", "modules/multiversion/a1/a/package.ceylon", "modules/multiversion/a1/a/A.ceylon").call();
        Assert.assertEquals(Boolean.TRUE, result);

        // Compile module A/2
        result = getCompilerTask(Arrays.asList("-src", getPackagePath()+"/modules/multiversion/a2"),
                "modules/multiversion/a2/a/module.ceylon", "modules/multiversion/a2/a/package.ceylon", "modules/multiversion/a2/a/A.ceylon").call();
        Assert.assertEquals(Boolean.TRUE, result);

        ErrorCollector collector = new ErrorCollector();
        // Compile module cImportsATwice which imports both A/1 and A/2
        result = getCompilerTask(Arrays.asList("-src", getPackagePath()+"/modules/multiversion/c"),
                collector,
                "modules/multiversion/c/cImportsATwice/module.ceylon", "modules/multiversion/c/cImportsATwice/C.ceylon").call();
        Assert.assertEquals(Boolean.FALSE, result);
        
        compareErrors(collector.get(Diagnostic.Kind.ERROR), 
                new CompilerError(20, "module (transitively) imports conflicting versions of dependency 'a': version '1' and version '2'"),
                new CompilerError(20, "source code imports two different versions of module 'a': version '1' and version '2'"),
                new CompilerError(22, "duplicate module import: 'a'")
        );
    }

    @Test
    public void testMdlMultipleVersionsDuringDependencyImport(){
        // Compile module A/1
        Boolean result = getCompilerTask(Arrays.asList("-src", getPackagePath()+"/modules/multiversion/a1"),
                "modules/multiversion/a1/a/module.ceylon", "modules/multiversion/a1/a/package.ceylon", "modules/multiversion/a1/a/A.ceylon").call();
        Assert.assertEquals(Boolean.TRUE, result);

        // Compile module A/2
        result = getCompilerTask(Arrays.asList("-src", getPackagePath()+"/modules/multiversion/a2"),
                "modules/multiversion/a2/a/module.ceylon", "modules/multiversion/a2/a/package.ceylon", "modules/multiversion/a2/a/A.ceylon").call();
        Assert.assertEquals(Boolean.TRUE, result);

        // Compile module B/1
        result = getCompilerTask(Arrays.asList("-src", getPackagePath()+"/modules/multiversion/b"),
                "modules/multiversion/b/b/module.ceylon", "modules/multiversion/b/b/package.ceylon", "modules/multiversion/b/b/B.ceylon").call();
        Assert.assertEquals(Boolean.TRUE, result);

        // Compile module cImportsABIndirectlyOK which imports both A/1 and A/2
        result = getCompilerTask(Arrays.asList("-src", getPackagePath()+"/modules/multiversion/c"),
                "modules/multiversion/c/cImportsABIndirectlyOK/module.ceylon", "modules/multiversion/c/cImportsABIndirectlyOK/C.ceylon").call();
        Assert.assertEquals(Boolean.TRUE, result);
    }

    @Test
    public void testMdlMultipleVersionsDuringImplicitImport(){
        // Compile module A/1
        Boolean result = getCompilerTask(Arrays.asList("-src", getPackagePath()+"/modules/multiversion/a1"),
                "modules/multiversion/a1/a/module.ceylon", "modules/multiversion/a1/a/package.ceylon", "modules/multiversion/a1/a/A.ceylon").call();
        Assert.assertEquals(Boolean.TRUE, result);

        // Compile module A/2
        result = getCompilerTask(Arrays.asList("-src", getPackagePath()+"/modules/multiversion/a2"),
                "modules/multiversion/a2/a/module.ceylon", "modules/multiversion/a2/a/package.ceylon", "modules/multiversion/a2/a/A.ceylon").call();
        Assert.assertEquals(Boolean.TRUE, result);

        // Compile module bExportsA1/1
        result = getCompilerTask(Arrays.asList("-src", getPackagePath()+"/modules/multiversion/b"),
                "modules/multiversion/b/bExportsA1/module.ceylon", "modules/multiversion/b/bExportsA1/package.ceylon", "modules/multiversion/b/bExportsA1/B.ceylon").call();
        Assert.assertEquals(Boolean.TRUE, result);

        // Compile module cImportsABIndirectlyFail which imports both A/1 and A/2
        ErrorCollector collector = new ErrorCollector();
        result = getCompilerTask(Arrays.asList("-src", getPackagePath()+"/modules/multiversion/c"),
                collector,
                "modules/multiversion/c/cImportsABIndirectlyFail/module.ceylon", "modules/multiversion/c/cImportsABIndirectlyFail/C.ceylon").call();
        Assert.assertEquals(Boolean.FALSE, result);
        
        compareErrors(collector.get(Diagnostic.Kind.ERROR),
                new CompilerError(20, "module (transitively) imports conflicting versions of dependency 'a': version '1' and version '2'"),
                new CompilerError(20, "source code imports two different versions of module 'a': version '1' and version '2'")
        );
    }

    
    private int countEntries(JarFile jar) {
        int count = 0;
        Enumeration<JarEntry> entries = jar.entries();
        while(entries.hasMoreElements()){
            count++;
            entries.nextElement();
        }
        return count;
    }

    @Test
    public void testMdlSha1Signatures() throws IOException{
        File sourceArchiveFile = getSourceArchive("com.redhat.ceylon.compiler.java.test.cmr.modules.single", "6.6.6");
        File sourceArchiveSignatureFile = new File(sourceArchiveFile.getPath()+".sha1");
        File moduleArchiveFile = getModuleArchive("com.redhat.ceylon.compiler.java.test.cmr.modules.single", "6.6.6");
        File moduleArchiveSignatureFile = new File(moduleArchiveFile.getPath()+".sha1");
        // cleanup
        sourceArchiveFile.delete();
        sourceArchiveSignatureFile.delete();
        moduleArchiveFile.delete();
        moduleArchiveSignatureFile.delete();
        // safety check
        assertFalse(sourceArchiveFile.exists());
        assertFalse(sourceArchiveSignatureFile.exists());
        assertFalse(moduleArchiveFile.exists());
        assertFalse(moduleArchiveSignatureFile.exists());

        // compile one file
        compile("modules/single/module.ceylon");

        // make sure everything was created
        assertTrue(sourceArchiveFile.exists());
        assertTrue(sourceArchiveSignatureFile.exists());
        assertTrue(moduleArchiveFile.exists());
        assertTrue(moduleArchiveSignatureFile.exists());

        // check the signatures vaguely
        checkSha1(sourceArchiveSignatureFile);
        checkSha1(moduleArchiveSignatureFile);
    }

    private void checkSha1(File signatureFile) throws IOException {
        Assert.assertEquals(40, signatureFile.length());
        FileInputStream reader = new FileInputStream(signatureFile);
        byte[] bytes = new byte[40];
        Assert.assertEquals(40, reader.read(bytes));
        reader.close();
        char[] sha1 = new String(bytes, "ASCII").toCharArray();
        for (int i = 0; i < sha1.length; i++) {
            char c = sha1[i];
            Assert.assertTrue((c >= '0' && c <= '9')
                    || (c >= 'a' && c <= 'f')
                    || (c >= 'A' && c <= 'F'));
        }
    }

    @Test
    public void testMdlJdkBaseModule() throws IOException{
        compile("modules/jdk/appletBroken/Foo.ceylon");
    }

    @Test
    public void testMdlUsesJavaWithoutImportingIt() throws IOException{
        assertErrors("modules/jdk/usesJavaWithoutImportingIt/Foo",
                new CompilerError(20, "package not found in imported modules: 'java.lang' (add module import to module descriptor of 'com.redhat.ceylon.compiler.java.test.cmr.modules.jdk.usesJavaWithoutImportingIt')"),
                new CompilerError(23, "function or value does not exist: 'nanoTime'"));
    }

    @Test
    public void testMdlDefaultUsesJavaWithoutImportingIt() throws IOException{
        List<String> options = new LinkedList<String>();
        options.add("-src");
        options.add(getPackagePath()+"/modules/jdk/defaultUsesJavaWithoutImportingIt");
        options.addAll(defaultOptions);
        
        assertErrors("modules/jdk/defaultUsesJavaWithoutImportingIt/Foo",
                new CompilerError(20, "package not found in imported modules: 'java.lang' (define a module and add module import to its module descriptor)"),
                new CompilerError(23, "function or value does not exist: 'nanoTime'"));
    }

    @Test
    public void testMdlLegacyImport(){
        // Compile a module that imports a legacy module that has a shared import of another legacy module
        compile("modules/legacyimport/module.ceylon", "modules/legacyimport/package.ceylon", "modules/legacyimport/A.ceylon");
        
        File carFile = getModuleArchive("com.redhat.ceylon.compiler.java.test.cmr.modules.legacyimport", "6.6.6");
        assertTrue(carFile.exists());
    }
    
    @Test
    public void testMdlBug1062IncompatibleMissingImport() throws IOException{
        // compile our java class
        File classesOutputFolder = new File(destDir+"-jar-classes");
        cleanCars(classesOutputFolder.getPath());
        classesOutputFolder.mkdirs();

        File jarOutputFolder = new File(destDir+"-jar");
        cleanCars(jarOutputFolder.getPath());
        jarOutputFolder.mkdirs();

        compileJavaModule(jarOutputFolder, classesOutputFolder, "bug1062.javaA", "1",
                new File(getPackagePath(), "modules/bug1062/javaA1-src"),
                new File[0],
                "bug1062/javaA/JavaA.java");
        compileJavaModule(jarOutputFolder, classesOutputFolder, "bug1062.javaA", "2",
                new File(getPackagePath(), "modules/bug1062/javaA2-src"),
                new File[0],
                "bug1062/javaA/JavaA.java");
        compileJavaModule(jarOutputFolder, classesOutputFolder, "bug1062.javaB", "1",
                new File(getPackagePath(), "modules/bug1062/javaB-nomodule-src"),
                new File[]{new File(jarOutputFolder, "bug1062/javaA/1/bug1062.javaA-1.jar")},
                "bug1062/javaB/JavaB.java");
        
        assertErrors("modules/bug1062/ceylon/test",
                Arrays.asList("-rep", jarOutputFolder.getPath()), null,
                new CompilerError(5, "could not determine type of method or attribute reference: 'method' of 'JavaB': Error while loading the bug1062.javaB/1 module:\n"+
                        "   Declaration 'bug1062.javaA.JavaA' could not be found in module 'bug1062.javaB' or its imported modules but was found in the non-imported module 'bug1062.javaA'"),
                new CompilerError(5, "parameter type could not be determined: 'arg0' of 'method' in 'JavaB': Error while loading the bug1062.javaB/1 module:\n" + 
                        "   Declaration 'bug1062.javaA.JavaA' could not be found in module 'bug1062.javaB' or its imported modules but was found in the non-imported module 'bug1062.javaA'")
                );
    }

    @Test
    public void testMdlBug1062IncompatibleNonSharedImport() throws IOException{
        // compile our java class
        File classesOutputFolder = new File(destDir+"-jar-classes");
        cleanCars(classesOutputFolder.getPath());
        classesOutputFolder.mkdirs();

        File jarOutputFolder = new File(destDir+"-jar");
        cleanCars(jarOutputFolder.getPath());
        jarOutputFolder.mkdirs();

        compileJavaModule(jarOutputFolder, classesOutputFolder, "bug1062.javaA", "1",
                new File(getPackagePath()+"/modules/bug1062/javaA1-src"),
                new File[0],
                "bug1062/javaA/JavaA.java");
        compileJavaModule(jarOutputFolder, classesOutputFolder, "bug1062.javaA", "2",
                new File(getPackagePath()+"/modules/bug1062/javaA2-src"),
                new File[0],
                "bug1062/javaA/JavaA.java");
        compileJavaModule(jarOutputFolder, classesOutputFolder, "bug1062.javaB", "1",
                new File(getPackagePath()+"/modules/bug1062/javaB-module-src"),
                new File[]{new File(jarOutputFolder, "bug1062/javaA/1/bug1062.javaA-1.jar")},
                "bug1062/javaB/JavaB.java");
        
        // ceylon module imports JavaA/2 and JavaB/1
        // JavaB/1 imports JavaA/1
        assertErrors("modules/bug1062/ceylon/test",
                Arrays.asList("-rep", jarOutputFolder.getPath(), "-cp", getClassPathAsPath()), null,
                new CompilerError(5, "could not determine type of method or attribute reference: 'method' of 'JavaB': Error while loading the bug1062.javaB/1 module:\n"+
                        "   Declaration 'bug1062.javaA.JavaA' could not be found in module 'bug1062.javaB' or its imported modules but was found in the non-imported module 'bug1062.javaA'"),
                new CompilerError(5, "parameter type could not be determined: 'arg0' of 'method' in 'JavaB': Error while loading the bug1062.javaB/1 module:\n" + 
                        "   Declaration 'bug1062.javaA.JavaA' could not be found in module 'bug1062.javaB' or its imported modules but was found in the non-imported module 'bug1062.javaA'")
                );
    }

    @Test
    public void testMdlBug1062IncompatibleSharedImport() throws IOException{
        // compile our java class
        File classesOutputFolder = new File(destDir+"-jar-classes");
        cleanCars(classesOutputFolder.getPath());
        classesOutputFolder.mkdirs();

        File jarOutputFolder = new File(destDir+"-jar");
        cleanCars(jarOutputFolder.getPath());
        jarOutputFolder.mkdirs();

        compileJavaModule(jarOutputFolder, classesOutputFolder, "bug1062.javaA", "1",
                new File(getPackagePath()+"/modules/bug1062/javaA1-src"),
                new File[0],
                "bug1062/javaA/JavaA.java");
        compileJavaModule(jarOutputFolder, classesOutputFolder, "bug1062.javaA", "2",
                new File(getPackagePath()+"/modules/bug1062/javaA2-src"),
                new File[0],
                "bug1062/javaA/JavaA.java");
        compileJavaModule(jarOutputFolder, classesOutputFolder, "bug1062.javaB", "1",
                new File(getPackagePath()+"/modules/bug1062/javaB-module-export-src"),
                new File[]{new File(jarOutputFolder, "bug1062/javaA/1/bug1062.javaA-1.jar")},
                "bug1062/javaB/JavaB.java");
        
        // ceylon module imports JavaA/2 and JavaB/1
        // JavaB/1 shared imports JavaA/1
        assertErrors("modules/bug1062/ceylon/test",
                Arrays.asList("-rep", jarOutputFolder.getPath()), null,
                new CompilerError(2, "module (transitively) imports conflicting versions of dependency 'bug1062.javaA': version '1' and version '2'"),
                new CompilerError(2, "source code imports two different versions of module 'bug1062.javaA': version '1' and version '2'")
                );
    }

    @Test
    public void testMdlDefaultImportsInexistantPackage() throws IOException{
        // we do it twice to make sure existing class files do not confuse it
        for(int i=0;i<2;i++){
            assertErrors(new String[]{
                    "modules/defaultImportsInexistantPackage/file.ceylon",
                    "modules/defaultImportsInexistantPackage/isModule/module.ceylon",
                    "modules/defaultImportsInexistantPackage/isModule/package.ceylon",
                    "modules/defaultImportsInexistantPackage/isModule/foo.ceylon",
                },
                defaultOptions,
                null,
                new CompilerError( 1, "package not found in imported modules: 'doesnotExist' (define a module and add module import to its module descriptor)"),
                new CompilerError( 2, "package not found in imported modules: 'com.redhat.ceylon.compiler.java.test.cmr.modules.defaultImportsInexistantPackage.isModule' (define a module and add module import to its module descriptor)")
            );
        }
    }
    
    @Test
    public void testMdlProducesOsgiManifest() throws IOException {
        compile("modules/osgi/a/module.ceylon",
                "modules/osgi/a/package.ceylon",
                "modules/osgi/a/A.ceylon");

        final String moduleName = "com.redhat.ceylon.compiler.java.test.cmr.modules.osgi.a";
        final String moduleVersion = "1.1.0";

        final Manifest manifest = getManifest(moduleName, moduleVersion);

        Attributes attr = manifest.getMainAttributes();
        assertEquals("2", attr.get(OsgiManifest.Bundle_ManifestVersion));

        assertEquals(moduleName, attr.get(OsgiManifest.Bundle_SymbolicName));
        String bundleVersion = (String) attr.get(OsgiManifest.Bundle_Version);
        int qualifierIndex = bundleVersion.lastIndexOf('.');
        String bundleVersionWithoutQualifier = qualifierIndex > 0 ? bundleVersion.substring(0, qualifierIndex) : bundleVersion;
        assertEquals(moduleVersion, bundleVersionWithoutQualifier);
    }

    @Test
    public void testMdlDefaultHasNoOsgiManifest() throws IOException {
        compile("modules/def/CeylonClass.ceylon");
        
        File carFile = getModuleArchive("default", null);
        assertTrue(carFile.exists());

        JarFile car = new JarFile(carFile);

        ZipEntry manifest = car.getEntry(OsgiManifest.MANIFEST_FILE_NAME);
        assertNull(manifest);
        car.close();
    }

    @Test
    public void testMdlOsgiManifestDisabled() throws IOException {
        ErrorCollector c = new ErrorCollector();
        List<String> options = new ArrayList<String>(defaultOptions.size()+1);
        options.addAll(defaultOptions);
        options.add("-noosgi");
        assertCompilesOk(c, getCompilerTask(options, c, "modules/osgi/a/module.ceylon",
                "modules/osgi/a/package.ceylon",
                "modules/osgi/a/A.ceylon").call2());

        final String moduleName = "com.redhat.ceylon.compiler.java.test.cmr.modules.osgi.a";
        final String moduleVersion = "1.1.0";

        File carFile = getModuleArchive(moduleName, moduleVersion);
        JarFile car = new JarFile(carFile);

        ZipEntry manifest = car.getEntry(OsgiManifest.MANIFEST_FILE_NAME);
        assertNull(manifest);
        
        car.close();
    }

    @Test
    public void testMdlOsgiManifestRequiresCeylonLanguageBundle() throws IOException {
        compile("modules/osgi/a/module.ceylon",
                "modules/osgi/a/package.ceylon",
                "modules/osgi/a/A.ceylon");

        final Manifest manifest = getManifest(
                "com.redhat.ceylon.compiler.java.test.cmr.modules.osgi.a", "1.1.0");

        assertEquals("ceylon.language;bundle-version="+Versions.CEYLON_VERSION_NUMBER+";visibility:=reexport" +
                ",com.redhat.ceylon.dist;bundle-version="+Versions.CEYLON_VERSION_NUMBER+";visibility:=reexport",
                manifest.getMainAttributes().get(OsgiManifest.Require_Bundle));
    }

    @Test
    public void testMdlOsgiManifestExportsSharedPackages() throws IOException {
        compile("modules/osgi/a/module.ceylon",
                "modules/osgi/a/package.ceylon", "modules/osgi/a/A.ceylon",
                "modules/osgi/a/b/package.ceylon", "modules/osgi/a/b/B.ceylon",
                "modules/osgi/a/c/package.ceylon", "modules/osgi/a/c/C.ceylon");

        final String moduleName = "com.redhat.ceylon.compiler.java.test.cmr.modules.osgi.a";
        final String moduleVersion = "1.1.0";

        final Manifest manifest = getManifest(moduleName, moduleVersion);
        assertNotNull(manifest);

        Attributes attr = manifest.getMainAttributes();
        String attribute = (String) attr.get(OsgiManifest.Export_Package);

        String[] exportPackage = attribute.split(",");
        assertEquals(2, exportPackage.length);

        assertThat(
                Arrays.asList(exportPackage),
                CoreMatchers.hasItems(
                        "com.redhat.ceylon.compiler.java.test.cmr.modules.osgi.a;version="+ moduleVersion,
                        "com.redhat.ceylon.compiler.java.test.cmr.modules.osgi.a.c;version="+ moduleVersion));

        assertThat( Arrays.asList(exportPackage),
                CoreMatchers.not(CoreMatchers.hasItem("com.redhat.ceylon.compiler.java.test.cmr.modules.osgi.a.b;version=" + moduleVersion)));
    }

    @Test
    public void testMdlOsgiManifestRequresImportedModules() throws IOException {
        compile("modules/osgi/a/module.ceylon",
                "modules/osgi/a/package.ceylon",
                "modules/osgi/a/A.ceylon");

        compile("modules/osgi/b/module.ceylon",
                "modules/osgi/b/package.ceylon",
                "modules/osgi/b/B.ceylon");

        final String moduleBName = "com.redhat.ceylon.compiler.java.test.cmr.modules.osgi.b";
        final String moduleVersion = "1.1.0";

        final Manifest manifest = getManifest(moduleBName, moduleVersion);

        final String[] requireBundle = ((String) manifest.getMainAttributes()
                .get(OsgiManifest.Require_Bundle)).split(",");
        assertEquals(3, requireBundle.length);

        assertThat(Arrays.asList(requireBundle), CoreMatchers.hasItems(
                "ceylon.language;bundle-version="+Versions.CEYLON_VERSION_NUMBER+";visibility:=reexport",
                "com.redhat.ceylon.compiler.java.test.cmr.modules.osgi.a;bundle-version=1.1.0",
                "com.redhat.ceylon.dist;bundle-version="+Versions.CEYLON_VERSION_NUMBER+";visibility:=reexport"));
    }

    @Test
    public void testMdlOsgiManifestReexportsSharedImportedModules() throws IOException {
        compile("modules/osgi/a/module.ceylon",
                "modules/osgi/a/package.ceylon",
                "modules/osgi/a/A.ceylon");

        compile("modules/osgi/c/module.ceylon",
                "modules/osgi/c/package.ceylon",
                "modules/osgi/c/C.ceylon");

        final String moduleCName = "com.redhat.ceylon.compiler.java.test.cmr.modules.osgi.c";
        final String moduleVersion = "1.1.0";

        final Manifest manifest = getManifest(moduleCName, moduleVersion);
        final String[] requireBundle = ((String) manifest.getMainAttributes()
                .get(OsgiManifest.Require_Bundle)).split(",");

        assertEquals(3, requireBundle.length);
        assertThat(Arrays.asList(requireBundle), CoreMatchers.hasItems(
                "ceylon.language;bundle-version="+Versions.CEYLON_VERSION_NUMBER+";visibility:=reexport",
                "com.redhat.ceylon.compiler.java.test.cmr.modules.osgi.a;bundle-version=1.1.0;visibility:=reexport",
                "com.redhat.ceylon.dist;bundle-version="+Versions.CEYLON_VERSION_NUMBER+";visibility:=reexport"));
    }

    @Test
    public void testMdlOsgiManifestWithJavaImportRequiresJavaSECapability() throws IOException {
        compile("modules/osgi/java/module.ceylon",
                "modules/osgi/java/package.ceylon",
                "modules/osgi/java/foo.ceylon");
        
        final String moduleName = "com.redhat.ceylon.compiler.java.test.cmr.modules.osgi.java";
        final String moduleVersion = "1.1.0";
        
        final Manifest manifest = getManifest(moduleName, moduleVersion);
        assertEquals("osgi.ee;filter:=\"(&(osgi.ee=JavaSE)(version>=1.7))\"",
                manifest.getMainAttributes().get(OsgiManifest.Require_Capability));
    }
    
    private Manifest getManifest(String moduleName, String moduleVersion) throws IOException {
        File carFile = getModuleArchive(moduleName, moduleVersion);
        Manifest manifest = null;
        try (JarFile car = new JarFile(carFile)) {
            manifest = car.getManifest();
        }
        assertNotNull(manifest);
        return manifest;
    }

    @Test
    public void testMdlPomManifest() throws IOException {
        compile("modules/pom/a/module.ceylon",
                "modules/pom/b/module.ceylon");

        final String moduleName = "com.redhat.ceylon.compiler.java.test.cmr.modules.pom.b";
        final String moduleVersion = "1";
        
        File carFile = getModuleArchive(moduleName, moduleVersion);
        assertTrue(carFile.exists());
        JarFile car = new JarFile(carFile);

        ZipEntry pomFile = car.getEntry("META-INF/maven/com.redhat.ceylon.compiler.java.test.cmr.modules.pom/b/pom.xml");
        assertNotNull(pomFile);
        String pomContents = read(car, pomFile);
        assertEquals("<?xml version=\"1.0\" ?>\n"
                +"<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd\">\n"
                +" <modelVersion>4.0.0</modelVersion>\n"
                +" <groupId>com.redhat.ceylon.compiler.java.test.cmr.modules.pom</groupId>\n"
                +" <artifactId>b</artifactId>\n"
                +" <version>1</version>\n"
                +" <name>com.redhat.ceylon.compiler.java.test.cmr.modules.pom.b</name>\n"
                +" <dependencies>\n"
                +"  <dependency>\n"
                +"    <groupId>com.redhat.ceylon.compiler.java.test.cmr.modules.pom</groupId>\n"
                +"    <artifactId>a</artifactId>\n"
                +"    <version>1</version>\n"
                +"  </dependency>\n"
                +" </dependencies>\n"
                +"</project>\n",
                pomContents);
        
        ZipEntry propertiesFile = car.getEntry("META-INF/maven/com.redhat.ceylon.compiler.java.test.cmr.modules.pom/b/pom.properties");
        assertNotNull(propertiesFile);
        String propertiesContents = read(car, propertiesFile);
        // remove the date comment
        propertiesContents = propertiesContents.replaceFirst("^(#Generated by Ceylon\n)#[^\n]+\n", "$1");
        assertEquals("#Generated by Ceylon\n"
                +"version=1\n"
                +"groupId=com.redhat.ceylon.compiler.java.test.cmr.modules.pom\n"
                +"artifactId=b\n", propertiesContents);
        car.close();
    }

    @Test
    public void testMdlNoPomManifest() throws IOException {
        ErrorCollector c = new ErrorCollector();
        assertCompilesOk(c, getCompilerTask(Arrays.asList("-nopom", "-out", destDir), c, "modules/pom/a/module.ceylon",
                "modules/pom/b/module.ceylon").call2());

        final String moduleName = "com.redhat.ceylon.compiler.java.test.cmr.modules.pom.b";
        final String moduleVersion = "1";
        
        File carFile = getModuleArchive(moduleName, moduleVersion);
        assertTrue(carFile.exists());
        JarFile car = new JarFile(carFile);

        ZipEntry pomFile = car.getEntry("META-INF/maven/com.redhat.ceylon.compiler.java.test.cmr.modules.pom/b/pom.xml");
        assertNull(pomFile);
        
        ZipEntry propertiesFile = car.getEntry("META-INF/maven/com.redhat.ceylon.compiler.java.test.cmr.modules.pom/b/pom.properties");
        assertNull(propertiesFile);
        car.close();
    }

    private String read(JarFile car, ZipEntry entry) throws IOException {
        try(InputStream is = car.getInputStream(entry);
                Reader r = new InputStreamReader(is);
                BufferedReader br = new BufferedReader(r)){
            StringBuilder sb = new StringBuilder();
            String line;
            while((line = br.readLine()) != null)
                sb.append(line).append("\n");
            return sb.toString();
        }
    }
    
    private void setupBinaryModulesForOverridesCeylonModuleTests() {
        Boolean result = null;
        for (String version : Arrays.asList("v1", "v2")) {
            // Compile modules and generate archives
            result = getCompilerTask(Arrays.asList("-src", getPackagePath()+"modules/overridesCeylonModule/" + version),
                    "modules/overridesCeylonModule/" + version + "/a/module.ceylon", 
                    "modules/overridesCeylonModule/" + version + "/b/module.ceylon", 
                    "modules/overridesCeylonModule/" + version + "/b/hidden/package.ceylon", 
                    "modules/overridesCeylonModule/" + version + "/b/shared/package.ceylon", 
                    "modules/overridesCeylonModule/" + version + "/c/module.ceylon", 
                    "modules/overridesCeylonModule/" + version + "/c/hidden/package.ceylon", 
                    "modules/overridesCeylonModule/" + version + "/c/shared/package.ceylon").call();
            Assert.assertEquals(Boolean.TRUE, result);
        }
    }
    
    private static class ModulesRetriever implements TaskListener {
        private com.sun.tools.javac.util.Context context = null;
        HashMap<String, Module> modules = null;

        public ModulesRetriever(com.sun.tools.javac.util.Context context) {
            this.context = context;
        }
        
        @Override
        public void started(TaskEvent e) {
        }

        @Override
        public void finished(TaskEvent e) {
            if(e.getKind() == TaskEvent.Kind.ENTER){
                if(modules == null) {
                    modules = new HashMap<>();
                    Context ceylonContext = LanguageCompiler.getCeylonContextInstance(context);
                    assert(ceylonContext != null);
                    Modules modules = ceylonContext.getModules();
                    assert(modules != null);
                    for (Module m : modules.getListOfModules()) {
                        String name = m.getNameAsString();
                        if (name.equals("a") || name.equals("b") ||  name.equals("c")) {
                            this.modules.put(name, m);
                        }
                    }
                }
            }
        }
    }
    
    @Test
    public void testOverridesCeylonModuleInSourceImport(){
        setupBinaryModulesForOverridesCeylonModuleTests();
            
        ErrorCollector collector = new ErrorCollector();
        CeyloncTaskImpl compilerTask = getCompilerTask(
                Arrays.asList(
                        "-continue",
                        "-src", getPackagePath()+"/modules",
                        "-overrides", getPackagePath() +"modules/overridesCeylonModule/overrides-a-version.xml"
                ),
                collector,
                "modules/overridesCeylonModule/module.ceylon");
        ModulesRetriever modulesRetriever = new ModulesRetriever(compilerTask.getContext());
        compilerTask.setTaskListener(modulesRetriever);
        Boolean result = compilerTask.call();
        Assert.assertEquals(Boolean.FALSE, result);
        compareErrors(collector.get(Diagnostic.Kind.ERROR),
                new CompilerError(2, "The module import should not be overriden, since it is explicitely imported by a project source module"));
        
        assert(modulesRetriever.modules != null);
        Module a = modulesRetriever.modules.get("a");
        Module b = modulesRetriever.modules.get("b");
        Module c = modulesRetriever.modules.get("c");
        assert(a != null);
        assert(b != null);
        assert(c != null);

        assertEquals("The version override should not be applied to modules imported in source code", "2", a.getVersion());
        assertEquals("The version override should not be applied to modules imported in source code", "2", b.getVersion());
        assertEquals("The version override should not be applied to modules imported in source code", "2", c.getVersion());
    }

    @Test
    public void testOverridesCeylonModuleVersionProducesJavaModule(){
        setupBinaryModulesForOverridesCeylonModuleTests();
            
        ErrorCollector collector = new ErrorCollector();
        CeyloncTaskImpl compilerTask = getCompilerTask(
                Arrays.asList(
                        "-src", getPackagePath()+"/modules",
                        "-overrides", getPackagePath() +"modules/overridesCeylonModule/overrides-b-version.xml"
                ),
                collector,
                "modules/overridesCeylonModule/module.ceylon");
        ModulesRetriever modulesRetriever = new ModulesRetriever(compilerTask.getContext());
        compilerTask.setTaskListener(modulesRetriever);
        Boolean result = compilerTask.call();
        Assert.assertEquals(Boolean.TRUE, result);
        assert(modulesRetriever.modules != null);

        Module b = modulesRetriever.modules.get("b");
        assert(b != null);
        assertEquals("The Ceylon module 'b' is now seen as a Java module when a version override is applied", false, b.isJava());
    }

    @Test
    public void testOverridesCeylonModuleVersionAlterdPackageSharing(){
        setupBinaryModulesForOverridesCeylonModuleTests();
            
        ErrorCollector collector = new ErrorCollector();
        CeyloncTaskImpl compilerTask = getCompilerTask(
                Arrays.asList(
                        "-src", getPackagePath()+"/modules",
                        "-overrides", getPackagePath() +"modules/overridesCeylonModule/overrides-b-version.xml"
                ),
                collector,
                "modules/overridesCeylonModule/module.ceylon", "modules/overridesCeylonModule/testImportHiddenPackage.ceylon");
        ModulesRetriever modulesRetriever = new ModulesRetriever(compilerTask.getContext());
        compilerTask.setTaskListener(modulesRetriever);
        Boolean result = compilerTask.call();
        Assert.assertEquals(Boolean.FALSE, result);
        
        compareErrors(collector.get(Diagnostic.Kind.ERROR),
                new CompilerError(2, "imported package is not shared: 'b.hidden'"));
    }

    private ModuleImport getModuleImport(Module m, String name) {
        for (ModuleImport i : m.getImports()) {
            if (i.getModule().getNameAsString().equals(name)) {
                return i;
            }
        }
        return null;
    }

    @Test
    public void testOverridesCeylonModuleShareImport() {
        setupBinaryModulesForOverridesCeylonModuleTests();
            
        ErrorCollector collector = new ErrorCollector();
        CeyloncTaskImpl compilerTask = getCompilerTask(
                Arrays.asList(
                        "-src", getPackagePath()+"/modules",
                        "-overrides", getPackagePath() +"modules/overridesCeylonModule/overrides-share-c-import.xml"
                ),
                collector,
                "modules/overridesCeylonModule/module.ceylon");
        ModulesRetriever modulesRetriever = new ModulesRetriever(compilerTask.getContext());
        compilerTask.setTaskListener(modulesRetriever);
        Boolean result = compilerTask.call();
        Assert.assertEquals(Boolean.TRUE, result);
        
        Module a = modulesRetriever.modules.get("a");
        assert(a != null);
        ModuleImport cImport = getModuleImport(a, "c");
        assert(cImport != null);
        assertEquals("The 'c' module import should be seen as 'exported' after applying the overrides file", true, cImport.isExport());
    }
    
    @Test
    public void testCacheFolderCreation() throws IOException{
        // move our user cache folder
        File userCacheRepo = Repositories.get().getCacheRepoDir();
        File tmpCacheRepo = null;
        boolean moved = false;
        if(userCacheRepo.exists()){
            // let's move it
            System.out.println("Moving user cache repo away");
            tmpCacheRepo = File.createTempFile("moved-", "-out-for-CMRTests", userCacheRepo.getParentFile());
            tmpCacheRepo.delete();
            assert(userCacheRepo.renameTo(tmpCacheRepo));
            moved = true;
        }
        try{
            String cachePath = getCachePath();
            File cacheFolder = new File(cachePath);
            // cleared
            assert(!cacheFolder.exists());
            // clear or moved
            assert(!userCacheRepo.exists());
            compile("modules/def/A.ceylon");
            // created
            assert(cacheFolder.exists());
            // still not there
            assert(!userCacheRepo.exists());
        }finally{
            if(moved){
                System.out.println("Moving user cache repo back");
                Assert.assertTrue("Moving back your cache repo has failed: it is now at "
                        +tmpCacheRepo+" so if you want to keep it at "
                        +userCacheRepo+" you need to move it manually. Sorry.", 
                        tmpCacheRepo.renameTo(userCacheRepo));
            }
        }
    }

    @Test
    public void testMdlNullVersion() throws IOException{
        assertErrors("modules/nullVersion/module",
                new CompilerError(21, "missing module version"),
                new CompilerError(21, "cannot find module artifact null-null(.car|.jar)\n  \t- dependency tree: com.redhat.ceylon.compiler.java.test.cmr.modules.nullVersion/1 -> null/null")
                );
    }
}
