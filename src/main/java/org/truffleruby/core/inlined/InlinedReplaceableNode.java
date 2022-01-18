/*
 * Copyright (c) 2016, 2020 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.inlined;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.AssumptionGroup;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeUtil;

import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.array.ArrayUtils;
import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.dispatch.RubyCallNode;
import org.truffleruby.language.dispatch.RubyCallNodeParameters;

public abstract class InlinedReplaceableNode extends RubyContextSourceNode {

    private final RubyCallNodeParameters parameters;

    protected final Assumption assumptions;

    private RubyCallNode replacedBy = null;

    protected InlinedReplaceableNode(
            RubyLanguage language,
            RubyCallNodeParameters callNodeParameters) {
        this.parameters = callNodeParameters;
        this.assumptions = language.traceFuncUnusedAssumption.getAssumption();
    }

    protected InlinedReplaceableNode(
            RubyLanguage language,
            RubyCallNodeParameters callNodeParameters,
            Assumption assumption) {
        this.parameters = callNodeParameters;
        this.assumptions = AssumptionGroup.create(new Assumption[] {language.traceFuncUnusedAssumption.getAssumption(), assumption});
    }

    protected InlinedReplaceableNode(
            RubyLanguage language,
            RubyCallNodeParameters callNodeParameters,
            Assumption assumption1,
            Assumption assumption2) {
        this.parameters = callNodeParameters;

        this.assumptions = AssumptionGroup.create(new Assumption[] {
                language.traceFuncUnusedAssumption.getAssumption(),
                assumption1, assumption2});
    }

    protected InlinedReplaceableNode(
            RubyLanguage language,
            RubyCallNodeParameters callNodeParameters,
            Assumption assumption1,
            Assumption assumption2,
            Assumption assumption3) {
        this.parameters = callNodeParameters;

        this.assumptions = AssumptionGroup.create(new Assumption[] {
                language.traceFuncUnusedAssumption.getAssumption(),
                assumption1, assumption2, assumption3});
    }

    protected RubyCallNode rewriteToCallNode() {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        return atomic(() -> {
            // Check if we are still in the AST
            boolean found = !NodeUtil.forEachChild(getParent(), node -> node != this);

            if (found) {
                // We need to pass the updated children of this node to the call node
                RubyCallNode callNode = new RubyCallNode(parameters.withReceiverAndArguments(
                        getReceiverNode(),
                        getArgumentNodes(),
                        getBlockNode()));
                callNode.unsafeSetSourceSection(getSourceIndexLength());
                replacedBy = callNode;
                return replace(callNode, this + " could not be executed inline");
            } else {
                return replacedBy;
            }
        });
    }

    protected abstract RubyNode getReceiverNode();

    protected abstract RubyNode[] getArgumentNodes();

    protected RubyNode getBlockNode() {
        return parameters.getBlock();
    }

    @Override
    public Object isDefined(VirtualFrame frame, RubyLanguage language, RubyContext context) {
        return rewriteToCallNode().isDefined(frame, language, context);
    }
}
