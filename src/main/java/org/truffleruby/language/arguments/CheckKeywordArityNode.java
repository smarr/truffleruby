/*
 * Copyright (c) 2013, 2018 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.arguments;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

import org.truffleruby.collections.ConsumerNode;
import org.truffleruby.core.hash.HashNodes.EachKeyNode;
import org.truffleruby.language.RubyGuards;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.methods.Arity;

public class CheckKeywordArityNode extends RubyNode {

    private final Arity arity;

    @Child private ReadUserKeywordsHashNode readUserKeywordsHashNode;
    @Child private EachKeyNode eachKeyNode;

    private final BranchProfile receivedKeywordsProfile = BranchProfile.create();
    private final BranchProfile basicArityCheckFailedProfile = BranchProfile.create();

    public CheckKeywordArityNode(Arity arity) {
        this.arity = arity;
        this.readUserKeywordsHashNode = new ReadUserKeywordsHashNode(arity.getRequired());
        this.eachKeyNode = EachKeyNode.create(new CheckKeywordArgumentsNode(arity));

    }

    @Override
    public void doExecuteVoid(VirtualFrame frame) {
        final Object keywordArguments = readUserKeywordsHashNode.execute(frame);

        int given = RubyArguments.getArgumentsCount(frame);

        if (keywordArguments != null) {
            receivedKeywordsProfile.enter();
            given -= 1;
        }

        if (!CheckArityNode.checkArity(arity, given)) {
            basicArityCheckFailedProfile.enter();
            throw new RaiseException(getContext(), coreExceptions().argumentError(given, arity.getRequired(), this));
        }

        if (keywordArguments != null) {
            receivedKeywordsProfile.enter();
            eachKeyNode.executeEachKey(frame, (DynamicObject) keywordArguments);
        }
    }

    @Override
    public Object execute(VirtualFrame frame) {
        doExecuteVoid(frame);
        return nil();
    }

    private static class CheckKeywordArgumentsNode extends ConsumerNode {

        private final boolean checkAllowedKeywords;
        private final boolean doesNotAcceptExtraArguments;
        private final int required;
        @CompilationFinal(dimensions = 1) private final DynamicObject[] allowedKeywords;

        private final ConditionProfile isSymbolProfile = ConditionProfile.createBinaryProfile();
        private final BranchProfile tooManyKeywordsProfile = BranchProfile.create();
        private final BranchProfile unknownKeywordProfile;

        public CheckKeywordArgumentsNode(Arity arity) {
            checkAllowedKeywords = !arity.hasKeywordsRest();
            doesNotAcceptExtraArguments = !arity.hasRest() && arity.getOptional() == 0;
            required = arity.getRequired();
            allowedKeywords = checkAllowedKeywords ? keywordsAsSymbols(arity) : null;
            unknownKeywordProfile = checkAllowedKeywords ? BranchProfile.create() : null;
        }

        @Override
        public void accept(VirtualFrame frame, Object key) {
            if (isSymbolProfile.profile(RubyGuards.isRubySymbol(key))) {
                if (checkAllowedKeywords && !keywordAllowed(key)) {
                    unknownKeywordProfile.enter();
                    throw new RaiseException(getContext(), coreExceptions().argumentErrorUnknownKeyword(key, this));
                }
            } else {
                final int given = RubyArguments.getArgumentsCount(frame); // -1 for keyword hash, +1 for reject Hash with non-Symbol key
                if (doesNotAcceptExtraArguments && given > required) {
                    tooManyKeywordsProfile.enter();
                    throw new RaiseException(getContext(), coreExceptions().argumentError(given, required, this));
                }
            }
        }

        @ExplodeLoop
        private boolean keywordAllowed(Object keyword) {
            for (int i = 0; i < allowedKeywords.length; i++) {
                if (allowedKeywords[i] == keyword) {
                    return true;
                }
            }

            return false;
        }

        private DynamicObject[] keywordsAsSymbols(Arity arity) {
            final String[] names = arity.getKeywordArguments();
            final DynamicObject[] symbols = new DynamicObject[names.length];
            for (int i = 0; i < names.length; i++) {
                symbols[i] = getSymbol(names[i]);
            }
            return symbols;
        }

    }

}
