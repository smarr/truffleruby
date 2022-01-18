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
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;

import org.truffleruby.RubyLanguage;
import org.truffleruby.core.array.ArrayUtils;
import org.truffleruby.language.dispatch.RubyCallNodeParameters;
import org.truffleruby.language.methods.TranslateExceptionNode;

public abstract class InlinedOperationNode extends InlinedReplaceableNode {

    public InlinedOperationNode(
            RubyLanguage language,
            RubyCallNodeParameters callNodeParameters) {
        super(language, callNodeParameters);
    }

    public InlinedOperationNode(
            RubyLanguage language,
            RubyCallNodeParameters callNodeParameters,
            Assumption assumption) {
        super(language, callNodeParameters, assumption);
    }

    public InlinedOperationNode(
            RubyLanguage language,
            RubyCallNodeParameters callNodeParameters,
            Assumption assumption1, Assumption assumption2) {
        super(language, callNodeParameters, assumption1, assumption2);
    }

    public InlinedOperationNode(
            RubyLanguage language,
            RubyCallNodeParameters callNodeParameters,
            Assumption assumption1, Assumption assumption2, Assumption assumption3) {
        super(language, callNodeParameters, assumption1, assumption2, assumption3);
    }

    protected Object rewriteAndCall(VirtualFrame frame, Object receiver, Object... arguments) {
        return rewriteAndCallWithBlock(frame, receiver, nil, arguments);
    }

    protected Object rewriteAndCallWithBlock(VirtualFrame frame, Object receiver, Object block,
            Object... arguments) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        if (getContext().getOptions().BASICOPS_LOG_REWRITE) {
            RubyLanguage.LOGGER.info(
                    "Rewrite " + this + " with arguments" + TranslateExceptionNode
                            .argumentsToString(new StringBuilder(), ArrayUtils.unshift(arguments, receiver)));
        }
        return rewriteToCallNode().executeWithArgumentsEvaluated(frame, receiver, block, arguments);
    }

    protected CoreMethods coreMethods() {
        return getContext().getCoreMethods();
    }
}
