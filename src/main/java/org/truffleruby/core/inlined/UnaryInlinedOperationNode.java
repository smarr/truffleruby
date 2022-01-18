/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.inlined;

import org.truffleruby.RubyLanguage;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.dispatch.RubyCallNodeParameters;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.dsl.NodeChild;

@NodeChild(value = "self", type = RubyNode.class)
public abstract class UnaryInlinedOperationNode extends InlinedOperationNode {

    public UnaryInlinedOperationNode(
            RubyLanguage language,
            RubyCallNodeParameters callNodeParameters) {
        super(language, callNodeParameters);
    }

    public UnaryInlinedOperationNode(
            RubyLanguage language,
            RubyCallNodeParameters callNodeParameters,
            Assumption assumption) {
        super(language, callNodeParameters, assumption);
    }

    public UnaryInlinedOperationNode(
            RubyLanguage language,
            RubyCallNodeParameters callNodeParameters,
            Assumption assumption1, Assumption assumption2) {
        super(language, callNodeParameters, assumption1, assumption2);
    }

    protected abstract RubyNode getSelf();

    @Override
    protected RubyNode getReceiverNode() {
        return getSelf();
    }

    @Override
    protected RubyNode[] getArgumentNodes() {
        return RubyNode.EMPTY_ARRAY;
    }

}
