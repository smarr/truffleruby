package org.truffleruby.core.supernodes;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import org.truffleruby.core.cast.BooleanCastNode;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.core.symbol.RubySymbol;
import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyGuards;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.arguments.EmptyArgumentsDescriptor;
import org.truffleruby.language.arguments.RubyArguments;
import org.truffleruby.language.dispatch.DispatchConfiguration;
import org.truffleruby.language.dispatch.DispatchNode;
import org.truffleruby.language.dispatch.InternalRespondToNode;
import org.truffleruby.language.methods.CallInternalMethodNode;
import org.truffleruby.language.methods.InternalMethod;
import org.truffleruby.language.methods.LookupMethodNode;
import org.truffleruby.language.objects.MetaClassNode;

import static org.truffleruby.language.dispatch.DispatchConfiguration.PUBLIC;

public class RespondToCheckAndCallOrElseNode extends RubyContextSourceNode {
    protected final RubySymbol lookupSymbol;
    protected final DispatchConfiguration respondToDispatchConfig;

    @Child protected RubyNode receiverExpr;
    @Child protected MetaClassNode receiverMetaclassNode;
    @Child protected LookupMethodNode respondToMethodLookup;
    @Child protected LookupMethodNode targetMethodLookup;

    @Child protected CallInternalMethodNode callTargetMethodNode;
    @Children protected RubyNode[] targetArguments;

    @Child protected InternalRespondToNode dispatchRespondToMissing;
    @Child protected BooleanCastNode castMissingResultNode;
    @Child protected DispatchNode respondToMissingNode;

    public RespondToCheckAndCallOrElseNode(RespondToCallNode respondToCall, RubyNode[] targetArguments) {
        this.receiverExpr = respondToCall.getReceiver();
        this.lookupSymbol = respondToCall.getLookupSymbol();
        this.receiverMetaclassNode = MetaClassNode.create();
        this.respondToDispatchConfig = respondToCall.getRespondToDispatchConfig();
        this.respondToMethodLookup = LookupMethodNode.create();
        this.targetArguments = targetArguments;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        final Object receiverObject = receiverExpr.execute(frame);
        final RubyClass receiverMetaclass = receiverMetaclassNode.execute(receiverObject);

        InternalMethod targetMethod = lookupTargetMethod(frame, receiverObject, receiverMetaclass);
        if (targetMethod == null) {
            return receiverObject;
        }

        return dispatchTarget(frame, receiverObject, targetMethod);
    }

    private InternalMethod lookupTargetMethod(VirtualFrame frame, Object receiverObject, RubyClass receiverMetaclass) {
        Object[] respondToArgs = RubyArguments.allocate(1);
        RubyArguments.setSelf(respondToArgs, receiverObject);
        RubyArguments.setArgument(respondToArgs, 0, lookupSymbol);
        RubyArguments.setDescriptor(respondToArgs, EmptyArgumentsDescriptor.INSTANCE);

        final InternalMethod method = checkRespondToAndReturnLookupResult(
                frame, receiverObject, respondToArgs, receiverMetaclass);
        return method;
    }

    @ExplodeLoop
    private void executeTargetArguments(VirtualFrame frame, Object[] rubyArgs) {
        for (int i = 0; i < targetArguments.length; i++) {
            RubyArguments.setArgument(rubyArgs, i, targetArguments[i].execute(frame));
        }
    }

    private Object dispatchTarget(VirtualFrame frame, Object receiverObject, InternalMethod targetMethod) {
        if (callTargetMethodNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            callTargetMethodNode = CallInternalMethodNode.create();
        }

        Object[] rubyArgs = RubyArguments.allocate(targetArguments.length);
        RubyArguments.setSelf(rubyArgs, receiverObject);
        executeTargetArguments(frame, rubyArgs);

        return doCall(frame, receiverObject, rubyArgs, targetMethod);
    }

    private Object doCall(VirtualFrame frame, Object receiverObject, Object[] rubyArgs, InternalMethod targetMethod) {
        RubyArguments.setDescriptor(rubyArgs, EmptyArgumentsDescriptor.INSTANCE);

        RubyArguments.setMethod(rubyArgs, targetMethod);
        // TODO: consider supporting FrameAndVariablesSendingNode.getFrameOrStorageIfRequired(.)
        // RubyArguments.setCallerData(rubyArgs, getFrameOrStorageIfRequired(frame));

        assert RubyArguments.assertFrameArguments(rubyArgs);
        Object returnValue = callTargetMethodNode.execute(frame, targetMethod, receiverObject, rubyArgs, null);

        assert RubyGuards.assertIsValidRubyValue(returnValue);
        return returnValue;
    }

    private InternalMethod checkRespondToAndReturnLookupResult(
            VirtualFrame frame, Object receiverObject, Object[] respondToArgs, RubyClass receiverMetaclass) {
        assert RubyArguments.getSelf(respondToArgs) == receiverObject;

        final InternalMethod respondTo = respondToMethodLookup.execute(
                frame, receiverMetaclass, "respond_to?", respondToDispatchConfig);

        if (respondTo != null && respondTo.isBuiltIn()) {
            assert respondTo == getContext().getCoreMethods().KERNEL_RESPOND_TO;
            return handleKernelRespondTo(frame, receiverObject, receiverMetaclass);
        }

        throw new RuntimeException("Not yet implemented");
//        if (methodMissing.profile(method == null || method.isUndefined())) {
//            return handleMethodMissing(frame, receiverObject, rubyArgs);
//        }
//
//        return handleCustomRespondTo(frame, receiverObject, rubyArgs, method);
    }

    private InternalMethod handleKernelRespondTo(VirtualFrame frame, Object receiverObject, RubyClass receiverMetaclass) {
        if (targetMethodLookup == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            targetMethodLookup = LookupMethodNode.create();
        }

        String methodName = lookupSymbol.getString();
        InternalMethod method = targetMethodLookup.execute(frame, receiverMetaclass, methodName, PUBLIC);
        if (method != null && method.isDefined() && method.isImplemented()) {
            return method;
        } else {
            return handleRespondToMissing(frame, receiverObject, receiverMetaclass);
        }
    }

    private InternalMethod handleRespondToMissing(VirtualFrame frame, Object self, RubyClass receiverMetaclass) {
        if (dispatchRespondToMissing == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            dispatchRespondToMissing = InternalRespondToNode.create();
        }

        if (dispatchRespondToMissing.execute(frame, self, "respond_to_missing?")) {
            if (castMissingResultNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                castMissingResultNode = BooleanCastNode.create();
                respondToMissingNode = DispatchNode.create();
            }
            boolean shouldHaveMethod = castMissingResultNode.execute(respondToMissingNode.call(self, "respond_to_missing?",
                    lookupSymbol, false));
            if (shouldHaveMethod) {
                if (targetMethodLookup == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    targetMethodLookup = LookupMethodNode.create();
                }

                String methodName = lookupSymbol.getString();
                InternalMethod method = targetMethodLookup.execute(frame, receiverMetaclass, methodName, PUBLIC);
                if (method != null && method.isDefined() && method.isImplemented()) {
                    return method;
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new RuntimeException("not yet implemented");
            }
        }
        return null;
    }
}
