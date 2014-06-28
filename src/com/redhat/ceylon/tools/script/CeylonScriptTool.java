package com.redhat.ceylon.tools.script;

import java.io.File;
import java.io.FilenameFilter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import com.redhat.ceylon.common.Constants;
import com.redhat.ceylon.common.ModuleUtil;
import com.redhat.ceylon.common.tool.Argument;
import com.redhat.ceylon.common.tool.Description;
import com.redhat.ceylon.common.tool.Summary;
import com.redhat.ceylon.common.tool.Tool;
import com.redhat.ceylon.common.tool.ToolFactory;
import com.redhat.ceylon.common.tool.ToolLoader;
import com.redhat.ceylon.common.tool.ToolModel;
import com.redhat.ceylon.common.tools.CeylonToolLoader;
import com.redhat.ceylon.compiler.typechecker.model.Module;


@Summary("Ceylon Script Tool")
@Description("Ceylon Script Tool is used to execute the Ceylon Script" +
             "which includes both compiling and running in a single tool" +
             "and it enables to run the ceylon script as an executible file too.")
public class CeylonScriptTool implements Tool {

	private List<String> modulesOrFiles = Arrays.asList("*");
	private List<String> args(String... args) {
        return Arrays.asList(args);
    }
	
    @Argument(argumentName="moduleOrFile", multiplicity="*")
    public void setModule(List<String> moduleOrFile) {
        this.modulesOrFiles = moduleOrFile;
    }
    
    @Override
    public void run() {
    	ToolFactory pluginFactory = new ToolFactory();
    	ToolLoader pluginLoader = new CeylonToolLoader();
    	
    	//Invoke CeylonCompileTool
    	ToolModel<Tool> compileModel = pluginLoader.loadToolModel("compile");
        Tool compileTool = pluginFactory.bindArguments(compileModel, 
                args("--src=" + "./source"));
        try {
			compileTool.run();
		} catch (Exception e) {
			e.printStackTrace();
		}
        
      
        //Find Module Name
        String CWD = System.getProperty("user.dir");
        for (String moduleOrFile : this.modulesOrFiles) {
        	/*Cases : 
        	 * 
        	 * 1) ./source/com/example/script/try.ceylon -> [./source/com/example/script/try.ceylon] : Handled
        	 *
        	 * 2) ceylon script source/com/example/script/try.ceylon -> [source/com/example/script/try.ceylon] : Handled
        	 * 
        	 * 3) ceylon script -> [*] : To be handled
        	 */
        	
        	String moduleName = Module.DEFAULT_MODULE_NAME; //Default Module Name If no module found

        	String completePath = CWD.concat(moduleOrFile.substring(0, 2).equals("./") ? moduleOrFile.substring(1) : "/" + moduleOrFile); //Handles Case 1 & 2
        	File nodeFile = new File(completePath), nodeFolder = new File(nodeFile.getParent());
        	Path sourceFolderPath = Paths.get(CWD + "/source");
        	
        	File[] matchingFiles = nodeFolder.listFiles(new FilenameFilter() {
        	    public boolean accept(File dir, String name) {
        	        return name.equals(Constants.MODULE_DESCRIPTOR);
        	    }
        	});
        	
        	if (matchingFiles.length == 1) { //Exact Match
        		//Module Name will be relative path between sourceFolderPath and nodeFolderPath
        		Path nodeFolderPath = Paths.get(nodeFolder.getPath());
        		String modulePath = sourceFolderPath.relativize(nodeFolderPath).toString();
        		moduleName = modulePath.replace(File.separatorChar, '.');
        	}
        	
        	//Get the Module Version ??
        	
            
            //Invoking CeylonRunTool
        	ToolModel<Tool> runModel = pluginLoader.loadToolModel("run");
            Tool runTool = pluginFactory.bindArguments(runModel, 
                    args(moduleName));

            try {
    			runTool.run();
    		} catch (Exception e) {
    			// TODO Auto-generated catch block
    			e.printStackTrace();
    		}
        }
        
    }
    @Override
    public void initialize() throws Exception {
    }
    
}
