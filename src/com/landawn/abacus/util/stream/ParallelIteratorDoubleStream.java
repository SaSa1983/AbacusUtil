package com.landawn.abacus.util.stream;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.Callable;

import com.landawn.abacus.util.CompletableFuture;
import com.landawn.abacus.util.DoubleList;
import com.landawn.abacus.util.DoubleSummaryStatistics;
import com.landawn.abacus.util.Holder;
import com.landawn.abacus.util.LongMultiset;
import com.landawn.abacus.util.Multimap;
import com.landawn.abacus.util.Multiset;
import com.landawn.abacus.util.MutableBoolean;
import com.landawn.abacus.util.MutableLong;
import com.landawn.abacus.util.N;
import com.landawn.abacus.util.Nth;
import com.landawn.abacus.util.Optional;
import com.landawn.abacus.util.OptionalDouble;
import com.landawn.abacus.util.OptionalNullable;
import com.landawn.abacus.util.Pair;
import com.landawn.abacus.util.function.BiConsumer;
import com.landawn.abacus.util.function.BinaryOperator;
import com.landawn.abacus.util.function.DoubleBiFunction;
import com.landawn.abacus.util.function.DoubleBinaryOperator;
import com.landawn.abacus.util.function.DoubleConsumer;
import com.landawn.abacus.util.function.DoubleFunction;
import com.landawn.abacus.util.function.DoublePredicate;
import com.landawn.abacus.util.function.DoubleToFloatFunction;
import com.landawn.abacus.util.function.DoubleToIntFunction;
import com.landawn.abacus.util.function.DoubleToLongFunction;
import com.landawn.abacus.util.function.DoubleUnaryOperator;
import com.landawn.abacus.util.function.Function;
import com.landawn.abacus.util.function.ObjDoubleConsumer;
import com.landawn.abacus.util.function.Predicate;
import com.landawn.abacus.util.function.Supplier;
import com.landawn.abacus.util.function.ToDoubleFunction;
import com.landawn.abacus.util.function.ToFloatFunction;
import com.landawn.abacus.util.function.ToIntFunction;
import com.landawn.abacus.util.function.ToLongFunction;

/**
 * This class is a sequential, stateful and immutable stream implementation.
 *
 */
final class ParallelIteratorDoubleStream extends AbstractDoubleStream {
    private final ImmutableDoubleIterator elements;
    private final boolean sorted;
    private final int maxThreadNum;
    private final Splitter splitter;
    private volatile IteratorDoubleStream sequential;
    private volatile Stream<Double> boxed;

    ParallelIteratorDoubleStream(ImmutableDoubleIterator values, Collection<Runnable> closeHandlers, boolean sorted, int maxThreadNum, Splitter splitter) {
        super(closeHandlers);

        this.elements = values;
        this.sorted = sorted;
        this.maxThreadNum = N.min(maxThreadNum, Stream.THREAD_POOL_SIZE);
        this.splitter = splitter == null ? Stream.DEFAULT_SPILTTER : splitter;
        this.sequential = new IteratorDoubleStream(this.elements, this.closeHandlers, this.sorted);
    }

    ParallelIteratorDoubleStream(DoubleStream stream, Set<Runnable> closeHandlers, boolean sorted, int maxThreadNum, Splitter splitter) {
        this(stream.doubleIterator(), Stream.mergeCloseHandlers(stream, closeHandlers), sorted, maxThreadNum, splitter);
    }

    ParallelIteratorDoubleStream(Stream<Double> stream, Set<Runnable> closeHandlers, boolean sorted, int maxThreadNum, Splitter splitter) {
        this(Stream.doubleIterator(stream.iterator()), Stream.mergeCloseHandlers(stream, closeHandlers), sorted, maxThreadNum, splitter);
    }

    @Override
    public DoubleStream filter(final DoublePredicate predicate) {
        return filter(predicate, Long.MAX_VALUE);
    }

    @Override
    public DoubleStream filter(final DoublePredicate predicate, final long max) {
        if (maxThreadNum <= 1) {
            return new ParallelIteratorDoubleStream(sequential().filter(predicate, max).doubleIterator(), closeHandlers, sorted, maxThreadNum, splitter);
        }

        final Stream<Double> stream = boxed().filter(new Predicate<Double>() {
            @Override
            public boolean test(Double value) {
                return predicate.test(value);
            }
        }, max);

        return new ParallelIteratorDoubleStream(stream, closeHandlers, false, maxThreadNum, splitter);
    }

    @Override
    public DoubleStream takeWhile(final DoublePredicate predicate) {
        return takeWhile(predicate, Long.MAX_VALUE);
    }

    @Override
    public DoubleStream takeWhile(final DoublePredicate predicate, final long max) {
        if (maxThreadNum <= 1) {
            return new ParallelIteratorDoubleStream(sequential().takeWhile(predicate, max).doubleIterator(), closeHandlers, sorted, maxThreadNum, splitter);
        }

        final Stream<Double> stream = boxed().takeWhile(new Predicate<Double>() {
            @Override
            public boolean test(Double value) {
                return predicate.test(value);
            }
        }, max);

        return new ParallelIteratorDoubleStream(stream, closeHandlers, false, maxThreadNum, splitter);
    }

    @Override
    public DoubleStream dropWhile(final DoublePredicate predicate) {
        return dropWhile(predicate, Long.MAX_VALUE);
    }

    @Override
    public DoubleStream dropWhile(final DoublePredicate predicate, final long max) {
        if (maxThreadNum <= 1) {
            return new ParallelIteratorDoubleStream(sequential().dropWhile(predicate, max).doubleIterator(), closeHandlers, sorted, maxThreadNum, splitter);
        }

        final Stream<Double> stream = boxed().dropWhile(new Predicate<Double>() {
            @Override
            public boolean test(Double value) {
                return predicate.test(value);
            }
        }, max);

        return new ParallelIteratorDoubleStream(stream, closeHandlers, false, maxThreadNum, splitter);
    }

    @Override
    public DoubleStream map(final DoubleUnaryOperator mapper) {
        if (maxThreadNum <= 1) {
            return new ParallelIteratorDoubleStream(sequential().map(mapper).doubleIterator(), closeHandlers, false, maxThreadNum, splitter);
        }

        final DoubleStream stream = boxed().mapToDouble(new ToDoubleFunction<Double>() {
            @Override
            public double applyAsDouble(Double value) {
                return mapper.applyAsDouble(value);
            }
        });

        return new ParallelIteratorDoubleStream(stream, closeHandlers, false, maxThreadNum, splitter);
    }

    @Override
    public IntStream mapToInt(final DoubleToIntFunction mapper) {
        if (maxThreadNum <= 1) {
            return new ParallelIteratorIntStream(sequential().mapToInt(mapper).intIterator(), closeHandlers, false, maxThreadNum, splitter);
        }

        final IntStream stream = boxed().mapToInt(new ToIntFunction<Double>() {
            @Override
            public int applyAsInt(Double value) {
                return mapper.applyAsInt(value);
            }
        });

        return new ParallelIteratorIntStream(stream, closeHandlers, false, maxThreadNum, splitter);
    }

    @Override
    public LongStream mapToLong(final DoubleToLongFunction mapper) {
        if (maxThreadNum <= 1) {
            return new ParallelIteratorLongStream(sequential().mapToLong(mapper).longIterator(), closeHandlers, false, maxThreadNum, splitter);
        }

        final LongStream stream = boxed().mapToLong(new ToLongFunction<Double>() {
            @Override
            public long applyAsLong(Double value) {
                return mapper.applyAsLong(value);
            }
        });

        return new ParallelIteratorLongStream(stream, closeHandlers, false, maxThreadNum, splitter);
    }

    @Override
    public FloatStream mapToFloat(final DoubleToFloatFunction mapper) {
        if (maxThreadNum <= 1) {
            return new ParallelIteratorFloatStream(sequential().mapToFloat(mapper).floatIterator(), closeHandlers, false, maxThreadNum, splitter);
        }

        final FloatStream stream = boxed().mapToFloat(new ToFloatFunction<Double>() {
            @Override
            public float applyAsFloat(Double value) {
                return mapper.applyAsFloat(value);
            }
        });

        return new ParallelIteratorFloatStream(stream, closeHandlers, false, maxThreadNum, splitter);
    }

    @Override
    public <U> Stream<U> mapToObj(final DoubleFunction<? extends U> mapper) {
        if (maxThreadNum <= 1) {
            return new ParallelIteratorStream<U>(sequential().mapToObj(mapper).iterator(), closeHandlers, false, null, maxThreadNum, splitter);
        }

        return boxed().map(new Function<Double, U>() {
            @Override
            public U apply(Double value) {
                return mapper.apply(value);
            }
        });
    }

    @Override
    public DoubleStream flatMap(final DoubleFunction<? extends DoubleStream> mapper) {
        if (maxThreadNum <= 1) {
            return new ParallelIteratorDoubleStream(sequential().flatMap(mapper).doubleIterator(), closeHandlers, false, maxThreadNum, splitter);
        }

        final DoubleStream stream = boxed().flatMapToDouble(new Function<Double, DoubleStream>() {
            @Override
            public DoubleStream apply(Double value) {
                return mapper.apply(value);
            }
        });

        return new ParallelIteratorDoubleStream(stream, closeHandlers, false, maxThreadNum, splitter);
    }

    @Override
    public IntStream flatMapToInt(final DoubleFunction<? extends IntStream> mapper) {
        if (maxThreadNum <= 1) {
            return new ParallelIteratorIntStream(sequential().flatMapToInt(mapper).intIterator(), closeHandlers, false, maxThreadNum, splitter);
        }

        final IntStream stream = boxed().flatMapToInt(new Function<Double, IntStream>() {
            @Override
            public IntStream apply(Double value) {
                return mapper.apply(value);
            }
        });

        return new ParallelIteratorIntStream(stream, closeHandlers, false, maxThreadNum, splitter);
    }

    @Override
    public LongStream flatMapToLong(final DoubleFunction<? extends LongStream> mapper) {
        if (maxThreadNum <= 1) {
            return new ParallelIteratorLongStream(sequential().flatMapToLong(mapper).longIterator(), closeHandlers, false, maxThreadNum, splitter);
        }

        final LongStream stream = boxed().flatMapToLong(new Function<Double, LongStream>() {
            @Override
            public LongStream apply(Double value) {
                return mapper.apply(value);
            }
        });

        return new ParallelIteratorLongStream(stream, closeHandlers, false, maxThreadNum, splitter);
    }

    @Override
    public FloatStream flatMapToFloat(final DoubleFunction<? extends FloatStream> mapper) {
        if (maxThreadNum <= 1) {
            return new ParallelIteratorFloatStream(sequential().flatMapToFloat(mapper).floatIterator(), closeHandlers, false, maxThreadNum, splitter);
        }

        final FloatStream stream = boxed().flatMapToFloat(new Function<Double, FloatStream>() {
            @Override
            public FloatStream apply(Double value) {
                return mapper.apply(value);
            }
        });

        return new ParallelIteratorFloatStream(stream, closeHandlers, false, maxThreadNum, splitter);
    }

    @Override
    public <T> Stream<T> flatMapToObj(final DoubleFunction<? extends Stream<T>> mapper) {
        if (maxThreadNum <= 1) {
            return new ParallelIteratorStream<>(sequential().flatMapToObj(mapper).iterator(), closeHandlers, false, null, maxThreadNum, splitter);
        }

        return boxed().flatMap(new Function<Double, Stream<T>>() {
            @Override
            public Stream<T> apply(Double value) {
                return mapper.apply(value);
            }
        });
    }

    @Override
    public Stream<DoubleStream> split(final int size) {
        return new ParallelIteratorStream<DoubleStream>(new ImmutableIterator<DoubleStream>() {
            @Override
            public boolean hasNext() {
                return elements.hasNext();
            }

            @Override
            public DoubleStream next() {
                if (elements.hasNext() == false) {
                    throw new NoSuchElementException();
                }

                final double[] a = new double[size];
                int cnt = 0;

                while (cnt < size && elements.hasNext()) {
                    a[cnt++] = elements.next();
                }

                return new ArrayDoubleStream(a, 0, cnt, null, sorted);
            }

        }, closeHandlers, false, null, maxThreadNum, splitter);
    }

    @Override
    public DoubleStream distinct() {
        return new ParallelIteratorDoubleStream(new ImmutableDoubleIterator() {
            private Iterator<Double> distinctIter;

            @Override
            public boolean hasNext() {
                if (distinctIter == null) {
                    removeDuplicated();
                }

                return distinctIter.hasNext();
            }

            @Override
            public double next() {
                if (distinctIter == null) {
                    removeDuplicated();
                }

                return distinctIter.next();
            }

            private void removeDuplicated() {
                final Set<Double> set = new LinkedHashSet<>();

                while (elements.hasNext()) {
                    set.add(elements.next());
                }

                distinctIter = set.iterator();
            }

        }, closeHandlers, sorted, maxThreadNum, splitter);
    }

    @Override
    public DoubleStream top(int n) {
        return top(n, Stream.DOUBLE_COMPARATOR);
    }

    @Override
    public DoubleStream top(int n, Comparator<? super Double> comparator) {
        if (n < 1) {
            throw new IllegalArgumentException("'n' can not be less than 1");
        }

        return boxed().top(n, comparator).mapToDouble(new ToDoubleFunction<Double>() {
            @Override
            public double applyAsDouble(Double value) {
                return value;
            }
        });
    }

    @Override
    public DoubleStream sorted() {
        if (sorted) {
            return this;
        }

        return new ParallelIteratorDoubleStream(new ImmutableDoubleIterator() {
            double[] a = null;
            int cursor = 0;

            @Override
            public boolean hasNext() {
                if (a == null) {
                    sort();
                }

                return cursor < a.length;
            }

            @Override
            public double next() {
                if (a == null) {
                    sort();
                }

                if (cursor >= a.length) {
                    throw new NoSuchElementException();
                }

                return a[cursor++];
            }

            @Override
            public long count() {
                if (a == null) {
                    sort();
                }

                return a.length - cursor;
            }

            @Override
            public void skip(long n) {
                if (a == null) {
                    sort();
                }

                cursor = n >= a.length - cursor ? a.length : cursor + (int) n;
            }

            @Override
            public double[] toArray() {
                if (a == null) {
                    sort();
                }

                if (cursor == 0) {
                    return a;
                } else {
                    return N.copyOfRange(a, cursor, a.length);
                }
            }

            private void sort() {
                a = elements.toArray();

                N.parallelSort(a);
            }
        }, closeHandlers, true, maxThreadNum, splitter);
    }

    @Override
    public DoubleStream peek(final DoubleConsumer action) {
        return new ParallelIteratorDoubleStream(new ImmutableDoubleIterator() {
            @Override
            public boolean hasNext() {
                return elements.hasNext();
            }

            @Override
            public double next() {
                final double next = elements.next();

                //    try {
                //        action.accept(next);
                //    } catch (Throwable e) {
                //        // ignore.
                //    }

                action.accept(next);
                return next;
            }

            //    @Override
            //    public long count() {
            //        return elements.count();
            //    }
            //
            //    @Override
            //    public void skip(long n) {
            //        elements.skip(n);
            //    }
            //
            //    @Override
            //    public double[] toArray() {
            //        return elements.toArray();
            //    }
        }, closeHandlers, sorted, maxThreadNum, splitter);
    }

    @Override
    public DoubleStream limit(final long maxSize) {
        if (maxSize < 0) {
            throw new IllegalArgumentException("'maxSize' can't be negative: " + maxSize);
        } else if (maxSize == Long.MAX_VALUE) {
            return this;
        }

        return new ParallelIteratorDoubleStream(new ImmutableDoubleIterator() {
            private long cnt = 0;

            @Override
            public boolean hasNext() {
                return cnt < maxSize && elements.hasNext();
            }

            @Override
            public double next() {
                if (cnt >= maxSize) {
                    throw new NoSuchElementException();
                }

                cnt++;
                return elements.next();
            }

            @Override
            public void skip(long n) {
                elements.skip(n);
            }
        }, closeHandlers, sorted, maxThreadNum, splitter);
    }

    @Override
    public DoubleStream skip(final long n) {
        if (n < 0) {
            throw new IllegalArgumentException("The skipped number can't be negative: " + n);
        } else if (n == 0) {
            return this;
        }

        return new ParallelIteratorDoubleStream(new ImmutableDoubleIterator() {
            private boolean skipped = false;

            @Override
            public boolean hasNext() {
                if (skipped == false) {
                    elements.skip(n);
                    skipped = true;
                }

                return elements.hasNext();
            }

            @Override
            public double next() {
                if (skipped == false) {
                    elements.skip(n);
                    skipped = true;
                }

                return elements.next();
            }

            @Override
            public long count() {
                if (skipped == false) {
                    elements.skip(n);
                    skipped = true;
                }

                return elements.count();
            }

            @Override
            public void skip(long n2) {
                if (skipped == false) {
                    elements.skip(n);
                    skipped = true;
                }

                elements.skip(n2);
            }

            @Override
            public double[] toArray() {
                if (skipped == false) {
                    elements.skip(n);
                    skipped = true;
                }

                return elements.toArray();
            }
        }, closeHandlers, sorted, maxThreadNum, splitter);
    }

    @Override
    public void forEach(final DoubleConsumer action) {
        if (maxThreadNum <= 1) {
            sequential().forEach(action);
            return;
        }

        final List<CompletableFuture<Void>> futureList = new ArrayList<>(maxThreadNum);
        final Holder<Throwable> eHolder = new Holder<>();

        for (int i = 0; i < maxThreadNum; i++) {
            futureList.add(Stream.asyncExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    double next = 0;

                    try {
                        while (eHolder.value() == null) {
                            synchronized (elements) {
                                if (elements.hasNext()) {
                                    next = elements.next();
                                } else {
                                    break;
                                }
                            }

                            action.accept(next);
                        }
                    } catch (Throwable e) {
                        Stream.setError(eHolder, e);
                    }
                }
            }));
        }

        if (eHolder.value() != null) {
            throw N.toRuntimeException(eHolder.value());
        }

        try {
            for (CompletableFuture<Void> future : futureList) {
                future.get();
            }
        } catch (Exception e) {
            throw N.toRuntimeException(e);
        }
    }

    //    @Override
    //    public boolean forEach2(final DoubleFunction<Boolean> action) {
    //        if (maxThreadNum <= 1) {
    //            return sequential().forEach2(action);
    //        }
    //
    //        final List<CompletableFuture<Void>> futureList = new ArrayList<>(maxThreadNum);
    //        final Holder<Throwable> eHolder = new Holder<>();
    //        final MutableBoolean result = MutableBoolean.of(true);
    //
    //        for (int i = 0; i < maxThreadNum; i++) {
    //            futureList.add(Stream.asyncExecutor.execute(new Runnable() {
    //                @Override
    //                public void run() {
    //                    double next = 0;
    //
    //                    try {
    //                        while (result.isTrue() && eHolder.value() == null) {
    //                            synchronized (elements) {
    //                                if (elements.hasNext()) {
    //                                    next = elements.next();
    //                                } else {
    //                                    break;
    //                                }
    //                            }
    //
    //                            if (action.apply(next) == false) {
    //                                result.setFalse();
    //                                break;
    //                            }
    //                        }
    //                    } catch (Throwable e) {
    //                        Stream.setError(eHolder, e);
    //                    }
    //                }
    //            }));
    //        }
    //
    //        if (eHolder.value() != null) {
    //            throw N.toRuntimeException(eHolder.value());
    //        }
    //
    //        try {
    //            for (CompletableFuture<Void> future : futureList) {
    //                future.get();
    //            }
    //        } catch (Exception e) {
    //            throw N.toRuntimeException(e);
    //        }
    //
    //        return result.booleanValue();
    //    }

    @Override
    public double[] toArray() {
        return elements.toArray();
    }

    @Override
    public DoubleList toDoubleList() {
        return DoubleList.of(toArray());
    }

    @Override
    public List<Double> toList() {
        final List<Double> result = new ArrayList<>();

        while (elements.hasNext()) {
            result.add(elements.next());
        }

        return result;
    }

    @Override
    public List<Double> toList(Supplier<? extends List<Double>> supplier) {
        final List<Double> result = supplier.get();

        while (elements.hasNext()) {
            result.add(elements.next());
        }

        return result;
    }

    @Override
    public Set<Double> toSet() {
        final Set<Double> result = new HashSet<>();

        while (elements.hasNext()) {
            result.add(elements.next());
        }

        return result;
    }

    @Override
    public Set<Double> toSet(Supplier<? extends Set<Double>> supplier) {
        final Set<Double> result = supplier.get();

        while (elements.hasNext()) {
            result.add(elements.next());
        }

        return result;
    }

    @Override
    public Multiset<Double> toMultiset() {
        final Multiset<Double> result = new Multiset<>();

        while (elements.hasNext()) {
            result.add(elements.next());
        }

        return result;
    }

    @Override
    public Multiset<Double> toMultiset(Supplier<? extends Multiset<Double>> supplier) {
        final Multiset<Double> result = supplier.get();

        while (elements.hasNext()) {
            result.add(elements.next());
        }

        return result;
    }

    @Override
    public LongMultiset<Double> toLongMultiset() {
        final LongMultiset<Double> result = new LongMultiset<>();

        while (elements.hasNext()) {
            result.add(elements.next());
        }

        return result;
    }

    @Override
    public LongMultiset<Double> toLongMultiset(Supplier<? extends LongMultiset<Double>> supplier) {
        final LongMultiset<Double> result = supplier.get();

        while (elements.hasNext()) {
            result.add(elements.next());
        }

        return result;
    }

    @Override
    public <K> Map<K, List<Double>> toMap(DoubleFunction<? extends K> classifier) {
        return toMap(classifier, new Supplier<Map<K, List<Double>>>() {
            @Override
            public Map<K, List<Double>> get() {
                return new HashMap<>();
            }
        });
    }

    @Override
    public <K, M extends Map<K, List<Double>>> M toMap(DoubleFunction<? extends K> classifier, Supplier<M> mapFactory) {
        final Collector<Double, ?, List<Double>> downstream = Collectors.toList();
        return toMap(classifier, downstream, mapFactory);
    }

    @Override
    public <K, A, D> Map<K, D> toMap(DoubleFunction<? extends K> classifier, Collector<Double, A, D> downstream) {
        return toMap(classifier, downstream, new Supplier<Map<K, D>>() {
            @Override
            public Map<K, D> get() {
                return new HashMap<>();
            }
        });
    }

    @Override
    public <K, D, A, M extends Map<K, D>> M toMap(final DoubleFunction<? extends K> classifier, final Collector<Double, A, D> downstream,
            final Supplier<M> mapFactory) {
        if (maxThreadNum <= 1) {
            return sequential().toMap(classifier, downstream, mapFactory);
        }

        final Function<? super Double, ? extends K> classifier2 = new Function<Double, K>() {
            @Override
            public K apply(Double value) {
                return classifier.apply(value);
            }
        };

        return boxed().toMap(classifier2, downstream, mapFactory);
    }

    @Override
    public <K, U> Map<K, U> toMap(DoubleFunction<? extends K> keyMapper, DoubleFunction<? extends U> valueMapper) {
        return toMap(keyMapper, valueMapper, new Supplier<Map<K, U>>() {
            @Override
            public Map<K, U> get() {
                return new HashMap<>();
            }
        });
    }

    @Override
    public <K, U, M extends Map<K, U>> M toMap(DoubleFunction<? extends K> keyMapper, DoubleFunction<? extends U> valueMapper, Supplier<M> mapSupplier) {
        final BinaryOperator<U> mergeFunction = Collectors.throwingMerger();
        return toMap(keyMapper, valueMapper, mergeFunction, mapSupplier);
    }

    @Override
    public <K, U> Map<K, U> toMap(DoubleFunction<? extends K> keyMapper, DoubleFunction<? extends U> valueMapper, BinaryOperator<U> mergeFunction) {
        return toMap(keyMapper, valueMapper, mergeFunction, new Supplier<Map<K, U>>() {
            @Override
            public Map<K, U> get() {
                return new HashMap<>();
            }
        });
    }

    @Override
    public <K, U, M extends Map<K, U>> M toMap(final DoubleFunction<? extends K> keyMapper, final DoubleFunction<? extends U> valueMapper,
            final BinaryOperator<U> mergeFunction, final Supplier<M> mapSupplier) {
        if (maxThreadNum <= 1) {
            return sequential().toMap(keyMapper, valueMapper, mergeFunction, mapSupplier);
        }

        final Function<? super Double, ? extends K> keyMapper2 = new Function<Double, K>() {
            @Override
            public K apply(Double value) {
                return keyMapper.apply(value);
            }
        };

        final Function<? super Double, ? extends U> valueMapper2 = new Function<Double, U>() {
            @Override
            public U apply(Double value) {
                return valueMapper.apply(value);
            }
        };

        return boxed().toMap(keyMapper2, valueMapper2, mergeFunction, mapSupplier);
    }

    @Override
    public <K, U> Multimap<K, U, List<U>> toMultimap(DoubleFunction<? extends K> keyMapper, DoubleFunction<? extends U> valueMapper) {
        return toMultimap(keyMapper, valueMapper, new Supplier<Multimap<K, U, List<U>>>() {
            @Override
            public Multimap<K, U, List<U>> get() {
                return N.newListMultimap();
            }
        });
    }

    @Override
    public <K, U, V extends Collection<U>> Multimap<K, U, V> toMultimap(final DoubleFunction<? extends K> keyMapper,
            final DoubleFunction<? extends U> valueMapper, final Supplier<Multimap<K, U, V>> mapSupplier) {

        if (maxThreadNum <= 1) {
            return sequential().toMultimap(keyMapper, valueMapper, mapSupplier);
        }

        final Function<? super Double, ? extends K> keyMapper2 = new Function<Double, K>() {
            @Override
            public K apply(Double value) {
                return keyMapper.apply(value);
            }
        };

        final Function<? super Double, ? extends U> valueMapper2 = new Function<Double, U>() {
            @Override
            public U apply(Double value) {
                return valueMapper.apply(value);
            }
        };

        return boxed().toMultimap(keyMapper2, valueMapper2, mapSupplier);
    }

    @Override
    public double reduce(final double identity, final DoubleBinaryOperator op) {
        if (maxThreadNum <= 1) {
            return sequential().reduce(identity, op);
        }

        final List<CompletableFuture<Double>> futureList = new ArrayList<>(maxThreadNum);
        final Holder<Throwable> eHolder = new Holder<>();

        for (int i = 0; i < maxThreadNum; i++) {
            futureList.add(Stream.asyncExecutor.execute(new Callable<Double>() {
                @Override
                public Double call() {
                    double result = identity;
                    double next = 0;

                    try {
                        while (eHolder.value() == null) {
                            synchronized (elements) {
                                if (elements.hasNext()) {
                                    next = elements.next();
                                } else {
                                    break;
                                }
                            }

                            result = op.applyAsDouble(result, next);
                        }
                    } catch (Throwable e) {
                        Stream.setError(eHolder, e);
                    }

                    return result;
                }
            }));
        }

        if (eHolder.value() != null) {
            throw N.toRuntimeException(eHolder.value());
        }

        Double result = null;

        try {
            for (CompletableFuture<Double> future : futureList) {
                if (result == null) {
                    result = future.get();
                } else {
                    result = op.applyAsDouble(result, future.get());
                }
            }
        } catch (Exception e) {
            throw N.toRuntimeException(e);
        }

        return result == null ? identity : result;
    }

    @Override
    public OptionalDouble reduce(final DoubleBinaryOperator accumulator) {
        if (maxThreadNum <= 1) {
            return sequential().reduce(accumulator);
        }

        final List<CompletableFuture<Double>> futureList = new ArrayList<>(maxThreadNum);
        final Holder<Throwable> eHolder = new Holder<>();

        for (int i = 0; i < maxThreadNum; i++) {
            futureList.add(Stream.asyncExecutor.execute(new Callable<Double>() {
                @Override
                public Double call() {
                    double result = 0;

                    synchronized (elements) {
                        if (elements.hasNext()) {
                            result = elements.next();
                        } else {
                            return null;
                        }
                    }

                    double next = 0;

                    try {
                        while (eHolder.value() == null) {
                            synchronized (elements) {
                                if (elements.hasNext()) {
                                    next = elements.next();
                                } else {
                                    break;
                                }
                            }

                            result = accumulator.applyAsDouble(result, next);
                        }
                    } catch (Throwable e) {
                        Stream.setError(eHolder, e);
                    }

                    return result;
                }
            }));
        }

        if (eHolder.value() != null) {
            throw N.toRuntimeException(eHolder.value());
        }

        Double result = null;

        try {
            for (CompletableFuture<Double> future : futureList) {
                final Double tmp = future.get();

                if (tmp == null) {
                    continue;
                } else if (result == null) {
                    result = tmp;
                } else {
                    result = accumulator.applyAsDouble(result, tmp);
                }
            }
        } catch (Exception e) {
            throw N.toRuntimeException(e);
        }

        return result == null ? OptionalDouble.empty() : OptionalDouble.of(result);
    }

    @Override
    public <R> R collect(final Supplier<R> supplier, final ObjDoubleConsumer<R> accumulator, final BiConsumer<R, R> combiner) {
        if (maxThreadNum <= 1) {
            return sequential().collect(supplier, accumulator, combiner);
        }

        final List<CompletableFuture<R>> futureList = new ArrayList<>(maxThreadNum);
        final Holder<Throwable> eHolder = new Holder<>();

        for (int i = 0; i < maxThreadNum; i++) {
            futureList.add(Stream.asyncExecutor.execute(new Callable<R>() {
                @Override
                public R call() {
                    R container = supplier.get();
                    double next = 0;

                    try {
                        while (eHolder.value() == null) {
                            synchronized (elements) {
                                if (elements.hasNext()) {
                                    next = elements.next();
                                } else {
                                    break;
                                }
                            }

                            accumulator.accept(container, next);
                        }
                    } catch (Throwable e) {
                        Stream.setError(eHolder, e);
                    }

                    return container;
                }
            }));
        }

        if (eHolder.value() != null) {
            throw N.toRuntimeException(eHolder.value());
        }

        R container = (R) Stream.NONE;

        try {
            for (CompletableFuture<R> future : futureList) {
                final R tmp = future.get();

                if (container == Stream.NONE) {
                    container = tmp;
                } else {
                    combiner.accept(container, tmp);
                }
            }
        } catch (Exception e) {
            throw N.toRuntimeException(e);
        }

        return container == Stream.NONE ? supplier.get() : container;
    }

    @Override
    public <R> R collect(Supplier<R> supplier, ObjDoubleConsumer<R> accumulator) {
        if (Stream.logger.isWarnEnabled()) {
            Stream.logger.warn("'collect' is sequentially executed in parallel stream");
        }

        return sequential().collect(supplier, accumulator);
    }

    @Override
    public OptionalDouble min() {
        if (elements.hasNext() == false) {
            return OptionalDouble.empty();
        }

        double candidate = elements.next();
        double next = 0;

        while (elements.hasNext()) {
            next = elements.next();

            if (N.compare(candidate, next) > 0) {
                candidate = next;
            }
        }

        return OptionalDouble.of(candidate);
    }

    @Override
    public OptionalDouble max() {
        if (elements.hasNext() == false) {
            return OptionalDouble.empty();
        }

        double candidate = elements.next();
        double next = 0;

        while (elements.hasNext()) {
            next = elements.next();

            if (N.compare(candidate, next) < 0) {
                candidate = next;
            }
        }

        return OptionalDouble.of(candidate);
    }

    @Override
    public OptionalDouble kthLargest(int k) {
        if (elements.hasNext() == false) {
            return OptionalDouble.empty();
        }

        final OptionalNullable<Double> optional = boxed().kthLargest(k, Stream.DOUBLE_COMPARATOR);

        return optional.isPresent() ? OptionalDouble.of(optional.get()) : OptionalDouble.empty();
    }

    @Override
    public Double sum() {
        return sequential().sum();
    }

    @Override
    public OptionalDouble average() {
        return sequential().average();
    }

    @Override
    public long count() {
        return elements.count();
    }

    @Override
    public DoubleSummaryStatistics summarize() {
        return sequential().summarize();
    }

    @Override
    public Optional<Map<String, Double>> distribution() {
        if (elements.hasNext() == false) {
            return Optional.empty();
        }

        final double[] a = sorted().toArray();

        return Optional.of(N.distribution(a));
    }

    @Override
    public boolean anyMatch(final DoublePredicate predicate) {
        if (maxThreadNum <= 1) {
            return sequential().anyMatch(predicate);
        }

        final List<CompletableFuture<Void>> futureList = new ArrayList<>(maxThreadNum);
        final Holder<Throwable> eHolder = new Holder<>();
        final MutableBoolean result = MutableBoolean.of(false);

        for (int i = 0; i < maxThreadNum; i++) {
            futureList.add(Stream.asyncExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    double next = 0;

                    try {
                        while (result.isFalse() && eHolder.value() == null) {
                            synchronized (elements) {
                                if (elements.hasNext()) {
                                    next = elements.next();
                                } else {
                                    break;
                                }
                            }

                            if (predicate.test(next)) {
                                result.setTrue();
                                break;
                            }
                        }
                    } catch (Throwable e) {
                        Stream.setError(eHolder, e);
                    }
                }
            }));
        }

        if (eHolder.value() != null) {
            throw N.toRuntimeException(eHolder.value());
        }

        try {
            for (CompletableFuture<Void> future : futureList) {
                future.get();
            }
        } catch (Exception e) {
            throw N.toRuntimeException(e);
        }

        return result.booleanValue();
    }

    @Override
    public boolean allMatch(final DoublePredicate predicate) {
        if (maxThreadNum <= 1) {
            return sequential().allMatch(predicate);
        }

        final List<CompletableFuture<Void>> futureList = new ArrayList<>(maxThreadNum);
        final Holder<Throwable> eHolder = new Holder<>();
        final MutableBoolean result = MutableBoolean.of(true);

        for (int i = 0; i < maxThreadNum; i++) {
            futureList.add(Stream.asyncExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    double next = 0;

                    try {
                        while (result.isTrue() && eHolder.value() == null) {
                            synchronized (elements) {
                                if (elements.hasNext()) {
                                    next = elements.next();
                                } else {
                                    break;
                                }
                            }

                            if (predicate.test(next) == false) {
                                result.setFalse();
                                break;
                            }
                        }
                    } catch (Throwable e) {
                        Stream.setError(eHolder, e);
                    }
                }
            }));
        }

        if (eHolder.value() != null) {
            throw N.toRuntimeException(eHolder.value());
        }

        try {
            for (CompletableFuture<Void> future : futureList) {
                future.get();
            }
        } catch (Exception e) {
            throw N.toRuntimeException(e);
        }

        return result.booleanValue();
    }

    @Override
    public boolean noneMatch(final DoublePredicate predicate) {
        if (maxThreadNum <= 1) {
            return sequential().noneMatch(predicate);
        }

        final List<CompletableFuture<Void>> futureList = new ArrayList<>(maxThreadNum);
        final Holder<Throwable> eHolder = new Holder<>();
        final MutableBoolean result = MutableBoolean.of(true);

        for (int i = 0; i < maxThreadNum; i++) {
            futureList.add(Stream.asyncExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    double next = 0;

                    try {
                        while (result.isTrue() && eHolder.value() == null) {
                            synchronized (elements) {
                                if (elements.hasNext()) {
                                    next = elements.next();
                                } else {
                                    break;
                                }
                            }

                            if (predicate.test(next)) {
                                result.setFalse();
                                break;
                            }
                        }
                    } catch (Throwable e) {
                        Stream.setError(eHolder, e);
                    }
                }
            }));
        }

        if (eHolder.value() != null) {
            throw N.toRuntimeException(eHolder.value());
        }

        try {
            for (CompletableFuture<Void> future : futureList) {
                future.get();
            }
        } catch (Exception e) {
            throw N.toRuntimeException(e);
        }

        return result.booleanValue();
    }

    //    @Override
    //    public OptionalDouble findFirst() {
    //        return count() == 0 ? OptionalDouble.empty() : OptionalDouble.of(elements[fromIndex]);
    //    }

    @Override
    public OptionalDouble findFirst(final DoublePredicate predicate) {
        if (maxThreadNum <= 1) {
            return sequential().findFirst(predicate);
        }

        final List<CompletableFuture<Pair<Long, Double>>> futureList = new ArrayList<>(maxThreadNum);
        final Holder<Throwable> eHolder = new Holder<>();
        final Holder<Pair<Long, Double>> resultHolder = new Holder<>();
        final MutableLong index = MutableLong.of(0);

        for (int i = 0; i < maxThreadNum; i++) {
            futureList.add(Stream.asyncExecutor.execute(new Callable<Pair<Long, Double>>() {
                @Override
                public Pair<Long, Double> call() {
                    final Pair<Long, Double> pair = new Pair<>();

                    try {
                        while (resultHolder.value() == null && eHolder.value() == null) {
                            synchronized (elements) {
                                if (elements.hasNext()) {
                                    pair.left = index.getAndIncrement();
                                    pair.right = elements.next();
                                } else {
                                    break;
                                }
                            }

                            if (predicate.test(pair.right)) {
                                synchronized (resultHolder) {
                                    if (resultHolder.value() == null || pair.left < resultHolder.value().left) {
                                        resultHolder.setValue(pair);
                                    }
                                }

                                break;
                            }
                        }
                    } catch (Throwable e) {
                        Stream.setError(eHolder, e);
                    }

                    return pair;
                }
            }));
        }

        if (eHolder.value() != null) {
            throw N.toRuntimeException(eHolder.value());
        }

        try {
            for (CompletableFuture<Pair<Long, Double>> future : futureList) {
                final Pair<Long, Double> pair = future.get();

                if (resultHolder.value() == null || pair.left < resultHolder.value().left) {
                    resultHolder.setValue(pair);
                }
            }
        } catch (Exception e) {
            throw N.toRuntimeException(e);
        }

        return resultHolder.value() == null ? OptionalDouble.empty() : OptionalDouble.of(resultHolder.value().right);
    }

    //    @Override
    //    public OptionalDouble findLast() {
    //        return count() == 0 ? OptionalDouble.empty() : OptionalDouble.of(elements[toIndex - 1]);
    //    }

    @Override
    public OptionalDouble findLast(final DoublePredicate predicate) {
        if (maxThreadNum <= 1) {
            return sequential().findLast(predicate);
        }

        final List<CompletableFuture<Pair<Long, Double>>> futureList = new ArrayList<>(maxThreadNum);
        final Holder<Throwable> eHolder = new Holder<>();
        final Holder<Pair<Long, Double>> resultHolder = new Holder<>();
        final MutableLong index = MutableLong.of(0);

        for (int i = 0; i < maxThreadNum; i++) {
            futureList.add(Stream.asyncExecutor.execute(new Callable<Pair<Long, Double>>() {
                @Override
                public Pair<Long, Double> call() {
                    final Pair<Long, Double> pair = new Pair<>();

                    try {
                        while (resultHolder.value() == null && eHolder.value() == null) {
                            synchronized (elements) {
                                if (elements.hasNext()) {
                                    pair.left = index.getAndIncrement();
                                    pair.right = elements.next();
                                } else {
                                    break;
                                }
                            }

                            if (predicate.test(pair.right)) {
                                synchronized (resultHolder) {
                                    if (resultHolder.value() == null || pair.left > resultHolder.value().left) {
                                        resultHolder.setValue(pair);
                                    }
                                }

                                break;
                            }
                        }
                    } catch (Throwable e) {
                        Stream.setError(eHolder, e);
                    }

                    return pair;
                }
            }));
        }

        if (eHolder.value() != null) {
            throw N.toRuntimeException(eHolder.value());
        }

        try {
            for (CompletableFuture<Pair<Long, Double>> future : futureList) {
                final Pair<Long, Double> pair = future.get();

                if (resultHolder.value() == null || pair.left > resultHolder.value().left) {
                    resultHolder.setValue(pair);
                }
            }
        } catch (Exception e) {
            throw N.toRuntimeException(e);
        }

        return resultHolder.value() == null ? OptionalDouble.empty() : OptionalDouble.of(resultHolder.value().right);
    }

    //    @Override
    //    public OptionalDouble findAny() {
    //        return count() == 0 ? OptionalDouble.empty() : OptionalDouble.of(elements[fromIndex]);
    //    }

    @Override
    public OptionalDouble findAny(final DoublePredicate predicate) {
        if (maxThreadNum <= 1) {
            return sequential().findAny(predicate);
        }

        final List<CompletableFuture<Object>> futureList = new ArrayList<>(maxThreadNum);
        final Holder<Throwable> eHolder = new Holder<>();
        final Holder<Object> resultHolder = Holder.of(Stream.NONE);

        for (int i = 0; i < maxThreadNum; i++) {
            futureList.add(Stream.asyncExecutor.execute(new Callable<Object>() {
                @Override
                public Object call() {
                    double next = 0;

                    try {
                        while (resultHolder.value() == Stream.NONE && eHolder.value() == null) {
                            synchronized (elements) {
                                if (elements.hasNext()) {
                                    next = elements.next();
                                } else {
                                    break;
                                }
                            }

                            if (predicate.test(next)) {
                                synchronized (resultHolder) {
                                    if (resultHolder.value() == Stream.NONE) {
                                        resultHolder.setValue(next);
                                    }
                                }

                                break;
                            }
                        }
                    } catch (Throwable e) {
                        Stream.setError(eHolder, e);
                    }

                    return next;
                }
            }));
        }

        if (eHolder.value() != null) {
            throw N.toRuntimeException(eHolder.value());
        }

        try {
            for (CompletableFuture<Object> future : futureList) {
                if (resultHolder.value() == Stream.NONE) {
                    future.get();
                } else {
                    break;
                }
            }
        } catch (Exception e) {
            throw N.toRuntimeException(e);
        }

        return resultHolder.value() == Stream.NONE ? OptionalDouble.empty() : OptionalDouble.of((Double) resultHolder.value());
    }

    @Override
    public DoubleStream except(final Collection<?> c) {
        return new ParallelIteratorDoubleStream(this.sequential().except(c).doubleIterator(), closeHandlers, sorted, maxThreadNum, splitter);
    }

    @Override
    public DoubleStream intersect(final Collection<?> c) {
        return new ParallelIteratorDoubleStream(this.sequential().intersect(c).doubleIterator(), closeHandlers, sorted, maxThreadNum, splitter);
    }

    //    @Override
    //    public DoubleStream exclude(final Collection<?> c) {
    //        if (maxThreadNum <= 1) {
    //            return new ParallelIteratorDoubleStream(sequential().exclude(c).doubleIterator(), closeHandlers, sorted, maxThreadNum, splitter);
    //        }
    //
    //        final Set<?> set = c instanceof Set ? (Set<?>) c : new HashSet<>(c);
    //
    //        return filter(new DoublePredicate() {
    //            @Override
    //            public boolean test(double value) {
    //                return !set.contains(value);
    //            }
    //        });
    //    }

    @Override
    public Stream<Double> boxed() {
        Stream<Double> tmp = boxed;

        if (tmp == null) {
            tmp = new ParallelIteratorStream<Double>(iterator(), closeHandlers, sorted, sorted ? Stream.DOUBLE_COMPARATOR : null, maxThreadNum, splitter);
            boxed = tmp;
        }

        return tmp;
    }

    @Override
    public DoubleStream append(final DoubleStream stream) {
        return new ParallelIteratorDoubleStream(DoubleStream.concat(this, stream), closeHandlers, false, maxThreadNum, splitter);
    }

    @Override
    public DoubleStream merge(final DoubleStream b, final DoubleBiFunction<Nth> nextSelector) {
        return new ParallelIteratorDoubleStream(DoubleStream.merge(this, b, nextSelector), closeHandlers, false, maxThreadNum, splitter);
    }

    @Override
    public ImmutableIterator<Double> iterator() {
        return this.sequential().iterator();
    }

    @Override
    public ImmutableDoubleIterator doubleIterator() {
        return this.sequential().doubleIterator();
    }

    @Override
    public boolean isParallel() {
        return true;
    }

    @Override
    public DoubleStream sequential() {
        IteratorDoubleStream tmp = sequential;

        if (tmp == null) {
            tmp = new IteratorDoubleStream(elements, closeHandlers, sorted);
            sequential = tmp;
        }

        return tmp;
    }

    @Override
    public DoubleStream parallel(int maxThreadNum, Splitter splitter) {
        if (this.maxThreadNum == maxThreadNum && this.splitter == splitter) {
            return this;
        }

        return new ParallelIteratorDoubleStream(elements, closeHandlers, sorted, maxThreadNum, splitter);
    }

    @Override
    public int maxThreadNum() {
        return maxThreadNum;
    }

    @Override
    public DoubleStream maxThreadNum(int maxThreadNum) {
        if (this.maxThreadNum == maxThreadNum) {
            return this;
        }

        return new ParallelIteratorDoubleStream(elements, closeHandlers, sorted, maxThreadNum, splitter);
    }

    @Override
    public BaseStream.Splitter splitter() {
        return splitter;
    }

    @Override
    public DoubleStream splitter(BaseStream.Splitter splitter) {
        if (this.splitter == splitter) {
            return this;
        }

        return new ParallelIteratorDoubleStream(elements, closeHandlers, sorted, maxThreadNum, splitter);
    }

    @Override
    public DoubleStream onClose(Runnable closeHandler) {
        final Set<Runnable> newCloseHandlers = new AbstractStream.LocalLinkedHashSet<>(N.isNullOrEmpty(this.closeHandlers) ? 1 : this.closeHandlers.size() + 1);

        if (N.notNullOrEmpty(this.closeHandlers)) {
            newCloseHandlers.addAll(this.closeHandlers);
        }

        newCloseHandlers.add(closeHandler);

        return new ParallelIteratorDoubleStream(elements, newCloseHandlers, sorted, maxThreadNum, splitter);
    }
}