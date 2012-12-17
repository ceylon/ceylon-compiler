package com.redhat.ceylon.tools.help;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import org.tautua.markdownpapers.ast.Document;

import com.redhat.ceylon.common.Versions;
import com.redhat.ceylon.common.tool.ArgumentModel;
import com.redhat.ceylon.common.tool.Description;
import com.redhat.ceylon.common.tool.Hidden;
import com.redhat.ceylon.common.tool.Multiplicity;
import com.redhat.ceylon.common.tool.OptionModel;
import com.redhat.ceylon.common.tool.OptionModel.ArgumentType;
import com.redhat.ceylon.common.tool.RemainingSections;
import com.redhat.ceylon.common.tool.SubtoolModel;
import com.redhat.ceylon.common.tool.Summary;
import com.redhat.ceylon.common.tool.Tool;
import com.redhat.ceylon.common.tool.ToolLoader;
import com.redhat.ceylon.common.tool.ToolModel;
import com.redhat.ceylon.common.tool.Tools;
import com.redhat.ceylon.tools.CeylonTool;
import com.redhat.ceylon.tools.help.Markdown.Section;
import com.redhat.ceylon.tools.help.model.DescribedSection;
import com.redhat.ceylon.tools.help.model.DescribedSection.Role;
import com.redhat.ceylon.tools.help.model.Doc;
import com.redhat.ceylon.tools.help.model.Option;
import com.redhat.ceylon.tools.help.model.OptionsSection;
import com.redhat.ceylon.tools.help.model.SummarySection;
import com.redhat.ceylon.tools.help.model.SynopsesSection;
import com.redhat.ceylon.tools.help.model.Synopsis;

public class DocBuilder {

    protected final ResourceBundle sectionsBundle = ResourceBundle.getBundle("com.redhat.ceylon.tools.help.resources.sections");
    protected ToolLoader toolLoader;
    protected boolean includeHidden = false;

    public DocBuilder(ToolLoader toolLoader) {
        super();
        this.toolLoader = toolLoader;
    }
    
    public boolean isIncludeHidden() {
        return includeHidden;
    }

    public void setIncludeHidden(boolean includeHidden) {
        this.includeHidden = includeHidden;
    }

    public Doc buildDoc(ToolModel<?> model, boolean specialRoot) {
        boolean rootHack = specialRoot && CeylonTool.class.isAssignableFrom(model.getToolClass());
        Doc doc = new Doc();
        doc.setVersion(Versions.CEYLON_VERSION);
        doc.setToolModel(model);
        doc.setInvocation(getCeylonInvocation(model));
        doc.setSummary(buildSummary(model));
        doc.setSynopses(rootHack ? buildRootSynopsis(model) : buildSynopsis(model));
        doc.setDescription(rootHack ? buildRootDescription(model) : buildDescription(model));
        doc.setOptions(buildOptions(model));
        if (model.getSubtoolModel() != null) {
            //doc.setSubcommands(buildSubcommands(model));
        }
        doc.setAdditionalSections(buildAdditionalSections(model));
        return doc;
    }
    
    private SynopsesSection buildRootSynopsis(ToolModel<?> model) {

        SynopsesSection synopsis = new SynopsesSection();
        synopsis.setTitle(sectionsBundle.getString("section.SYNOPSIS"));
        List<Synopsis> synopsisList = new ArrayList<>();
        {
            Synopsis s1 = new Synopsis();
            s1.setInvocation(Tools.progName());
            OptionModel<Boolean> option = new OptionModel();
            option.setLongName("version");
            option.setArgumentType(ArgumentType.NOT_ALLOWED);
            ArgumentModel<Boolean> argument = new ArgumentModel<>();
            argument.setMultiplicity(Multiplicity._1);
            argument.setType(Boolean.TYPE);
            option.setArgument(argument);
            s1.setOptionsAndArguments(Collections.singletonList(option));//model.getOption("version")));
            synopsisList.add(s1);
        }
        {
            Synopsis s2 = new Synopsis();
            s2.setInvocation(Tools.progName());
            
            ArrayList args = new ArrayList(model.getOptions());
            args.remove(model.getOption("version"));
            /*ArgumentModel<?> options = new ArgumentModel();
            options.setMultiplicity(Multiplicity._0_OR_MORE);
            options.setName("cey\u2011options");
            args.add(options);*/
            
            ArgumentModel<?> command = new ArgumentModel();
            command.setMultiplicity(Multiplicity._1);
            command.setName("command");
            args.add(command);
            
            ArgumentModel<?> commandOptions = new ArgumentModel();
            commandOptions.setMultiplicity(Multiplicity._0_OR_MORE);
            commandOptions.setName("command\u2011options");
            args.add(commandOptions);
            
            ArgumentModel<?> commandArgs = new ArgumentModel();
            commandArgs.setMultiplicity(Multiplicity._0_OR_MORE);
            commandArgs.setName("command\u2011args");
            args.add(commandArgs);
            
            s2.setOptionsAndArguments(args);
            synopsisList.add(s2);
        }
        synopsis.setSynopses(synopsisList);
        
        return synopsis;
    }
    
    private DescribedSection buildRootDescription(
            ToolModel<?> rootModel) {
        
        StringBuilder sb = new StringBuilder();
        final String newline = "\n";
        sb.append(newline);
        sb.append(newline);
        for (String toolName : toolLoader.getToolNames()) {
            final ToolModel<?> model = toolLoader.loadToolModel(toolName);
            if (model == null) {
                throw new RuntimeException(toolName);
             }
            if (!model.isPorcelain() && !includeHidden) {
                continue;
            }
            sb.append("* `").append(toolName).append("` ");
            String summary = getSummaryValue(model);
            if (summary != null) {
                sb.append(summary);
            }
            sb.append(newline);
            sb.append(newline);
        }
        sb.append(newline);
        sb.append("See `" + Tools.progName() + " help <command>` for more information on a particular command");
        sb.append(newline);
        sb.append(newline);
        
        String both = getDescription(rootModel) + sb.toString();
        
        DescribedSection description = buildDescription(rootModel, both);
        return description;
    }

    public Doc buildDoc(ToolModel<?> model) {
        return buildDoc(model, false);
    }

    private List<DescribedSection> buildAdditionalSections(ToolModel<?> model) {
        List<DescribedSection> additionalSections = new ArrayList<DescribedSection>();
        String sections = getSections(model);
        if (sections != null && !sections.isEmpty()) {
            Document doc = Markdown.markdown(sections);
            List<Section> markdownSections = Markdown.extractSections(doc);
            for (Markdown.Section sect : markdownSections) {
                DescribedSection ds = new DescribedSection();
                ds.setRole(Role.ADDITIONAL);
                Document sectionDoc = sect.getDoc();
                if (sect.getHeading() == null) {
                    // TODO Warn that there were no section headings
                    continue;
                } else {
                    // Adjust the heading levels, so that the most prominent 
                    // heading is H2
                    Markdown.adjustHeadings(sectionDoc, 2-sect.getHeading().getLevel());
                }
                ds.setTitle(sect.getHeading());
                ds.setDescription(sectionDoc);
                additionalSections.add(ds);
            }
        }
        return additionalSections;
    }

    private OptionsSection buildOptions(ToolModel<?> model) {
        OptionsSection optionsSection = new OptionsSection();
        optionsSection.setTitle(sectionsBundle.getString("section.OPTIONS"));
        List<Option> options = new ArrayList<>();
        for (OptionModel<?> opt : sortedOptions(model.getOptions())) {
            Option option = new Option();
            option.setOption(opt);
            String descriptionMd = getOptionDescription(model, opt);
            if (descriptionMd == null || descriptionMd.isEmpty()) {
                descriptionMd = sectionsBundle.getString("option.undocumented");
            }
            option.setDescription(Markdown.markdown(descriptionMd));
            options.add(option);
        }
        optionsSection.setOptions(options);
        return optionsSection;
    }

    private DescribedSection buildDescription(ToolModel<?> model) {
        return buildDescription(model, getDescription(model));
    }
    private DescribedSection buildDescription(ToolModel<?> model, String description) {
        DescribedSection section = null;
        if (!description.isEmpty()) {
            section = new DescribedSection();
            section.setRole(Role.DESCRIPTION);
            section.setTitle(
                    Markdown.markdown("##" + sectionsBundle.getString("section.DESCRIPTION") + "\n"));
            section.setDescription(Markdown.markdown(description));
        }
        return section;
    }
    

    private DescribedSection buildSubcommands(ToolModel<?> model) {
        /*
        DescribedSection section = null;
        if (!description.isEmpty()) {
            SubtoolModel<?> subtool = model.getSubtoolModel();
            for (String toolName : subtool.getToolLoader().getToolNames()) {
                ToolModel<Tool> subtoolModel = subtool.getToolLoader().loadToolModel(toolName);
            }
            / *
             * Here I need to build up the markdown something like as follows
             * 
            The command `ceylon config` takes various subcommands
            
            ## SUBCOMMANDS
            
            ### `ceylon config foo`
            
            summary
            
            description
            
            options
            
            ### `ceylon config bar baz`
            
            summary
            
            description
            
            options
            
            * /
            section = new DescribedSection();
            section.setRole(Role.SUBCOMMANDS);
            section.setDescription(Markdown.markdown(
                    "##" + sectionsBundle.getString("section.SUBCOMMANDS") + "\n\n" +
                    description));
        }
        return section;*/
        return null;
    }

    private SynopsesSection buildSynopsis(ToolModel<?> model) {
        //Synopsis synopsis = out.startSynopsis(bundle.getString("section.SYNOPSIS"));
        // TODO Make auto generated SYNOPSIS better -- we need to know which options
        // form groups, or should we just have a @Synopses({@Synopsis(""), ...})
        SynopsesSection synopsesSection = new SynopsesSection();
        synopsesSection.setTitle(sectionsBundle.getString("section.SYNOPSIS"));
        List<Synopsis> synopsisList = new ArrayList<>();
        
        if (model.getSubtoolModel() != null) {
            for (List<?> optionsAndArguments : buildSubtoolSynopsis(model.getSubtoolModel())) {
                List<Object> l = optionsAndArguments(model);
                l.addAll(optionsAndArguments);
                Synopsis synopsis = new Synopsis();
                synopsis.setInvocation(getCeylonInvocation(model));
                synopsis.setOptionsAndArguments(l);
                synopsisList.add(synopsis);
            }
        } else {
            Synopsis synopsis = new Synopsis();
            synopsis.setInvocation(getCeylonInvocation(model));
            synopsis.setOptionsAndArguments(optionsAndArguments(model));
            synopsisList.add(synopsis);
        }
        synopsesSection.setSynopses(synopsisList);
        return synopsesSection;
    }

    private <E> List<E> optionsAndArguments(ToolModel<?> model) {
        List<E> optionsAndArguments = (List)sortedOptions(model.getOptions());
        optionsAndArguments.addAll((List)model.getArguments());
        return optionsAndArguments;
    }

    private List<List<?>> buildSubtoolSynopsis(SubtoolModel<?> subtoolModel) {
        List<List<?>> result = new ArrayList<List<?>>();
        ToolLoader subtoolLoader = subtoolModel.getToolLoader();
        for (String toolName : subtoolLoader.getToolNames()) {
            ToolModel<?> model = subtoolLoader.loadToolModel(toolName);
            if (model.getSubtoolModel() != null) {
                for (List subOptAndArgs : buildSubtoolSynopsis(model.getSubtoolModel())) {
                    List optionsAndArguments = optionsAndArguments(model);
                    optionsAndArguments.add(0, new Synopsis.NameAndSubtool(toolName, subtoolModel));
                    optionsAndArguments.addAll((List)subOptAndArgs);
                    result.add(optionsAndArguments);
                }
            } else {
                List<Object> optionsAndArguments = optionsAndArguments(model);
                optionsAndArguments.add(0, new Synopsis.NameAndSubtool(toolName, subtoolModel));
                result.add(optionsAndArguments);
            }
        }
        return result;
    }

    private boolean skipHiddenOption(OptionModel<?> option) {
        return option.getArgument().getSetter().getAnnotation(Hidden.class) != null 
                && !includeHidden;
    }

    private ArrayList<OptionModel<?>> sortedOptions(final Collection<OptionModel<?>> options2) {
        ArrayList<OptionModel<?>> options = new ArrayList<OptionModel<?>>(options2);
        for (Iterator<OptionModel<?>> iter = options.iterator(); iter.hasNext(); ) {
            OptionModel<?> option = iter.next();
            if (skipHiddenOption(option)) {
                iter.remove();
            }
        }
        Collections.sort(options, new Comparator<OptionModel<?>>() {
            @Override
            public int compare(OptionModel<?> o1, OptionModel<?> o2) {
                return o1.getLongName().compareTo(o2.getLongName());
            }
        });
        return options;
    }

    private SummarySection buildSummary(ToolModel<?> model) {
        SummarySection summary = new SummarySection();
        summary.setTitle(
                Markdown.markdown("##" + sectionsBundle.getString("section.NAME") + "\n"));
        summary.setSummary(getSummaryValue(model));
        return summary;
    }
    
    
    private String getName(ToolModel<?> model) {
        return model.getName();
    }
    
    private String msg(ResourceBundle toolBundle, String key) {
        if (toolBundle != null && toolBundle.containsKey(key)) {
            String msg = toolBundle.getString(key);
            if (msg != null) {
                return msg;
            }
        }
        return "";
    }
    
    private String getSummaryValue(ToolModel<?> model) {
        ResourceBundle toolBundle = getToolBundle(model);
        String msg = msg(toolBundle, "summary");
        if (msg.isEmpty()) {
            Summary summary = getSummary(model);
            if (summary != null) {
                msg = summary.value();
            }
        }
        return msg;
    }

    private ResourceBundle getToolBundle(ToolModel<?> model) {
        ResourceBundle toolBundle;
        try {
            toolBundle = ResourceBundle.getBundle(model.getToolClass().getName());
        } catch (MissingResourceException e) {
            toolBundle = null;
        }
        return toolBundle;
    }

    private Summary getSummary(ToolModel<?> model) {
        return model.getToolClass().getAnnotation(Summary.class);
    }

    private String getDescription(ToolModel<?> model) {
        ResourceBundle toolBundle = getToolBundle(model);
        String msg = msg(toolBundle, "description");
        if (msg.isEmpty()) {
            Description description = model.getToolClass().getAnnotation(Description.class);
            if (description != null) {
                msg = description.value();
            }
        }
        return msg;
    }

    private String getSections(ToolModel<?> model) {
        RemainingSections sections = model.getToolClass().getAnnotation(RemainingSections.class);
        if (sections != null) {
            return sections.value();
        }
        return "";
    }

    private String getOptionDescription(ToolModel<?> model, OptionModel<?> opt) {
        ResourceBundle toolBundle = getToolBundle(model);
        String msg = msg(toolBundle, "option."+opt.getLongName());
        if (msg.isEmpty()) {
            Description description = opt.getArgument().getSetter().getAnnotation(Description.class);
            if (description != null) {
                msg = description.value();
            }
        }
        return msg;
    }

    private String getCeylonInvocation(ToolModel<?> model) {
        return getName(model).isEmpty() ? Tools.progName(): Tools.progName() + " " + model.getName();
    }


}