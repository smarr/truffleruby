// CheckStyle: start generated
package org.truffleruby.language.supercall;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.AssumptionGroup;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.GeneratedBy;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.memory.MemoryFence;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.NodeCost;
import java.util.concurrent.locks.Lock;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.core.module.MethodLookupResult;
import org.truffleruby.language.methods.InternalMethod;

public final class LookupSuperMethodNodeGen extends LookupSuperMethodNode {

    @CompilationFinal private volatile int state_0_;
    @CompilationFinal private LookupSuperMethodCachedData lookupSuperMethodCached_cache;

    private LookupSuperMethodNodeGen() {
    }

    @ExplodeLoop
    @Override
    public InternalMethod executeLookupSuperMethod(VirtualFrame frameValue, Object arg0Value) {
        int state_0 = this.state_0_;
        if (state_0 != 0 /* is-state_0 lookupSuperMethodCached(VirtualFrame, Object, InternalMethod, RubyClass, MethodLookupResult) || lookupSuperMethodUncached(VirtualFrame, Object) */) {
            if ((state_0 & 0b1) != 0 /* is-state_0 lookupSuperMethodCached(VirtualFrame, Object, InternalMethod, RubyClass, MethodLookupResult) */) {
                assert (isSingleContext());
                LookupSuperMethodCachedData s0_ = this.lookupSuperMethodCached_cache;
                while (s0_ != null) {
                    if (!s0_.assumption0_.isValid()) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        removeLookupSuperMethodCached_(s0_);
                        return executeAndSpecialize(frameValue, arg0Value);
                    }
                    if ((getCurrentMethod(frameValue) == s0_.currentMethod_) && (metaClass(arg0Value) == s0_.selfMetaClass_)) {
                        return lookupSuperMethodCached(frameValue, arg0Value, s0_.currentMethod_, s0_.selfMetaClass_, s0_.superMethod_);
                    }
                    s0_ = s0_.next_;
                }
            }
            if ((state_0 & 0b10) != 0 /* is-state_0 lookupSuperMethodUncached(VirtualFrame, Object) */) {
                return lookupSuperMethodUncached(frameValue, arg0Value);
            }
        }
        CompilerDirectives.transferToInterpreterAndInvalidate();
        return executeAndSpecialize(frameValue, arg0Value);
    }

    private InternalMethod executeAndSpecialize(VirtualFrame frameValue, Object arg0Value) {
        Lock lock = getLock();
        boolean hasLock = true;
        lock.lock();
        try {
            int state_0 = this.state_0_;
            if ((isSingleContext())) {
                int count0_ = 0;
                LookupSuperMethodCachedData s0_ = this.lookupSuperMethodCached_cache;
                if ((state_0 & 0b1) != 0 /* is-state_0 lookupSuperMethodCached(VirtualFrame, Object, InternalMethod, RubyClass, MethodLookupResult) */) {
                    while (s0_ != null) {
                        if ((getCurrentMethod(frameValue) == s0_.currentMethod_) && (metaClass(arg0Value) == s0_.selfMetaClass_) && (s0_.assumption0_ == null || s0_.assumption0_.isValid())) {
                            break;
                        }
                        s0_ = s0_.next_;
                        count0_++;
                    }
                }
                if (s0_ == null) {
                    {
                        InternalMethod currentMethod__ = (getCurrentMethod(frameValue));
                        if ((getCurrentMethod(frameValue) == currentMethod__)) {
                            RubyClass selfMetaClass__ = (metaClass(arg0Value));
                            if ((metaClass(arg0Value) == selfMetaClass__)) {
                                MethodLookupResult superMethod__ = (doLookup(currentMethod__, selfMetaClass__));
                                AssumptionGroup assumption0 = (superMethod__.getAssumptions());
                                if (assumption0.isValid()) {
                                    if (count0_ < (getCacheLimit())) {
                                        s0_ = new LookupSuperMethodCachedData(lookupSuperMethodCached_cache);
                                        s0_.currentMethod_ = currentMethod__;
                                        s0_.selfMetaClass_ = selfMetaClass__;
                                        s0_.superMethod_ = superMethod__;
                                        s0_.assumption0_ = assumption0;
                                        MemoryFence.storeStore();
                                        this.lookupSuperMethodCached_cache = s0_;
                                        this.state_0_ = state_0 = state_0 | 0b1 /* add-state_0 lookupSuperMethodCached(VirtualFrame, Object, InternalMethod, RubyClass, MethodLookupResult) */;
                                    }
                                }
                            }
                        }
                    }
                }
                if (s0_ != null) {
                    lock.unlock();
                    hasLock = false;
                    return lookupSuperMethodCached(frameValue, arg0Value, s0_.currentMethod_, s0_.selfMetaClass_, s0_.superMethod_);
                }
            }
            this.state_0_ = state_0 = state_0 | 0b10 /* add-state_0 lookupSuperMethodUncached(VirtualFrame, Object) */;
            lock.unlock();
            hasLock = false;
            return lookupSuperMethodUncached(frameValue, arg0Value);
        } finally {
            if (hasLock) {
                lock.unlock();
            }
        }
    }

    @Override
    public NodeCost getCost() {
        int state_0 = this.state_0_;
        if (state_0 == 0) {
            return NodeCost.UNINITIALIZED;
        } else {
            if ((state_0 & (state_0 - 1)) == 0 /* is-single-state_0  */) {
                LookupSuperMethodCachedData s0_ = this.lookupSuperMethodCached_cache;
                if ((s0_ == null || s0_.next_ == null)) {
                    return NodeCost.MONOMORPHIC;
                }
            }
        }
        return NodeCost.POLYMORPHIC;
    }

    void removeLookupSuperMethodCached_(Object s0_) {
        Lock lock = getLock();
        lock.lock();
        try {
            LookupSuperMethodCachedData prev = null;
            LookupSuperMethodCachedData cur = this.lookupSuperMethodCached_cache;
            while (cur != null) {
                if (cur == s0_) {
                    if (prev == null) {
                        this.lookupSuperMethodCached_cache = cur.next_;
                    } else {
                        prev.next_ = cur.next_;
                    }
                    break;
                }
                prev = cur;
                cur = cur.next_;
            }
            if (this.lookupSuperMethodCached_cache == null) {
                this.state_0_ = this.state_0_ & 0xfffffffe /* remove-state_0 lookupSuperMethodCached(VirtualFrame, Object, InternalMethod, RubyClass, MethodLookupResult) */;
            }
        } finally {
            lock.unlock();
        }
    }

    public static LookupSuperMethodNode create() {
        return new LookupSuperMethodNodeGen();
    }

    @GeneratedBy(LookupSuperMethodNode.class)
    private static final class LookupSuperMethodCachedData {

        @CompilationFinal LookupSuperMethodCachedData next_;
        @CompilationFinal InternalMethod currentMethod_;
        @CompilationFinal RubyClass selfMetaClass_;
        @CompilationFinal MethodLookupResult superMethod_;
        @CompilationFinal AssumptionGroup assumption0_;

        LookupSuperMethodCachedData(LookupSuperMethodCachedData next_) {
            this.next_ = next_;
        }

    }
}
