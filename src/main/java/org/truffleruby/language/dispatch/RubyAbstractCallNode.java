package org.truffleruby.language.dispatch;

import com.oracle.truffle.api.frame.VirtualFrame;
import org.truffleruby.language.arguments.ArgumentsDescriptor;

public abstract class RubyAbstractCallNode extends LiteralCallNode {

    protected RubyAbstractCallNode(boolean isSplatted, ArgumentsDescriptor descriptor) {
        super(isSplatted, descriptor);
    }
    public abstract Object executeWithArgumentsEvaluated(VirtualFrame frame, Object receiverObject, Object blockObject,
                                                Object[] argumentsObjects);
}
