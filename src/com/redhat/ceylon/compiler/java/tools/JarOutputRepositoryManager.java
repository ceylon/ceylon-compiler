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

package com.redhat.ceylon.compiler.java.tools;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;

import com.redhat.ceylon.cmr.api.ArtifactContext;
import com.redhat.ceylon.cmr.api.RepositoryManager;
import com.redhat.ceylon.cmr.api.ArtifactCreator;
import com.redhat.ceylon.cmr.ceylon.CeylonUtils;
import com.redhat.ceylon.cmr.util.JarUtils;
import com.redhat.ceylon.common.Constants;
import com.redhat.ceylon.common.FileUtil;
import com.redhat.ceylon.common.log.Logger;
import com.redhat.ceylon.compiler.java.tools.JarEntryManifestFileObject.OsgiManifest;
import com.redhat.ceylon.model.typechecker.model.Module;
import com.sun.source.util.TaskListener;
import com.sun.tools.javac.main.OptionName;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Options;

public class JarOutputRepositoryManager {
    
    private Map<Module,ProgressiveJar> openJars = new HashMap<Module, ProgressiveJar>();
    private Log log;
    private Options options;
    private CeyloncFileManager ceyloncFileManager;
    private TaskListener taskListener;
    
    JarOutputRepositoryManager(Log log, Options options, CeyloncFileManager ceyloncFileManager, TaskListener taskListener){
        this.log = log;
        this.options = options;
        this.ceyloncFileManager = ceyloncFileManager;
        this.taskListener = taskListener;
    }
    
    public JavaFileObject getFileObject(RepositoryManager repositoryManager, Module module, String fileName, File sourceFile) throws IOException{
        ProgressiveJar progressiveJar = getProgressiveJar(repositoryManager, module);
        return progressiveJar.getJavaFileObject(fileName, sourceFile);
    }
    
    private ProgressiveJar getProgressiveJar(RepositoryManager repositoryManager, Module module) throws IOException {
        ProgressiveJar jarFile = openJars.get(module);
        if(jarFile == null){
            jarFile = new ProgressiveJar(repositoryManager, module, log, options, ceyloncFileManager, taskListener);
            openJars.put(module, jarFile);
        }
        return jarFile;
    }

    public void flush() throws IOException {
        Exception ex = null;
        try{
            for(ProgressiveJar jarFile : openJars.values()){
                try {
                    jarFile.close();
                } catch (Exception e) {
                    ex = e;
                }
            }
        }finally{
            // make sure we clear on return and throw, so we don't try to flush again on throw
            openJars.clear();
        }
        if (ex instanceof IOException) {
            throw (IOException)ex;
        }
    }
    
    static class ProgressiveJar {
        private static final String META_INF = "META-INF";
        private static final String MAPPING_FILE = META_INF+"/mapping.txt";
        private File originalJarFile;
        private File outputJarFile;
        private JarOutputStream jarOutputStream;
        final private Set<String> modifiedSourceFiles = new HashSet<String>();
        final private Set<String> modifiedResourceFilesRel = new HashSet<String>();
        final private Set<String> modifiedResourceFilesFull = new HashSet<String>();
        final private Properties writtenClassesMapping = new Properties(); 
        private Logger cmrLog;
        private Options options;
        private RepositoryManager repoManager;
        private ArtifactContext carContext;
        private ArtifactCreator srcCreator;
        private ArtifactCreator resourceCreator;
        private Module module;
        private Set<String> folders = new HashSet<String>();
        private boolean manifestWritten = false;
        private boolean writeOsgiManifest;
        private String osgiProvidedBundles;
        private final String resourceRootPath;
        private boolean writeMavenManifest;
        private TaskListener taskListener;

        public ProgressiveJar(RepositoryManager repoManager, Module module, Log log, Options options, CeyloncFileManager ceyloncFileManager, TaskListener taskListener) throws IOException{
            this.options = options;
            this.repoManager = repoManager;
            this.carContext = new ArtifactContext(module.getNameAsString(), module.getVersion(), ArtifactContext.CAR);
            this.cmrLog = new JavacLogger(options, Log.instance(ceyloncFileManager.getContext()));
            this.srcCreator = CeylonUtils.makeSourceArtifactCreator(
                    repoManager,
                    ceyloncFileManager.getLocation(StandardLocation.SOURCE_PATH),
                    module.getNameAsString(), module.getVersion(),
                    options.get(OptionName.VERBOSE) != null, cmrLog);
            this.resourceCreator = CeylonUtils.makeResourceArtifactCreator(
                    repoManager,
                    ceyloncFileManager.getLocation(StandardLocation.SOURCE_PATH),
                    ceyloncFileManager.getLocation(CeylonLocation.RESOURCE_PATH),
                    options.get(OptionName.CEYLONRESOURCEROOT),
                    module.getNameAsString(), module.getVersion(),
                    options.get(OptionName.VERBOSE) != null, cmrLog);
            this.module = module;
            this.writeOsgiManifest = !options.isSet(OptionName.CEYLONNOOSGI);
            this.osgiProvidedBundles = options.get(OptionName.CEYLONOSGIPROVIDEDBUNDLES);
            this.writeMavenManifest = !options.isSet(OptionName.CEYLONNOPOM);
            
            // Determine the special path that signals that the files it contains
            // should be moved to the root of the output JAR/CAR
            String rrp = module.getNameAsString().replace('.', '/');
            if (!rrp.isEmpty() && !rrp.endsWith("/")) {
                rrp = rrp + "/";
            }
            String rootName = options.get(OptionName.CEYLONRESOURCEROOT);
            if (rootName == null) {
                rootName = Constants.DEFAULT_RESOURCE_ROOT;
            }
            this.resourceRootPath = rrp + rootName + "/";
            this.taskListener = taskListener;
            
            this.originalJarFile = repoManager.getArtifact(carContext);
            this.outputJarFile = File.createTempFile("ceylon-compiler-", ".car");
            this.jarOutputStream = new JarOutputStream(new FileOutputStream(outputJarFile));
        }

        private Properties getPreviousMapping() throws IOException {
            if (originalJarFile != null) {
                JarFile jarFile = null;
                jarFile = new JarFile(originalJarFile);
                try {
                    JarEntry entry = jarFile.getJarEntry(MAPPING_FILE);
                    if (entry != null) {
                        InputStream inputStream = jarFile.getInputStream(entry);
                        try {
                            Properties previousMapping = new Properties();
                            previousMapping.load(inputStream);
                            return previousMapping;
                        } finally {
                            inputStream.close();
                        }
                    }
                } finally {
                    jarFile.close();
                }
            }
            return null;
        }

        private Manifest getPreviousManifest() throws IOException {
            if (originalJarFile != null) {
                JarFile jarFile = null;
                jarFile = new JarFile(originalJarFile);
                try {
                    return jarFile.getManifest();
                } finally {
                    jarFile.close();
                }
            }
            return null;
        }

        public void close() throws IOException {
            try {
                Set<String> copiedSourceFiles = srcCreator.copy(modifiedSourceFiles);
                resourceCreator.copy(modifiedResourceFilesFull);
    
                if (writeOsgiManifest && !manifestWritten && !module.isDefault()) {
                    Manifest manifest = new OsgiManifest(module, getPreviousManifest(), osgiProvidedBundles).build();
                    writeManifestJarEntry(manifest);
                }
                if (writeMavenManifest) {
                    writeMavenManifest(module);
                }
    
                Properties previousMapping = getPreviousMapping();
                writeMappingJarEntry(previousMapping, getJarFilter(previousMapping, copiedSourceFiles));
                
                JarUtils.finishUpdatingJar(
                        originalJarFile, outputJarFile, carContext, jarOutputStream,
                        getJarFilter(previousMapping, copiedSourceFiles),
                        repoManager, options.get(OptionName.VERBOSE) != null, cmrLog, folders, options.isSet(OptionName.CEYLONPACK200));
                
                String info;
                if(module.isDefault())
                    info = module.getNameAsString();
                else
                    info = module.getNameAsString() + "/" + module.getVersion();
                cmrLog.info("Created module " + info);
                if(taskListener instanceof CeylonTaskListener){
                    ((CeylonTaskListener) taskListener).moduleCompiled(module.getNameAsString(), module.getVersion());
                }
            } finally {
                FileUtil.deleteQuietly(outputJarFile);
            }
        }

        private JarUtils.JarEntryFilter getJarFilter(final Properties previousMapping, final Set<String> copiedSourceFiles) {
            return new JarUtils.JarEntryFilter() {
                @Override
                public boolean avoid(String entryFullName) {
                    if (entryFullName.endsWith(".class")) {
                        boolean classWasUpdated = writtenClassesMapping.containsKey(entryFullName);
                        if (previousMapping != null) {
                            String sourceFileForClass = previousMapping.getProperty(entryFullName);
                            classWasUpdated = classWasUpdated || copiedSourceFiles.contains(sourceFileForClass);
                        }
                        return classWasUpdated;
                    } else {
                        return modifiedResourceFilesRel.contains(entryFullName)
                                || entryFullName.equals(MAPPING_FILE)
                                || (writeOsgiManifest && OsgiManifest.isManifestFileName(entryFullName))
                                || (writeMavenManifest && MavenPomUtil.isMavenDescriptor(entryFullName, module));
                    }
                }
            };
        }


        private void writeManifestJarEntry(Manifest manifest) {
            try {
                folders.add(META_INF+"/");
                jarOutputStream.putNextEntry(new ZipEntry(OsgiManifest.MANIFEST_FILE_NAME));
                manifest.write(jarOutputStream);
            }
            catch (IOException e) {
                // TODO : log to the right place
            }
            finally {
                try {
                    jarOutputStream.closeEntry();
                }
                catch (IOException ignore) {
                }
            }
        }

        private void writeMavenManifest(Module module) {
            MavenPomUtil.writeMavenManifest(jarOutputStream, module, folders);
        }

        private void writeMappingJarEntry(Properties previousMapping, JarUtils.JarEntryFilter filter) {
            Properties newMapping = new Properties();
            newMapping.putAll(writtenClassesMapping);
            if (previousMapping != null) {
                // Add the previous mapping entries that are not related to an updated source file 
                for (String classFullName : previousMapping.stringPropertyNames()) {
                    if (!filter.avoid(classFullName)) {
                        newMapping.setProperty(classFullName, previousMapping.getProperty(classFullName));
                    }
                }
            }
            // Write the mapping file to the Jar
            try {
                folders.add(META_INF+"/");
                jarOutputStream.putNextEntry(new ZipEntry(MAPPING_FILE));
                newMapping.store(jarOutputStream, "");
            }
            catch(IOException e) {
                // TODO : log to the right place
            }
            finally {
                try {
                    jarOutputStream.closeEntry();
                } catch (IOException e) {
                }
            }
        }

        public JavaFileObject getJavaFileObject(String fileName, File sourceFile) {
            String entryName = fileName.replace(File.separatorChar, '/');
            
            if (!resourceRootPath.isEmpty() && entryName.startsWith(resourceRootPath)) {
                // Files in the special "resource root path" get moved
                // to the root of the output JAR/CAR
                entryName = entryName.substring(resourceRootPath.length());
            }
            
            String folder = JarUtils.getFolder(entryName);
            if (folder != null) {
                folders.add(folder);
            }

            if (sourceFile != null) {
                modifiedSourceFiles.add(sourceFile.getPath());
                // record the class file we produce so that we don't save it from the original jar
            	addMappingEntry(entryName, JarUtils.toPlatformIndependentPath(srcCreator.getPaths(), sourceFile.getPath()));
            } else {
                modifiedResourceFilesRel.add(entryName);
                modifiedResourceFilesFull.add(FileUtil.applyPath(resourceCreator.getPaths(), fileName).getPath());
                if (writeOsgiManifest && OsgiManifest.isManifestFileName(entryName) && !module.isDefault()) {
                    this.manifestWritten = true;
                    return new JarEntryManifestFileObject(outputJarFile.getPath(), jarOutputStream, entryName, module, osgiProvidedBundles);
                }
            }
            return new JarEntryFileObject(outputJarFile.getPath(), jarOutputStream, entryName);
        }

        private void addMappingEntry(String className,
                String sourcePath) {
            writtenClassesMapping.put(className, sourcePath);
        }
    }
}
