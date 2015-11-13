package com.redhat.ceylon.launcher;


import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.jar.JarFile;

import com.redhat.ceylon.common.Versions;

/**
 * Ceylon-specific class loader that knows how to find and add
 * all needed dependencies for compiler and runtime.
 * Implements child-first class loading to prevent mix-ups with
 * Java's own tool-chain.
 *
 * @author Tako Schotanus
 *
 */
public class CeylonClassLoader extends URLClassLoader {

    public static CeylonClassLoader newInstance() throws URISyntaxException, MalformedURLException, FileNotFoundException {
        return new CeylonClassLoader(getClassPath());
    }

    public static CeylonClassLoader newInstance(List<File> classPath) throws URISyntaxException, MalformedURLException, FileNotFoundException {
        return new CeylonClassLoader(classPath);
    }

    private String signature;
    
    private CeylonClassLoader(List<File> classPath) throws URISyntaxException, MalformedURLException, FileNotFoundException {
        super(toUrls(classPath));
        this.signature = toString(classPath);
    }

    private CeylonClassLoader(List<File> classPath, ClassLoader parentLoader) throws URISyntaxException, MalformedURLException, FileNotFoundException {
        super(toUrls(classPath), parentLoader);
        this.signature = toString(classPath);
    }

    public String getSignature(){
        return signature;
    }
    
    public boolean hasSignature(String signature){
        return signature != null && this.signature.equals(signature);
    }
    
    private static URL[] toUrls(List<File> cp) throws MalformedURLException {
        URL[] urls = new URL[cp.size()];
        int i = 0;
        for (File f : cp) {
            urls[i++] = f.toURI().toURL();
        }
        return urls;
    }

    private static String toString(List<File> cp) {
        StringBuilder classPath = new StringBuilder();
        for (File f : cp) {
            if (classPath.length() > 0) {
                classPath.append(File.pathSeparatorChar);
            }
            classPath.append(f.getAbsolutePath());
        }
        return classPath.toString();
    }

    public static String getClassPathAsString() throws URISyntaxException, FileNotFoundException {
        return toString(getClassPath());
    }

    public static String getClassPathSignature(List<File> cp) {
        return toString(cp);
    }

    public static List<File> getClassPath() throws URISyntaxException, FileNotFoundException {
        // Determine the necessary folders
        File ceylonHome = LauncherUtil.determineHome();
        File ceylonRepo = LauncherUtil.determineRepo(ceylonHome);

        // Perform some sanity checks
        checkFolders(ceylonHome, ceylonRepo);

        List<File> archives = new LinkedList<File>();

        // List all the necessary Ceylon JARs and CARs
        String version = LauncherUtil.determineSystemVersion();
        archives.add(getRepoJar(ceylonRepo, "com.redhat.ceylon.compiler.java", version));
        archives.add(getRepoCar(ceylonRepo, "ceylon.language", version));
        archives.add(getRepoJar(ceylonRepo, "ceylon.runtime", version));
        archives.add(getRepoJar(ceylonRepo, "com.redhat.ceylon.compiler.js", version));
        archives.add(getRepoJar(ceylonRepo, "com.redhat.ceylon.typechecker", version));
        archives.add(getRepoJar(ceylonRepo, "com.redhat.ceylon.common", version));
        archives.add(getRepoJar(ceylonRepo, "com.redhat.ceylon.model", version));
        archives.add(getRepoJar(ceylonRepo, "com.redhat.ceylon.module-resolver", version));
        archives.add(getRepoJar(ceylonRepo, "org.jboss.jandex", Versions.DEPENDENCY_JANDEX_VERSION));
        archives.add(getRepoJar(ceylonRepo, "org.jboss.modules", Versions.DEPENDENCY_JBOSS_MODULES_VERSION));
        archives.add(getRepoJar(ceylonRepo, "org.jboss.logmanager", Versions.DEPENDENCY_LOGMANAGER_VERSION));
        // Maven support for CMR
        archives.add(getRepoJar(ceylonRepo, "com.redhat.ceylon.maven-support", "2.0")); // optional
        // For the typechecker
        archives.add(getRepoJar(ceylonRepo, "org.antlr.runtime", "3.4"));
        // For the JS backend
        archives.add(getRepoJar(ceylonRepo, "net.minidev.json-smart", "1.1.1"));
        // For the "doc" tool
        archives.add(getRepoJar(ceylonRepo, "org.tautua.markdownpapers.core", "1.2.7"));
        archives.add(getRepoJar(ceylonRepo, "com.github.rjeschke.txtmark", "0.13"));
        // For the --out http:// functionality of the compiler
        archives.add(getRepoJar(ceylonRepo, "com.github.lookfirst.sardine", "5.1"));
        archives.add(getRepoJar(ceylonRepo, "org.apache.httpcomponents.httpclient", "4.3.2"));
        archives.add(getRepoJar(ceylonRepo, "org.apache.httpcomponents.httpcore", "4.3.2"));
        archives.add(getRepoJar(ceylonRepo, "org.apache.commons.logging", "1.1.1"));
        archives.add(getRepoJar(ceylonRepo, "org.apache.commons.codec", "1.8"));
        archives.add(getRepoJar(ceylonRepo, "org.slf4j.api", "1.6.1"));
        archives.add(getRepoJar(ceylonRepo, "org.slf4j.simple", "1.6.1")); // optional

        return archives;
    }

    private static File getRepoJar(File repo, String moduleName, String version) {
        return getRepoUrl(repo, moduleName, version, "jar");
    }

    private static File getRepoCar(File repo, String moduleName, String version) {
        return getRepoUrl(repo, moduleName, version, "car");
    }

    private static File getRepoUrl(File repo, String moduleName, String version, String extension) {
        return new File(repo, moduleName.replace('.', '/') + "/" + version + "/" + moduleName + "-" + version + "." + extension);
    }

    public static File getRepoJar(String moduleName, String version) throws FileNotFoundException, URISyntaxException {
        return getRepoUrl(moduleName, version, "jar");
    }

    public static File getRepoCar(String moduleName, String version) throws FileNotFoundException, URISyntaxException {
        return getRepoUrl(moduleName, version, "car");
    }

    public static File getRepoUrl(String moduleName, String version, String extension) throws URISyntaxException, FileNotFoundException {
        // Determine the necessary folders
        File ceylonHome = LauncherUtil.determineHome();
        File ceylonRepo = LauncherUtil.determineRepo(ceylonHome);

        // Perform some sanity checks
        checkFolders(ceylonHome, ceylonRepo);
        
        return new File(ceylonRepo, moduleName.replace('.', '/') + "/" + version + "/" + moduleName + "-" + version + "." + extension);
    }

    private static void checkFolders(File ceylonHome, File ceylonRepo) throws FileNotFoundException {
        if (!ceylonHome.isDirectory()) {
            throw new FileNotFoundException("Could not determine the Ceylon home directory (" + ceylonHome + ")");
        }
        if (!ceylonRepo.isDirectory()) {
            throw new FileNotFoundException("The Ceylon system repository could not be found (" + ceylonRepo + ")");
        }
    }

    @Override
    protected synchronized Class<?> loadClass(String name, boolean resolve)
            throws ClassNotFoundException {
        // First, check if the class has already been loaded
        Class<?> c = findLoadedClass(name);
        if (c == null) {
            try {
                // checking local
                c = findClass(name);
            } catch (ClassNotFoundException e) {
                // checking parent
                // This call to loadClass may eventually call findClass again, in case the parent doesn't find anything.
                c = super.loadClass(name, resolve);
            }
        }
        if (resolve) {
            resolveClass(c);
        }
        return c;
    }

    @Override
    public URL getResource(String name) {
        URL url = findResource(name);
        if (url == null) {
            // This call to getResource may eventually call findResource again, in case the parent doesn't find anything.
            url = super.getResource(name);
        }
        return url;
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        /**
        * Similar to super, but local resources are enumerated before parent resources
        */
        Enumeration<URL> localUrls = findResources(name);
        Enumeration<URL> parentUrls = null;
        if (getParent() != null) {
            parentUrls = getParent().getResources(name);
        }
        final List<URL> urls = new ArrayList<URL>();
        if (localUrls != null) {
            while (localUrls.hasMoreElements()) {
                urls.add(localUrls.nextElement());
            }
        }
        if (parentUrls != null) {
            while (parentUrls.hasMoreElements()) {
                urls.add(parentUrls.nextElement());
            }
        }
        return Collections.enumeration(urls);
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        URL url = getResource(name);
        if (url != null) {
            try {
                URLConnection con = url.openConnection();
                con.setUseCaches(false);
                return con.getInputStream();
            } catch (IOException e) {
            }
        }
        return null;
    }

    /**
     * Cleans up any resource associated with this class loader. This class loader will not be usable after calling this
     * method, so any code using it to run better not be running anymore.
     */
    public void clearCache() {
        try {
            Class<?> klass = java.net.URLClassLoader.class;
            Field ucp = klass.getDeclaredField("ucp");
            ucp.setAccessible(true);
            Object sunMiscURLClassPath = ucp.get(this);
            Field loaders = sunMiscURLClassPath.getClass().getDeclaredField("loaders");
            loaders.setAccessible(true);
            Object collection = loaders.get(sunMiscURLClassPath);
            for (Object sunMiscURLClassPathJarLoader : ((Collection<?>) collection).toArray()) {
                try {
                    Field loader = sunMiscURLClassPathJarLoader.getClass().getDeclaredField("jar");
                    loader.setAccessible(true);
                    Object jarFile = loader.get(sunMiscURLClassPathJarLoader);
                    ((JarFile) jarFile).close();
                } catch (Throwable t) {
                    // not a JAR loader?
                    t.printStackTrace();
                }
            }
        } catch (Throwable t) {
            // Something's wrong
            t.printStackTrace();
        }
        return;
    }
}
