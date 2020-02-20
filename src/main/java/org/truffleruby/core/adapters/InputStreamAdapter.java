/*
 * Copyright (c) 2015, 2019 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.adapters;

import java.io.InputStream;

import org.truffleruby.RubyContext;

import com.oracle.truffle.api.object.DynamicObject;
import org.truffleruby.language.Nil;

public class InputStreamAdapter extends InputStream {

    private final RubyContext context;
    private final DynamicObject object;

    public InputStreamAdapter(RubyContext context, DynamicObject object) {
        this.context = context;
        this.object = object;
    }

    @Override
    public int read() {
        final Object result = context.send(object, "getbyte");

        if (result == Nil.INSTANCE) {
            return -1;
        }

        return (int) result;
    }
}
