/*
 * Copyright (c) 2015, 2020 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.constants;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.ConditionProfile;
import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.module.ConstantLookupResult;
import org.truffleruby.core.module.ModuleOperations;
import org.truffleruby.core.module.RubyModule;
import org.truffleruby.core.string.FrozenStrings;
import org.truffleruby.language.LexicalScope;
import org.truffleruby.language.RubyConstant;
import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.control.RaiseException;

import com.oracle.truffle.api.frame.VirtualFrame;

/** Read a constant using the current lexical scope: CONST */
public abstract class ReadConstantWithLexicalScopeNode extends RubyContextSourceNode {

    private final LexicalScope lexicalScope;
    private final String name;

    @Child private LookupConstantWithLexicalScopeNode lookupConstantNode;
    @Child private GetConstantNode getConstantNode;

    public ReadConstantWithLexicalScopeNode(LexicalScope lexicalScope, String name) {
        this.lexicalScope = lexicalScope;
        this.name = name;
    }

    @Specialization(assumptions = "constant.getAssumptions()", guards = "constantValue != null")
    protected Object perfectConstant(
            @Cached("doLookup()") ConstantLookupResult constant,
            @Cached("getPerfectValue(constant)") Object constantValue) {
        return constantValue;
    }

    protected Object getPerfectValue(ConstantLookupResult constant) {
        if (constant == null || constant.isDeprecated()) {
            return null;
        }
        RubyConstant c = constant.getConstant();
        if (c == null || !c.hasValue()) {
            return null;
        }
        return c.getValue();
    }

    @Specialization
    public Object notPerfect(VirtualFrame frame) {
        final RubyModule module = lexicalScope.getLiveModule();
        if (lookupConstantNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            lookupConstantNode = LookupConstantWithLexicalScopeNodeGen.create(lexicalScope, name);
            getConstantNode = insert(GetConstantNode.create());
        }

        final RubyConstant constant = lookupConstantNode.lookupConstant(lexicalScope, module, name, true);
        return getConstantNode.executeGetConstant(lexicalScope, module, name, constant, lookupConstantNode);
    }

    @CompilerDirectives.TruffleBoundary
    protected ConstantLookupResult doLookup() {
        return ModuleOperations.lookupConstantWithLexicalScope(getContext(), lexicalScope, name);
    }

    @Override
    public Object isDefined(VirtualFrame frame, RubyLanguage language, RubyContext context) {
        final RubyConstant constant;
        try {
            if (lookupConstantNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lookupConstantNode = LookupConstantWithLexicalScopeNodeGen.create(lexicalScope, name);
                getConstantNode = insert(GetConstantNode.create());
            }
            constant = lookupConstantNode.executeLookupConstant();
        } catch (RaiseException e) {
            if (e.getException().getLogicalClass() == coreLibrary().nameErrorClass) {
                // private constant
                return nil;
            }
            throw e;
        }

        if (ModuleOperations.isConstantDefined(constant)) {
            return FrozenStrings.CONSTANT;
        } else {
            return nil;
        }
    }

}
