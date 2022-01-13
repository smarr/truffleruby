// CheckStyle: start generated
package org.truffleruby.language.methods;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.AssumptionGroup;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.GeneratedBy;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.memory.MemoryFence;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.profiles.ConditionProfile;
import java.util.concurrent.locks.Lock;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.core.module.MethodLookupResult;
import org.truffleruby.language.dispatch.DispatchConfiguration;
import org.truffleruby.language.objects.MetaClassNode;

public final class LookupMethodNodeGen extends LookupMethodNode {

    private static final Uncached UNCACHED = new Uncached();

    @CompilationFinal private volatile int state_0_;
    @CompilationFinal private volatile int exclude_;
    @CompilationFinal private LookupMethodCachedData lookupMethodCached_cache;
    @Child private LookupMethodUncachedData lookupMethodUncached_cache;

    private LookupMethodNodeGen() {
    }

    @ExplodeLoop
    @Override
    public InternalMethod execute(Frame frameValue, RubyClass arg0Value, String arg1Value, DispatchConfiguration arg2Value) {
        int state_0 = this.state_0_;
        if (state_0 != 0 /* is-state_0 lookupMethodCached(Frame, RubyClass, String, DispatchConfiguration, RubyClass, String, DispatchConfiguration, MethodLookupResult) || lookupMethodUncached(Frame, RubyClass, String, DispatchConfiguration, MetaClassNode, ConditionProfile, ConditionProfile, ConditionProfile, ConditionProfile, ConditionProfile, ConditionProfile, ConditionProfile, ConditionProfile) */) {
            if ((state_0 & 0b1) != 0 /* is-state_0 lookupMethodCached(Frame, RubyClass, String, DispatchConfiguration, RubyClass, String, DispatchConfiguration, MethodLookupResult) */) {
                assert (isSingleContext());
                LookupMethodCachedData s0_ = this.lookupMethodCached_cache;
                while (s0_ != null) {
                    if (!s0_.assumption0_.isValid()) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        removeLookupMethodCached_(s0_);
                        return executeAndSpecialize(frameValue, arg0Value, arg1Value, arg2Value);
                    }
                    if ((arg0Value == s0_.cachedMetaClass_) && (arg1Value == s0_.cachedName_) && (arg2Value == s0_.cachedConfig_)) {
                        return lookupMethodCached(frameValue, arg0Value, arg1Value, arg2Value, s0_.cachedMetaClass_, s0_.cachedName_, s0_.cachedConfig_, s0_.methodLookupResult_);
                    }
                    s0_ = s0_.next_;
                }
            }
            if ((state_0 & 0b10) != 0 /* is-state_0 lookupMethodUncached(Frame, RubyClass, String, DispatchConfiguration, MetaClassNode, ConditionProfile, ConditionProfile, ConditionProfile, ConditionProfile, ConditionProfile, ConditionProfile, ConditionProfile, ConditionProfile) */) {
                LookupMethodUncachedData s1_ = this.lookupMethodUncached_cache;
                if (s1_ != null) {
                    return lookupMethodUncached(frameValue, arg0Value, arg1Value, arg2Value, s1_.metaClassNode_, s1_.noCallerMethodProfile_, s1_.noPrependedModulesProfile_, s1_.onMetaClassProfile_, s1_.hasRefinementsProfile_, s1_.notFoundProfile_, s1_.publicProfile_, s1_.privateProfile_, s1_.isVisibleProfile_);
                }
            }
        }
        CompilerDirectives.transferToInterpreterAndInvalidate();
        return executeAndSpecialize(frameValue, arg0Value, arg1Value, arg2Value);
    }

    private InternalMethod executeAndSpecialize(Frame frameValue, RubyClass arg0Value, String arg1Value, DispatchConfiguration arg2Value) {
        Lock lock = getLock();
        boolean hasLock = true;
        lock.lock();
        try {
            int state_0 = this.state_0_;
            int exclude = this.exclude_;
            int oldState_0 = state_0;
            int oldExclude = exclude;
            int oldCacheCount = countCaches();
            try {
                if ((exclude) == 0 /* is-not-exclude lookupMethodCached(Frame, RubyClass, String, DispatchConfiguration, RubyClass, String, DispatchConfiguration, MethodLookupResult) */ && (isSingleContext())) {
                    int count0_ = 0;
                    LookupMethodCachedData s0_ = this.lookupMethodCached_cache;
                    if ((state_0 & 0b1) != 0 /* is-state_0 lookupMethodCached(Frame, RubyClass, String, DispatchConfiguration, RubyClass, String, DispatchConfiguration, MethodLookupResult) */) {
                        while (s0_ != null) {
                            if ((arg0Value == s0_.cachedMetaClass_) && (arg1Value == s0_.cachedName_) && (arg2Value == s0_.cachedConfig_) && (s0_.assumption0_ == null || s0_.assumption0_.isValid())) {
                                break;
                            }
                            s0_ = s0_.next_;
                            count0_++;
                        }
                    }
                    if (s0_ == null) {
                        {
                            RubyClass cachedMetaClass__ = (arg0Value);
                            String cachedName__ = (arg1Value);
                            MethodLookupResult methodLookupResult__ = (LookupMethodNode.lookupCached(getContext(), frameValue, cachedMetaClass__, cachedName__, arg2Value));
                            // assert (arg0Value == s0_.cachedMetaClass_);
                            // assert (arg1Value == s0_.cachedName_);
                            // assert (arg2Value == s0_.cachedConfig_);
                            Assumption assumption0 = (methodLookupResult__.getAssumption());
                            if (assumption0.isValid()) {
                                if (count0_ < (getCacheLimit())) {
                                    s0_ = new LookupMethodCachedData(lookupMethodCached_cache);
                                    s0_.cachedMetaClass_ = cachedMetaClass__;
                                    s0_.cachedName_ = cachedName__;
                                    s0_.cachedConfig_ = (arg2Value);
                                    s0_.methodLookupResult_ = methodLookupResult__;
                                    s0_.assumption0_ = assumption0;
                                    MemoryFence.storeStore();
                                    this.lookupMethodCached_cache = s0_;
                                    this.state_0_ = state_0 = state_0 | 0b1 /* add-state_0 lookupMethodCached(Frame, RubyClass, String, DispatchConfiguration, RubyClass, String, DispatchConfiguration, MethodLookupResult) */;
                                }
                            }
                        }
                    }
                    if (s0_ != null) {
                        lock.unlock();
                        hasLock = false;
                        return lookupMethodCached(frameValue, arg0Value, arg1Value, arg2Value, s0_.cachedMetaClass_, s0_.cachedName_, s0_.cachedConfig_, s0_.methodLookupResult_);
                    }
                }
                LookupMethodUncachedData s1_ = super.insert(new LookupMethodUncachedData());
                s1_.metaClassNode_ = s1_.insertAccessor((MetaClassNode.create()));
                s1_.noCallerMethodProfile_ = (ConditionProfile.create());
                s1_.noPrependedModulesProfile_ = (ConditionProfile.create());
                s1_.onMetaClassProfile_ = (ConditionProfile.create());
                s1_.hasRefinementsProfile_ = (ConditionProfile.create());
                s1_.notFoundProfile_ = (ConditionProfile.create());
                s1_.publicProfile_ = (ConditionProfile.create());
                s1_.privateProfile_ = (ConditionProfile.create());
                s1_.isVisibleProfile_ = (ConditionProfile.create());
                MemoryFence.storeStore();
                this.lookupMethodUncached_cache = s1_;
                this.exclude_ = exclude = exclude | 0b1 /* add-exclude lookupMethodCached(Frame, RubyClass, String, DispatchConfiguration, RubyClass, String, DispatchConfiguration, MethodLookupResult) */;
                this.lookupMethodCached_cache = null;
                state_0 = state_0 & 0xfffffffe /* remove-state_0 lookupMethodCached(Frame, RubyClass, String, DispatchConfiguration, RubyClass, String, DispatchConfiguration, MethodLookupResult) */;
                this.state_0_ = state_0 = state_0 | 0b10 /* add-state_0 lookupMethodUncached(Frame, RubyClass, String, DispatchConfiguration, MetaClassNode, ConditionProfile, ConditionProfile, ConditionProfile, ConditionProfile, ConditionProfile, ConditionProfile, ConditionProfile, ConditionProfile) */;
                lock.unlock();
                hasLock = false;
                return lookupMethodUncached(frameValue, arg0Value, arg1Value, arg2Value, s1_.metaClassNode_, s1_.noCallerMethodProfile_, s1_.noPrependedModulesProfile_, s1_.onMetaClassProfile_, s1_.hasRefinementsProfile_, s1_.notFoundProfile_, s1_.publicProfile_, s1_.privateProfile_, s1_.isVisibleProfile_);
            } finally {
                if (oldState_0 != 0 || oldExclude != 0) {
                    checkForPolymorphicSpecialize(oldState_0, oldExclude, oldCacheCount);
                }
            }
        } finally {
            if (hasLock) {
                lock.unlock();
            }
        }
    }

    private void checkForPolymorphicSpecialize(int oldState_0, int oldExclude, int oldCacheCount) {
        int newState_0 = this.state_0_;
        int newExclude = this.exclude_;
        if (((oldState_0 ^ newState_0) != 0) || (oldExclude ^ newExclude) != 0 || oldCacheCount < countCaches()) {
            this.reportPolymorphicSpecialize();
        }
    }

    private int countCaches() {
        int cacheCount = 0;
        LookupMethodCachedData s0_ = this.lookupMethodCached_cache;
        while (s0_ != null) {
            cacheCount++;
            s0_= s0_.next_;
        }
        return cacheCount;
    }

    @Override
    public NodeCost getCost() {
        int state_0 = this.state_0_;
        if (state_0 == 0) {
            return NodeCost.UNINITIALIZED;
        } else {
            if ((state_0 & (state_0 - 1)) == 0 /* is-single-state_0  */) {
                LookupMethodCachedData s0_ = this.lookupMethodCached_cache;
                if ((s0_ == null || s0_.next_ == null)) {
                    return NodeCost.MONOMORPHIC;
                }
            }
        }
        return NodeCost.POLYMORPHIC;
    }

    void removeLookupMethodCached_(Object s0_) {
        Lock lock = getLock();
        lock.lock();
        try {
            LookupMethodCachedData prev = null;
            LookupMethodCachedData cur = this.lookupMethodCached_cache;
            while (cur != null) {
                if (cur == s0_) {
                    if (prev == null) {
                        this.lookupMethodCached_cache = cur.next_;
                    } else {
                        prev.next_ = cur.next_;
                    }
                    break;
                }
                prev = cur;
                cur = cur.next_;
            }
            if (this.lookupMethodCached_cache == null) {
                this.state_0_ = this.state_0_ & 0xfffffffe /* remove-state_0 lookupMethodCached(Frame, RubyClass, String, DispatchConfiguration, RubyClass, String, DispatchConfiguration, MethodLookupResult) */;
            }
        } finally {
            lock.unlock();
        }
    }

    public static LookupMethodNode create() {
        return new LookupMethodNodeGen();
    }

    public static LookupMethodNode getUncached() {
        return LookupMethodNodeGen.UNCACHED;
    }

    @GeneratedBy(LookupMethodNode.class)
    private static final class LookupMethodCachedData {

        @CompilationFinal LookupMethodCachedData next_;
        @CompilationFinal RubyClass cachedMetaClass_;
        @CompilationFinal String cachedName_;
        @CompilationFinal DispatchConfiguration cachedConfig_;
        @CompilationFinal MethodLookupResult methodLookupResult_;
        @CompilationFinal Assumption assumption0_;

        LookupMethodCachedData(LookupMethodCachedData next_) {
            this.next_ = next_;
        }

    }
    @GeneratedBy(LookupMethodNode.class)
    private static final class LookupMethodUncachedData extends Node {

        @Child MetaClassNode metaClassNode_;
        @CompilationFinal ConditionProfile noCallerMethodProfile_;
        @CompilationFinal ConditionProfile noPrependedModulesProfile_;
        @CompilationFinal ConditionProfile onMetaClassProfile_;
        @CompilationFinal ConditionProfile hasRefinementsProfile_;
        @CompilationFinal ConditionProfile notFoundProfile_;
        @CompilationFinal ConditionProfile publicProfile_;
        @CompilationFinal ConditionProfile privateProfile_;
        @CompilationFinal ConditionProfile isVisibleProfile_;

        LookupMethodUncachedData() {
        }

        @Override
        public NodeCost getCost() {
            return NodeCost.NONE;
        }

        <T extends Node> T insertAccessor(T node) {
            return super.insert(node);
        }

    }
    @GeneratedBy(LookupMethodNode.class)
    private static final class Uncached extends LookupMethodNode {

        @TruffleBoundary
        @Override
        public InternalMethod execute(Frame frameValue, RubyClass arg0Value, String arg1Value, DispatchConfiguration arg2Value) {
            return lookupMethodUncached(frameValue, arg0Value, arg1Value, arg2Value, (MetaClassNode.getUncached()), (ConditionProfile.getUncached()), (ConditionProfile.getUncached()), (ConditionProfile.getUncached()), (ConditionProfile.getUncached()), (ConditionProfile.getUncached()), (ConditionProfile.getUncached()), (ConditionProfile.getUncached()), (ConditionProfile.getUncached()));
        }

        @Override
        public NodeCost getCost() {
            return NodeCost.MEGAMORPHIC;
        }

        @Override
        public boolean isAdoptable() {
            return false;
        }

    }
}
