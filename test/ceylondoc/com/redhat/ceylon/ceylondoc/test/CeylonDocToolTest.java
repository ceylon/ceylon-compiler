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
package com.redhat.ceylon.ceylondoc.test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.CharBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.tools.JavaFileObject;

import junit.framework.Assert;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import com.redhat.ceylon.ceylondoc.CeylonDocTool;
import com.redhat.ceylon.ceylondoc.Util;
import com.redhat.ceylon.compiler.java.tools.CeyloncTool;
import com.redhat.ceylon.compiler.typechecker.TypeChecker;
import com.redhat.ceylon.compiler.typechecker.model.Module;
import com.sun.source.util.JavacTask;
import com.sun.tools.javac.file.JavacFileManager;

public class CeylonDocToolTest {

    @Rule 
    public TestName name = new TestName();

    private CeylonDocTool tool(List<File> path, List<String> modules, 
            boolean throwOnError, String... repositories)
            throws IOException {
        CeylonDocTool tool = new CeylonDocTool(path, 
                Arrays.asList(repositories), 
                modules,
                throwOnError);
        File dir = new File("build", "CeylonDocToolTest/" + name.getMethodName());
        if (dir.exists()) {
            Util.delete(dir);
        }
        tool.setOutputRepository(dir.getAbsolutePath(), null, null);
        return tool;
    }
    
    private CeylonDocTool tool(String pathname, String moduleName, 
            boolean throwOnError, String... repositories)
            throws IOException {
        return tool(Arrays.asList(new File(pathname)),
                Arrays.asList(moduleName),
                throwOnError, repositories);
    }

    protected void assertFileExists(File destDir, String path) {
        File file = new File(destDir, path);
        Assert.assertTrue(file + " doesn't exist", file.exists());
        Assert.assertTrue(file + " exists but is not a file", file.isFile());
    }
    
    protected void assertFileNotExists(File destDir, String path) {
        File file = new File(destDir, path);
        Assert.assertFalse(file + " does exist", file.exists());
    }
    
    protected void assertDirectoryExists(File destDir, String path) {
        File file = new File(destDir, path);
        Assert.assertTrue(file + " doesn't exist", file.exists());
        Assert.assertTrue(file + " exist but isn't a directory", file.isDirectory());
    }
    
    static interface GrepAsserter {

        void makeAssertions(Matcher matcher);

    }
    
    static GrepAsserter AT_LEAST_ONE_MATCH = new GrepAsserter() {

        @Override
        public void makeAssertions(Matcher matcher) {
            Assert.assertTrue("Zero matches for " + matcher.pattern().pattern(), matcher.find());
        }
        
    };
    
    static GrepAsserter NO_MATCHES = new GrepAsserter() {

        @Override
        public void makeAssertions(Matcher matcher) {
            boolean found = matcher.find();
            if (found) {
                Assert.fail("Unexpected match for " + matcher.pattern().pattern() + ": " + matcher.group(0));
            }
        }
        
    };
    
    protected void assertMatchInFile(File destDir, String path, Pattern pattern, GrepAsserter asserter) throws IOException {
        assertFileExists(destDir, path);
        Charset charset = Charset.forName("UTF-8");
        
        File file = new File(destDir, path);
        FileInputStream stream = new FileInputStream(file);
        try  {
            FileChannel channel = stream.getChannel();
            try {
                MappedByteBuffer map = channel.map(MapMode.READ_ONLY, 0, channel.size());
                CharBuffer chars = charset.decode(map);
                Matcher matcher = pattern.matcher(chars);
                asserter.makeAssertions(matcher);
            } finally {
                channel.close();
            }
        } finally {
            stream.close();
        }
    }
    
    protected void assertMatchInFile(File destDir, String path, Pattern pattern) throws IOException {
        assertMatchInFile(destDir, path, pattern, AT_LEAST_ONE_MATCH);
    }
    
    protected void assertNoMatchInFile(File destDir, String path, Pattern pattern) throws IOException {
        assertMatchInFile(destDir, path, pattern, NO_MATCHES);
    }
    
    @Test
    public void moduleA() throws IOException {
        String pathname = "test/ceylondoc";
        String moduleName = "com.redhat.ceylon.ceylondoc.test.modules.single";

        CeylonDocTool tool = tool(pathname, moduleName, true);
        tool.setIncludeNonShared(false);
        tool.setIncludeSourceCode(true);
        tool.makeDoc();
        
        Module module = new Module();
        module.setName(Arrays.asList(moduleName));
        module.setVersion("3.1.4");
        
        File destDir = getOutputDir(tool, module);
        
        assertFileExists(destDir, false);
        assertBasicContent(destDir, false);
        assertBy(destDir);
        assertParametersDocumentation(destDir);
        assertThrows(destDir);
        assertSee(destDir);
        assertIcons(destDir);
        assertInnerTypesDoc(destDir);
        assertDeprecated(destDir);
        assertTagged(destDir);
        assertDocumentationOfRefinedMember(destDir);
        assertSequencedParameter(destDir);
        assertCallableParameter(destDir);
        assertFencedCodeBlockWithSyntaxHighlighter(destDir);
        assertWikiStyleLinkSyntax(destDir);
        assertBug659ShowInheritedMembers(destDir);
        assertBug691AbbreviatedOptionalType(destDir);
    }

    @Test
    public void moduleAWithPrivate() throws IOException {
        String pathname = "test/ceylondoc";
        String moduleName = "com.redhat.ceylon.ceylondoc.test.modules.single";
        
        CeylonDocTool tool = tool(pathname, moduleName, true);
        tool.setIncludeNonShared(true);
        tool.setIncludeSourceCode(true);
        tool.makeDoc();
        
        Module module = new Module();
        module.setName(Arrays.asList(moduleName));
        module.setVersion("3.1.4");
    
        File destDir = getOutputDir(tool, module);
        
        assertFileExists(destDir, true);
        assertBasicContent(destDir, true);
        assertBy(destDir);
        assertParametersDocumentation(destDir);
        assertThrows(destDir);
        assertSee(destDir);
        assertIcons(destDir);
        assertInnerTypesDoc(destDir);
        assertDeprecated(destDir);
        assertTagged(destDir);
        assertDocumentationOfRefinedMember(destDir);
        assertSequencedParameter(destDir);
        assertCallableParameter(destDir);
        assertFencedCodeBlockWithSyntaxHighlighter(destDir);
        assertWikiStyleLinkSyntax(destDir);
        assertBug659ShowInheritedMembers(destDir);
        assertBug691AbbreviatedOptionalType(destDir);
    }

    @Test
    public void dependentOnBinaryModule() throws IOException {
        String pathname = "test/ceylondoc";
        
        // compile the b module
        compile(pathname, "com.redhat.ceylon.ceylondoc.test.modules.dependency.b");
        
        CeylonDocTool tool = tool(pathname, "com.redhat.ceylon.ceylondoc.test.modules.dependency.c", true, "build/ceylon-cars");
        tool.makeDoc();
    }

    @Test
    public void classLoading() throws IOException {
        String pathname = "test/ceylondoc";
        
        // compile the a and b modules
        compile(pathname, "com.redhat.ceylon.ceylondoc.test.modules.classloading.a");
        compile(pathname, "com.redhat.ceylon.ceylondoc.test.modules.classloading.b");
        
        // now run docs on c, which uses b, which uses a
        CeylonDocTool tool = tool(pathname, "com.redhat.ceylon.ceylondoc.test.modules.classloading.c", true, "build/ceylon-cars");
        tool.makeDoc();
    }

    @Test
    public void containsJavaCode() throws IOException {
        String pathname = "test/ceylondoc";
        String moduleName = "com.redhat.ceylon.ceylondoc.test.modules.mixed";
        
        // compile the java code first
        compileJavaModule(pathname, "com/redhat/ceylon/ceylondoc/test/modules/mixed/Java.java");
        
        CeylonDocTool tool = tool(pathname, moduleName, true, "build/ceylon-cars");
        tool.makeDoc();
    }

    @Test
    public void documentSingleModule() throws IOException {
        String pathname = "test/ceylondoc";
        String moduleName = "com.redhat.ceylon.ceylondoc.test.modules.multi.a";
        
        CeylonDocTool tool = tool(pathname, moduleName, true, "build/ceylon-cars");
        tool.makeDoc();

        Module a = makeModule("com.redhat.ceylon.ceylondoc.test.modules.multi.a", "1");
        File destDirA = getOutputDir(tool, a);
        Module b = makeModule("com.redhat.ceylon.ceylondoc.test.modules.multi.b", "1");
        File destDirB = getOutputDir(tool, b);
        Module def = makeDefaultModule();
        File destDirDef = getOutputDir(tool, def);
        
        assertFileExists(destDirA, "index.html");
        assertFileNotExists(destDirB, "index.html");
        assertFileNotExists(destDirDef, "index.html");
    }

    @Test
    public void documentPackage() throws IOException {
        String pathname = "test/ceylondoc";
        String moduleName = "com.redhat.ceylon.ceylondoc.test.modules.multi.a.sub";
        
        try{
            CeylonDocTool tool = tool(pathname, moduleName, true, "build/ceylon-cars");
            tool.makeDoc();
        }catch(RuntimeException x){
            Assert.assertEquals("Can't find module: com.redhat.ceylon.ceylondoc.test.modules.multi.a.sub", x.getMessage());
            return;
        }
        Assert.fail("Expected exception");
    }

    @Test
    public void documentDefaultModule() throws IOException {
        String pathname = "test/ceylondoc";
        String moduleName = "default";
        
        CeylonDocTool tool = tool(pathname, moduleName, true, "build/ceylon-cars");
        tool.makeDoc();

        Module a = makeModule("com.redhat.ceylon.ceylondoc.test.modules.multi.a", "1");
        File destDirA = getOutputDir(tool, a);
        Module b = makeModule("com.redhat.ceylon.ceylondoc.test.modules.multi.b", "1");
        File destDirB = getOutputDir(tool, b);
        Module def = makeDefaultModule();
        File destDirDef = getOutputDir(tool, def);
        
        assertFileNotExists(destDirA, "index.html");
        assertFileNotExists(destDirB, "index.html");
        assertFileExists(destDirDef, "index.html");
        assertFileExists(destDirDef, "com/redhat/ceylon/ceylondoc/test/modules/multi/goes/into/object_bar.html");
        assertFileExists(destDirDef, "com/redhat/ceylon/ceylondoc/test/modules/multi/goes/into/defaultmodule/object_foo.html");
    }

    @Test
    public void ceylonLanguage() throws IOException {
        String pathname = "../ceylon.language/src";
        String moduleName = "ceylon.language";
        CeylonDocTool tool = tool(pathname, moduleName, false);
        tool.setIncludeNonShared(false);
        tool.setIncludeSourceCode(true);
        tool.makeDoc();
        
        Module module = makeModule("ceylon.language", TypeChecker.LANGUAGE_MODULE_VERSION);
        File destDir = getOutputDir(tool, module);
        
        assertFileExists(destDir, "index.html");
    }

    @Ignore("Disabled unless you have the sdk checked out")
    @Test
    public void ceylonMath() throws IOException {
        String[] moduleNames = {"file", "collection", "net", "json", "process", "math"};
        List<String> fullModuleNames = new ArrayList<String>(moduleNames.length);
        List<File> path = new ArrayList<File>(moduleNames.length);
        for(String moduleName : moduleNames){
            path.add(new File("../ceylon-sdk/"+moduleName+"/source"));
            fullModuleNames.add("ceylon." + moduleName);
        }
        CeylonDocTool tool = tool(path, fullModuleNames, false);
        tool.setIncludeNonShared(false);
        tool.setIncludeSourceCode(true);
        tool.makeDoc();
        
        for(String moduleName : moduleNames){
            Module module = makeModule("ceylon." + moduleName, "0.3.3");
            File destDir = getOutputDir(tool, module);

            assertFileExists(destDir, "index.html");
        }
    }

    private Module makeDefaultModule() {
        Module module = new Module();
        module.setName(Arrays.asList(Module.DEFAULT_MODULE_NAME));
        module.setDefault(true);
        return module;
    }

    private Module makeModule(String name, String version) {
        Module module = new Module();
        module.setName(Arrays.asList(name.split("\\.")));
        module.setVersion(version);
        return module;
    }

    private void assertFileExists(File destDir, boolean includeNonShared) {
        assertDirectoryExists(destDir, ".resources");
        assertFileExists(destDir, ".resources/index.js");
        assertFileExists(destDir, ".resources/icons.png");
        assertFileExists(destDir, "index.html");
        assertFileExists(destDir, "search.html");
        assertFileExists(destDir, "interface_Types.html");
        assertFileExists(destDir, "class_SharedClass.html");
        assertFileExists(destDir, "class_CaseSensitive.html");
        assertFileExists(destDir, "object_caseSensitive.html");
        if( includeNonShared ) {
            assertFileExists(destDir, "class_PrivateClass.html");
        }
        else {
            assertFileNotExists(destDir, "class_PrivateClass.html");
        }
    }

    private void assertBasicContent(File destDir, boolean includeNonShared) throws IOException {
        assertMatchInFile(destDir, "index.html", 
                Pattern.compile("This is a <strong>test</strong> module"));
        assertMatchInFile(destDir, "index.html", 
                Pattern.compile("This is a <strong>test</strong> package"));
        
        assertMatchInFile(destDir, "class_SharedClass.html", 
                Pattern.compile("<.*? id='sharedAttribute'.*?>"));
        assertMatchInFile(destDir, "class_SharedClass.html", 
                Pattern.compile("<.*? id='sharedGetter'.*?>"));
        assertMatchInFile(destDir, "class_SharedClass.html", 
                Pattern.compile("<.*? id='sharedMethod'.*?>"));
        
        if( includeNonShared ) {
            assertMatchInFile(destDir, "class_SharedClass.html", 
                    Pattern.compile("<.*? id='privateAttribute'.*?>"));
            assertMatchInFile(destDir, "class_SharedClass.html", 
                    Pattern.compile("<.*? id='privateMethod'.*?>"));
            assertMatchInFile(destDir, "class_SharedClass.html", 
                    Pattern.compile("<.*? id='privateGetter'.*?>"));
        }
        else {
            assertNoMatchInFile(destDir, "class_SharedClass.html", 
                    Pattern.compile("<.*? id='privateAttribute'.*?>"));
            assertNoMatchInFile(destDir, "class_SharedClass.html", 
                    Pattern.compile("<.*? id='privateMethod'.*?>"));
            assertNoMatchInFile(destDir, "class_SharedClass.html", 
                    Pattern.compile("<.*? id='privateGetter'.*?>"));
        }
    }

    private void assertBy(File destDir) throws IOException {
        assertMatchInFile(destDir, "index.html", 
                Pattern.compile("<div class='by'>By: Tom Bentley</div>"));
        assertMatchInFile(destDir, "interface_Types.html", 
                Pattern.compile("<div class='by'>By: Tom Bentley</div>"));
    }

    private void assertParametersDocumentation(File destDir) throws IOException {
    	assertMatchInFile(destDir, "index.html", 
    			Pattern.compile("<div class='parameters'>Parameters: <ul><li>numbers<p>Sequenced parameters <code>numbers</code></p>"));
        assertMatchInFile(destDir, "class_StubClass.html", 
                Pattern.compile("<div class='parameters'>Parameters:"));        
        assertMatchInFile(destDir, "class_StubClass.html", 
                Pattern.compile("<li>a<p>Constructor parameter <code>a</code></p>"));
        assertMatchInFile(destDir, "class_StubClass.html", 
                Pattern.compile("<li>b<p>Constructor parameter <code>b</code></p>"));        
        assertMatchInFile(destDir, "class_StubClass.html", 
                Pattern.compile("<li>a<p>Method parameter <code>a</code></p>"));
        assertMatchInFile(destDir, "class_StubClass.html", 
                Pattern.compile("<li>b<p>Method parameter <code>b</code></p>"));
	}

	private void assertThrows(File destDir) throws IOException {
        assertMatchInFile(destDir, "class_StubClass.html", 
                Pattern.compile("<div class='throws'>Throws:"));        
        assertMatchInFile(destDir, "class_StubClass.html", 
                Pattern.compile("OverflowException<p>if the number is too large to be represented as an integer</p>"));        
        assertMatchInFile(destDir, "class_StubClass.html", 
                Pattern.compile("<a href='class_StubException.html'>StubException</a><p><code>when</code> with <strong>WIKI</strong> syntax</p>"));
    }

    private void assertSee(File destDir) throws IOException {
        assertMatchInFile(destDir, "index.html", Pattern.compile("<div class='see'>See also: <a href='class_StubClass.html'>StubClass</a>, <a href='index.html#stubTopLevelMethod'>stubTopLevelMethod</a>"));
        assertMatchInFile(destDir, "index.html", Pattern.compile("<div class='see'>See also: <a href='class_StubClass.html'>StubClass</a>, <a href='index.html#stubTopLevelAttribute'>stubTopLevelAttribute</a>"));
        
        assertMatchInFile(destDir, "class_StubClass.html", Pattern.compile("<div class='see'>See also: <a href='interface_StubInterface.html'>StubInterface</a>, <a href='index.html#stubTopLevelAttribute'>stubTopLevelAttribute</a>, <a href='index.html#stubTopLevelMethod'>stubTopLevelMethod</a>"));
        assertMatchInFile(destDir, "class_StubClass.html", Pattern.compile("<div class='see'>See also: <a href='class_StubClass.html#methodWithSee'>methodWithSee</a>, <a href='class_StubException.html'>StubException</a></div>"));
        assertMatchInFile(destDir, "class_StubClass.html", Pattern.compile("<div class='see'>See also: <a href='class_StubClass.html#attributeWithSee'>attributeWithSee</a>, <a href='class_StubException.html'>StubException</a>, <a href='a/class_A1.html'>A1</a></div>"));
    }
    
    private void assertIcons(File destDir) throws IOException {
        assertMatchInFile(destDir, "interface_StubInterface.html", Pattern.compile("Interface <i class='icon-interface'></i><code>StubInterface</code>"));
        assertMatchInFile(destDir, "interface_StubInterface.html", Pattern.compile("<td id='formalMethodFromStubInterface'><code><i class='icon-shared-member'><i class='icon-decoration-formal'></i></i>"));
        assertMatchInFile(destDir, "interface_StubInterface.html", Pattern.compile("<td id='defaultDeprecatedMethodFromStubInterface'><code><i class='icon-decoration-deprecated'><i class='icon-shared-member'></i></i>"));

        assertMatchInFile(destDir, "class_StubClass.html", Pattern.compile("<i class='icon-interface'></i><a href='interface_StubClass.StubInnerInterface.html'>StubInnerInterface</a>"));
        assertMatchInFile(destDir, "class_StubClass.html", Pattern.compile("<i class='icon-class'></i><a href='class_StubClass.StubInnerClass.html'>StubInnerClass</a>"));
        assertMatchInFile(destDir, "class_StubClass.html", Pattern.compile("<i class='icon-class'></i>StubClass()"));
        assertMatchInFile(destDir, "class_StubClass.html", Pattern.compile("<td id='formalMethodFromStubInterface'><code><i class='icon-shared-member'><i class='icon-decoration-impl'></i></i>"));
        assertMatchInFile(destDir, "class_StubClass.html", Pattern.compile("<td id='defaultDeprecatedMethodFromStubInterface'><code><i class='icon-decoration-deprecated'><i class='icon-shared-member'><i class='icon-decoration-over'></i></i></i>"));        
    }
    
    private void assertInnerTypesDoc(File destDir) throws IOException {
        assertFileExists(destDir, "interface_StubClass.StubInnerInterface.html");
        assertFileExists(destDir, "class_StubClass.StubInnerClass.html");
        assertFileExists(destDir, "class_StubClass.StubInnerException.html");
        
        assertMatchInFile(destDir, "class_StubClass.html", 
                Pattern.compile("Nested Interfaces"));
        assertMatchInFile(destDir, "class_StubClass.html", 
                Pattern.compile("<a href='interface_StubClass.StubInnerInterface.html'>StubInnerInterface</a>"));
        assertMatchInFile(destDir, "class_StubClass.html", 
                Pattern.compile("Nested Classes"));
        assertMatchInFile(destDir, "class_StubClass.html", 
                Pattern.compile("<a href='class_StubClass.StubInnerClass.html'>StubInnerClass</a>"));
        assertMatchInFile(destDir, "class_StubClass.html", 
                Pattern.compile("Nested Exceptions"));
        assertMatchInFile(destDir, "class_StubClass.html", 
                Pattern.compile("<a href='class_StubClass.StubInnerException.html'>StubInnerException</a>"));
        
        assertMatchInFile(destDir, "interface_StubClass.StubInnerInterface.html", 
                Pattern.compile("Enclosing class: <i class='icon-class'></i><a href='class_StubClass.html'>StubClass</a>"));
        assertMatchInFile(destDir, "class_StubClass.StubInnerClass.html", 
                Pattern.compile("Enclosing class: <i class='icon-class'></i><a href='class_StubClass.html'>StubClass</a>"));
        assertMatchInFile(destDir, "class_StubClass.StubInnerClass.html", 
                Pattern.compile("Satisfied Interfaces: <a href='interface_StubClass.StubInnerInterface.html'>StubInnerInterface</a>"));                
    }
    
    private void assertDeprecated(File destDir) throws IOException {
        assertFileExists(destDir, "class_DeprecatedClass.html");
        
        assertMatchInFile(destDir, "index.html",
                Pattern.compile("<i class='icon-decoration-deprecated'><i class='icon-class'></i></i><span class='modifiers'>shared</span> <a href='class_DeprecatedClass.html'>DeprecatedClass</a></code></td><td><div class='description'><div class='deprecated'><p><strong>Deprecated:</strong> This is <code>DeprecatedClass</code></p>"));
        assertMatchInFile(destDir, "class_DeprecatedClass.html",
                Pattern.compile("<div class='deprecated'><p><strong>Deprecated:</strong> Don't use this attribute!"));
        assertMatchInFile(destDir, "class_DeprecatedClass.html",
                Pattern.compile("<div class='deprecated'><p><strong>Deprecated:</strong> Don't use this method"));
    }
    
    private void assertTagged(File destDir) throws IOException {
        assertMatchInFile(destDir, ".resources/index.js", 
                Pattern.compile("var tagIndex = \\[\\n'stubInnerMethodTag1',"));
        assertMatchInFile(destDir, ".resources/index.js", 
                Pattern.compile("\\{'name': 'StubClass', 'type': 'class', 'url': 'class_StubClass.html', 'doc': '<p>This is <code>StubClass</code></p>\\\\n', 'tags': \\['stubTag1', 'stubTag2'\\]\\}"));
        assertMatchInFile(destDir, ".resources/index.js", 
                Pattern.compile("\\{'name': 'StubClass.attributeWithTagged', 'type': 'value', 'url': 'class_StubClass.html#attributeWithTagged', 'doc': '<p>The stub attribute with <code>tagged</code>.</p>\\\\n', 'tags': \\['stubTag1'\\]\\}"));
        assertMatchInFile(destDir, ".resources/index.js", 
                Pattern.compile("\\{'name': 'StubClass.methodWithTagged', 'type': 'function', 'url': 'class_StubClass.html#methodWithTagged', 'doc': '<p>The stub method with <code>tagged</code> .*?</p>\\\\n', 'tags': \\['stubTag2'\\]\\}"));
        
        assertMatchInFile(destDir, "class_StubClass.html", 
                Pattern.compile("<div class='tags'><span class='tagCaption'>Tags: </span><a class='tagLabel' name='stubTag1' href='search.html\\?q=stubTag1'>stubTag1</a><a class='tagLabel' name='stubTag2' href='search.html\\?q=stubTag2'>stubTag2</a></div>"));
        assertMatchInFile(destDir, "class_StubClass.html", 
                Pattern.compile("<div class='tags'><span class='tagCaption'>Tags: </span><a class='tagLabel' name='stubTag1' href='search.html\\?q=stubTag1'>stubTag1</a></div><code>attributeWithTagged"));
        assertMatchInFile(destDir, "class_StubClass.html", 
                Pattern.compile("<div class='tags'><span class='tagCaption'>Tags: </span><a class='tagLabel' name='stubTag2' href='search.html\\?q=stubTag2'>stubTag2</a></div><code>methodWithTagged"));
        
        assertMatchInFile(destDir, "index.html", 
                Pattern.compile("<div class='tags'><span class='tagCaption'>Tags: </span><a class='tagLabel' name='stubTag1a' href='search.html\\?q=stubTag1a'>stubTag1a</a><a class='tagLabel' name='stubTag1b' href='search.html\\?q=stubTag1b'>stubTag1b</a><a class='tagLabel' name='stubTagWithVeryLongName ... !!!' href='search.html\\?q=stubTagWithVeryLongName ... !!!'>stubTagWithVeryLongName ... !!!</a></div><div class='description'><div class='doc'><p>This is <code>StubInterface</code></p>"));
        assertMatchInFile(destDir, "index.html", 
                Pattern.compile("<div class='tags'><span class='tagCaption'>Tags: </span><a class='tagLabel' name='stubTag1' href='search.html\\?q=stubTag1'>stubTag1</a><a class='tagLabel' name='stubTag2' href='search.html\\?q=stubTag2'>stubTag2</a></div><div class='description'><div class='doc'><p>This is <code>StubClass</code></p>"));
    }
    
    private void assertDocumentationOfRefinedMember(File destDir) throws IOException {
        assertMatchInFile(destDir, "class_StubClass.html", 
                Pattern.compile("Description of StubInterface.formalMethodFromStubInterface"));
        assertMatchInFile(destDir, "class_StubClass.html", 
                Pattern.compile("Description of StubInterface.defaultDeprecatedMethodFromStubInterface"));
        assertMatchInFile(destDir, "class_StubClass.html", 
                Pattern.compile("Deprecated in StubInterface.defaultDeprecatedMethodFromStubInterface"));
    }
    
	private void assertSequencedParameter(File destDir) throws IOException {
        assertMatchInFile(destDir, "class_StubClass.html", 
                Pattern.compile("<code>methodWithSequencedParameter\\(Integer... numbers\\)</code>"));
	}
    
    private void assertCallableParameter(File destDir) throws IOException {
        assertMatchInFile(destDir, "class_StubClass.html", 
                Pattern.compile("<code>methodWithCallableParameter1\\(Void onClick\\(\\)\\)</code>"));
        
        assertMatchInFile(destDir, "class_StubClass.html", 
                Pattern.compile("<code>methodWithCallableParameter2&lt;Element&gt;\\(Boolean selecting\\(<span class='type-parameter'>Element</span> element\\)\\)</code>"));

        assertMatchInFile(destDir, "class_StubClass.html", 
                Pattern.compile("<code>methodWithCallableParameter3\\(Void fce1\\(Void fce2\\(Void fce3\\(\\)\\)\\)\\)</code>"));
    }

    private void assertFencedCodeBlockWithSyntaxHighlighter(File destDir) throws IOException {
        assertMatchInFile(destDir, "class_StubClass.html", 
                Pattern.compile("<link href='.resources/shCore.css' rel='stylesheet' type='text/css'/>"));
        assertMatchInFile(destDir, "class_StubClass.html", 
                Pattern.compile("<link href='.resources/shThemeDefault.css' rel='stylesheet' type='text/css'/>"));
        assertMatchInFile(destDir, "class_StubClass.html", 
                Pattern.compile("<script type='text/javascript' src='.resources/shCore.js'>"));
        assertMatchInFile(destDir, "class_StubClass.html", 
                Pattern.compile("<script type='text/javascript' src='.resources/shBrushCeylon.js'>"));
        assertMatchInFile(destDir, "class_StubClass.html", 
                Pattern.compile("<pre class=\"brush: ceylon\">shared default Boolean subset\\(Set set\\) \\{"));
    }
    
    private void assertWikiStyleLinkSyntax(File destDir) throws IOException {
        assertMatchInFile(destDir, "class_StubClass.html", 
                Pattern.compile("StubClass = <a href='class_StubClass.html'>StubClass</a>"));
        assertMatchInFile(destDir, "class_StubClass.html", 
                Pattern.compile("StubInterface = <a href='interface_StubInterface.html'>StubInterface</a>"));
        assertMatchInFile(destDir, "class_StubClass.html", 
                Pattern.compile("StubInnerException = <a href='class_StubClass.StubInnerException.html'>StubInnerException</a>"));
        assertMatchInFile(destDir, "class_StubClass.html", 
                Pattern.compile("stubTopLevelMethod = <a href='index.html#stubTopLevelMethod'>stubTopLevelMethod</a>"));
        assertMatchInFile(destDir, "class_StubClass.html", 
                Pattern.compile("stubTopLevelAttribute = <a href='index.html#stubTopLevelAttribute'>stubTopLevelAttribute</a>"));
        assertMatchInFile(destDir, "class_StubClass.html", 
                Pattern.compile("StubInterface.formalMethodFromStubInterface = <a href='interface_StubInterface.html#formalMethodFromStubInterface'>StubInterface.formalMethodFromStubInterface</a>"));
        assertMatchInFile(destDir, "class_StubClass.html", 
                Pattern.compile("StubClass.StubInnerClass = <a href='class_StubClass.StubInnerClass.html'>StubClass.StubInnerClass</a>"));
        assertMatchInFile(destDir, "class_StubClass.html", 
                Pattern.compile("StubClass.StubInnerClass.innerMethod = <a href='class_StubClass.StubInnerClass.html#innerMethod'>StubClass.StubInnerClass.innerMethod</a>"));
        assertMatchInFile(destDir, "class_StubClass.html", 
                Pattern.compile("StubInterface with custom name = <a href='interface_StubInterface.html'>custom stub interface</a>"));
        assertMatchInFile(destDir, "class_StubClass.html", 
                Pattern.compile("unresolvable = unresolvable"));
        
        assertMatchInFile(destDir, "class_StubClass.html", 
                Pattern.compile("imported A1 = <a href='a/class_A1.html'>A1</a>"));
        assertMatchInFile(destDir, "class_StubClass.html", 
                Pattern.compile("imported AliasA2 = <a href='a/class_A2.html'>AliasA2</a>"));
        
        assertMatchInFile(destDir, "class_StubClass.html", 
                Pattern.compile("fullStubInterface = <a href='interface_StubInterface.html'>com.redhat.ceylon.ceylondoc.test.modules.single@StubInterface</a>"));
        assertMatchInFile(destDir, "class_StubClass.html", 
                Pattern.compile("fullStubInterface.formalMethodFromStubInterface = <a href='interface_StubInterface.html#formalMethodFromStubInterface'>com.redhat.ceylon.ceylondoc.test.modules.single@StubInterface.formalMethodFromStubInterface</a>"));
        assertMatchInFile(destDir, "class_StubClass.html", 
                Pattern.compile("fullStubInterface with custom name = <a href='interface_StubInterface.html'>full custom stub interface</a>"));
        assertMatchInFile(destDir, "class_StubClass.html", 
                Pattern.compile("fullUnresolvable = unresolvable@StubInterface"));
    }

    private void assertBug659ShowInheritedMembers(File destDir) throws IOException {
    	assertMatchInFile(destDir, "class_StubClass.html",
    			Pattern.compile("Show inherited methods"));
    	assertMatchInFile(destDir, "class_StubClass.html",
    			Pattern.compile("<a href='interface_StubInterface.html#defaultDeprecatedMethodFromStubInterface'>defaultDeprecatedMethodFromStubInterface</a>"));
    	assertMatchInFile(destDir, "class_StubClass.html",
    			Pattern.compile("<a href='interface_StubInterface.html#formalMethodFromStubInterface'>formalMethodFromStubInterface</a>"));
    }

    private void assertBug691AbbreviatedOptionalType(File destDir) throws IOException {
        assertMatchInFile(destDir, "class_StubClass.html",
                Pattern.compile("id='bug691AbbreviatedOptionalType1'><code><i class='icon-shared-member'></i><span class='modifiers'>shared</span> String\\?</code>"));
        assertMatchInFile(destDir, "class_StubClass.html",
                Pattern.compile("id='bug691AbbreviatedOptionalType2'><code><i class='icon-shared-member'></i><span class='modifiers'>shared</span> <span class='type-parameter'>Element</span>\\?</code>"));
    }
    
    private File getOutputDir(CeylonDocTool tool, Module module) {
        String outputRepo = tool.getOutputRepository();
        return new File(com.redhat.ceylon.compiler.java.util.Util.getModulePath(new File(outputRepo), module),
                "module-doc");
    }

    private void compile(String pathname, String moduleName) throws IOException {
        CeyloncTool compiler = new CeyloncTool();
        List<String> options = Arrays.asList("-src", pathname, "-out", "build/ceylon-cars");
        JavacTask task = compiler.getTask(null, null, null, options, Arrays.asList(moduleName), null);
        Boolean ret = task.call();
        Assert.assertEquals("Compilation failed", Boolean.TRUE, ret);
    }

    private void compileJavaModule(String pathname, String... fileNames) throws IOException {
        CeyloncTool compiler = new CeyloncTool();
        List<String> options = Arrays.asList("-src", pathname, "-out", "build/ceylon-cars");
        JavacFileManager fileManager = compiler.getStandardFileManager(null, null, null);
        List<String> qualifiedNames = new ArrayList<String>(fileNames.length);
        for(String name : fileNames){
            qualifiedNames.add(pathname + File.separator + name);
        }
        Iterable<? extends JavaFileObject> fileObjects = fileManager.getJavaFileObjectsFromStrings(qualifiedNames);
        JavacTask task = compiler.getTask(null, null, null, options, null, fileObjects);
        Boolean ret = task.call();
        Assert.assertEquals("Compilation failed", Boolean.TRUE, ret);
    }
}
