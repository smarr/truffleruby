package org.truffleruby.core.supernodes;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import org.truffleruby.core.symbol.RubySymbol;
import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyGuards;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.arguments.EmptyArgumentsDescriptor;
import org.truffleruby.language.arguments.RubyArguments;
import org.truffleruby.language.dispatch.DispatchConfiguration;
import org.truffleruby.language.dispatch.DispatchNode;
import org.truffleruby.language.dispatch.RubyCallNodeParameters;
import org.truffleruby.language.literal.ObjectLiteralNode;

import static org.truffleruby.language.dispatch.DispatchConfiguration.PRIVATE;
import static org.truffleruby.language.dispatch.DispatchConfiguration.PROTECTED;

public class RespondToCallNode extends RubyContextSourceNode {
    private final RubySymbol lookupSymbol;
    private final DispatchConfiguration dispatchConfig;

    @Child private RubyNode receiver;
    @Child private DispatchNode dispatch;

    public RespondToCallNode(RubyCallNodeParameters parameters) {
        this.receiver = parameters.getReceiver();
        RubyNode[] args = parameters.getArguments();
        assert args.length == 1 && args[0] instanceof ObjectLiteralNode;
        lookupSymbol = (RubySymbol) ((ObjectLiteralNode) args[0]).getObject();
        this.dispatchConfig = parameters.isIgnoreVisibility() ? PRIVATE : PROTECTED;

        assert parameters.getBlock() == null;
        assert !parameters.isSafeNavigation();
        assert !parameters.isSplatted();
        assert !parameters.isAttrAssign();
    }

    @Override
    public Object execute(VirtualFrame frame) {
        final Object receiverObject = receiver.execute(frame);

        Object[] rubyArgs = RubyArguments.allocate(1);
        RubyArguments.setSelf(rubyArgs, receiverObject);
        RubyArguments.setArgument(rubyArgs, 0, lookupSymbol);

        return doCall(frame, receiverObject, rubyArgs);
    }

    public Object doCall(VirtualFrame frame, Object receiverObject, Object[] rubyArgs) {
        RubyArguments.setDescriptor(rubyArgs, EmptyArgumentsDescriptor.INSTANCE);

        if (dispatch == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            dispatch = insert(DispatchNode.create(dispatchConfig));
        }

        final Object returnValue = dispatch.dispatch(frame, receiverObject, "respond_to?", rubyArgs, null);
        assert RubyGuards.assertIsValidRubyValue(returnValue);
        return returnValue;
    }
}
