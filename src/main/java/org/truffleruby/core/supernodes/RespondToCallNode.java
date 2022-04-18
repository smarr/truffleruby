package org.truffleruby.core.supernodes;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import org.truffleruby.core.cast.BooleanCastNode;
import org.truffleruby.core.exception.ExceptionOperations;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.core.symbol.RubySymbol;
import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyGuards;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.arguments.EmptyArgumentsDescriptor;
import org.truffleruby.language.arguments.RubyArguments;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.dispatch.DispatchConfiguration;
import org.truffleruby.language.dispatch.DispatchNode;
import org.truffleruby.language.dispatch.InternalRespondToNode;
import org.truffleruby.language.dispatch.RubyCallNodeParameters;
import org.truffleruby.language.literal.ObjectLiteralNode;
import org.truffleruby.language.methods.CallForeignMethodNode;
import org.truffleruby.language.methods.CallInternalMethodNode;
import org.truffleruby.language.methods.InternalMethod;
import org.truffleruby.language.methods.LookupMethodNode;
import org.truffleruby.language.objects.MetaClassNode;

import static org.truffleruby.language.dispatch.DispatchConfiguration.PRIVATE;
import static org.truffleruby.language.dispatch.DispatchConfiguration.PROTECTED;
import static org.truffleruby.language.dispatch.DispatchConfiguration.PUBLIC;
import static org.truffleruby.language.dispatch.MissingBehavior.CALL_METHOD_MISSING;

public class RespondToCallNode extends RubyContextSourceNode {
    protected final RubySymbol lookupSymbol;
    protected final DispatchConfiguration respondToDispatchConfig;

    @Child protected RubyNode receiverExpr;
    @Child protected MetaClassNode metaclassNode;
    @Child protected LookupMethodNode respondToMethodLookup;
    @Child protected LookupMethodNode targetMethodLookup;
    @Child protected InternalRespondToNode dispatchRespondToMissing;

    @CompilationFinal protected ConditionProfile respondToMissingProfile;
    @CompilationFinal protected BranchProfile methodMissingMissing;

    @Child protected CallInternalMethodNode callNode;

    @Child protected BooleanCastNode castMissingResultNode;
    @Child protected DispatchNode respondToMissingNode;
    @Child protected DispatchNode callMethodMissing;

    @Child protected CallForeignMethodNode callForeign;

    public RespondToCallNode(RubyCallNodeParameters parameters) {
        this.receiverExpr = parameters.getReceiver();
        RubyNode[] args = parameters.getArguments();
        assert args.length == 1 && args[0] instanceof ObjectLiteralNode;
        lookupSymbol = (RubySymbol) ((ObjectLiteralNode) args[0]).getObject();
        this.respondToDispatchConfig = parameters.isIgnoreVisibility() ? PRIVATE : PROTECTED;

        assert parameters.getBlock() == null;
        assert !parameters.isSafeNavigation();
        assert !parameters.isSplatted();
        assert !parameters.isAttrAssign();

        metaclassNode = MetaClassNode.create();
        respondToMethodLookup = LookupMethodNode.create();
    }

    public DispatchConfiguration getRespondToDispatchConfig() {
        return respondToDispatchConfig;
    }

    public RubyNode getReceiver() {
        return receiverExpr;
    }

    public RubySymbol getLookupSymbol() {
        return lookupSymbol;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        final Object receiverObject = receiverExpr.execute(frame);

        Object[] rubyArgs = RubyArguments.allocate(1);
        RubyArguments.setSelf(rubyArgs, receiverObject);
        RubyArguments.setArgument(rubyArgs, 0, lookupSymbol);

        return doCall(frame, receiverObject, rubyArgs);
    }

    public Object doCall(VirtualFrame frame, Object receiverObject, Object[] rubyArgs) {
        RubyArguments.setDescriptor(rubyArgs, EmptyArgumentsDescriptor.INSTANCE);

        final Object returnValue = dispatch(frame, receiverObject, rubyArgs);
        assert RubyGuards.assertIsValidRubyValue(returnValue);
        return returnValue;
    }

    private Object dispatch(VirtualFrame frame, Object receiverObject, Object[] rubyArgs) {
        assert RubyArguments.getSelf(rubyArgs) == receiverObject;

        final RubyClass receiverMetaclass = metaclassNode.execute(receiverObject);
        final InternalMethod method = respondToMethodLookup.execute(frame, receiverMetaclass, "respond_to?", respondToDispatchConfig);

        if (method != null && method.isBuiltIn()) {
            assert method == getContext().getCoreMethods().KERNEL_RESPOND_TO;
            return handleKernelRespondTo(frame, receiverObject, receiverMetaclass);
        }

        if (method == null || method.isUndefined()) {
            return handleMethodMissing(frame, receiverObject, rubyArgs);
        }

        return handleCustomRespondTo(frame, receiverObject, rubyArgs, method);
    }

    private boolean handleKernelRespondTo(VirtualFrame frame, Object receiverObject, RubyClass receiverMetaclass) {
        if (targetMethodLookup == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            targetMethodLookup = LookupMethodNode.create();
        }
        String methodName = lookupSymbol.getString();
        InternalMethod method = targetMethodLookup.execute(frame, receiverMetaclass, methodName, PUBLIC);
        if (method != null && method.isDefined() && method.isImplemented()) {
            return true;
        } else {
            return handleRespondToMissing(frame, receiverObject);
        }
    }

    protected Object handleCustomRespondTo(VirtualFrame frame, Object receiverObject, Object[] rubyArgs, InternalMethod method) {
        if (callNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();;
            callNode = CallInternalMethodNode.create();
        }
        RubyArguments.setMethod(rubyArgs, method);

        // REM(sm): for now, I am not handling this. Here DispatchNode.getFrameOrStorageIfRequired(frame) was used
        // RubyArguments.setCallerData(rubyArgs, getFrameOrStorageIfRequired(frame));

        assert RubyArguments.assertFrameArguments(rubyArgs);
        return callNode.execute(frame, method, receiverObject, rubyArgs, null);
    }

    private boolean handleRespondToMissing(VirtualFrame frame, Object self) {
        if (respondToMissingProfile == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            respondToMissingProfile = ConditionProfile.createBinaryProfile();
            dispatchRespondToMissing = InternalRespondToNode.create();
            castMissingResultNode = BooleanCastNode.create();
            respondToMissingNode = DispatchNode.create();
        }

        if (respondToMissingProfile
                .profile(dispatchRespondToMissing.execute(frame, self, "respond_to_missing?"))) {
            return castMissingResultNode.execute(respondToMissingNode.call(self, "respond_to_missing?",
                    lookupSymbol, false));
        } else {
            return false;
        }
    }

    protected Object handleMethodMissing(VirtualFrame frame, Object self, Object[] rubyArgs) {
        assert respondToDispatchConfig.missingBehavior == CALL_METHOD_MISSING;

        // Both branches implicitly profile through lazy node creation
        if (RubyGuards.isForeignObject(self)) { // TODO (eregon, 16 Aug 2021) maybe use a final boolean on the class to know if foreign
            return callForeign(self, rubyArgs);
        } else {
            return callMethodMissing(frame, self, rubyArgs);
        }
    }

    protected Object callForeign(Object receiver, Object[] rubyArgs) {
        // profiles through lazy node creation
        final CallForeignMethodNode callForeignMethodNode = getCallForeignMethodNode();

        final Object block = RubyArguments.getBlock(rubyArgs);
        final Object[] arguments = RubyArguments.getPositionalArguments(rubyArgs, false);
        return callForeignMethodNode.execute(receiver, "respond_to?", block, arguments);
    }

    protected CallForeignMethodNode getCallForeignMethodNode() {
        if (callForeign == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            callForeign = insert(CallForeignMethodNode.create());
        }
        return callForeign;
    }

    protected DispatchNode getMethodMissingNode() {
        if (callMethodMissing == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            // #method_missing ignores refinements on CRuby: https://bugs.ruby-lang.org/issues/13129
            callMethodMissing = insert(
                    DispatchNode.create(DispatchConfiguration.PRIVATE_RETURN_MISSING_IGNORE_REFINEMENTS));
            methodMissingMissing = BranchProfile.create();
        }
        return callMethodMissing;
    }

    private Object callMethodMissing(Frame frame, Object receiver, Object[] rubyArgs) {
        final Object[] newArgs = RubyArguments.repack(rubyArgs, receiver, 0, 1);

        RubyArguments.setArgument(newArgs, 0, coreSymbols().RESPOND_TO);
        final Object result = getMethodMissingNode().dispatch(frame, receiver, "method_missing", newArgs,
                null);

        if (result == DispatchNode.MISSING) {
            methodMissingMissing.enter();
            throw new RaiseException(getContext(), coreExceptions().noMethodErrorFromMethodMissing(
                    ExceptionOperations.ExceptionFormatter.NO_METHOD_ERROR,
                    receiver,
                    "respond_to?",
                    RubyArguments.getPositionalArguments(rubyArgs, false),
                    this));
        }

        return result;
    }
}
