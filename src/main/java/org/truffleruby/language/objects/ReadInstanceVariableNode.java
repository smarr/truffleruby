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

import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameUtil;
import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.string.FrozenStrings;
import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyDynamicObject;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.profiles.ConditionProfile;

public final class ReadInstanceVariableNode extends RubyContextSourceNode {

    private final String name;
    protected final FrameSlot selfSlot;

    /**
     * 0: uninitalized
     * 1: slot is object and object is RubyDynamicObject
     * 2: return nil
     * 3: fallback
     */
    @CompilationFinal private byte state;

    @Child private DynamicObjectLibrary objectLibrary;

    public ReadInstanceVariableNode(String name, FrameDescriptor frameDescriptor) {
        this.name = name;
        this.selfSlot = frameDescriptor.findOrAddFrameSlot(SelfNode.SELF_IDENTIFIER);
        state = 0;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        boolean slotIsObject = frame.isObject(selfSlot);
        if (slotIsObject) {
            if (state == 1) {
                Object receiverObject = FrameUtil.getObjectSafe(frame, selfSlot);
                if (receiverObject instanceof RubyDynamicObject) {
                    final DynamicObjectLibrary objectLibrary = getObjectLibrary();
                    final RubyDynamicObject dynamicObject = (RubyDynamicObject) receiverObject;
                    return objectLibrary.getOrDefault(dynamicObject, name, nil);
                }
            }
        }

        if (state == 2) {
            return nil;
        }

        if (state == 3) {
            return fallback(frame, slotIsObject);
        }

        CompilerDirectives.transferToInterpreterAndInvalidate();
        return executeAndSpecialize(frame, slotIsObject);
    }

    private Object fallback(VirtualFrame frame, boolean slotIsObject) {
        if (slotIsObject) {
            Object receiverObject = FrameUtil.getObjectSafe(frame, selfSlot);
            if (receiverObject instanceof RubyDynamicObject) {
                final DynamicObjectLibrary objectLibrary = getObjectLibrary();
                final RubyDynamicObject dynamicObject = (RubyDynamicObject) receiverObject;
                return objectLibrary.getOrDefault(dynamicObject, name, nil);
            }
        }
        return nil;
    }

    private Object executeAndSpecialize(VirtualFrame frame, boolean slotIsObject) {
        if (slotIsObject) {
            Object receiverObject = FrameUtil.getObjectSafe(frame, selfSlot);
            if (receiverObject instanceof RubyDynamicObject) {
                final DynamicObjectLibrary objectLibrary = getObjectLibrary();
                final RubyDynamicObject dynamicObject = (RubyDynamicObject) receiverObject;
                state = 1;
                return objectLibrary.getOrDefault(dynamicObject, name, nil);
            }
            state = 3;
            return nil;
         } else {
            state = 2;
            return nil;
        }
    }

    @Override
    public Object isDefined(VirtualFrame frame, RubyLanguage language, RubyContext context) {
        final Object receiverObject = frame.getValue(selfSlot);

        if (receiverObject instanceof RubyDynamicObject) {
            final DynamicObjectLibrary objectLibrary = getObjectLibrary();
            final RubyDynamicObject dynamicObject = (RubyDynamicObject) receiverObject;
            if (objectLibrary.containsKey(dynamicObject, name)) {
                return FrozenStrings.INSTANCE_VARIABLE;
            } else {
                return nil;
            }
        } else {
            return false;
        }
    }

    private DynamicObjectLibrary getObjectLibrary() {
        if (objectLibrary == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            objectLibrary = insert(
                    DynamicObjectLibrary
                            .getFactory()
                            .createDispatched(getLanguage().options.INSTANCE_VARIABLE_CACHE));
        }
        return objectLibrary;
    }

}
