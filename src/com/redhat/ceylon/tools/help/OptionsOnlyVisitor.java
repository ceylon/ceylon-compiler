package com.redhat.ceylon.tools.help;

import java.util.Set;

import com.redhat.ceylon.common.tool.ArgumentModel;
import com.redhat.ceylon.common.tool.OptionModel;
import com.redhat.ceylon.tools.help.model.DescribedSection;
import com.redhat.ceylon.tools.help.model.Doc;
import com.redhat.ceylon.tools.help.model.Option;
import com.redhat.ceylon.tools.help.model.OptionsSection;
import com.redhat.ceylon.tools.help.model.SummarySection;
import com.redhat.ceylon.tools.help.model.SynopsesSection;
import com.redhat.ceylon.tools.help.model.Synopsis;
import com.redhat.ceylon.tools.help.model.Synopsis.NameAndSubtool;
import com.redhat.ceylon.tools.help.model.Visitor;

public class OptionsOnlyVisitor implements Visitor {

    private final Visitor visitor;
    private final Set<String> longOptionNames;

    public OptionsOnlyVisitor(Visitor visitor, Set<String> longOptionNames) {
        super();
        this.visitor = visitor;
        this.longOptionNames = longOptionNames;
    }

    public void start(Doc doc) {
        visitor.start(doc);
    }

    public void end(Doc doc) {
        visitor.end(doc);
    }

    public void visitOption(Option option) {
        if (longOptionNames == null || longOptionNames.contains(option.getOption().getLongName())) {
            visitor.visitOption(option);
        }
    }
    
    public void visitAdditionalSection(DescribedSection describedSection) {
        // Only visiting synopsis
    }

    public void startOptions(OptionsSection optionsSection) {
        // Only visiting synopsis
    }

    public void endOptions(OptionsSection optionsSection) {
        // Only visiting synopsis
    }

    public void visitSummary(SummarySection summarySection) {
        // Only visiting synopsis
    }

    public void startSynopses(SynopsesSection synopsesSection) {
        // Only visiting synopsis
    }

    public void startSynopsis(Synopsis synopsis) {
        // Only visiting synopsis
    }

    public void visitSynopsisOption(OptionModel<?> option) {
        // Only visiting synopsis
    }

    public void visitSynopsisArgument(ArgumentModel<?> option) {
        // Only visiting synopsis
    }

    public void visitSynopsisSubtool(NameAndSubtool option) {
        // Only visiting synopsis
    }

    public void endSynopsis(Synopsis synopsis) {
        // Only visiting synopsis
    }

    public void endSynopses(SynopsesSection synopsesSection) {
        // Only visiting synopsis
    }

    public void visitDescription(DescribedSection descriptionSection) {
        // Only visiting synopsis
    }
    
    
    
}
