package org.truffleruby.core.inlined;

import org.truffleruby.RubyLanguage;
import org.truffleruby.core.string.StringNodes;
import org.truffleruby.language.dispatch.RubyCallNodeParameters;
import org.truffleruby.language.library.RubyStringLibrary;
import org.truffleruby.language.methods.LookupMethodOnSelfNode;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;

public abstract class InlinedNotEqualNode extends BinaryInlinedOperationNode {
    protected static final String METHOD = "!=";

    final Assumption integerNotEqualAssumption;
    final Assumption floatNotEqualAssumption;

    public InlinedNotEqualNode(RubyLanguage language, RubyCallNodeParameters callNodeParameters) {
        super(language, callNodeParameters);
        this.integerNotEqualAssumption = language.coreMethodAssumptions.integerNotEqualAssumption;
        this.floatNotEqualAssumption = language.coreMethodAssumptions.floatNotEqualAssumption;
    }

    @Specialization(assumptions = { "assumptions", "integerNotEqualAssumption" })
    protected boolean intEqual(int a, int b) {
        return a != b;
    }

    @Specialization(assumptions = { "assumptions", "integerNotEqualAssumption" })
    protected boolean longEqual(long a, long b) {
        return a != b;
    }

    @Specialization(assumptions = { "assumptions", "floatNotEqualAssumption" })
    protected boolean doDouble(double a, double b) {
        return a != b;
    }

    @Specialization(assumptions = { "assumptions", "integerNotEqualAssumption" })
    protected boolean longDouble(long a, double b) {
        return a != b;
    }

    @Specialization(assumptions = { "assumptions", "floatNotEqualAssumption" })
    protected boolean doubleLong(double a, long b) {
        return a != b;
    }

    @Specialization(
            guards = {
                    "stringsSelf.isRubyString(self)",
                    "stringsB.isRubyString(b)",
                    //"lookupNode.lookupProtected(frame, self, METHOD).isUndefined()"
            },
            assumptions = "assumptions")
    protected boolean stringEqual(VirtualFrame frame, Object self, Object b,
            @CachedLibrary(limit = "2") RubyStringLibrary stringsSelf,
            @CachedLibrary(limit = "2") RubyStringLibrary stringsB,
            @Cached LookupMethodOnSelfNode lookupNode,
            @Cached StringNodes.StringEqualNode stringEqualNode) {
        return !stringEqualNode.executeStringEqual(stringsSelf.getRope(self), stringsB.getRope(b));
    }

    @Specialization
    protected Object fallback(VirtualFrame frame, Object a, Object b) {
        return rewriteAndCall(frame, a, b);
    }
}
