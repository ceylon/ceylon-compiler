package com.redhat.ceylon.tools.help;

import org.tautua.markdownpapers.ast.Node;

import com.redhat.ceylon.common.tool.ArgumentModel;
import com.redhat.ceylon.common.tool.OptionModel;
import com.redhat.ceylon.common.tool.OptionModel.ArgumentType;
import com.redhat.ceylon.common.tool.WordWrap;
import com.redhat.ceylon.tools.help.model.DescribedSection;
import com.redhat.ceylon.tools.help.model.Doc;
import com.redhat.ceylon.tools.help.model.Option;
import com.redhat.ceylon.tools.help.model.OptionsSection;
import com.redhat.ceylon.tools.help.model.SummarySection;
import com.redhat.ceylon.tools.help.model.SynopsesSection;
import com.redhat.ceylon.tools.help.model.Synopsis;
import com.redhat.ceylon.tools.help.model.Synopsis.NameAndSubtool;
import com.redhat.ceylon.tools.help.model.Visitor;

public class PlainVisitor implements Visitor {

    private int numOptions;
    protected final WordWrap out;
    protected String ceylonName;
    boolean hadFirstArgument = false;
    private boolean hadOptions;

    public PlainVisitor(WordWrap wrap) {
        super();
        this.out = wrap;
    }

    private void markdown(Node doc) {
        PlaintextMarkdownVisitor markdownVisitor = new PlaintextMarkdownVisitor(out);
        doc.accept(markdownVisitor);
    }

    @Override
    public void start(Doc doc) {
        ceylonName = doc.getInvocation();
    }

    @Override
    public void end(Doc doc) {
        out.flush();
    }

    private String multiplicity(ArgumentModel<?> argument, String name) {
        name = "<" + name + ">";
        if (argument.getMultiplicity().isMultivalued()) {
            name += "...";
        }
        return name;
    }
    
    @Override
    public void startSynopsis(Synopsis synopsis) {
        int indent = out.getIndentFirstLine();
        String invocation = synopsis.getInvocation();
        out.setIndentRestLines(indent + invocation.length() + 1);
        out.append(invocation);
        hadFirstArgument = false;
        hadOptions = false;
    }
    
    @Override
    public void endSynopsis(Synopsis synopsis) {
        out.newline();
    }

    @Override
    public void visitSynopsisArgument(ArgumentModel<?> argument) {
        if (hadOptions && !hadFirstArgument) {
            out.append(" [--]");
            hadFirstArgument = true;
        }
        
        out.append(" ");
        String name = argument.getName();
        if (!argument.getMultiplicity().isRequired()) {
            out.append("[");
        }
        out.append("<" + name);
        if (argument.getMultiplicity().isMultivalued()) {
            out.append("...");
        }
        out.append(">");
        if (!argument.getMultiplicity().isRequired()) {
            out.append("]");
        }
    }

    @Override
    public void visitSynopsisSubtool(NameAndSubtool option) {
        out.append(" " + option.getName());
    }
    
    @Override
    public void visitSynopsisOption(OptionModel<?> option) {
        hadOptions = true;
        out.append(" ");
        final ArgumentModel<?> argument = option.getArgument();
        if (!argument.getMultiplicity().isRequired()) {
            out.append("[");
        }
        if (option.getLongName() != null) {
            out.append("--" + option.getLongName());
            if (option.getArgumentType() == ArgumentType.REQUIRED) {
                out.append("=");
                out.append(multiplicity(argument, argument.getName()));
            } else if (option.getArgumentType() == ArgumentType.OPTIONAL) {
                out.append("[=");
                out.append(multiplicity(argument, argument.getName()));
                out.append("]");
            }
        } else {
            out.append("-" + option.getShortName());
            if (option.getArgumentType() != ArgumentType.NOT_ALLOWED) {
                out.append(" ");
                out.append(multiplicity(argument, argument.getName()));
            }
        }
        if (!argument.getMultiplicity().isRequired()) {
            out.append("]");
        }
    }
    
    
    @Override
    public void startSynopses(SynopsesSection synopsesSection) {
        out.append(synopsesSection.getTitle().toUpperCase()).newline().newline();
        out.setIndent(8);
    }
    
    @Override
    public void endSynopses(SynopsesSection synopsesSection) {
        out.setIndent(0);
        out.newline();
        out.newline();   
    }
    
    @Override
    public void startOptions(OptionsSection optionsSection) {
        out.append(optionsSection.getTitle().toUpperCase()).newline().newline();
        out.setIndent(8);
    }

    @Override
    public void visitOption(Option option) {
        String shortName = option.getShortName();
        String longName = option.getLongName();
        String argumentName = option.getArgumentName();
        ArgumentType argumentType = option.getOption().getArgumentType();
        numOptions++;
        out.append(longName);
        if (argumentType == ArgumentType.OPTIONAL) {
            out.append("[");
        }
        if (argumentType != ArgumentType.NOT_ALLOWED) {
            out.append("=<" + argumentName + ">");
        }
        if (argumentType == ArgumentType.OPTIONAL) {
            out.append("]");
        }
        if (shortName != null) {
            out.append(", ");
            out.append(shortName);
            if (argumentType != ArgumentType.NOT_ALLOWED) {
                out.append(" ");
                if (argumentType == ArgumentType.OPTIONAL) {
                    out.append("[");
                }
                out.append("<" + argumentName + ">");
                if (argumentType == ArgumentType.OPTIONAL) {
                    out.append("]");
                }
            }
        }
        
        out.setIndent(12);
        out.newline();
        markdown(option.getDescription());    
        out.newline();
        out.setIndent(8);
    
    }

    @Override
    public void endOptions(OptionsSection optionsSection) {
        if(numOptions == 0) {
            out.append(ceylonName+" has no options").newline();
        }
        out.setIndent(0);
        out.newline();
    }
    
    @Override
    public void visitAdditionalSection(DescribedSection describedSection) {
        describedSection(describedSection);    
    }

    private void describedSection(DescribedSection describedSection) {
        markdown(describedSection.getTitle());
        markdown(describedSection.getDescription());
        out.setIndent(0);
        out.newline();
    }

    @Override
    public void visitSummary(SummarySection summarySection) {
        markdown(summarySection.getTitle());
        markdown(Markdown.markdown("`" + ceylonName + "` - " + summarySection.getSummary()));
        out.setIndent(0);
        out.newline();
    }

    @Override
    public void visitDescription(DescribedSection descriptionSection) {
        describedSection(descriptionSection);
    }


}
