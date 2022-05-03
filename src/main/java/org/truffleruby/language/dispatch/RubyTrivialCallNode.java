package org.truffleruby.language.dispatch;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.array.ArrayAppendOneNode;
import org.truffleruby.core.array.AssignableNode;
import org.truffleruby.core.cast.BooleanCastNode;
import org.truffleruby.core.cast.BooleanCastNodeGen;
import org.truffleruby.core.string.FrozenStrings;
import org.truffleruby.core.symbol.RubySymbol;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.RubyGuards;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.arguments.ArgumentsDescriptor;
import org.truffleruby.language.arguments.EmptyArgumentsDescriptor;
import org.truffleruby.language.arguments.KeywordArgumentsDescriptor;
import org.truffleruby.language.arguments.RubyArguments;
import org.truffleruby.language.literal.NilLiteralNode;
import org.truffleruby.language.methods.InternalMethod;
import org.truffleruby.language.methods.LookupMethodOnSelfNode;

import java.util.Map;

import static org.truffleruby.language.dispatch.DispatchConfiguration.PRIVATE;
import static org.truffleruby.language.dispatch.DispatchConfiguration.PRIVATE_RETURN_MISSING;
import static org.truffleruby.language.dispatch.DispatchConfiguration.PROTECTED;

public class RubyTrivialCallNode extends RubyAbstractCallNode implements AssignableNode {
    private final String methodName;

    @Child private RubyNode receiver;
    @Children private final RubyNode[] arguments;

    private final DispatchConfiguration dispatchConfig;

    @Child private DispatchNode dispatch;
    @Child private RubyTrivialCallNode.DefinedNode definedNode;

    public RubyTrivialCallNode(RubyCallNodeParameters parameters) {
        super(parameters.isSplatted(), parameters.getDescriptor());
        assert !parameters.isSplatted();
        assert null == parameters.getBlock();
        assert !parameters.isVCall();
        assert !parameters.isSafeNavigation();
        assert !parameters.isAttrAssign();

        this.methodName = parameters.getMethodName();
        this.receiver = parameters.getReceiver();
        this.arguments = parameters.getArguments();

        this.dispatchConfig = parameters.isIgnoreVisibility() ? PRIVATE : PROTECTED;
    }

    public RubyNode[] getArguments() {
        return arguments;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        final Object receiverObject = receiver.execute(frame);
        Object[] rubyArgs = RubyArguments.allocate(arguments.length);
        RubyArguments.setSelf(rubyArgs, receiverObject);

        ArgumentsDescriptor descriptor = this.descriptor;
        boolean ruby2KeywordsHash = false;
        executeArguments(frame, rubyArgs);

        RubyArguments.setBlock(rubyArgs, nil);

        return doCall(frame, receiverObject, descriptor, rubyArgs, ruby2KeywordsHash);
    }

    @Override
    public void assign(VirtualFrame frame, Object value) {
        assert (getLastArgumentNode() instanceof NilLiteralNode &&
                ((NilLiteralNode) getLastArgumentNode()).isImplicit()) : getLastArgumentNode();

        final Object receiverObject = receiver.execute(frame);
        Object[] rubyArgs = RubyArguments.allocate(arguments.length);
        RubyArguments.setSelf(rubyArgs, receiverObject);

        executeArguments(frame, rubyArgs);

        assert RubyArguments.getLastArgument(rubyArgs) == nil;
        RubyArguments.setLastArgument(rubyArgs, value);

        RubyArguments.setBlock(rubyArgs, nil);

        // no ruby2_keywords behavior for assign
        doCall(frame, receiverObject, descriptor, rubyArgs, false);
    }

    public Object doCall(VirtualFrame frame, Object receiverObject, ArgumentsDescriptor descriptor, Object[] rubyArgs,
                         boolean ruby2KeywordsHash) {
        // Remove empty kwargs in the caller, so the callee does not need to care about this special case
        if (descriptor instanceof KeywordArgumentsDescriptor && emptyKeywordArguments(rubyArgs)) {
            rubyArgs = removeEmptyKeywordArguments(rubyArgs);
            descriptor = EmptyArgumentsDescriptor.INSTANCE;
        }
        RubyArguments.setDescriptor(rubyArgs, descriptor);

        if (dispatch == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            dispatch = insert(DispatchNode.create(dispatchConfig));
        }

        final Object returnValue = dispatch.dispatch(frame, receiverObject, methodName, rubyArgs,
                ruby2KeywordsHash ? this : null);

        assert RubyGuards.assertIsValidRubyValue(returnValue);
        return returnValue;
    }

    public Object executeWithArgumentsEvaluated(VirtualFrame frame, Object receiverObject, Object blockObject,
                                                Object[] argumentsObjects) {
        assert !isSplatted;
        Object[] rubyArgs = RubyArguments.allocate(argumentsObjects.length);
        RubyArguments.setSelf(rubyArgs, receiverObject);
        RubyArguments.setBlock(rubyArgs, blockObject);
        RubyArguments.setArguments(rubyArgs, argumentsObjects);
        return doCall(frame, receiverObject, descriptor, rubyArgs, false);
    }

    @ExplodeLoop
    private void executeArguments(VirtualFrame frame, Object[] rubyArgs) {
        for (int i = 0; i < arguments.length; i++) {
            RubyArguments.setArgument(rubyArgs, i, arguments[i].execute(frame));
        }
    }

    @Override
    public Object isDefined(VirtualFrame frame, RubyLanguage language, RubyContext context) {
        if (definedNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            definedNode = insert(new RubyTrivialCallNode.DefinedNode());
        }

        return definedNode.isDefined(frame, context);
    }

    public String getName() {
        return methodName;
    }

    public boolean isVCall() {
        return false;
    }

    public boolean hasLiteralBlock() {
        return false;
    }

    private RubyNode getLastArgumentNode() {
        final RubyNode lastArg = arguments[arguments.length - 1];
        if (isSplatted && lastArg instanceof ArrayAppendOneNode) {
            return ((ArrayAppendOneNode) lastArg).getValueNode();
        }
        return lastArg;
    }

    @Override
    public AssignableNode toAssignableNode() {
        return this;
    }

    @Override
    public Map<String, Object> getDebugProperties() {
        final Map<String, Object> map = super.getDebugProperties();
        map.put("methodName", methodName);
        return map;
    }

    private class DefinedNode extends RubyBaseNode {

        private final RubySymbol methodNameSymbol = getSymbol(methodName);

        @Child private DispatchNode respondToMissing = DispatchNode.create(PRIVATE_RETURN_MISSING);
        @Child private BooleanCastNode respondToMissingCast = BooleanCastNodeGen.create(null);


        @Child private LookupMethodOnSelfNode lookupMethodNode = LookupMethodOnSelfNode.create();

        private final ConditionProfile receiverDefinedProfile = ConditionProfile.create();
        private final BranchProfile argumentNotDefinedProfile = BranchProfile.create();
        private final BranchProfile allArgumentsDefinedProfile = BranchProfile.create();
        private final BranchProfile receiverExceptionProfile = BranchProfile.create();
        private final ConditionProfile methodNotFoundProfile = ConditionProfile.create();

        @ExplodeLoop
        public Object isDefined(VirtualFrame frame, RubyContext context) {
            if (receiverDefinedProfile.profile(receiver.isDefined(frame, getLanguage(), context) == nil)) {
                return nil;
            }

            for (RubyNode argument : arguments) {
                if (argument.isDefined(frame, getLanguage(), context) == nil) {
                    argumentNotDefinedProfile.enter();
                    return nil;
                }
            }

            allArgumentsDefinedProfile.enter();

            final Object receiverObject;

            try {
                receiverObject = receiver.execute(frame);
            } catch (Exception e) {
                receiverExceptionProfile.enter();
                return nil;
            }

            final InternalMethod method = lookupMethodNode.execute(frame, receiverObject, methodName, dispatchConfig);

            if (methodNotFoundProfile.profile(method == null)) {
                final Object r = respondToMissing.call(receiverObject, "respond_to_missing?", methodNameSymbol, false);

                if (r != DispatchNode.MISSING && !respondToMissingCast.execute(r)) {
                    return nil;
                }
            }

            return FrozenStrings.METHOD;
        }
    }
}
