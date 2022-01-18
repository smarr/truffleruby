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

import java.util.List;

public class MethodLookupResult {

    private final InternalMethod method;
    private final Assumption assumptions;

    public MethodLookupResult(InternalMethod method, Assumption assumptions) {
        this.method = method;
        this.assumptions = assumptions;
    }

    public MethodLookupResult withNoMethod() {
        if (method == null) {
            return this;
        } else {
            return new MethodLookupResult(null, assumptions);
        }
    }

    public boolean isDefined() {
        return method != null && !method.isUndefined();
    }

    public void copyAssumptionsTo(List<Assumption> target) {
        if (assumptions.getClass() == AssumptionGroup.class) {
            for (Assumption a : ((AssumptionGroup) assumptions).getAssumptions()) {
                target.add(a);
            }
        } else {
            target.add(assumptions);
        }
    }

    public InternalMethod getMethod() {
        return method;
    }

    public Assumption getAssumptions() {
        return assumptions;
    }

}
