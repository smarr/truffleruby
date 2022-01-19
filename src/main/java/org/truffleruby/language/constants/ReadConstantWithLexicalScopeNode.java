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

import com.oracle.truffle.api.Assumption;
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
public final class ReadConstantWithLexicalScopeNode extends RubyContextSourceNode {

    private final LexicalScope lexicalScope;
    private final String name;

    @Child private LookupConstantWithLexicalScopeNode lookupConstantNode;
    @Child private GetConstantNode getConstantNode;

    @CompilerDirectives.CompilationFinal Assumption constantAssumption;
    @CompilerDirectives.CompilationFinal Object constantValue;

    public ReadConstantWithLexicalScopeNode(LexicalScope lexicalScope, String name) {
        this.lexicalScope = lexicalScope;
        this.name = name;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        // take cached result if possible
        if (constantValue != null && constantAssumption != null && constantAssumption.isValid()) {
            return constantValue;
        }

        if (constantAssumption == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            // we didn't previously do the lookup
            ConstantLookupResult lookupResult = ModuleOperations.lookupConstantWithLexicalScope(getContext(), lexicalScope, name);
            constantAssumption = lookupResult.getAssumptions();
            if (!lookupResult.isDeprecated()) {
                RubyConstant c = lookupResult.getConstant();
                if (c != null && c.hasValue()) {
                    return constantValue = c.getValue();
                }
            }
        }

        // we attempted a lookup previously, so, this isn't a perfect constant
        return notPerfect();
    }


    public Object notPerfect() {
        final RubyModule module = lexicalScope.getLiveModule();
        if (lookupConstantNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            lookupConstantNode = insert(LookupConstantWithLexicalScopeNodeGen.create(lexicalScope, name));
        }
        if (getConstantNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            getConstantNode = insert(GetConstantNode.create());
        }

        final RubyConstant constant = lookupConstantNode.lookupConstant(lexicalScope, module, name, true);
        return getConstantNode.executeGetConstant(lexicalScope, module, name, constant, lookupConstantNode);
    }

    @Override
    public Object isDefined(VirtualFrame frame, RubyLanguage language, RubyContext context) {
        final RubyConstant constant;
        try {
            if (lookupConstantNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lookupConstantNode = LookupConstantWithLexicalScopeNodeGen.create(lexicalScope, name);
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
