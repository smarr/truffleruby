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

import org.truffleruby.language.RubyNode;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameUtil;
import com.oracle.truffle.api.frame.VirtualFrame;

@SuppressWarnings("deprecation")
public abstract class ReadLocalVariableNode extends ReadLocalNode {

    protected ReadLocalVariableNode(LocalVariableType type, FrameSlot frameSlot) {
        super(frameSlot, type);
    }

    @Override
    public abstract Object execute(VirtualFrame frame);

    @Override
    protected Object readFrameSlot(VirtualFrame frame) {
        return execute(frame);
    }

    @Specialization(guards = "frame.isBoolean(frameSlot)")
    protected boolean readBoolean(VirtualFrame frame) {
        return FrameUtil.getBooleanSafe(frame, frameSlot);
    }

    @Specialization(guards = "frame.isInt(frameSlot)")
    protected int readInt(VirtualFrame frame) {
        return FrameUtil.getIntSafe(frame, frameSlot);
    }

    @Specialization(guards = "frame.isLong(frameSlot)")
    protected long readLong(VirtualFrame frame) {
        return FrameUtil.getLongSafe(frame, frameSlot);
    }

    @Specialization(guards = "frame.isDouble(frameSlot)")
    protected double readDouble(VirtualFrame frame) {
        return FrameUtil.getDoubleSafe(frame, frameSlot);
    }

    @Specialization(guards = "frame.isObject(frameSlot)")
    protected Object readObject(VirtualFrame frame) {
        return FrameUtil.getObjectSafe(frame, frameSlot);
    }

    @Override
    public WriteLocalNode makeWriteNode(RubyNode rhs) {
        return WriteLocalVariableNodeGen.create(frameSlot, rhs);
    }

}
