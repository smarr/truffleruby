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

public class MethodLookupResult {

    private final InternalMethod method;
    private final Assumption assumption;

    public MethodLookupResult(InternalMethod method, Assumption... assumptions) {
        this.method = method;
        this.assumption = AssumptionGroup.create(assumptions);
    }

    protected MethodLookupResult(Assumption assumption, InternalMethod method) {
        this.method = method;
        this.assumption = assumption;
    }

    public MethodLookupResult withNoMethod() {
        if (method == null) {
            return this;
        }

        if (assumption instanceof AssumptionGroup) {
            return new MethodLookupResult(null, ((AssumptionGroup) assumption).getAssumptions());
        }

        return new MethodLookupResult(assumption, null);
    }

    public boolean isDefined() {
        return method != null && !method.isUndefined();
    }

    public InternalMethod getMethod() {
        return method;
    }

    public Assumption getAssumption() {
        return assumption;
    }

}
