/*
 * Copyright (c) 2016, 2021 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.inlined;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeUtil;

import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.array.ArrayUtils;
import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.dispatch.RubyAbstractCallNode;
import org.truffleruby.language.dispatch.RubyCallNode;
import org.truffleruby.language.dispatch.RubyCallNodeParameters;
import org.truffleruby.language.dispatch.RubyTrivialCallNode;

public abstract class InlinedReplaceableNode extends RubyContextSourceNode {

    private final RubyCallNodeParameters parameters;

    @CompilationFinal(dimensions = 1) protected final Assumption[] assumptions;

    private RubyAbstractCallNode replacedBy = null;

    protected InlinedReplaceableNode(
            RubyLanguage language,
            RubyCallNodeParameters callNodeParameters,
            Assumption... assumptions) {
        this.parameters = callNodeParameters;

        this.assumptions = new Assumption[1 + assumptions.length];
        this.assumptions[0] = language.traceFuncUnusedAssumption.getAssumption();
        ArrayUtils.arraycopy(assumptions, 0, this.assumptions, 1, assumptions.length);

    }

    protected RubyAbstractCallNode rewriteToCallNode() {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        return atomic(() -> {
            // Check if we are still in the AST
            boolean found = !NodeUtil.forEachChild(getParent(), node -> node != this);

            if (found) {
                // We need to pass the updated children of this node to the call node
                RubyCallNodeParameters params = parameters.withReceiverAndArguments(
                        getReceiverNode(),
                        getArgumentNodes(),
                        getBlockNode());
                if (params.isTrivial()) {
                    RubyTrivialCallNode callNode = new RubyTrivialCallNode(params);
                    callNode.unsafeSetSourceSection(getSourceIndexLength());
                    replacedBy = callNode;
                    return replace(callNode, this + " could not be executed inline");
                } else {
                    RubyCallNode callNode = new RubyCallNode(params);
                    callNode.unsafeSetSourceSection(getSourceIndexLength());
                    replacedBy = callNode;
                    return replace(callNode, this + " could not be executed inline");
                }
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
