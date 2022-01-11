/*
 * Copyright (c) 2013, 2020 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.objects;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameUtil;
import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.string.FrozenStrings;
import org.truffleruby.language.RubyContextSourceNode;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.object.HiddenKey;

public abstract class SelfNode extends RubyContextSourceNode {

    public static final HiddenKey SELF_IDENTIFIER = new HiddenKey("(self)");

    protected final FrameSlot selfSlot;

    public SelfNode(FrameDescriptor frameDescriptor) {
        this.selfSlot = frameDescriptor.findOrAddFrameSlot(SelfNode.SELF_IDENTIFIER);
    }

    @Override
    public abstract Object execute(VirtualFrame frame);

    @Specialization(guards = "frame.isBoolean(selfSlot)")
    protected boolean readBoolean(VirtualFrame frame) {
        return FrameUtil.getBooleanSafe(frame, selfSlot);
    }

    @Specialization(guards = "frame.isInt(selfSlot)")
    protected int readInt(VirtualFrame frame) {
        return FrameUtil.getIntSafe(frame, selfSlot);
    }

    @Specialization(guards = "frame.isLong(selfSlot)")
    protected long readLong(VirtualFrame frame) {
        return FrameUtil.getLongSafe(frame, selfSlot);
    }

    @Specialization(guards = "frame.isDouble(selfSlot)")
    protected double readDouble(VirtualFrame frame) {
        return FrameUtil.getDoubleSafe(frame, selfSlot);
    }

    @Specialization(guards = "frame.isObject(selfSlot)")
    protected Object readObject(VirtualFrame frame) {
        return FrameUtil.getObjectSafe(frame, selfSlot);
    }

    @Override
    public Object isDefined(VirtualFrame frame, RubyLanguage language, RubyContext context) {
        return FrozenStrings.SELF;
    }

}
