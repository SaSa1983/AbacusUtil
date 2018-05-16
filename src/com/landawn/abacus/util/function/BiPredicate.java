/*
 * Copyright (C) 2016 HaiYang Li
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.landawn.abacus.util.function;

import com.landawn.abacus.util.Fn;
import com.landawn.abacus.util.N;
import com.landawn.abacus.util.Try;

/**
 * Refer to JDK API documentation at: <a href="https://docs.oracle.com/javase/8/docs/api/java/util/function/package-summary.html">https://docs.oracle.com/javase/8/docs/api/java/util/function/package-summary.html</a>
 * @since 0.8
 * 
 * @author Haiyang Li
 */
public interface BiPredicate<T, U> extends java.util.function.BiPredicate<T, U>, Try.BiPredicate<T, U, RuntimeException> {

    @Override
    boolean test(T t, U u);

    @Override
    default BiPredicate<T, U> negate() {
        return (T t, U u) -> !test(t, u);
    }

    @Override
    default BiPredicate<T, U> and(java.util.function.BiPredicate<? super T, ? super U> other) {
        N.checkArgNotNull(other);
        return (T t, U u) -> test(t, u) && other.test(t, u);
    }

    @Override
    default BiPredicate<T, U> or(java.util.function.BiPredicate<? super T, ? super U> other) {
        N.checkArgNotNull(other);
        return (T t, U u) -> test(t, u) || other.test(t, u);
    }

    /**
     * Returns the specified instance
     * 
     * @param predicate
     * @return
     */
    static <T, U> BiPredicate<T, U> of(final BiPredicate<T, U> predicate) {
        N.checkArgNotNull(predicate);

        return predicate;
    }

    static <T, U> BiPredicate<T, U> alwaysTrue() {
        return Fn.BiPredicates.alwaysTrue();
    }

    static <T, U> BiPredicate<T, U> alwaysFalse() {
        return Fn.BiPredicates.alwaysFalse();
    }

    static <T, U> BiPredicate<T, U> equal() {
        return Fn.equal();
    }

    static <T, U> BiPredicate<T, U> notEqual() {
        return Fn.notEqual();
    }

    static <T extends Comparable<? super T>> BiPredicate<T, T> greaterThan() {
        return Fn.greaterThan();
    }

    static <T extends Comparable<? super T>> BiPredicate<T, T> greaterEqual() {
        return Fn.greaterEqual();
    }

    static <T extends Comparable<? super T>> BiPredicate<T, T> lessThan() {
        return Fn.lessThan();
    }

    static <T extends Comparable<? super T>> BiPredicate<T, T> lessEqual() {
        return Fn.lessEqual();
    }
}
