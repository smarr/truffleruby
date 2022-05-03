package org.truffleruby.language.control;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.NodeInfo;
import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyNode;

@NodeInfo(cost = NodeCost.NONE)
public class Sequence4Node extends RubyContextSourceNode {
    @Node.Child private RubyNode stmt1;
    @Node.Child private RubyNode stmt2;
    @Node.Child private RubyNode stmt3;
    @Node.Child private RubyNode stmt4;

    public Sequence4Node(RubyNode stmt1, RubyNode stmt2, RubyNode stmt3, RubyNode stmt4) {
        this.stmt1 = stmt1;
        this.stmt2 = stmt2;
        this.stmt3 = stmt3;
        this.stmt4 = stmt4;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        stmt1.doExecuteVoid(frame);
        stmt2.doExecuteVoid(frame);
        stmt3.doExecuteVoid(frame);
        return stmt4.execute(frame);
    }

    @ExplodeLoop
    @Override
    public void doExecuteVoid(VirtualFrame frame) {
        stmt1.doExecuteVoid(frame);
        stmt2.doExecuteVoid(frame);
        stmt3.doExecuteVoid(frame);
        stmt4.doExecuteVoid(frame);
    }

    public RubyNode[] getSequence() {
        return new RubyNode[] {stmt1, stmt2, stmt3, stmt4};
    }

    @Override
    public boolean isContinuable() {
        return stmt1.isContinuable() && stmt2.isContinuable() && stmt3.isContinuable() && stmt4.isContinuable();
    }

    @Override
    public RubyNode simplifyAsTailExpression() {
        stmt4 = stmt4.simplifyAsTailExpression();
        return this;
    }
}
