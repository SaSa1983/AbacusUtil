/*
 * Copyright (C) 2016, 2017, 2018, 2019 HaiYang Li
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

package com.landawn.abacus.util.stream;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

import com.landawn.abacus.util.AsyncExecutor;
import com.landawn.abacus.util.CharIterator;
import com.landawn.abacus.util.ContinuableFuture;
import com.landawn.abacus.util.Holder;
import com.landawn.abacus.util.MutableBoolean;
import com.landawn.abacus.util.MutableLong;
import com.landawn.abacus.util.N;
import com.landawn.abacus.util.Nth;
import com.landawn.abacus.util.OptionalChar;
import com.landawn.abacus.util.Pair;
import com.landawn.abacus.util.Try;
import com.landawn.abacus.util.function.BiConsumer;
import com.landawn.abacus.util.function.BinaryOperator;
import com.landawn.abacus.util.function.CharBiFunction;
import com.landawn.abacus.util.function.CharBinaryOperator;
import com.landawn.abacus.util.function.CharConsumer;
import com.landawn.abacus.util.function.CharFunction;
import com.landawn.abacus.util.function.CharPredicate;
import com.landawn.abacus.util.function.CharToIntFunction;
import com.landawn.abacus.util.function.CharTriFunction;
import com.landawn.abacus.util.function.CharUnaryOperator;
import com.landawn.abacus.util.function.Consumer;
import com.landawn.abacus.util.function.Function;
import com.landawn.abacus.util.function.ObjCharConsumer;
import com.landawn.abacus.util.function.Predicate;
import com.landawn.abacus.util.function.Supplier;
import com.landawn.abacus.util.function.ToCharFunction;
import com.landawn.abacus.util.function.ToIntFunction;

/** 
 * 
 */
final class ParallelIteratorCharStream extends IteratorCharStream {
    private final int maxThreadNum;
    private final Splitor splitor;
    private final AsyncExecutor asyncExecutor;
    private volatile IteratorCharStream sequential;
    private volatile Stream<Character> boxed;

    ParallelIteratorCharStream(final CharIterator values, final boolean sorted, final int maxThreadNum, final Splitor splitor, final AsyncExecutor asyncExector,
            final Collection<Runnable> closeHandlers) {
        super(values, sorted, closeHandlers);

        this.maxThreadNum = checkMaxThreadNum(maxThreadNum);
        this.splitor = splitor == null ? DEFAULT_SPLITOR : splitor;
        this.asyncExecutor = asyncExector == null ? DEFAULT_ASYNC_EXECUTOR : asyncExector;
    }

    ParallelIteratorCharStream(final CharStream stream, final boolean sorted, final int maxThreadNum, final Splitor splitor, final AsyncExecutor asyncExector,
            final Deque<Runnable> closeHandlers) {
        this(stream.iteratorEx(), sorted, maxThreadNum, splitor, asyncExector, mergeCloseHandlers(stream, closeHandlers));
    }

    ParallelIteratorCharStream(final Stream<Character> stream, final boolean sorted, final int maxThreadNum, final Splitor splitor,
            final AsyncExecutor asyncExector, final Deque<Runnable> closeHandlers) {
        this(charIterator(stream.iteratorEx()), sorted, maxThreadNum, splitor, asyncExector, mergeCloseHandlers(stream, closeHandlers));
    }

    @Override
    public CharStream filter(final CharPredicate predicate) {
        if (maxThreadNum <= 1) {
            return super.filter(predicate);
        }

        final Stream<Character> stream = boxed().filter(new Predicate<Character>() {
            @Override
            public boolean test(Character value) {
                return predicate.test(value);
            }
        });

        return new ParallelIteratorCharStream(stream, false, maxThreadNum, splitor, asyncExecutor, closeHandlers);
    }

    @Override
    public CharStream takeWhile(final CharPredicate predicate) {
        if (maxThreadNum <= 1) {
            return super.takeWhile(predicate);
        }

        final Stream<Character> stream = boxed().takeWhile(new Predicate<Character>() {
            @Override
            public boolean test(Character value) {
                return predicate.test(value);
            }
        });

        return new ParallelIteratorCharStream(stream, false, maxThreadNum, splitor, asyncExecutor, closeHandlers);
    }

    @Override
    public CharStream dropWhile(final CharPredicate predicate) {
        if (maxThreadNum <= 1) {
            return super.dropWhile(predicate);
        }

        final Stream<Character> stream = boxed().dropWhile(new Predicate<Character>() {
            @Override
            public boolean test(Character value) {
                return predicate.test(value);
            }
        });

        return new ParallelIteratorCharStream(stream, false, maxThreadNum, splitor, asyncExecutor, closeHandlers);
    }

    @Override
    public CharStream map(final CharUnaryOperator mapper) {
        if (maxThreadNum <= 1) {
            return super.map(mapper);
        }

        final CharStream stream = boxed().mapToChar(new ToCharFunction<Character>() {
            @Override
            public char applyAsChar(Character value) {
                return mapper.applyAsChar(value);
            }
        });

        return new ParallelIteratorCharStream(stream, false, maxThreadNum, splitor, asyncExecutor, closeHandlers);
    }

    @Override
    public IntStream mapToInt(final CharToIntFunction mapper) {
        if (maxThreadNum <= 1) {
            return super.mapToInt(mapper);
        }

        final IntStream stream = boxed().mapToInt(new ToIntFunction<Character>() {
            @Override
            public int applyAsInt(Character value) {
                return mapper.applyAsInt(value);
            }
        });

        return new ParallelIteratorIntStream(stream, false, maxThreadNum, splitor, asyncExecutor, closeHandlers);
    }

    @Override
    public <U> Stream<U> mapToObj(final CharFunction<? extends U> mapper) {
        if (maxThreadNum <= 1) {
            return super.mapToObj(mapper);
        }

        return boxed().map(new Function<Character, U>() {
            @Override
            public U apply(Character value) {
                return mapper.apply(value);
            }
        });
    }

    @Override
    public CharStream flatMap(final CharFunction<? extends CharStream> mapper) {
        if (maxThreadNum <= 1) {
            return new ParallelIteratorCharStream(sequential().flatMap(mapper), false, maxThreadNum, splitor, asyncExecutor, null);
        }

        final CharStream stream = boxed().flatMapToChar(new Function<Character, CharStream>() {
            @Override
            public CharStream apply(Character value) {
                return mapper.apply(value);
            }
        });

        return new ParallelIteratorCharStream(stream, false, maxThreadNum, splitor, asyncExecutor, null);
    }

    @Override
    public IntStream flatMapToInt(final CharFunction<? extends IntStream> mapper) {
        if (maxThreadNum <= 1) {
            return new ParallelIteratorIntStream(sequential().flatMapToInt(mapper), false, maxThreadNum, splitor, asyncExecutor, null);
        }

        final IntStream stream = boxed().flatMapToInt(new Function<Character, IntStream>() {
            @Override
            public IntStream apply(Character value) {
                return mapper.apply(value);
            }
        });

        return new ParallelIteratorIntStream(stream, false, maxThreadNum, splitor, asyncExecutor, null);
    }

    @Override
    public <T> Stream<T> flatMapToObj(final CharFunction<? extends Stream<T>> mapper) {
        if (maxThreadNum <= 1) {
            return new ParallelIteratorStream<>(sequential().flatMapToObj(mapper), false, null, maxThreadNum, splitor, asyncExecutor, null);
        }

        return boxed().flatMap(new Function<Character, Stream<T>>() {
            @Override
            public Stream<T> apply(Character value) {
                return mapper.apply(value);
            }
        });
    }

    @Override
    public CharStream peek(final CharConsumer action) {
        if (maxThreadNum <= 1) {
            return super.peek(action);
        }

        final CharStream stream = boxed().peek(new Consumer<Character>() {
            @Override
            public void accept(Character t) {
                action.accept(t);
            }
        }).sequential().mapToChar(ToCharFunction.UNBOX);

        return new ParallelIteratorCharStream(stream, false, maxThreadNum, splitor, asyncExecutor, closeHandlers);
    }

    @Override
    public <E extends Exception> void forEach(final Try.CharConsumer<E> action) throws E {
        if (maxThreadNum <= 1) {
            super.forEach(action);
            return;
        }

        final List<ContinuableFuture<Void>> futureList = new ArrayList<>(maxThreadNum);
        final Holder<Throwable> eHolder = new Holder<>();

        for (int i = 0; i < maxThreadNum; i++) {
            futureList.add(asyncExecutor.execute(new Try.Runnable<RuntimeException>() {
                @Override
                public void run() {
                    char next = 0;

                    try {
                        while (eHolder.value() == null) {
                            synchronized (elements) {
                                if (elements.hasNext()) {
                                    next = elements.nextChar();
                                } else {
                                    break;
                                }
                            }

                            action.accept(next);
                        }
                    } catch (Exception e) {
                        setError(eHolder, e);
                    }
                }
            }));
        }

        try {
            complette(futureList, eHolder, (E) null);
        } finally {
            close();
        }
    }

    @Override
    public <K, V, M extends Map<K, V>> M toMap(final CharFunction<? extends K> keyExtractor, final CharFunction<? extends V> valueMapper,
            final BinaryOperator<V> mergeFunction, final Supplier<M> mapFactory) {
        if (maxThreadNum <= 1) {
            return super.toMap(keyExtractor, valueMapper, mergeFunction, mapFactory);
        }

        final Function<? super Character, ? extends K> keyExtractor2 = new Function<Character, K>() {
            @Override
            public K apply(Character value) {
                return keyExtractor.apply(value);
            }
        };

        final Function<? super Character, ? extends V> valueMapper2 = new Function<Character, V>() {
            @Override
            public V apply(Character value) {
                return valueMapper.apply(value);
            }
        };

        return boxed().toMap(keyExtractor2, valueMapper2, mergeFunction, mapFactory);
    }

    @Override
    public <K, A, D, M extends Map<K, D>> M toMap(final CharFunction<? extends K> classifier, final Collector<Character, A, D> downstream,
            final Supplier<M> mapFactory) {
        if (maxThreadNum <= 1) {
            return super.toMap(classifier, downstream, mapFactory);
        }

        final Function<? super Character, ? extends K> classifier2 = new Function<Character, K>() {
            @Override
            public K apply(Character value) {
                return classifier.apply(value);
            }
        };

        return boxed().toMap(classifier2, downstream, mapFactory);
    }

    @Override
    public char reduce(final char identity, final CharBinaryOperator op) {
        if (maxThreadNum <= 1) {
            return super.reduce(identity, op);
        }

        final List<ContinuableFuture<Character>> futureList = new ArrayList<>(maxThreadNum);
        final Holder<Throwable> eHolder = new Holder<>();

        for (int i = 0; i < maxThreadNum; i++) {
            futureList.add(asyncExecutor.execute(new Callable<Character>() {
                @Override
                public Character call() {
                    char result = identity;
                    char next = 0;

                    try {
                        while (eHolder.value() == null) {
                            synchronized (elements) {
                                if (elements.hasNext()) {
                                    next = elements.nextChar();
                                } else {
                                    break;
                                }
                            }

                            result = op.applyAsChar(result, next);
                        }
                    } catch (Exception e) {
                        setError(eHolder, e);
                    }

                    return result;
                }
            }));
        }

        if (eHolder.value() != null) {
            close();
            throw N.toRuntimeException(eHolder.value());
        }

        Character result = null;

        try {
            for (ContinuableFuture<Character> future : futureList) {
                if (result == null) {
                    result = future.get();
                } else {
                    result = op.applyAsChar(result, future.get());
                }
            }
        } catch (InterruptedException | ExecutionException e) { 
            throw N.toRuntimeException(e);
        } finally {
            close();
        }

        return result == null ? identity : result;
    }

    @Override
    public OptionalChar reduce(final CharBinaryOperator accumulator) {
        if (maxThreadNum <= 1) {
            return super.reduce(accumulator);
        }

        final List<ContinuableFuture<Character>> futureList = new ArrayList<>(maxThreadNum);
        final Holder<Throwable> eHolder = new Holder<>();

        for (int i = 0; i < maxThreadNum; i++) {
            futureList.add(asyncExecutor.execute(new Callable<Character>() {
                @Override
                public Character call() {
                    char result = 0;

                    synchronized (elements) {
                        if (elements.hasNext()) {
                            result = elements.nextChar();
                        } else {
                            return null;
                        }
                    }

                    char next = 0;

                    try {
                        while (eHolder.value() == null) {
                            synchronized (elements) {
                                if (elements.hasNext()) {
                                    next = elements.nextChar();
                                } else {
                                    break;
                                }
                            }

                            result = accumulator.applyAsChar(result, next);
                        }
                    } catch (Exception e) {
                        setError(eHolder, e);
                    }

                    return result;
                }
            }));
        }

        if (eHolder.value() != null) {
            close();
            throw N.toRuntimeException(eHolder.value());
        }

        Character result = null;

        try {
            for (ContinuableFuture<Character> future : futureList) {
                final Character tmp = future.get();

                if (tmp == null) {
                    continue;
                } else if (result == null) {
                    result = tmp;
                } else {
                    result = accumulator.applyAsChar(result, tmp);
                }
            }
        } catch (InterruptedException | ExecutionException e) { 
            throw N.toRuntimeException(e);
        } finally {
            close();
        }

        return result == null ? OptionalChar.empty() : OptionalChar.of(result);
    }

    @Override
    public <R> R collect(final Supplier<R> supplier, final ObjCharConsumer<R> accumulator, final BiConsumer<R, R> combiner) {
        if (maxThreadNum <= 1) {
            return super.collect(supplier, accumulator, combiner);
        }

        final List<ContinuableFuture<R>> futureList = new ArrayList<>(maxThreadNum);
        final Holder<Throwable> eHolder = new Holder<>();

        for (int i = 0; i < maxThreadNum; i++) {
            futureList.add(asyncExecutor.execute(new Callable<R>() {
                @Override
                public R call() {
                    final R container = supplier.get();
                    char next = 0;

                    try {
                        while (eHolder.value() == null) {
                            synchronized (elements) {
                                if (elements.hasNext()) {
                                    next = elements.nextChar();
                                } else {
                                    break;
                                }
                            }

                            accumulator.accept(container, next);
                        }
                    } catch (Exception e) {
                        setError(eHolder, e);
                    }

                    return container;
                }
            }));
        }

        if (eHolder.value() != null) {
            close();
            throw N.toRuntimeException(eHolder.value());
        }

        R container = (R) NONE;

        try {
            for (ContinuableFuture<R> future : futureList) {
                if (container == NONE) {
                    container = future.get();
                } else {
                    combiner.accept(container, future.get());
                }
            }
        } catch (InterruptedException | ExecutionException e) { 
            throw N.toRuntimeException(e);
        } finally {
            close();
        }

        return container == NONE ? supplier.get() : container;
    }

    @Override
    public <E extends Exception> boolean anyMatch(final Try.CharPredicate<E> predicate) throws E {
        if (maxThreadNum <= 1) {
            return super.anyMatch(predicate);
        }

        final List<ContinuableFuture<Void>> futureList = new ArrayList<>(maxThreadNum);
        final Holder<Throwable> eHolder = new Holder<>();
        final MutableBoolean result = MutableBoolean.of(false);

        for (int i = 0; i < maxThreadNum; i++) {
            futureList.add(asyncExecutor.execute(new Try.Runnable<RuntimeException>() {
                @Override
                public void run() {
                    char next = 0;

                    try {
                        while (result.isFalse() && eHolder.value() == null) {
                            synchronized (elements) {
                                if (elements.hasNext()) {
                                    next = elements.nextChar();
                                } else {
                                    break;
                                }
                            }

                            if (predicate.test(next)) {
                                result.setTrue();
                                break;
                            }
                        }
                    } catch (Exception e) {
                        setError(eHolder, e);
                    }
                }
            }));
        }

        try {
            complette(futureList, eHolder, (E) null);
        } finally {
            close();
        }

        return result.value();
    }

    @Override
    public <E extends Exception> boolean allMatch(final Try.CharPredicate<E> predicate) throws E {
        if (maxThreadNum <= 1) {
            return super.allMatch(predicate);
        }

        final List<ContinuableFuture<Void>> futureList = new ArrayList<>(maxThreadNum);
        final Holder<Throwable> eHolder = new Holder<>();
        final MutableBoolean result = MutableBoolean.of(true);

        for (int i = 0; i < maxThreadNum; i++) {
            futureList.add(asyncExecutor.execute(new Try.Runnable<RuntimeException>() {
                @Override
                public void run() {
                    char next = 0;

                    try {
                        while (result.isTrue() && eHolder.value() == null) {
                            synchronized (elements) {
                                if (elements.hasNext()) {
                                    next = elements.nextChar();
                                } else {
                                    break;
                                }
                            }

                            if (predicate.test(next) == false) {
                                result.setFalse();
                                break;
                            }
                        }
                    } catch (Exception e) {
                        setError(eHolder, e);
                    }
                }
            }));
        }

        try {
            complette(futureList, eHolder, (E) null);
        } finally {
            close();
        }

        return result.value();
    }

    @Override
    public <E extends Exception> boolean noneMatch(final Try.CharPredicate<E> predicate) throws E {
        if (maxThreadNum <= 1) {
            return super.noneMatch(predicate);
        }

        final List<ContinuableFuture<Void>> futureList = new ArrayList<>(maxThreadNum);
        final Holder<Throwable> eHolder = new Holder<>();
        final MutableBoolean result = MutableBoolean.of(true);

        for (int i = 0; i < maxThreadNum; i++) {
            futureList.add(asyncExecutor.execute(new Try.Runnable<RuntimeException>() {
                @Override
                public void run() {
                    char next = 0;

                    try {
                        while (result.isTrue() && eHolder.value() == null) {
                            synchronized (elements) {
                                if (elements.hasNext()) {
                                    next = elements.nextChar();
                                } else {
                                    break;
                                }
                            }

                            if (predicate.test(next)) {
                                result.setFalse();
                                break;
                            }
                        }
                    } catch (Exception e) {
                        setError(eHolder, e);
                    }
                }
            }));
        }

        try {
            complette(futureList, eHolder, (E) null);
        } finally {
            close();
        }

        return result.value();
    }

    @Override
    public <E extends Exception> OptionalChar findFirst(final Try.CharPredicate<E> predicate) throws E {
        if (maxThreadNum <= 1) {
            return super.findFirst(predicate);
        }

        final List<ContinuableFuture<Void>> futureList = new ArrayList<>(maxThreadNum);
        final Holder<Throwable> eHolder = new Holder<>();
        final Holder<Pair<Long, Character>> resultHolder = new Holder<>();
        final MutableLong index = MutableLong.of(0);

        for (int i = 0; i < maxThreadNum; i++) {
            futureList.add(asyncExecutor.execute(new Try.Runnable<RuntimeException>() {
                @Override
                public void run() {
                    final Pair<Long, Character> pair = new Pair<>();

                    try {
                        while (resultHolder.value() == null && eHolder.value() == null) {
                            synchronized (elements) {
                                if (elements.hasNext()) {
                                    pair.left = index.getAndIncrement();
                                    pair.right = elements.nextChar();
                                } else {
                                    break;
                                }
                            }

                            if (predicate.test(pair.right)) {
                                synchronized (resultHolder) {
                                    if (resultHolder.value() == null || pair.left < resultHolder.value().left) {
                                        resultHolder.setValue(pair.copy());
                                    }
                                }

                                break;
                            }
                        }
                    } catch (Exception e) {
                        setError(eHolder, e);
                    }
                }
            }));
        }

        try {
            complette(futureList, eHolder, (E) null);
        } finally {
            close();
        }

        return resultHolder.value() == null ? OptionalChar.empty() : OptionalChar.of(resultHolder.value().right);
    }

    @Override
    public <E extends Exception> OptionalChar findLast(final Try.CharPredicate<E> predicate) throws E {
        if (maxThreadNum <= 1) {
            return super.findLast(predicate);
        }

        final List<ContinuableFuture<Void>> futureList = new ArrayList<>(maxThreadNum);
        final Holder<Throwable> eHolder = new Holder<>();
        final Holder<Pair<Long, Character>> resultHolder = new Holder<>();
        final MutableLong index = MutableLong.of(0);

        for (int i = 0; i < maxThreadNum; i++) {
            futureList.add(asyncExecutor.execute(new Try.Runnable<RuntimeException>() {
                @Override
                public void run() {
                    final Pair<Long, Character> pair = new Pair<>();

                    try {
                        while (eHolder.value() == null) {
                            synchronized (elements) {
                                if (elements.hasNext()) {
                                    pair.left = index.getAndIncrement();
                                    pair.right = elements.nextChar();
                                } else {
                                    break;
                                }
                            }

                            if (predicate.test(pair.right)) {
                                synchronized (resultHolder) {
                                    if (resultHolder.value() == null || pair.left > resultHolder.value().left) {
                                        resultHolder.setValue(pair.copy());
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        setError(eHolder, e);
                    }
                }
            }));
        }

        try {
            complette(futureList, eHolder, (E) null);
        } finally {
            close();
        }

        return resultHolder.value() == null ? OptionalChar.empty() : OptionalChar.of(resultHolder.value().right);
    }

    @Override
    public <E extends Exception> OptionalChar findAny(final Try.CharPredicate<E> predicate) throws E {
        if (maxThreadNum <= 1) {
            return super.findAny(predicate);
        }

        final List<ContinuableFuture<Void>> futureList = new ArrayList<>(maxThreadNum);
        final Holder<Throwable> eHolder = new Holder<>();
        final Holder<Object> resultHolder = Holder.of(NONE);

        for (int i = 0; i < maxThreadNum; i++) {
            futureList.add(asyncExecutor.execute(new Try.Runnable<RuntimeException>() {
                @Override
                public void run() {
                    char next = 0;

                    try {
                        while (resultHolder.value() == NONE && eHolder.value() == null) {
                            synchronized (elements) {
                                if (elements.hasNext()) {
                                    next = elements.nextChar();
                                } else {
                                    break;
                                }
                            }

                            if (predicate.test(next)) {
                                synchronized (resultHolder) {
                                    if (resultHolder.value() == NONE) {
                                        resultHolder.setValue(next);
                                    }
                                }

                                break;
                            }
                        }
                    } catch (Exception e) {
                        setError(eHolder, e);
                    }
                }
            }));
        }

        try {
            complette(futureList, eHolder, (E) null);
        } finally {
            close();
        }

        return resultHolder.value() == NONE ? OptionalChar.empty() : OptionalChar.of((Character) resultHolder.value());
    }

    @Override
    public Stream<Character> boxed() {
        Stream<Character> tmp = boxed;

        if (tmp == null) {
            tmp = new ParallelIteratorStream<>(iterator(), sorted, sorted ? CHAR_COMPARATOR : null, maxThreadNum, splitor, asyncExecutor, closeHandlers);
            boxed = tmp;
        }

        return tmp;
    }

    @Override
    public CharStream append(CharStream stream) {
        return new ParallelIteratorCharStream(CharStream.concat(this, stream), false, maxThreadNum, splitor, asyncExecutor, closeHandlers);
    }

    @Override
    public CharStream prepend(CharStream stream) {
        return new ParallelIteratorCharStream(CharStream.concat(stream, this), false, maxThreadNum, splitor, asyncExecutor, closeHandlers);
    }

    @Override
    public CharStream merge(final CharStream b, final CharBiFunction<Nth> nextSelector) {
        return new ParallelIteratorCharStream(CharStream.merge(this, b, nextSelector), false, maxThreadNum, splitor, asyncExecutor, closeHandlers);
    }

    @Override
    public CharStream zipWith(CharStream b, CharBiFunction<Character> zipFunction) {
        return new ParallelIteratorCharStream(CharStream.zip(this, b, zipFunction), false, maxThreadNum, splitor, asyncExecutor, closeHandlers);
    }

    @Override
    public CharStream zipWith(CharStream b, CharStream c, CharTriFunction<Character> zipFunction) {
        return new ParallelIteratorCharStream(CharStream.zip(this, b, c, zipFunction), false, maxThreadNum, splitor, asyncExecutor, closeHandlers);
    }

    @Override
    public CharStream zipWith(CharStream b, char valueForNoneA, char valueForNoneB, CharBiFunction<Character> zipFunction) {
        return new ParallelIteratorCharStream(CharStream.zip(this, b, valueForNoneA, valueForNoneB, zipFunction), false, maxThreadNum, splitor, asyncExecutor,
                closeHandlers);
    }

    @Override
    public CharStream zipWith(CharStream b, CharStream c, char valueForNoneA, char valueForNoneB, char valueForNoneC, CharTriFunction<Character> zipFunction) {
        return new ParallelIteratorCharStream(CharStream.zip(this, b, c, valueForNoneA, valueForNoneB, valueForNoneC, zipFunction), false, maxThreadNum,
                splitor, asyncExecutor, closeHandlers);
    }

    @Override
    public boolean isParallel() {
        return true;
    }

    @Override
    public CharStream sequential() {
        IteratorCharStream tmp = sequential;

        if (tmp == null) {
            tmp = new IteratorCharStream(elements, sorted, closeHandlers);
            sequential = tmp;
        }

        return tmp;
    }

    @Override
    public CharStream parallel(int maxThreadNum, Splitor splitor) {
        if (this.maxThreadNum == checkMaxThreadNum(maxThreadNum) && this.splitor == checkSplitor(splitor)) {
            return this;
        }

        return new ParallelIteratorCharStream(elements, sorted, maxThreadNum, splitor, asyncExecutor, closeHandlers);
    }

    @Override
    protected int maxThreadNum() {
        return maxThreadNum;
    }

    @Override
    protected BaseStream.Splitor splitor() {
        return splitor;
    }

    @Override
    protected AsyncExecutor asyncExecutor() {
        return asyncExecutor;
    }

    @Override
    public CharStream onClose(Runnable closeHandler) {
        final Deque<Runnable> newCloseHandlers = new LocalArrayDeque<>(N.isNullOrEmpty(this.closeHandlers) ? 1 : this.closeHandlers.size() + 1);

        newCloseHandlers.add(wrapCloseHandlers(closeHandler));

        if (N.notNullOrEmpty(this.closeHandlers)) {
            newCloseHandlers.addAll(this.closeHandlers);
        }

        return new ParallelIteratorCharStream(elements, sorted, maxThreadNum, splitor, asyncExecutor, newCloseHandlers);
    }
}
