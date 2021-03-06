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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import com.landawn.abacus.DataSet;
import com.landawn.abacus.exception.DuplicatedResultException;
import com.landawn.abacus.type.Type;
import com.landawn.abacus.util.Array;
import com.landawn.abacus.util.BufferedWriter;
import com.landawn.abacus.util.Comparators;
import com.landawn.abacus.util.Fn;
import com.landawn.abacus.util.Fn.Suppliers;
import com.landawn.abacus.util.IOUtil;
import com.landawn.abacus.util.Indexed;
import com.landawn.abacus.util.Iterators;
import com.landawn.abacus.util.JdbcUtil;
import com.landawn.abacus.util.Joiner;
import com.landawn.abacus.util.ListMultimap;
import com.landawn.abacus.util.Matrix;
import com.landawn.abacus.util.Multimap;
import com.landawn.abacus.util.Multiset;
import com.landawn.abacus.util.MutableBoolean;
import com.landawn.abacus.util.MutableLong;
import com.landawn.abacus.util.N;
import com.landawn.abacus.util.Nth;
import com.landawn.abacus.util.ObjIterator;
import com.landawn.abacus.util.Objectory;
import com.landawn.abacus.util.Optional;
import com.landawn.abacus.util.OptionalDouble;
import com.landawn.abacus.util.Pair;
import com.landawn.abacus.util.Percentage;
import com.landawn.abacus.util.PermutationIterator;
import com.landawn.abacus.util.StringUtil.Strings;
import com.landawn.abacus.util.Try;
import com.landawn.abacus.util.function.BiConsumer;
import com.landawn.abacus.util.function.BiFunction;
import com.landawn.abacus.util.function.BiPredicate;
import com.landawn.abacus.util.function.BinaryOperator;
import com.landawn.abacus.util.function.BooleanSupplier;
import com.landawn.abacus.util.function.Consumer;
import com.landawn.abacus.util.function.Function;
import com.landawn.abacus.util.function.IntFunction;
import com.landawn.abacus.util.function.Predicate;
import com.landawn.abacus.util.function.Supplier;
import com.landawn.abacus.util.function.ToDoubleFunction;
import com.landawn.abacus.util.function.ToIntFunction;
import com.landawn.abacus.util.function.ToLongFunction;
import com.landawn.abacus.util.function.TriFunction;

/**
 *
 * @param <T> 
 */
abstract class AbstractStream<T> extends Stream<T> {

    AbstractStream(final boolean sorted, final Comparator<? super T> cmp, final Collection<Runnable> closeHandlers) {
        super(sorted, cmp, closeHandlers);
    }

    @Override
    public Stream<T> skip(final long n, final Consumer<? super T> action) {
        if (n <= 0) {
            return this;
        }

        final Predicate<T> filter = isParallel() ? new Predicate<T>() {
            final AtomicLong cnt = new AtomicLong(n);

            @Override
            public boolean test(T value) {
                return cnt.getAndDecrement() > 0;
            }
        } : new Predicate<T>() {
            final MutableLong cnt = MutableLong.of(n);

            @Override
            public boolean test(T value) {
                return cnt.getAndDecrement() > 0;
            }
        };

        return dropWhile(filter, action);
    }

    @Override
    public Stream<T> peekFirst(final Consumer<? super T> action) {
        final Function<? super T, ? extends T> mapperForFirst = new Function<T, T>() {
            @Override
            public T apply(T t) {
                action.accept(t);
                return t;
            }
        };

        return mapFirst(mapperForFirst);
    }

    @Override
    public Stream<T> peekLast(final Consumer<? super T> action) {
        final Function<? super T, ? extends T> mapperForFirst = new Function<T, T>() {
            @Override
            public T apply(T t) {
                action.accept(t);
                return t;
            }
        };

        return mapLast(mapperForFirst);
    }

    @Override
    public Stream<T> removeIf(final Predicate<? super T> predicate) {
        N.checkArgNotNull(predicate);

        return filter(new Predicate<T>() {
            @Override
            public boolean test(T value) {
                return predicate.test(value) == false;
            }
        });
    }

    @Override
    public Stream<T> removeIf(final Predicate<? super T> predicate, final Consumer<? super T> action) {
        N.checkArgNotNull(predicate);
        N.checkArgNotNull(action);

        return filter(new Predicate<T>() {
            @Override
            public boolean test(T value) {
                if (predicate.test(value)) {
                    action.accept(value);
                    return false;
                }

                return true;
            }
        });
    }

    @Override
    public Stream<T> dropWhile(final Predicate<? super T> predicate, final Consumer<? super T> action) {
        N.checkArgNotNull(predicate);
        N.checkArgNotNull(action);

        return dropWhile(new Predicate<T>() {
            @Override
            public boolean test(T value) {
                if (predicate.test(value)) {
                    action.accept(value);
                    return true;
                }

                return false;
            }
        });
    }

    @Override
    public Stream<T> step(final long step) {
        N.checkArgPositive(step, "step");

        if (step == 1) {
            return this;
        }

        final long skip = step - 1;
        final ObjIteratorEx<T> iter = iteratorEx();

        final Iterator<T> iterator = new ObjIteratorEx<T>() {
            @Override
            public boolean hasNext() {
                return iter.hasNext();
            }

            @Override
            public T next() {
                final T next = iter.next();
                iter.skip(skip);
                return next;
            }
        };

        return newStream(iterator, sorted, cmp);
    }

    //    @Override
    //    public <R> Stream<R> biMap(BiFunction<? super T, ? super T, ? extends R> mapper) {
    //        return biMap(mapper, false);
    //    }
    //
    //    @Override
    //    public <R> Stream<R> triMap(TriFunction<? super T, ? super T, ? super T, ? extends R> mapper) {
    //        return triMap(mapper, false);
    //    }

    @Override
    public <R> Stream<R> slidingMap(BiFunction<? super T, ? super T, R> mapper) {
        return slidingMap(mapper, 1);
    }

    @Override
    public <R> Stream<R> slidingMap(BiFunction<? super T, ? super T, R> mapper, int increment) {
        return slidingMap(mapper, increment, false);
    }

    @Override
    public <R> Stream<R> slidingMap(TriFunction<? super T, ? super T, ? super T, R> mapper) {
        return slidingMap(mapper, 1);
    }

    @Override
    public <R> Stream<R> slidingMap(TriFunction<? super T, ? super T, ? super T, R> mapper, int increment) {
        return slidingMap(mapper, increment, false);
    }

    @Override
    public <U> Stream<U> rangeMap(final BiPredicate<? super T, ? super T> sameRange, final BiFunction<? super T, ? super T, ? extends U> mapper) {
        final Iterator<T> iter = iterator();

        return newStream(new ObjIteratorEx<U>() {
            private final T NULL = (T) StreamBase.NONE;
            private T left = NULL;
            private T right = null;
            private T next = null;

            @Override
            public boolean hasNext() {
                return left != NULL || iter.hasNext();
            }

            @Override
            public U next() {
                if (left == NULL) {
                    left = iter.next();
                }

                right = left;
                boolean hasNext = false;

                while (iter.hasNext()) {
                    next = iter.next();

                    if (sameRange.test(left, next)) {
                        right = next;
                    } else {
                        hasNext = true;
                        break;
                    }
                }

                final U res = mapper.apply(left, right);

                left = hasNext ? next : NULL;

                return res;
            }
        }, false, null);
    }

    @Override
    public <K, V> EntryStream<K, V> mapToEntry(final Function<? super T, ? extends Map.Entry<K, V>> mapper) {
        final Function<T, T> mapper2 = Fn.identity();

        if (mapper == mapper2) {
            return EntryStream.of((Stream<Map.Entry<K, V>>) this);
        }

        return EntryStream.of(map(mapper));
    }

    @Override
    public <K, V> EntryStream<K, V> mapToEntry(final Function<? super T, K> keyMapper, final Function<? super T, V> valueMapper) {
        final Function<T, Map.Entry<K, V>> mapper = new Function<T, Map.Entry<K, V>>() {
            @Override
            public Entry<K, V> apply(T t) {
                return new SimpleImmutableEntry<>(keyMapper.apply(t), valueMapper.apply(t));
            }
        };

        return mapToEntry(mapper);
    }

    //    private static final Predicate<Optional<?>> IS_PRESENT = new Predicate<Optional<?>>() {
    //        @Override
    //        public boolean test(Optional<?> t) {
    //            return t.isPresent();
    //        }
    //    };
    //
    //    private static final Function<Optional<Object>, Object> OPTIONAL_GET = new Function<Optional<Object>, Object>() {
    //        @Override
    //        public Object apply(Optional<Object> t) {
    //            return t.get();
    //        }
    //    };
    //
    //    @SuppressWarnings("rawtypes")
    //    @Override
    //    public <U> Stream<U> mapp(Function<? super T, ? extends Optional<? extends U>> mapper) {
    //        final Function<Optional<? extends U>, U> optionalGetter = (Function) OPTIONAL_GET;
    //        return map(mapper).filter(IS_PRESENT).map(optionalGetter);
    //    }

    @Override
    public <R> Stream<R> flattMap(final Function<? super T, ? extends Collection<? extends R>> mapper) {
        return flatMap(new Function<T, Stream<? extends R>>() {
            @Override
            public Stream<? extends R> apply(T t) {
                return Stream.of(mapper.apply(t));
            }
        });
    }

    @Override
    public <R> Stream<R> flatMapp(final Function<? super T, R[]> mapper) {
        return flatMap(new Function<T, Stream<? extends R>>() {
            @Override
            public Stream<? extends R> apply(T t) {
                return Stream.of(mapper.apply(t));
            }
        });
    }

    @Override
    public CharStream flattMapToChar(final Function<? super T, char[]> mapper) {
        return flatMapToChar(new Function<T, CharStream>() {
            @Override
            public CharStream apply(T t) {
                return CharStream.of(mapper.apply(t));
            }
        });
    }

    @Override
    public ByteStream flattMapToByte(final Function<? super T, byte[]> mapper) {
        return flatMapToByte(new Function<T, ByteStream>() {
            @Override
            public ByteStream apply(T t) {
                return ByteStream.of(mapper.apply(t));
            }
        });
    }

    @Override
    public ShortStream flattMapToShort(final Function<? super T, short[]> mapper) {
        return flatMapToShort(new Function<T, ShortStream>() {
            @Override
            public ShortStream apply(T t) {
                return ShortStream.of(mapper.apply(t));
            }
        });
    }

    @Override
    public IntStream flattMapToInt(final Function<? super T, int[]> mapper) {
        return flatMapToInt(new Function<T, IntStream>() {
            @Override
            public IntStream apply(T t) {
                return IntStream.of(mapper.apply(t));
            }
        });
    }

    @Override
    public LongStream flattMapToLong(final Function<? super T, long[]> mapper) {
        return flatMapToLong(new Function<T, LongStream>() {
            @Override
            public LongStream apply(T t) {
                return LongStream.of(mapper.apply(t));
            }
        });
    }

    @Override
    public FloatStream flattMapToFloat(final Function<? super T, float[]> mapper) {
        return flatMapToFloat(new Function<T, FloatStream>() {
            @Override
            public FloatStream apply(T t) {
                return FloatStream.of(mapper.apply(t));
            }
        });
    }

    @Override
    public DoubleStream flattMapToDouble(final Function<? super T, double[]> mapper) {
        return flatMapToDouble(new Function<T, DoubleStream>() {
            @Override
            public DoubleStream apply(T t) {
                return DoubleStream.of(mapper.apply(t));
            }
        });
    }

    @Override
    public <K, V> EntryStream<K, V> flatMapToEntry(final Function<? super T, ? extends Stream<? extends Map.Entry<K, V>>> mapper) {
        return EntryStream.of(flatMap(mapper));
    }

    @Override
    @ParallelSupported
    public <K, V> EntryStream<K, V> flattMapToEntry(final Function<? super T, ? extends Map<K, V>> mapper) {
        final Function<? super T, ? extends Stream<? extends Entry<K, V>>> mapper2 = new Function<T, Stream<? extends Entry<K, V>>>() {
            @Override
            public Stream<? extends Entry<K, V>> apply(T t) {
                return Stream.of(mapper.apply(t));
            }
        };

        return flatMapToEntry(mapper2);
    }

    @Override
    @ParallelSupported
    public <K, V> EntryStream<K, V> flatMappToEntry(final Function<? super T, ? extends EntryStream<K, V>> mapper) {
        final Function<? super T, ? extends Stream<? extends Entry<K, V>>> mapper2 = new Function<T, Stream<? extends Entry<K, V>>>() {
            @Override
            public Stream<? extends Entry<K, V>> apply(T t) {
                return mapper.apply(t).entries();
            }
        };

        return flatMapToEntry(mapper2);
    }

    //    @Override
    //    public <V> EntryStream<T, V> flattMapToEntry(final Function<? super T, ? extends Collection<? extends V>> flatValueMapper) {
    //        final Function<T, Stream<Map.Entry<T, V>>> flatEntryMapper = new Function<T, Stream<Map.Entry<T, V>>>() {
    //            @Override
    //            public Stream<Map.Entry<T, V>> apply(final T t) {
    //                final Function<V, Map.Entry<T, V>> entryMapper = new Function<V, Map.Entry<T, V>>() {
    //                    @Override
    //                    public Entry<T, V> apply(V v) {
    //                        return new SimpleImmutableEntry<>(t, v);
    //                    }
    //                };
    //
    //                return Stream.of(flatValueMapper.apply(t)).map(entryMapper);
    //            }
    //        };
    //
    //        return flatMapToEntry(flatEntryMapper);
    //    }

    @Override
    @SuppressWarnings("rawtypes")
    public Stream<T> sortedBy(final Function<? super T, ? extends Comparable> keyExtractor) {
        final Comparator<? super T> comparator = new Comparator<T>() {
            @Override
            public int compare(T o1, T o2) {
                return N.compare(keyExtractor.apply(o1), keyExtractor.apply(o2));
            }
        };

        return sorted(comparator);
    }

    @Override
    public Stream<T> sortedByInt(final ToIntFunction<? super T> keyExtractor) {
        final Comparator<? super T> comparator = Comparators.comparingInt(keyExtractor);

        return sorted(comparator);
    }

    @Override
    public Stream<T> sortedByLong(final ToLongFunction<? super T> keyExtractor) {
        final Comparator<? super T> comparator = Comparators.comparingLong(keyExtractor);

        return sorted(comparator);
    }

    @Override
    public Stream<T> sortedByDouble(final ToDoubleFunction<? super T> keyExtractor) {
        final Comparator<? super T> comparator = Comparators.comparingDouble(keyExtractor);

        return sorted(comparator);
    }

    @Override
    public Stream<Stream<T>> split(final int size) {
        return splitToList(size).map(new Function<List<T>, Stream<T>>() {
            @Override
            public Stream<T> apply(List<T> t) {
                return new ArrayStream<>(Stream.toArray(t), 0, t.size(), sorted, cmp, null);
            }
        });
    }

    @Override
    public Stream<List<T>> splitToList(final int size) {
        return split(size, Fn.Factory.<T> ofList());
    }

    @Override
    public Stream<Set<T>> splitToSet(final int size) {
        return split(size, Fn.Factory.<T> ofSet());
    }

    @Override
    public Stream<Stream<T>> split(final Predicate<? super T> predicate) {
        return splitToList(predicate).map(new Function<List<T>, Stream<T>>() {
            @Override
            public Stream<T> apply(List<T> t) {
                return new ArrayStream<>(Stream.toArray(t), 0, t.size(), sorted, cmp, null);
            }
        });
    }

    @Override
    public Stream<List<T>> splitToList(final Predicate<? super T> predicate) {
        return split(predicate, Suppliers.<T> ofList());
    }

    @Override
    public Stream<Set<T>> splitToSet(final Predicate<? super T> predicate) {
        return split(predicate, Suppliers.<T> ofSet());
    }

    @Override
    public Stream<Stream<T>> splitBy(final Predicate<? super T> where) {
        N.checkArgNotNull(where);

        final IteratorEx<T> iter = iteratorEx();

        return newStream(new ObjIteratorEx<Stream<T>>() {
            private int cursor = 0;
            private T next = null;
            private boolean hasNext = false;

            @Override
            public boolean hasNext() {
                return cursor < 2;
            }

            @Override
            public Stream<T> next() {
                if (hasNext() == false) {
                    throw new NoSuchElementException();
                }

                Stream<T> result = null;

                if (cursor == 0) {
                    final List<T> list = new ArrayList<>();

                    while (iter.hasNext()) {
                        next = iter.next();

                        if (where.test(next)) {
                            list.add(next);
                        } else {
                            hasNext = true;
                            break;
                        }
                    }

                    result = new ArrayStream<T>(Stream.toArray(list), 0, list.size(), sorted, cmp, null);
                } else {
                    IteratorEx<T> iterEx = iter;

                    if (hasNext) {
                        iterEx = new ObjIteratorEx<T>() {
                            private boolean isFirst = true;

                            @Override
                            public boolean hasNext() {
                                return isFirst || iter.hasNext();
                            }

                            @Override
                            public T next() {
                                if (hasNext() == false) {
                                    throw new NoSuchElementException();
                                }

                                if (isFirst) {
                                    isFirst = false;
                                    return next;
                                } else {
                                    return iter.next();
                                }
                            }
                        };
                    }

                    result = new IteratorStream<T>(iterEx, sorted, cmp, null);
                }

                cursor++;

                return result;
            }

            @Override
            public long count() {
                iter.count();

                return 2 - cursor;
            }

            @Override
            public void skip(long n) {
                N.checkArgNotNegative(n, "n");

                if (n == 0) {
                    return;
                } else if (n == 1) {
                    if (cursor == 0) {
                        while (iter.hasNext()) {
                            next = iter.next();

                            if (where.test(next) == false) {
                                hasNext = true;
                                break;
                            }
                        }
                    } else {
                        iter.skip(Long.MAX_VALUE);
                    }
                } else {
                    iter.skip(Long.MAX_VALUE);
                }

                cursor = n >= 2 ? 2 : cursor + (int) n;
            }

        }, false, null);
    }

    @Override
    public <A, R> Stream<R> splitBy(final Predicate<? super T> where, Collector<? super T, A, R> collector) {
        N.checkArgNotNull(where, "where");
        N.checkArgNotNull(collector, "collector");

        final Supplier<A> supplier = collector.supplier();
        final BiConsumer<A, ? super T> accumulator = collector.accumulator();
        final Function<A, R> finisher = collector.finisher();

        final IteratorEx<T> iter = iteratorEx();

        return newStream(new ObjIteratorEx<R>() {
            private int cursor = 0;
            private T next = null;
            private boolean hasNext = false;

            @Override
            public boolean hasNext() {
                return cursor < 2;
            }

            @Override
            public R next() {
                if (hasNext()) {
                    throw new NoSuchElementException();
                }

                final A container = supplier.get();

                if (cursor == 0) {
                    while (iter.hasNext()) {
                        next = iter.next();

                        if (where.test(next)) {
                            accumulator.accept(container, next);
                        } else {
                            hasNext = true;
                            break;
                        }
                    }
                } else {
                    if (hasNext) {
                        accumulator.accept(container, next);
                    }

                    while (iter.hasNext()) {
                        accumulator.accept(container, iter.next());
                    }
                }

                cursor++;

                return finisher.apply(container);
            }

            @Override
            public long count() {
                iter.count();

                return 2 - cursor;
            }

            @Override
            public void skip(long n) {
                N.checkArgNotNegative(n, "n");

                if (n == 0) {
                    return;
                } else if (n == 1) {
                    if (cursor == 0) {
                        while (iter.hasNext()) {
                            next = iter.next();

                            if (where.test(next) == false) {
                                hasNext = true;
                                break;
                            }
                        }
                    } else {
                        iter.skip(Long.MAX_VALUE);
                    }
                } else {
                    iter.skip(Long.MAX_VALUE);
                }

                cursor = n >= 2 ? 2 : cursor + (int) n;
            }
        }, false, null);
    }

    @Override
    public Stream<Stream<T>> sliding(final int windowSize, final int increment) {
        return slidingToList(windowSize, increment).map(new Function<List<T>, Stream<T>>() {
            @Override
            public Stream<T> apply(List<T> t) {
                return new ArrayStream<>(toArray(t), 0, t.size(), sorted, cmp, null);
            }
        });
    }

    @Override
    public Stream<List<T>> slidingToList(final int windowSize, final int increment) {
        return sliding(windowSize, increment, Fn.Factory.<T> ofList());
    }

    @Override
    public <C extends Collection<T>> Stream<C> sliding(final int windowSize, IntFunction<C> collectionSupplier) {
        return sliding(windowSize, 1, collectionSupplier);
    }

    @Override
    public <A, R> Stream<R> sliding(int windowSize, Collector<? super T, A, R> collector) {
        return sliding(windowSize, 1, collector);
    }

    @Override
    public Stream<T> collapse(final BiPredicate<? super T, ? super T> collapsible, final BiFunction<? super T, ? super T, T> mergeFunction) {
        final ObjIteratorEx<T> iter = iteratorEx();

        return newStream(new ObjIteratorEx<T>() {
            private boolean hasNext = false;
            private T next = null;

            @Override
            public boolean hasNext() {
                return hasNext || iter.hasNext();
            }

            @Override
            public T next() {
                T res = hasNext ? next : (next = iter.next());

                while ((hasNext = iter.hasNext())) {
                    if (collapsible.test(next, (next = iter.next()))) {
                        res = mergeFunction.apply(res, next);
                    } else {
                        break;
                    }
                }

                return res;
            }
        }, false, null);
    }

    @Override
    public <R> Stream<R> collapse(final BiPredicate<? super T, ? super T> collapsible, final Supplier<R> supplier, final BiConsumer<R, ? super T> accumulator) {
        final ObjIteratorEx<T> iter = iteratorEx();

        return newStream(new ObjIteratorEx<R>() {
            private boolean hasNext = false;
            private T next = null;

            @Override
            public boolean hasNext() {
                return hasNext || iter.hasNext();
            }

            @Override
            public R next() {
                final R res = supplier.get();
                accumulator.accept(res, hasNext ? next : (next = iter.next()));

                while ((hasNext = iter.hasNext())) {
                    if (collapsible.test(next, (next = iter.next()))) {
                        accumulator.accept(res, next);
                    } else {
                        break;
                    }
                }

                return res;
            }
        }, false, null);
    }

    @Override
    public <R, A> Stream<R> collapse(final BiPredicate<? super T, ? super T> collapsible, final Collector<? super T, A, R> collector) {
        final Supplier<A> supplier = collector.supplier();
        final BiConsumer<A, ? super T> accumulator = collector.accumulator();
        final Function<A, R> finisher = collector.finisher();
        final ObjIteratorEx<T> iter = iteratorEx();

        return newStream(new ObjIteratorEx<R>() {
            private boolean hasNext = false;
            private T next = null;

            @Override
            public boolean hasNext() {
                return hasNext || iter.hasNext();
            }

            @Override
            public R next() {
                final A c = supplier.get();
                accumulator.accept(c, hasNext ? next : (next = iter.next()));

                while ((hasNext = iter.hasNext())) {
                    if (collapsible.test(next, (next = iter.next()))) {
                        accumulator.accept(c, next);
                    } else {
                        break;
                    }
                }

                return finisher.apply(c);
            }
        }, false, null);
    }

    @Override
    public Stream<T> scan(final BiFunction<? super T, ? super T, T> accumulator) {
        final ObjIteratorEx<T> iter = iteratorEx();

        return newStream(new ObjIteratorEx<T>() {
            private T res = null;
            private boolean isFirst = true;

            @Override
            public boolean hasNext() {
                return iter.hasNext();
            }

            @Override
            public T next() {
                if (isFirst) {
                    isFirst = false;
                    return (res = iter.next());
                } else {
                    return (res = accumulator.apply(res, iter.next()));
                }
            }
        }, false, null);
    }

    @Override
    public <R> Stream<R> scan(final R seed, final BiFunction<? super R, ? super T, R> accumulator) {
        final ObjIteratorEx<T> iter = iteratorEx();

        return newStream(new ObjIteratorEx<R>() {
            private R res = seed;

            @Override
            public boolean hasNext() {
                return iter.hasNext();
            }

            @Override
            public R next() {
                return (res = accumulator.apply(res, iter.next()));
            }
        }, false, null);
    }

    @Override
    public <R> Stream<R> scan(final R seed, final BiFunction<? super R, ? super T, R> accumulator, boolean seedIncluded) {
        if (seedIncluded == false) {
            return scan(seed, accumulator);
        }

        final ObjIteratorEx<T> iter = iteratorEx();

        return newStream(new ObjIteratorEx<R>() {
            private boolean isFirst = true;
            private R res = seed;

            @Override
            public boolean hasNext() {
                return isFirst || iter.hasNext();
            }

            @Override
            public R next() {
                if (isFirst) {
                    isFirst = false;
                    return seed;
                }

                return (res = accumulator.apply(res, iter.next()));
            }
        }, false, null);
    }

    @Override
    public Stream<T> intersperse(final T delimiter) {
        return newStream(new ObjIteratorEx<T>() {
            private final Iterator<T> iter = iterator();
            private boolean toInsert = false;

            @Override
            public boolean hasNext() {
                return iter.hasNext();
            }

            @Override
            public T next() {
                if (hasNext() == false) {
                    throw new NoSuchElementException();
                }

                if (toInsert) {
                    toInsert = false;
                    return delimiter;
                } else {
                    final T res = iter.next();
                    toInsert = true;
                    return res;
                }
            }
        }, false, null);
    }

    @Override
    public <E extends Exception> void forEachPair(final Try.BiConsumer<? super T, ? super T, E> action) throws E {
        forEachPair(action, 1);
    }

    @Override
    public <E extends Exception> void forEachTriple(final Try.TriConsumer<? super T, ? super T, ? super T, E> action) throws E {
        forEachTriple(action, 1);
    }

    @Override
    public <K> Stream<Entry<K, List<T>>> groupBy(final Function<? super T, ? extends K> classifier) {
        return groupBy(classifier, Fn.<T> identity());
    }

    @Override
    public <K> Stream<Entry<K, List<T>>> groupBy(final Function<? super T, ? extends K> classifier, Supplier<? extends Map<K, List<T>>> mapFactory) {
        return groupBy(classifier, Fn.<T> identity(), mapFactory);
    }

    @Override
    public <K, U> Stream<Entry<K, List<U>>> groupBy(Function<? super T, ? extends K> classifier, Function<? super T, ? extends U> valueMapper) {
        final Collector<T, ?, List<U>> downstream = Collectors.mapping(valueMapper, Collectors.<U> toList());

        return groupBy(classifier, downstream);
    }

    @Override
    public <K, U> Stream<Map.Entry<K, List<U>>> groupBy(Function<? super T, ? extends K> classifier, Function<? super T, ? extends U> valueMapper,
            Supplier<? extends Map<K, List<U>>> mapFactory) {
        final Collector<T, ?, List<U>> downstream = Collectors.mapping(valueMapper, Collectors.<U> toList());

        return groupBy(classifier, downstream, mapFactory);
    }

    @Override
    public <K, A, D> Stream<Entry<K, D>> groupBy(final Function<? super T, ? extends K> classifier, Collector<? super T, A, D> downstream) {
        return groupBy(classifier, downstream, Fn.Suppliers.<K, D> ofMap());
    }

    @Override
    public <K, A, D> Stream<Entry<K, D>> groupBy(final Function<? super T, ? extends K> classifier, final Collector<? super T, A, D> downstream,
            final Supplier<? extends Map<K, D>> mapFactory) {
        return newStream(new ObjIteratorEx<Entry<K, D>>() {
            private Iterator<Entry<K, D>> iter = null;

            @Override
            public boolean hasNext() {
                init();
                return iter.hasNext();
            }

            @Override
            public Entry<K, D> next() {
                init();
                return iter.next();
            }

            private void init() {
                if (iter == null) {
                    iter = AbstractStream.this.toMap(classifier, downstream, mapFactory).entrySet().iterator();
                }
            }
        }, false, null);
    }

    @Override
    public <K, U, A, D> Stream<Entry<K, D>> groupBy(final Function<? super T, ? extends K> classifier, final Function<? super T, ? extends U> valueMapper,
            Collector<? super U, A, D> downstream) {
        return groupBy(classifier, valueMapper, downstream, Fn.Suppliers.<K, D> ofMap());
    }

    @Override
    public <K, U, A, D> Stream<Entry<K, D>> groupBy(final Function<? super T, ? extends K> classifier, final Function<? super T, ? extends U> valueMapper,
            Collector<? super U, A, D> downstream, Supplier<? extends Map<K, D>> mapFactory) {
        final Collector<T, ?, D> downstream2 = Collectors.mapping(valueMapper, downstream);

        return groupBy(classifier, downstream2, mapFactory);
    }

    @Override
    public <K, U> Stream<Entry<K, U>> groupBy(final Function<? super T, ? extends K> classifier, final Function<? super T, ? extends U> valueMapper,
            BinaryOperator<U> mergeFunction) {
        return groupBy(classifier, valueMapper, mergeFunction, Fn.Suppliers.<K, U> ofMap());
    }

    @Override
    public <K, V> Stream<Entry<K, V>> groupBy(final Function<? super T, ? extends K> classifier, final Function<? super T, ? extends V> valueMapper,
            final BinaryOperator<V> mergeFunction, final Supplier<? extends Map<K, V>> mapFactory) {
        return newStream(new ObjIteratorEx<Entry<K, V>>() {
            private Iterator<Entry<K, V>> iter = null;

            @Override
            public boolean hasNext() {
                init();
                return iter.hasNext();
            }

            @Override
            public Entry<K, V> next() {
                init();
                return iter.next();
            }

            private void init() {
                if (iter == null) {
                    iter = AbstractStream.this.toMap(classifier, valueMapper, mergeFunction, mapFactory).entrySet().iterator();
                }
            }
        }, false, null);
    }

    @Override
    public Stream<Entry<Boolean, List<T>>> partitionBy(Predicate<? super T> predicate) {
        return partitionBy(predicate, Collectors.<T> toList());
    }

    @Override
    public <A, D> Stream<Entry<Boolean, D>> partitionBy(final Predicate<? super T> predicate, final Collector<? super T, A, D> downstream) {
        return newStream(new ObjIteratorEx<Entry<Boolean, D>>() {
            private Iterator<Entry<Boolean, D>> iter = null;

            @Override
            public boolean hasNext() {
                init();
                return iter.hasNext();
            }

            @Override
            public Entry<Boolean, D> next() {
                init();
                return iter.next();
            }

            private void init() {
                if (iter == null) {
                    iter = AbstractStream.this.partitionTo(predicate, downstream).entrySet().iterator();
                }
            }
        }, false, null);
    }

    @Override
    public EntryStream<Boolean, List<T>> partitionByToEntry(Predicate<? super T> predicate) {
        return partitionByToEntry(predicate, Collectors.<T> toList());
    }

    @Override
    public <A, D> EntryStream<Boolean, D> partitionByToEntry(Predicate<? super T> predicate, Collector<? super T, A, D> downstream) {
        final Function<Map.Entry<Boolean, D>, Map.Entry<Boolean, D>> mapper = Fn.identity();

        return partitionBy(predicate, downstream).mapToEntry(mapper);
    }

    @Override
    public <K> EntryStream<K, List<T>> groupByToEntry(Function<? super T, ? extends K> classifier) {
        return groupByToEntry(classifier, Fn.<T> identity());
    }

    @Override
    public <K> EntryStream<K, List<T>> groupByToEntry(Function<? super T, ? extends K> classifier, Supplier<? extends Map<K, List<T>>> mapFactory) {
        return groupByToEntry(classifier, Fn.<T> identity(), mapFactory);
    }

    @Override
    public <K, U> EntryStream<K, List<U>> groupByToEntry(Function<? super T, ? extends K> classifier, Function<? super T, ? extends U> valueMapper) {
        return groupByToEntry(classifier, valueMapper, Fn.Suppliers.<K, List<U>> ofMap());
    }

    @Override
    public <K, U> EntryStream<K, List<U>> groupByToEntry(Function<? super T, ? extends K> classifier, Function<? super T, ? extends U> valueMapper,
            Supplier<? extends Map<K, List<U>>> mapFactory) {
        final Function<Map.Entry<K, List<U>>, Map.Entry<K, List<U>>> mapper = Fn.identity();

        return groupBy(classifier, valueMapper, mapFactory).mapToEntry(mapper);
    }

    @Override
    public <K, A, D> EntryStream<K, D> groupByToEntry(Function<? super T, ? extends K> classifier, Collector<? super T, A, D> downstream) {
        return groupByToEntry(classifier, downstream, Fn.Suppliers.<K, D> ofMap());
    }

    @Override
    public <K, A, D> EntryStream<K, D> groupByToEntry(Function<? super T, ? extends K> classifier, Collector<? super T, A, D> downstream,
            Supplier<? extends Map<K, D>> mapFactory) {
        final Function<Map.Entry<K, D>, Map.Entry<K, D>> mapper = Fn.identity();

        return groupBy(classifier, downstream, mapFactory).mapToEntry(mapper);
    }

    @Override
    public <K, U, A, D> EntryStream<K, D> groupByToEntry(final Function<? super T, ? extends K> classifier, final Function<? super T, ? extends U> valueMapper,
            Collector<? super U, A, D> downstream) {
        return groupByToEntry(classifier, valueMapper, downstream, Fn.Suppliers.<K, D> ofMap());
    }

    @Override
    public <K, U, A, D> EntryStream<K, D> groupByToEntry(final Function<? super T, ? extends K> classifier, final Function<? super T, ? extends U> valueMapper,
            Collector<? super U, A, D> downstream, Supplier<? extends Map<K, D>> mapFactory) {
        final Function<Map.Entry<K, D>, Map.Entry<K, D>> mapper = Fn.identity();

        return groupBy(classifier, valueMapper, downstream, mapFactory).mapToEntry(mapper);
    }

    @Override
    public <K, U> EntryStream<K, U> groupByToEntry(Function<? super T, ? extends K> classifier, Function<? super T, ? extends U> valueMapper,
            BinaryOperator<U> mergeFunction) {
        return groupByToEntry(classifier, valueMapper, mergeFunction, Fn.Suppliers.<K, U> ofMap());
    }

    @Override
    public <K, V> EntryStream<K, V> groupByToEntry(Function<? super T, ? extends K> classifier, Function<? super T, ? extends V> valueMapper,
            BinaryOperator<V> mergeFunction, Supplier<? extends Map<K, V>> mapFactory) {
        final Function<Map.Entry<K, V>, Map.Entry<K, V>> mapper = Fn.identity();

        return groupBy(classifier, valueMapper, mergeFunction, mapFactory).mapToEntry(mapper);
    }

    @Override
    public <K, V> Map<K, V> toMap(Function<? super T, ? extends K> keyExtractor, Function<? super T, ? extends V> valueMapper) {
        final Supplier<? extends Map<K, V>> mapFactory = Fn.Suppliers.ofMap();

        return toMap(keyExtractor, valueMapper, mapFactory);
    }

    @Override
    public <K, V, M extends Map<K, V>> M toMap(Function<? super T, ? extends K> keyExtractor, Function<? super T, ? extends V> valueMapper,
            Supplier<M> mapFactory) {
        final BinaryOperator<V> mergeFunction = Fn.throwingMerger();

        return toMap(keyExtractor, valueMapper, mergeFunction, mapFactory);
    }

    @Override
    public <K, V> Map<K, V> toMap(Function<? super T, ? extends K> keyExtractor, Function<? super T, ? extends V> valueMapper,
            BinaryOperator<V> mergeFunction) {
        final Supplier<? extends Map<K, V>> mapFactory = Fn.Suppliers.ofMap();

        return toMap(keyExtractor, valueMapper, mergeFunction, mapFactory);
    }

    @Override
    public <K, A, D> Map<K, D> toMap(Function<? super T, ? extends K> classifier, Collector<? super T, A, D> downstream) {
        final Supplier<? extends Map<K, D>> mapFactory = Fn.Suppliers.ofMap();

        return toMap(classifier, downstream, mapFactory);
    }

    @Override
    public <K, U, A, D> Map<K, D> toMap(Function<? super T, ? extends K> classifier, Function<? super T, ? extends U> valueMapper,
            Collector<? super U, A, D> downstream) {
        final Supplier<? extends Map<K, D>> mapFactory = Fn.Suppliers.ofMap();

        return toMap(classifier, valueMapper, downstream, mapFactory);
    }

    @Override
    public <K, U, A, D, M extends Map<K, D>> M toMap(final Function<? super T, ? extends K> classifier, final Function<? super T, ? extends U> valueMapper,
            final Collector<? super U, A, D> downstream, final Supplier<M> mapFactory) {
        return toMap(classifier, Collectors.mapping(valueMapper, downstream), mapFactory);
    }

    @Override
    public <K> Map<K, List<T>> groupTo(Function<? super T, ? extends K> classifier) {
        final Supplier<? extends Map<K, List<T>>> mapFactory = Fn.Suppliers.ofMap();

        return groupTo(classifier, mapFactory);
    }

    @Override
    public <K, M extends Map<K, List<T>>> M groupTo(Function<? super T, ? extends K> classifier, Supplier<M> mapFactory) {
        final Collector<? super T, ?, List<T>> downstream = Collectors.toList();

        return toMap(classifier, downstream, mapFactory);
    }

    @Override
    public <K, U> Map<K, List<U>> groupTo(Function<? super T, ? extends K> keyExtractor, Function<? super T, ? extends U> valueMapper) {
        return toMap(keyExtractor, (Collector<T, ?, List<U>>) (Collector<?, ?, ?>) Collectors.mapping(valueMapper, Collectors.toList()));
    }

    @SuppressWarnings("rawtypes")
    @Override
    public <K, U, M extends Map<K, List<U>>> M groupTo(Function<? super T, ? extends K> keyExtractor, Function<? super T, ? extends U> valueMapper,
            Supplier<M> mapFactory) {
        return toMap(keyExtractor, (Collector<T, ?, List<U>>) (Collector) Collectors.mapping(valueMapper, Collectors.toList()), mapFactory);
    }

    @Override
    public Map<Boolean, List<T>> partitionTo(Predicate<? super T> predicate) {
        final Collector<? super T, ?, List<T>> downstream = Collectors.toList();

        return partitionTo(predicate, downstream);
    }

    @Override
    public <A, D> Map<Boolean, D> partitionTo(Predicate<? super T> predicate, Collector<? super T, A, D> downstream) {
        return collect(Collectors.partitioningBy(predicate, downstream));
    }

    @Override
    public <K> ListMultimap<K, T> toMultimap(Function<? super T, ? extends K> keyExtractor) {
        return toMultimap(keyExtractor, Fn.<T> identity());
    }

    @Override
    public <K, V extends Collection<T>, M extends Multimap<K, T, V>> M toMultimap(Function<? super T, ? extends K> keyExtractor, Supplier<M> mapFactory) {
        return toMultimap(keyExtractor, Fn.<T> identity(), mapFactory);
    }

    @Override
    public <K, U> ListMultimap<K, U> toMultimap(Function<? super T, ? extends K> keyExtractor, Function<? super T, ? extends U> valueMapper) {
        return toMultimap(keyExtractor, valueMapper, Fn.Suppliers.<K, U> ofListMultimap());
    }

    @Override
    public Matrix<T> toMatrix(final Class<T> type) {
        final T[] a = toArray(new IntFunction<T[]>() {
            @Override
            public T[] apply(int value) {
                return (T[]) N.newArray(type, 0);
            }
        });

        return Matrix.of(a);
    }

    @Override
    public int sumInt(ToIntFunction<? super T> mapper) {
        return collect(Collectors.summingInt(mapper));
    }

    @Override
    public long sumLong(ToLongFunction<? super T> mapper) {
        return collect(Collectors.summingLong(mapper));
    }

    @Override
    public double sumDouble(ToDoubleFunction<? super T> mapper) {
        return collect(Collectors.summingDouble(mapper));
    }

    @Override
    public OptionalDouble averageInt(ToIntFunction<? super T> mapper) {
        return collect(Collectors.averagingIntt(mapper));
    }

    @Override
    public OptionalDouble averageLong(ToLongFunction<? super T> mapper) {
        return collect(Collectors.averagingLongg(mapper));
    }

    @Override
    public OptionalDouble averageDouble(ToDoubleFunction<? super T> mapper) {
        return collect(Collectors.averagingDoubble(mapper));
    }

    @Override
    public <U> Stream<Pair<T, U>> innerJoin(final Collection<U> b, final Function<? super T, ?> leftKeyMapper, final Function<? super U, ?> rightKeyMapper) {
        final ListMultimap<Object, U> rightKeyMap = ListMultimap.from(b, rightKeyMapper);

        return flatMap(new Function<T, Stream<Pair<T, U>>>() {
            @Override
            public Stream<Pair<T, U>> apply(final T t) {
                final Stream<U> s = Stream.of(rightKeyMap.get(leftKeyMapper.apply(t)));

                return s == null ? Stream.<Pair<T, U>> empty() : s.map(new Function<U, Pair<T, U>>() {
                    @Override
                    public Pair<T, U> apply(U u) {
                        return Pair.of(t, u);
                    }
                });
            }
        });
    }

    @Override
    public <U> Stream<Pair<T, U>> innerJoin(final Collection<U> b, final BiPredicate<? super T, ? super U> predicate) {
        return flatMap(new Function<T, Stream<Pair<T, U>>>() {
            @Override
            public Stream<Pair<T, U>> apply(final T t) {
                return Stream.of(b).filter(new Predicate<U>() {
                    @Override
                    public boolean test(final U u) {
                        return predicate.test(t, u);
                    }
                }).map(new Function<U, Pair<T, U>>() {
                    @Override
                    public Pair<T, U> apply(U u) {
                        return Pair.of(t, u);
                    }
                });
            }
        });
    }

    @Override
    public <U> Stream<Pair<T, U>> fullJoin(final Collection<U> b, final Function<? super T, ?> leftKeyMapper, final Function<? super U, ?> rightKeyMapper) {
        final ListMultimap<Object, U> rightKeyMap = ListMultimap.from(b, rightKeyMapper);
        final Map<U, U> joinedRights = new IdentityHashMap<>();
        final boolean isParallelStream = this.isParallel();

        return flatMap(new Function<T, Stream<Pair<T, U>>>() {
            @Override
            public Stream<Pair<T, U>> apply(final T t) {
                final Stream<U> s = Stream.of(rightKeyMap.get(leftKeyMapper.apply(t)));

                return s == null ? Stream.of(Pair.of(t, (U) null)) : s.map(new Function<U, Pair<T, U>>() {
                    @Override
                    public Pair<T, U> apply(U u) {
                        if (isParallelStream) {
                            synchronized (joinedRights) {
                                joinedRights.put(u, u);
                            }
                        } else {
                            joinedRights.put(u, u);
                        }

                        return Pair.of(t, u);
                    }
                });
            }
        }).append(Stream.of(b).filter(new Predicate<U>() {
            @Override
            public boolean test(U u) {
                return joinedRights.containsKey(u) == false;
            }
        }).map(new Function<U, Pair<T, U>>() {
            @Override
            public Pair<T, U> apply(U u) {
                return Pair.of((T) null, u);
            }
        }));
    }

    @Override
    public <U> Stream<Pair<T, U>> fullJoin(final Collection<U> b, final BiPredicate<? super T, ? super U> predicate) {
        final Map<U, U> joinedRights = new IdentityHashMap<>();
        final boolean isParallelStream = this.isParallel();

        return flatMap(new Function<T, Stream<Pair<T, U>>>() {
            @Override
            public Stream<Pair<T, U>> apply(final T t) {
                final MutableBoolean joined = MutableBoolean.of(false);

                return Stream.of(b).filter(new Predicate<U>() {
                    @Override
                    public boolean test(final U u) {
                        return predicate.test(t, u);
                    }
                }).map(new Function<U, Pair<T, U>>() {
                    @Override
                    public Pair<T, U> apply(U u) {
                        joined.setTrue();

                        if (isParallelStream) {
                            synchronized (joinedRights) {
                                joinedRights.put(u, u);
                            }
                        } else {
                            joinedRights.put(u, u);
                        }

                        return Pair.of(t, u);
                    }
                }).append(Stream.iterate(new BooleanSupplier() {
                    @Override
                    public boolean getAsBoolean() {
                        return joined.isFalse();
                    }
                }, new Supplier<Pair<T, U>>() {
                    @Override
                    public Pair<T, U> get() {
                        joined.setTrue();
                        return Pair.of(t, (U) null);
                    }
                }));
            }
        }).append(Stream.of(b).filter(new Predicate<U>() {
            @Override
            public boolean test(U u) {
                return joinedRights.containsKey(u) == false;
            }
        }).map(new Function<U, Pair<T, U>>() {
            @Override
            public Pair<T, U> apply(U u) {
                return Pair.of((T) null, u);
            }
        }));
    }

    @Override
    public <U> Stream<Pair<T, U>> leftJoin(final Collection<U> b, final Function<? super T, ?> leftKeyMapper, final Function<? super U, ?> rightKeyMapper) {
        final ListMultimap<Object, U> rightKeyMap = ListMultimap.from(b, rightKeyMapper);

        return flatMap(new Function<T, Stream<Pair<T, U>>>() {
            @Override
            public Stream<Pair<T, U>> apply(final T t) {
                final Stream<U> s = Stream.of(rightKeyMap.get(leftKeyMapper.apply(t)));

                return s == null ? Stream.of(Pair.of(t, (U) null)) : s.map(new Function<U, Pair<T, U>>() {
                    @Override
                    public Pair<T, U> apply(U u) {
                        return Pair.of(t, u);
                    }
                });
            }
        });
    }

    @Override
    public <U> Stream<Pair<T, U>> leftJoin(final Collection<U> b, final BiPredicate<? super T, ? super U> predicate) {
        return flatMap(new Function<T, Stream<Pair<T, U>>>() {
            @Override
            public Stream<Pair<T, U>> apply(final T t) {
                final MutableBoolean joined = MutableBoolean.of(false);

                return Stream.of(b).filter(new Predicate<U>() {
                    @Override
                    public boolean test(final U u) {
                        return predicate.test(t, u);
                    }
                }).map(new Function<U, Pair<T, U>>() {
                    @Override
                    public Pair<T, U> apply(U u) {
                        joined.setTrue();

                        return Pair.of(t, u);
                    }
                }).append(Stream.iterate(new BooleanSupplier() {
                    @Override
                    public boolean getAsBoolean() {
                        return joined.isFalse();
                    }
                }, new Supplier<Pair<T, U>>() {
                    @Override
                    public Pair<T, U> get() {
                        joined.setTrue();
                        return Pair.of(t, (U) null);
                    }
                }));
            }
        });
    }

    @Override
    public <U> Stream<Pair<T, U>> rightJoin(final Collection<U> b, final Function<? super T, ?> leftKeyMapper, final Function<? super U, ?> rightKeyMapper) {
        final ListMultimap<Object, U> rightKeyMap = ListMultimap.from(b, rightKeyMapper);
        final Map<U, U> joinedRights = new IdentityHashMap<>();
        final boolean isParallelStream = this.isParallel();

        return flatMap(new Function<T, Stream<Pair<T, U>>>() {
            @Override
            public Stream<Pair<T, U>> apply(final T t) {
                final Stream<U> s = Stream.of(rightKeyMap.get(leftKeyMapper.apply(t)));

                return s == null ? Stream.<Pair<T, U>> empty() : s.map(new Function<U, Pair<T, U>>() {
                    @Override
                    public Pair<T, U> apply(U u) {
                        if (isParallelStream) {
                            synchronized (joinedRights) {
                                joinedRights.put(u, u);
                            }
                        } else {
                            joinedRights.put(u, u);
                        }

                        return Pair.of(t, u);
                    }
                });
            }
        }).append(Stream.of(b).filter(new Predicate<U>() {
            @Override
            public boolean test(U u) {
                return joinedRights.containsKey(u) == false;
            }
        }).map(new Function<U, Pair<T, U>>() {
            @Override
            public Pair<T, U> apply(U u) {
                return Pair.of((T) null, u);
            }
        }));
    }

    @Override
    public <U> Stream<Pair<T, U>> rightJoin(final Collection<U> b, final BiPredicate<? super T, ? super U> predicate) {
        final Map<U, U> joinedRights = new IdentityHashMap<>();
        final boolean isParallelStream = this.isParallel();

        return flatMap(new Function<T, Stream<Pair<T, U>>>() {
            @Override
            public Stream<Pair<T, U>> apply(final T t) {
                return Stream.of(b).filter(new Predicate<U>() {
                    @Override
                    public boolean test(final U u) {
                        return predicate.test(t, u);
                    }
                }).map(new Function<U, Pair<T, U>>() {
                    @Override
                    public Pair<T, U> apply(U u) {

                        if (isParallelStream) {
                            synchronized (joinedRights) {
                                joinedRights.put(u, u);
                            }
                        } else {
                            joinedRights.put(u, u);
                        }

                        return Pair.of(t, u);
                    }
                });
            }
        }).append(Stream.of(b).filter(new Predicate<U>() {
            @Override
            public boolean test(U u) {
                return joinedRights.containsKey(u) == false;
            }
        }).map(new Function<U, Pair<T, U>>() {
            @Override
            public Pair<T, U> apply(U u) {
                return Pair.of((T) null, u);
            }
        }));
    }

    @Override
    public <E extends Exception> Optional<T> findAny(final Try.Predicate<? super T, E> predicate) throws E {
        return findFirst(predicate);
    }

    @Override
    public <E extends Exception, E2 extends Exception> Optional<T> findFirstOrLast(final Try.Predicate<? super T, E> predicateForFirst,
            final Try.Predicate<? super T, E2> predicateForLast) throws E, E2 {
        assertNotClosed();

        try {
            final ObjIteratorEx<T> iter = iteratorEx();
            T last = (T) NONE;
            T next = null;

            while (iter.hasNext()) {
                next = iter.next();

                if (predicateForFirst.test(next)) {
                    return Optional.of(next);
                } else if (predicateForLast.test(next)) {
                    last = next;
                }
            }

            return last == NONE ? (Optional<T>) Optional.empty() : Optional.of(last);
        } finally {
            close();
        }
    }

    @Override
    public <U, E extends Exception, E2 extends Exception> Optional<T> findFirstOrLast(final U seed,
            final Try.BiPredicate<? super T, ? super U, E> predicateForFirst, final Try.BiPredicate<? super T, ? super U, E2> predicateForLast) throws E, E2 {
        assertNotClosed();

        try {
            final ObjIteratorEx<T> iter = iteratorEx();
            T last = (T) NONE;
            T next = null;

            while (iter.hasNext()) {
                next = iter.next();

                if (predicateForFirst.test(next, seed)) {
                    return Optional.of(next);
                } else if (predicateForLast.test(next, seed)) {
                    last = next;
                }
            }

            return last == NONE ? (Optional<T>) Optional.empty() : Optional.of(last);
        } finally {
            close();
        }
    }

    @Override
    public <U, E extends Exception, E2 extends Exception> Optional<T> findFirstOrLast(final Function<? super T, U> preFunc,
            final Try.BiPredicate<? super T, ? super U, E> predicateForFirst, final Try.BiPredicate<? super T, ? super U, E2> predicateForLast) throws E, E2 {
        assertNotClosed();

        try {
            final ObjIteratorEx<T> iter = iteratorEx();
            U seed = null;
            T last = (T) NONE;
            T next = null;

            while (iter.hasNext()) {
                next = iter.next();
                seed = preFunc.apply(next);

                if (predicateForFirst.test(next, seed)) {
                    return Optional.of(next);
                } else if (predicateForLast.test(next, seed)) {
                    last = next;
                }
            }

            return last == NONE ? (Optional<T>) Optional.empty() : Optional.of(last);
        } finally {
            close();
        }
    }

    @Override
    @SafeVarargs
    public final boolean containsAll(final T... a) {
        assertNotClosed();

        try {
            if (N.isNullOrEmpty(a)) {
                return true;
            } else if (a.length == 1) {
                return anyMatch(Fn.equal(a[0]));
            } else if (a.length == 2) {
                return filter(new Predicate<T>() {
                    private final T val1 = a[0];
                    private final T val2 = a[1];

                    @Override
                    public boolean test(T t) {
                        return N.equals(t, val1) || N.equals(t, val2);
                    }
                }).distinct().limit(2).count() == 2;
            } else {
                return containsAll(N.asSet(a));
            }
        } finally {
            close();
        }
    }

    @Override
    public boolean containsAll(final Collection<? extends T> c) {
        assertNotClosed();

        try {
            if (N.isNullOrEmpty(c)) {
                return true;
            } else if (c.size() == 1) {
                final T val = c instanceof List ? ((List<T>) c).get(0) : c.iterator().next();
                return anyMatch(Fn.equal(val));
            } else {
                final Set<T> set = c instanceof Set ? (Set<T>) c : N.newHashSet(c);
                return filter(new Predicate<T>() {
                    @Override
                    public boolean test(T t) {
                        return set.contains(t);
                    }
                }).distinct().limit(set.size()).count() == set.size();
            }
        } finally {
            close();
        }
    }

    @Override
    public Optional<T> first() {
        assertNotClosed();

        try {
            final Iterator<T> iter = this.iterator();

            if (iter.hasNext() == false) {
                return Optional.empty();
            }

            return Optional.of(iter.next());
        } finally {
            close();
        }
    }

    @Override
    public Optional<T> last() {
        assertNotClosed();

        try {
            final Iterator<T> iter = this.iterator();

            if (iter.hasNext() == false) {
                return Optional.empty();
            }

            T next = iter.next();

            while (iter.hasNext()) {
                next = iter.next();
            }

            return Optional.of(next);
        } finally {
            close();
        }
    }

    @Override
    public Optional<T> onlyOne() throws DuplicatedResultException {
        assertNotClosed();

        try {
            final Iterator<T> iter = this.iteratorEx();

            final Optional<T> result = iter.hasNext() ? Optional.of(iter.next()) : Optional.<T> empty();

            if (result.isPresent() && iter.hasNext()) {
                throw new DuplicatedResultException("There are at least two elements: " + Strings.concat(result.get(), ", ", iter.next()));
            }

            return result;
        } finally {
            close();
        }
    }

    @Override
    public Stream<T> skipNull() {
        return filter(Fn.notNull());
    }

    @Override
    public Stream<List<T>> slidingToList(int windowSize) {
        return slidingToList(windowSize, 1);
    }

    @Override
    public Stream<T> intersection(final Collection<?> c) {
        final Multiset<?> multiset = Multiset.from(c);

        return newStream(this.sequential().filter(new Predicate<T>() {
            @Override
            public boolean test(T value) {
                return multiset.getAndRemove(value) > 0;
            }
        }).iterator(), sorted, cmp);
    }

    @Override
    public Stream<T> intersection(final Function<? super T, ?> mapper, final Collection<?> c) {
        final Multiset<?> multiset = Multiset.from(c);

        return newStream(this.sequential().filter(new Predicate<T>() {
            @Override
            public boolean test(T value) {
                return multiset.getAndRemove(mapper.apply(value)) > 0;
            }
        }).iterator(), sorted, cmp);
    }

    @Override
    public Stream<T> difference(final Collection<?> c) {
        final Multiset<?> multiset = Multiset.from(c);

        return newStream(this.sequential().filter(new Predicate<T>() {
            @Override
            public boolean test(T value) {
                return multiset.getAndRemove(value) < 1;
            }
        }).iterator(), sorted, cmp);
    }

    @Override
    public Stream<T> difference(final Function<? super T, ?> mapper, final Collection<?> c) {
        final Multiset<?> multiset = Multiset.from(c);

        return newStream(this.sequential().filter(new Predicate<T>() {
            @Override
            public boolean test(T value) {
                return multiset.getAndRemove(mapper.apply(value)) < 1;
            }
        }).iterator(), sorted, cmp);
    }

    @Override
    public Stream<T> symmetricDifference(final Collection<T> c) {
        final Multiset<?> multiset = Multiset.from(c);

        return newStream(this.sequential().filter(new Predicate<T>() {
            @Override
            public boolean test(T value) {
                return multiset.getAndRemove(value) < 1;
            }
        }).append(Stream.of(c).filter(new Predicate<T>() {
            @Override
            public boolean test(T value) {
                return multiset.getAndRemove(value) > 0;
            }
        })).iterator(), false, null);
    }

    @Override
    public Stream<T> reversed() {
        return newStream(new ObjIteratorEx<T>() {
            private boolean initialized = false;
            private T[] aar;
            private int cursor;

            @Override
            public boolean hasNext() {
                if (initialized == false) {
                    init();
                }

                return cursor > 0;
            }

            @Override
            public T next() {
                if (initialized == false) {
                    init();
                }

                if (cursor <= 0) {
                    throw new NoSuchElementException();
                }

                return aar[--cursor];
            }

            @Override
            public long count() {
                if (initialized == false) {
                    init();
                }

                return cursor;
            }

            @Override
            public void skip(long n) {
                N.checkArgNotNegative(n, "n");

                if (initialized == false) {
                    init();
                }

                cursor = n < cursor ? cursor - (int) n : 0;
            }

            @Override
            public <A> A[] toArray(A[] a) {
                if (initialized == false) {
                    init();
                }

                a = a.length >= cursor ? a : (A[]) N.newArray(a.getClass().getComponentType(), cursor);

                for (int i = 0; i < cursor; i++) {
                    a[i] = (A) aar[cursor - i - 1];
                }

                return a;
            }

            private void init() {
                if (initialized == false) {
                    initialized = true;
                    aar = (T[]) AbstractStream.this.toArray();
                    cursor = aar.length;
                }
            }
        }, false, null);
    }

    @Override
    public Stream<T> shuffled(final Random rnd) {
        return lazyLoad(new Function<Object[], Object[]>() {
            @Override
            public Object[] apply(final Object[] a) {
                N.shuffle(a, rnd);
                return a;
            }
        }, false, null);
    }

    @Override
    public Stream<T> rotated(final int distance) {
        return newStream(new ObjIteratorEx<T>() {
            private boolean initialized = false;
            private T[] aar;
            private int len;
            private int start;
            private int cnt = 0;

            @Override
            public boolean hasNext() {
                if (initialized == false) {
                    init();
                }

                return cnt < len;
            }

            @Override
            public T next() {
                if (hasNext() == false) {
                    throw new NoSuchElementException();
                }

                return aar[(start + cnt++) % len];
            }

            @Override
            public long count() {
                if (initialized == false) {
                    init();
                }

                return len - cnt;
            }

            @Override
            public void skip(long n) {
                N.checkArgNotNegative(n, "n");

                if (initialized == false) {
                    init();
                }

                cnt = n < len - cnt ? cnt + (int) n : len;
            }

            @Override
            public <A> A[] toArray(A[] a) {
                if (initialized == false) {
                    init();
                }

                a = a.length >= len - cnt ? a : (A[]) N.newArray(a.getClass().getComponentType(), len - cnt);

                for (int i = cnt; i < len; i++) {
                    a[i - cnt] = (A) aar[(start + i) % len];
                }

                return a;
            }

            private void init() {
                if (initialized == false) {
                    initialized = true;
                    aar = (T[]) AbstractStream.this.toArray();
                    len = aar.length;

                    if (len > 0) {
                        start = distance % len;

                        if (start < 0) {
                            start += len;
                        }

                        start = len - start;
                    }
                }
            }
        }, distance == 0 && sorted, distance == 0 ? cmp : null);
    }

    @Override
    public Stream<T> sorted() {
        return sorted(NATURAL_COMPARATOR);
    }

    @Override
    public Stream<T> reverseSorted() {
        return sorted(REVERSED_COMPARATOR);
    }

    @Override
    public Stream<T> sorted(final Comparator<? super T> comparator) {
        final Comparator<? super T> cmp = comparator == null ? NATURAL_COMPARATOR : comparator;

        if (sorted && cmp == this.cmp) {
            return this;
        }

        return lazyLoad(new Function<Object[], Object[]>() {
            @Override
            public Object[] apply(final Object[] a) {
                if (isParallel()) {
                    N.parallelSort((T[]) a, cmp);
                } else {
                    N.sort((T[]) a, cmp);
                }

                return a;
            }
        }, true, cmp);
    }

    private Stream<T> lazyLoad(final Function<Object[], Object[]> op, final boolean sorted, final Comparator<? super T> cmp) {
        return newStream(new ObjIteratorEx<T>() {
            private boolean initialized = false;
            private T[] aar;
            private int cursor = 0;
            private int len;

            @Override
            public boolean hasNext() {
                if (initialized == false) {
                    init();
                }

                return cursor < len;
            }

            @Override
            public T next() {
                if (initialized == false) {
                    init();
                }

                if (cursor >= len) {
                    throw new NoSuchElementException();
                }

                return aar[cursor++];
            }

            @Override
            public long count() {
                if (initialized == false) {
                    init();
                }

                return len - cursor;
            }

            @Override
            public void skip(long n) {
                N.checkArgNotNegative(n, "n");

                if (initialized == false) {
                    init();
                }

                cursor = n > len - cursor ? len : cursor + (int) n;
            }

            @Override
            public <A> A[] toArray(A[] a) {
                if (initialized == false) {
                    init();
                }

                a = a.length >= (len - cursor) ? a : (A[]) N.newArray(a.getClass().getComponentType(), (len - cursor));

                for (int i = cursor; i < len; i++) {
                    a[i - cursor] = (A) aar[i];
                }

                return a;
            }

            private void init() {
                if (initialized == false) {
                    initialized = true;
                    aar = (T[]) op.apply(AbstractStream.this.toArray());
                    len = aar.length;
                }
            }
        }, sorted, cmp);
    }

    @Override
    public Stream<T> distinct() {
        final Set<Object> set = new HashSet<>();

        return newStream(this.sequential().filter(new Predicate<T>() {
            @Override
            public boolean test(T value) {
                return set.add(hashKey(value));
            }
        }).iterator(), sorted, cmp);
    }

    @Override
    public Stream<T> distinctBy(final Function<? super T, ?> keyExtractor) {
        //    final Set<Object> set = new HashSet<>();
        //
        //    final Predicate<T> predicate = isParallel() ? new Predicate<T>() {
        //        @Override
        //        public boolean test(T value) {
        //            final Object key = hashKey(keyExtractor.apply(value));
        //
        //            synchronized (set) {
        //                return set.add(key);
        //            }
        //        }
        //    } : new Predicate<T>() {
        //        @Override
        //        public boolean test(T value) {
        //            return set.add(hashKey(keyExtractor.apply(value)));
        //        }
        //    };

        final Predicate<T> predicate = isParallel() ? new Predicate<T>() {
            private final ConcurrentHashMap<Object, Object> map = new ConcurrentHashMap<>();

            @Override
            public boolean test(T value) {
                return map.put(hashKey(keyExtractor.apply(value)), StreamBase.NONE) == null;
            }
        } : new Predicate<T>() {
            private final Set<Object> set = new HashSet<>();

            @Override
            public boolean test(T value) {
                return set.add(hashKey(keyExtractor.apply(value)));
            }
        };

        return filter(predicate);
    }

    @Override
    public Stream<T> top(int n) {
        return top(n, NATURAL_COMPARATOR);
    }

    @Override
    public Optional<Map<Percentage, T>> percentiles() {
        assertNotClosed();

        try {
            final Object[] a = sorted().toArray();

            if (N.isNullOrEmpty(a)) {
                return Optional.empty();
            }

            return Optional.of((Map<Percentage, T>) N.percentiles(a));
        } finally {
            close();
        }
    }

    @Override
    public Optional<Map<Percentage, T>> percentiles(Comparator<? super T> comparator) {
        assertNotClosed();

        try {
            final Object[] a = sorted(comparator).toArray();

            if (N.isNullOrEmpty(a)) {
                return Optional.empty();
            }

            return Optional.of((Map<Percentage, T>) N.percentiles(a));
        } finally {
            close();
        }
    }

    //    @Override
    //    public Stream<Set<T>> powerSet() {
    //        final Set<T> set = toSet(new Supplier<Set<T>>() {
    //            @Override
    //            public Set<T> get() {
    //                return new LinkedHashSet<>();
    //            }
    //        });
    //
    //        return newStream(Seq.powerSet(set).iterator(), false, null);
    //    }

    @Override
    public Stream<List<T>> combinations() {
        if (this instanceof ArrayStream) {
            return newStream(IntStream.rangeClosed(0, (int) count()).flatMapToObj(new IntFunction<Stream<List<T>>>() {
                @Override
                public Stream<List<T>> apply(int value) {
                    return combinations(value);
                }
            }).iterator(), false, null);
        } else {
            return newStream((T[]) toArray(), false, null).combinations();
        }
    }

    @Override
    public Stream<List<T>> combinations(final int len) {
        if (this instanceof ArrayStream) {
            N.checkFromIndexSize(0, len, (int) count());

            if (len == 0) {
                return newStream(N.asArray(N.<T> emptyList()), false, null);
            } else if (len == 1) {
                return map(new Function<T, List<T>>() {
                    @Override
                    public List<T> apply(T t) {
                        return N.asList(t);
                    }
                });
            } else if (len == count()) {
                return newStream(N.asArray(toList()), false, null);
            } else {
                final T[] a = ((ArrayStream<T>) this).elements;
                final int fromIndex = ((ArrayStream<T>) this).fromIndex;
                final int toIndex = ((ArrayStream<T>) this).toIndex;

                return newStream(new ObjIteratorEx<List<T>>() {
                    private final int[] indices = Array.range(fromIndex, fromIndex + len);

                    @Override
                    public boolean hasNext() {
                        return indices[0] <= toIndex - len;
                    }

                    @Override
                    public List<T> next() {
                        final List<T> result = new ArrayList<>(len);

                        for (int idx : indices) {
                            result.add(a[idx]);
                        }

                        if (++indices[len - 1] == toIndex) {
                            for (int i = len - 1; i > 0; i--) {
                                if (indices[i] > toIndex - (len - i)) {
                                    indices[i - 1]++;

                                    for (int j = i; j < len; j++) {
                                        indices[j] = indices[j - 1] + 1;
                                    }
                                }
                            }
                        }

                        return result;
                    }

                }, false, null);
            }
        } else {
            return newStream((T[]) toArray(), false, null).combinations(len);
        }
    }

    @Override
    public Stream<List<T>> combinations(final int len, final boolean repeat) {
        if (repeat == false) {
            return combinations(len);
        } else {
            return newStream(new ObjIteratorEx<List<T>>() {
                private List<List<T>> list = null;
                private int size = 0;
                private int cursor = 0;

                @Override
                public boolean hasNext() {
                    init();
                    return cursor < size;
                }

                @Override
                public List<T> next() {
                    if (hasNext() == false) {
                        throw new NoSuchElementException();
                    }

                    return list.get(cursor++);
                }

                @Override
                public void skip(long n) {
                    N.checkArgNotNegative(n, "n");

                    cursor = n <= size - cursor ? cursor + (int) n : size;
                }

                @Override
                public long count() {
                    return size - cursor;
                }

                private void init() {
                    if (list == null) {
                        list = N.cartesianProduct(N.repeat(AbstractStream.this.toList(), len));
                        size = list.size();
                    }
                }
            }, false, null);
        }
    }

    @Override
    public Stream<List<T>> permutations() {
        return newStream(PermutationIterator.of(toList()), false, null);
    }

    @Override
    public Stream<List<T>> orderedPermutations() {
        return orderedPermutations(NATURAL_COMPARATOR);
    }

    @Override
    public Stream<List<T>> orderedPermutations(Comparator<? super T> comparator) {
        final Iterator<List<T>> iter = PermutationIterator.ordered(toList(), comparator == null ? NATURAL_COMPARATOR : comparator);

        return newStream(iter, false, null);
    }

    @Override
    public Stream<List<T>> cartesianProduct(final Collection<? extends Collection<? extends T>> cs) {
        final List<Collection<? extends T>> cList = new ArrayList<>(cs.size() + 1);
        cList.add(this.toList());
        cList.addAll(cs);

        return newStream(new ObjIteratorEx<List<T>>() {
            private List<List<T>> list = null;
            private int size = 0;
            private int cursor = 0;

            @Override
            public boolean hasNext() {
                init();
                return cursor < size;
            }

            @Override
            public List<T> next() {
                if (hasNext() == false) {
                    throw new NoSuchElementException();
                }

                return list.get(cursor++);
            }

            @Override
            public void skip(long n) {
                N.checkArgNotNegative(n, "n");

                cursor = n <= size - cursor ? cursor + (int) n : size;
            }

            @Override
            public long count() {
                return size - cursor;
            }

            private void init() {
                if (list == null) {
                    list = N.cartesianProduct(cList);
                    size = list.size();
                }
            }

        }, false, null);
    }

    @Override
    public <A> A[] toArray(IntFunction<A[]> generator) {
        assertNotClosed();

        try {
            final Object[] src = toArray();
            final A[] res = generator.apply(src.length);
            System.arraycopy(src, 0, res, 0, src.length);
            return res;
        } finally {
            close();
        }
    }

    @Override
    public DataSet toDataSet() {
        return toDataSet(null);
    }

    @Override
    public DataSet toDataSet(boolean isFirstTitle) {
        assertNotClosed();

        try {
            if (isFirstTitle) {
                final ObjIterator<T> iter = this.iterator();

                if (iter.hasNext() == false) {
                    return N.newDataSet(new ArrayList<String>(0), new ArrayList<List<Object>>(0));
                }

                final T header = iter.next();
                final Type<?> type = N.typeOf(header.getClass());
                List<String> columnNames = null;

                if (type.isArray()) {
                    final Object[] a = (Object[]) header;
                    columnNames = new ArrayList<>(a.length);

                    for (Object e : a) {
                        columnNames.add(N.stringOf(e));
                    }
                } else if (type.isCollection()) {
                    final Collection<?> c = (Collection<?>) header;
                    columnNames = new ArrayList<>(c.size());

                    for (Object e : c) {
                        columnNames.add(N.stringOf(e));
                    }
                } else {
                    throw new IllegalArgumentException("Unsupported header type: " + type.name());
                }

                return N.newDataSet(columnNames, Iterators.toList(iter));
            } else {
                return toDataSet(null);
            }
        } finally {
            close();
        }
    }

    @Override
    public DataSet toDataSet(List<String> columnNames) {
        return N.newDataSet(columnNames, toList());
    }

    @Override
    public String join(CharSequence delimiter, CharSequence prefix, CharSequence suffix) {
        assertNotClosed();

        try {
            final Joiner joiner = Joiner.with(delimiter, prefix, suffix).reuseCachedBuffer(true);
            final IteratorEx<T> iter = this.iteratorEx();

            while (iter.hasNext()) {
                joiner.append(iter.next());
            }

            return joiner.toString();
        } finally {
            close();
        }
    }

    @Override
    public boolean hasDuplicates() {
        assertNotClosed();

        try {
            final Set<T> set = new HashSet<>();
            final Iterator<T> iter = iterator();

            while (iter.hasNext()) {
                if (set.add(iter.next()) == false) {
                    return true;
                }
            }

            return false;
        } finally {
            close();
        }
    }

    @Override
    public <R> R collect(Supplier<R> supplier, BiConsumer<R, ? super T> accumulator) {
        final BiConsumer<R, R> combiner = collectingCombiner;

        return collect(supplier, accumulator, combiner);
    }

    @Override
    public <R, A> R collect(java.util.stream.Collector<? super T, A, R> collector) {
        return collect(Collector.of(collector));
    }

    @Override
    public <R, A, RR> RR collectAndThen(Collector<? super T, A, R> downstream, Function<R, RR> func) {
        return func.apply(collect(downstream));
    }

    @Override
    public <R, A, RR> RR collectAndThen(java.util.stream.Collector<? super T, A, R> downstream, java.util.function.Function<R, RR> func) {
        return func.apply(collect(downstream));
    }

    @Override
    public <R> R toListAndThen(final Function<? super List<T>, R> func) {
        return func.apply(toList());
    }

    @Override
    public <R> R toSetAndThen(final Function<? super Set<T>, R> func) {
        return func.apply(toSet());
    }

    //    @Override
    //    public Pair<Optional<T>, Stream<T>> headAndTail() {
    //        return Pair.of(head(), tail());
    //    }

    //    @SuppressWarnings("deprecation")
    //    @Override
    //    public Pair<Stream<T>, Optional<T>> headAndTaill() {
    //        return Pair.of(headd(), taill());
    //    }

    @Override
    public Stream<Indexed<T>> indexed() {
        final MutableLong idx = MutableLong.of(0);

        return newStream(this.sequential().map(new Function<T, Indexed<T>>() {
            @Override
            public Indexed<T> apply(T t) {
                return Indexed.of(t, idx.getAndIncrement());
            }
        }).iterator(), true, INDEXED_COMPARATOR);
    }

    @Override
    public Stream<T> queued() {
        return queued(DEFAULT_QUEUE_SIZE_PER_ITERATOR);
    }

    @Override
    public Stream<T> append(Stream<T> stream) {
        return Stream.concat(this, stream);
    }

    @Override
    public Stream<T> append(Collection<? extends T> c) {
        if (N.isNullOrEmpty(c)) {
            return this;
        }

        return append(Stream.of(c));
    }

    @Override
    public Stream<T> prepend(Stream<T> stream) {
        return Stream.concat(stream, this);
    }

    @Override
    public Stream<T> prepend(Collection<? extends T> c) {
        if (N.isNullOrEmpty(c)) {
            return this;
        }

        return prepend(Stream.of(c));
    }

    @Override
    public Stream<T> merge(final Stream<? extends T> b, final BiFunction<? super T, ? super T, Nth> nextSelector) {
        return Stream.merge(this, b, nextSelector);
    }

    @Override
    public <T2, R> Stream<R> zipWith(Stream<T2> b, BiFunction<? super T, ? super T2, R> zipFunction) {
        return Stream.zip(this, b, zipFunction);
    }

    @Override
    public <T2, T3, R> Stream<R> zipWith(Stream<T2> b, Stream<T3> c, TriFunction<? super T, ? super T2, ? super T3, R> zipFunction) {
        return Stream.zip(this, b, c, zipFunction);
    }

    @Override
    public <T2, R> Stream<R> zipWith(Stream<T2> b, T valueForNoneA, T2 valueForNoneB, BiFunction<? super T, ? super T2, R> zipFunction) {
        return Stream.zip(this, b, valueForNoneA, valueForNoneB, zipFunction);
    }

    @Override
    public <T2, T3, R> Stream<R> zipWith(Stream<T2> b, Stream<T3> c, T valueForNoneA, T2 valueForNoneB, T3 valueForNoneC,
            TriFunction<? super T, ? super T2, ? super T3, R> zipFunction) {
        return Stream.zip(this, b, c, valueForNoneA, valueForNoneB, valueForNoneC, zipFunction);
    }

    @Override
    public long persist(File file, Try.Function<? super T, String, IOException> toLine) throws IOException {
        final Writer writer = new FileWriter(file);

        try {
            return persist(writer, toLine);
        } finally {
            IOUtil.close(writer);
        }
    }

    @Override
    public long persist(OutputStream os, Try.Function<? super T, String, IOException> toLine) throws IOException {
        final BufferedWriter bw = Objectory.createBufferedWriter(os);

        try {
            return persist(bw, toLine);
        } finally {
            Objectory.recycle(bw);
        }
    }

    @Override
    public long persist(Writer writer, Try.Function<? super T, String, IOException> toLine) throws IOException {
        assertNotClosed();

        try {
            final Iterator<T> iter = iterator();
            final BufferedWriter bw = writer instanceof BufferedWriter ? (BufferedWriter) writer : Objectory.createBufferedWriter(writer);
            long cnt = 0;

            try {
                while (iter.hasNext()) {
                    bw.write(toLine.apply(iter.next()));
                    bw.write(IOUtil.LINE_SEPARATOR);
                    cnt++;
                }
            } finally {
                if (bw != writer) {
                    Objectory.recycle(bw);
                }
            }

            return cnt;
        } finally {
            close();
        }
    }

    @Override
    public long persist(final Connection conn, final String insertSQL, final int batchSize, final int batchInterval,
            final Try.BiConsumer<? super PreparedStatement, ? super T, SQLException> stmtSetter) throws SQLException {
        PreparedStatement stmt = null;

        try {
            stmt = conn.prepareStatement(insertSQL);

            return persist(stmt, batchSize, batchInterval, stmtSetter);
        } finally {
            JdbcUtil.closeQuietly(stmt);
        }
    }

    @Override
    public long persist(final PreparedStatement stmt, final int batchSize, final int batchInterval,
            final Try.BiConsumer<? super PreparedStatement, ? super T, SQLException> stmtSetter) throws SQLException {
        N.checkArgument(batchSize > 0 && batchInterval >= 0, "'batchSize'=%s must be greater than 0 and 'batchInterval'=%s can't be negative", batchSize,
                batchInterval);
        assertNotClosed();

        try {
            final Iterator<T> iter = iterator();

            long cnt = 0;
            while (iter.hasNext()) {
                stmtSetter.accept(stmt, iter.next());

                stmt.addBatch();

                if ((++cnt % batchSize) == 0) {
                    stmt.executeBatch();
                    stmt.clearBatch();

                    if (batchInterval > 0) {
                        N.sleep(batchInterval);
                    }
                }
            }

            if ((cnt % batchSize) > 0) {
                stmt.executeBatch();
                stmt.clearBatch();
            }

            return cnt;
        } finally {
            close();
        }
    }

    @Override
    public <K, V> EntryStream<K, V> mapToEntryER(final Function<? super T, K> keyMapper, final Function<? super T, V> valueMapper) {
        N.checkState(isParallel() == false, "mapToEntryER can't be applied to parallel stream");

        final Function<T, Map.Entry<K, V>> mapper = new Function<T, Map.Entry<K, V>>() {
            private final EntryStream.ReusableEntry<K, V> entry = new EntryStream.ReusableEntry<>();

            @Override
            public Entry<K, V> apply(T t) {
                entry.set(keyMapper.apply(t), valueMapper.apply(t));

                return entry;
            }
        };

        return mapToEntry(mapper);
    }

    //    @Override
    //    public <V> EntryStream<T, V> flatMapToEntryER(final Function<? super T, ? extends Collection<? extends V>> flatValueMapper) {
    //        N.checkState(isParallel() == false, "flatMapToEntryER can't be applied to parallel stream");
    //
    //        final Function<T, Stream<Map.Entry<T, V>>> flatEntryMapper = new Function<T, Stream<Map.Entry<T, V>>>() {
    //            private final EntryStream.ReusableEntry<T, V> entry = new EntryStream.ReusableEntry<>();
    //
    //            @Override
    //            public Stream<Map.Entry<T, V>> apply(final T t) {
    //                final Function<V, Map.Entry<T, V>> entryMapper = new Function<V, Map.Entry<T, V>>() {
    //                    @Override
    //                    public Entry<T, V> apply(V v) {
    //                        entry.set(t, v);
    //
    //                        return entry;
    //                    }
    //                };
    //
    //                return Stream.of(flatValueMapper.apply(t)).map(entryMapper);
    //            }
    //        };
    //
    //        return flatMapToEntry(flatEntryMapper);
    //    }
}
