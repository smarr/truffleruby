/*
 * Copyright (c) 2017, 2019 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.module;

import com.oracle.truffle.api.AssumptionGroup;
import org.truffleruby.language.methods.InternalMethod;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

public class MethodLookupResult {

    private final InternalMethod method;
    private final AssumptionGroup assumption;

    public MethodLookupResult(InternalMethod method, Assumption... assumptions) {
        this.method = method;
        this.assumption = new AssumptionGroup(assumptions);
    }

    public MethodLookupResult withNoMethod() {
        if (method == null) {
            return this;
        } else {
            return new MethodLookupResult(null, assumption.getAssumptions());
        }
    }

    public boolean isDefined() {
        return method != null && !method.isUndefined();
    }

    public InternalMethod getMethod() {
        return method;
    }

    public AssumptionGroup getAssumptions() {
        return assumption;
    }

}
