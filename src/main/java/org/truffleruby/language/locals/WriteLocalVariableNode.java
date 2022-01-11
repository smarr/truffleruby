/*
 * Copyright (c) 2013, 2020 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.locals;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlotKind;
import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.array.AssignableNode;
import org.truffleruby.core.string.FrozenStrings;
import org.truffleruby.language.RubyNode;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;

@NodeChild(value = "valueNode", type = RubyNode.class)
public abstract class WriteLocalVariableNode extends WriteLocalNode {

    public WriteLocalVariableNode(FrameSlot frameSlot) {
        super(frameSlot);
    }

    @Override
    public abstract Object execute(VirtualFrame frame);

    public abstract RubyNode getValueNode();

    @Specialization(guards = "checkBooleanKind(frame)")
    protected boolean writeBoolean(VirtualFrame frame, boolean value) {
        frame.setBoolean(frameSlot, value);
        return value;
    }

    @Specialization(guards = "checkIntegerKind(frame)")
    protected int writeInteger(VirtualFrame frame, int value) {
        frame.setInt(frameSlot, value);
        return value;
    }

    @Specialization(guards = "checkLongKind(frame)")
    protected long writeLong(VirtualFrame frame, long value) {
        frame.setLong(frameSlot, value);
        return value;
    }

    @Specialization(guards = "checkDoubleKind(frame)")
    protected double writeDouble(VirtualFrame frame, double value) {
        frame.setDouble(frameSlot, value);
        return value;
    }

    @Specialization(
            guards = "checkObjectKind(frame)",
            replaces = { "writeBoolean", "writeInteger", "writeLong", "writeDouble" })
    protected Object writeObject(VirtualFrame frame, Object value) {
        frame.setObject(frameSlot, value);
        return value;
    }

    protected boolean checkBooleanKind(VirtualFrame frame) {
        return checkKind(frame, FrameSlotKind.Boolean);
    }

    protected boolean checkIntegerKind(VirtualFrame frame) {
        return checkKind(frame, FrameSlotKind.Int);
    }

    protected boolean checkLongKind(VirtualFrame frame) {
        return checkKind(frame, FrameSlotKind.Long);
    }

    protected boolean checkDoubleKind(VirtualFrame frame) {
        return checkKind(frame, FrameSlotKind.Double);
    }

    protected boolean checkObjectKind(VirtualFrame frame) {
        frame.getFrameDescriptor().setFrameSlotKind(frameSlot, FrameSlotKind.Object);
        return true;
    }

    private boolean checkKind(VirtualFrame frame, FrameSlotKind kind) {
        if (frame.getFrameDescriptor().getFrameSlotKind(frameSlot) == kind) {
            return true;
        } else {
            return initialSetKind(frame, kind);
        }
    }

    private boolean initialSetKind(VirtualFrame frame, FrameSlotKind kind) {
        if (frame.getFrameDescriptor().getFrameSlotKind(frameSlot) == FrameSlotKind.Illegal) {
            frame.getFrameDescriptor().setFrameSlotKind(frameSlot, kind);
            return true;
        }

        return false;
    }

    @Override
    public void assign(VirtualFrame frame, Object value) {
        throw CompilerDirectives.shouldNotReachHere("Should be simplified with getSimplifiedAssignableNode()");
    }

    @Override
    public AssignableNode toAssignableNode() {
        return WriteFrameSlotNodeGen.create(frameSlot);
    }

    @Override
    public Object isDefined(VirtualFrame frame, RubyLanguage language, RubyContext context) {
        return FrozenStrings.ASSIGNMENT;
    }

    @Override
    public String toString() {
        return super.toString() + " " + frameSlot.getIdentifier() + " = " + getValueNode();
    }

}
