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

import java.util.Objects;

import com.landawn.abacus.util.Try;

/**
 * 
 * @since 0.8
 * 
 * @author Haiyang Li
 */
public interface CharFunction<R> extends Try.CharFunction<R, RuntimeException> {
    static final CharFunction<Character> BOX = new CharFunction<Character>() {
        @Override
        public Character apply(char value) {
            return value;
        }
    };

    @Override
    R apply(char value);

    default <V> CharFunction<V> andThen(Function<? super R, ? extends V> after) {
        Objects.requireNonNull(after);

        return t -> after.apply(apply(t));
    }

    static CharFunction<Character> identity() {
        return t -> t;
    }
}
