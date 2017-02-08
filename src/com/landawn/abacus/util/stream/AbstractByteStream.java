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

package com.landawn.abacus.util.stream;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import com.landawn.abacus.util.ByteIterator;
import com.landawn.abacus.util.ByteList;
import com.landawn.abacus.util.ByteMatrix;
import com.landawn.abacus.util.ByteSummaryStatistics;
import com.landawn.abacus.util.IndexedByte;
import com.landawn.abacus.util.Joiner;
import com.landawn.abacus.util.Multimap;
import com.landawn.abacus.util.Multiset;
import com.landawn.abacus.util.MutableByte;
import com.landawn.abacus.util.MutableLong;
import com.landawn.abacus.util.N;
import com.landawn.abacus.util.Nth;
import com.landawn.abacus.util.Optional;
import com.landawn.abacus.util.OptionalByte;
import com.landawn.abacus.util.Pair;
import com.landawn.abacus.util.Percentage;
import com.landawn.abacus.util.function.BiConsumer;
import com.landawn.abacus.util.function.BiFunction;
import com.landawn.abacus.util.function.BinaryOperator;
import com.landawn.abacus.util.function.ByteBiFunction;
import com.landawn.abacus.util.function.ByteConsumer;
import com.landawn.abacus.util.function.ByteFunction;
import com.landawn.abacus.util.function.BytePredicate;
import com.landawn.abacus.util.function.ByteTriFunction;
import com.landawn.abacus.util.function.Consumer;
import com.landawn.abacus.util.function.Function;
import com.landawn.abacus.util.function.ObjByteConsumer;
import com.landawn.abacus.util.function.Predicate;
import com.landawn.abacus.util.function.Supplier;
import com.landawn.abacus.util.function.ToByteFunction;

/**
 * This class is a sequential, stateful and immutable stream implementation.
 *
 * @since 0.8
 * 
 * @author Haiyang Li
 */
abstract class AbstractByteStream extends ByteStream {

    AbstractByteStream(final Collection<Runnable> closeHandlers, final boolean sorted) {
        super(closeHandlers, sorted);
    }

    @Override
    public ByteStream drop(final long n, final ByteConsumer action) {
        if (n < 0) {
            throw new IllegalArgumentException("'n' can't be less than 0");
        } else if (n == 0) {
            return this;
        }

        if (this.isParallel()) {
            final AtomicLong cnt = new AtomicLong(n);

            return dropWhile(new BytePredicate() {
                @Override
                public boolean test(byte value) {
                    return cnt.getAndDecrement() > 0;
                }
            }, action);
        } else {
            final MutableLong cnt = MutableLong.of(n);

            return dropWhile(new BytePredicate() {
                @Override
                public boolean test(byte value) {
                    return cnt.getAndDecrement() > 0;
                }
            }, action);
        }
    }

    @Override
    public ByteStream dropWhile(final BytePredicate predicate, final ByteConsumer action) {
        N.requireNonNull(predicate);
        N.requireNonNull(action);

        return dropWhile(new BytePredicate() {
            @Override
            public boolean test(byte value) {
                if (predicate.test(value)) {
                    action.accept(value);
                    return true;
                }

                return false;
            }
        });
    }

    @Override
    public ByteStream step(final long step) {
        N.checkArgument(step > 0, "'step' can't be 0 or negative: %s", step);

        if (step == 1) {
            return this;
        }

        final long skip = step - 1;
        final ImmutableByteIterator iter = this.byteIterator();

        final ByteIterator byteIterator = new ImmutableByteIterator() {
            @Override
            public boolean hasNext() {
                return iter.hasNext();
            }

            @Override
            public byte next() {
                final byte next = iter.next();
                iter.skip(skip);
                return next;
            }
        };

        return newStream(byteIterator, sorted);
    }

    @Override
    public Stream<ByteStream> split(final int size) {
        return split0(size).map(new Function<ByteList, ByteStream>() {
            @Override
            public ByteStream apply(ByteList t) {
                return new ArrayByteStream(t.array(), 0, t.size(), null, sorted);
            }
        });
    }

    @Override
    public <U> Stream<ByteStream> split(final U identity, final BiFunction<? super Byte, ? super U, Boolean> predicate,
            final Consumer<? super U> identityUpdate) {
        return split0(identity, predicate, identityUpdate).map(new Function<ByteList, ByteStream>() {
            @Override
            public ByteStream apply(ByteList t) {
                return new ArrayByteStream(t.array(), 0, t.size(), null, sorted);
            }
        });
    }

    @Override
    public Stream<ByteStream> sliding(final int windowSize, final int increment) {
        return sliding0(windowSize, increment).map(new Function<ByteList, ByteStream>() {
            @Override
            public ByteStream apply(ByteList t) {
                return new ArrayByteStream(t.array(), 0, t.size(), null, sorted);
            }
        });
    }

    @Override
    public <K> Map<K, List<Byte>> toMap(ByteFunction<? extends K> classifier) {
        @SuppressWarnings("rawtypes")
        final Supplier<Map<K, List<Byte>>> mapFactory = (Supplier) Supplier.MAP;

        return toMap(classifier, mapFactory);
    }

    @Override
    public <K, M extends Map<K, List<Byte>>> M toMap(ByteFunction<? extends K> classifier, Supplier<M> mapFactory) {
        final Collector<Byte, ?, List<Byte>> downstream = Collectors.toList();

        return toMap(classifier, downstream, mapFactory);
    }

    @Override
    public <K, A, D> Map<K, D> toMap(ByteFunction<? extends K> classifier, Collector<Byte, A, D> downstream) {
        @SuppressWarnings("rawtypes")
        final Supplier<Map<K, D>> mapFactory = (Supplier) Supplier.MAP;

        return toMap(classifier, downstream, mapFactory);
    }

    @Override
    public <K, U> Map<K, U> toMap(ByteFunction<? extends K> keyMapper, ByteFunction<? extends U> valueMapper) {
        @SuppressWarnings("rawtypes")
        final Supplier<Map<K, U>> mapFactory = (Supplier) Supplier.MAP;

        return toMap(keyMapper, valueMapper, mapFactory);
    }

    @Override
    public <K, U, M extends Map<K, U>> M toMap(ByteFunction<? extends K> keyMapper, ByteFunction<? extends U> valueMapper, Supplier<M> mapFactory) {
        final BinaryOperator<U> mergeFunction = Collectors.throwingMerger();

        return toMap(keyMapper, valueMapper, mergeFunction, mapFactory);
    }

    @Override
    public <K, U> Map<K, U> toMap(ByteFunction<? extends K> keyMapper, ByteFunction<? extends U> valueMapper, BinaryOperator<U> mergeFunction) {
        @SuppressWarnings("rawtypes")
        final Supplier<Map<K, U>> mapFactory = (Supplier) Supplier.MAP;

        return toMap(keyMapper, valueMapper, mergeFunction, mapFactory);
    }

    @Override
    public <K> Multimap<K, Byte, List<Byte>> toMultimap(ByteFunction<? extends K> keyMapper) {
        return toMultimap(keyMapper, ByteFunction.BOX);
    }

    @Override
    public <K, V extends Collection<Byte>> Multimap<K, Byte, V> toMultimap(ByteFunction<? extends K> keyMapper, Supplier<Multimap<K, Byte, V>> mapFactory) {
        return toMultimap(keyMapper, ByteFunction.BOX, mapFactory);
    }

    @Override
    public <K, U> Multimap<K, U, List<U>> toMultimap(ByteFunction<? extends K> keyMapper, ByteFunction<? extends U> valueMapper) {
        return toMultimap(keyMapper, valueMapper, new Supplier<Multimap<K, U, List<U>>>() {
            @Override
            public Multimap<K, U, List<U>> get() {
                return N.newListMultimap();
            }
        });
    }

    @Override
    public ByteMatrix toMatrix() {
        return ByteMatrix.of(toArray());
    }

    @Override
    public ByteStream distinct() {
        final Set<Object> set = new HashSet<>();

        return newStream(this.sequential().filter(new BytePredicate() {
            @Override
            public boolean test(byte value) {
                return set.add(value);
            }
        }).byteIterator(), sorted);
    }

    @Override
    public OptionalByte first() {
        final ByteIterator iter = this.byteIterator();

        return iter.hasNext() ? OptionalByte.of(iter.next()) : OptionalByte.empty();
    }

    @Override
    public OptionalByte last() {
        final ByteIterator iter = this.byteIterator();

        if (iter.hasNext() == false) {
            return OptionalByte.empty();
        }

        byte next = iter.next();

        while (iter.hasNext()) {
            next = iter.next();
        }

        return OptionalByte.of(next);
    }

    @Override
    public OptionalByte findFirstOrLast(BytePredicate predicateForFirst, BytePredicate predicateForLast) {
        final ImmutableByteIterator iter = byteIterator();
        MutableByte last = null;
        byte next = 0;

        while (iter.hasNext()) {
            next = iter.next();

            if (predicateForFirst.test(next)) {
                return OptionalByte.of(next);
            } else if (predicateForLast.test(next)) {
                if (last == null) {
                    last = MutableByte.of(next);
                } else {
                    last.setValue(next);
                }
            }
        }

        return last == null ? OptionalByte.empty() : OptionalByte.of(last.value());
    }

    @Override
    public Pair<OptionalByte, OptionalByte> findFirstAndLast(BytePredicate predicateForFirst, BytePredicate predicateForLast) {
        final Pair<OptionalByte, OptionalByte> result = new Pair<>();
        final ImmutableByteIterator iter = byteIterator();
        MutableByte last = null;
        byte next = 0;

        while (iter.hasNext()) {
            next = iter.next();

            if (result.left == null && predicateForFirst.test(next)) {
                result.left = OptionalByte.of(next);
            }

            if (predicateForLast.test(next)) {
                if (last == null) {
                    last = MutableByte.of(next);
                } else {
                    last.setValue(next);
                }
            }
        }

        if (result.left == null) {
            result.left = OptionalByte.empty();
        }

        result.right = last == null ? OptionalByte.empty() : OptionalByte.of(last.value());

        return result;
    }

    @Override
    public ByteStream intersection(final Collection<?> c) {
        final Multiset<?> multiset = Multiset.from(c);

        return newStream(this.sequential().filter(new BytePredicate() {
            @Override
            public boolean test(byte value) {
                return multiset.getAndRemove(value) > 0;
            }
        }).byteIterator(), sorted);
    }

    @Override
    public ByteStream difference(final Collection<?> c) {
        final Multiset<?> multiset = Multiset.from(c);

        return newStream(this.sequential().filter(new BytePredicate() {
            @Override
            public boolean test(byte value) {
                return multiset.getAndRemove(value) < 1;
            }
        }).byteIterator(), sorted);
    }

    @Override
    public ByteStream symmetricDifference(final Collection<Byte> c) {
        final Multiset<?> multiset = Multiset.from(c);

        return newStream(this.sequential().filter(new BytePredicate() {
            @Override
            public boolean test(byte value) {
                return multiset.getAndRemove(value) < 1;
            }
        }).append(Stream.of(c).filter(new Predicate<Byte>() {
            @Override
            public boolean test(Byte value) {
                return multiset.getAndRemove(value) > 0;
            }
        }).mapToByte(ToByteFunction.UNBOX)).byteIterator(), false);
    }

    @Override
    public Stream<ByteStream> splitAt(final int n) {
        if (n < 0) {
            throw new IllegalArgumentException("'n' can't be negative");
        }

        final ByteIterator iter = this.byteIterator();
        final ByteList list = new ByteList();

        while (list.size() < n && iter.hasNext()) {
            list.add(iter.next());
        }

        final ByteStream[] a = { new ArrayByteStream(list.array(), 0, list.size(), null, sorted), new IteratorByteStream(iter, null, sorted) };

        return this.newStream(a, false, null);
    }

    @Override
    public Stream<ByteStream> splitBy(BytePredicate where) {
        N.requireNonNull(where);

        final ByteIterator iter = this.byteIterator();
        final ByteList list = new ByteList();
        byte next = 0;
        ByteStream s = null;

        while (iter.hasNext()) {
            next = iter.next();

            if (where.test(next)) {
                list.add(next);
            } else {
                s = ByteStream.of(next);

                break;
            }
        }

        final ByteStream[] a = { new ArrayByteStream(list.array(), 0, list.size(), null, sorted), new IteratorByteStream(iter, null, sorted) };

        if (s != null) {
            if (sorted) {
                a[1] = new IteratorByteStream(a[1].prepend(s).byteIterator(), null, sorted);
            } else {
                a[1] = a[1].prepend(s);
            }
        }

        return this.newStream(a, false, null);
    }

    @Override
    public ByteStream reverse() {
        final byte[] tmp = toArray();

        return newStream(new ImmutableByteIterator() {
            private int cursor = tmp.length;

            @Override
            public boolean hasNext() {
                return cursor > 0;
            }

            @Override
            public byte next() {
                if (cursor <= 0) {
                    throw new NoSuchElementException();
                }

                return tmp[--cursor];
            }

            @Override
            public long count() {
                return cursor;
            }

            @Override
            public void skip(long n) {
                cursor = n < cursor ? cursor - (int) n : 0;
            }

            @Override
            public byte[] toArray() {
                final byte[] a = new byte[cursor];

                for (int i = 0, len = tmp.length; i < len; i++) {
                    a[i] = tmp[cursor - i - 1];
                }

                return a;
            }
        }, false);
    }

    @Override
    public ByteStream shuffle() {
        final byte[] a = toArray();

        N.shuffle(a);

        return newStream(a, false);
    }

    @Override
    public ByteStream rotate(int distance) {
        final byte[] a = toArray();

        N.rotate(a, distance);

        return newStream(a, false);
    }

    @Override
    public Optional<Map<Percentage, Byte>> distribution() {
        final byte[] a = sorted().toArray();

        if (a.length == 0) {
            return Optional.empty();
        }

        return Optional.of(N.distribution(a));
    }

    @Override
    public Pair<ByteSummaryStatistics, Optional<Map<Percentage, Byte>>> summarize2() {
        final byte[] a = sorted().toArray();

        if (N.isNullOrEmpty(a)) {
            return Pair.of(new ByteSummaryStatistics(), Optional.<Map<Percentage, Byte>> empty());
        } else {
            return Pair.of(new ByteSummaryStatistics(a.length, N.sum(a), a[0], a[a.length - 1]), Optional.of(N.distribution(a)));
        }
    }

    @Override
    public String join(final CharSequence delimiter) {
        return join(delimiter, "", "");
    }

    @Override
    public String join(final CharSequence delimiter, final CharSequence prefix, final CharSequence suffix) {
        final Supplier<Joiner> supplier = new Supplier<Joiner>() {
            @Override
            public Joiner get() {
                return new Joiner(delimiter, prefix, suffix);
            }
        };

        final ObjByteConsumer<Joiner> accumulator = new ObjByteConsumer<Joiner>() {
            @Override
            public void accept(Joiner a, byte t) {
                a.add(t);
            }
        };

        final BiConsumer<Joiner, Joiner> combiner = new BiConsumer<Joiner, Joiner>() {
            @Override
            public void accept(Joiner a, Joiner b) {
                a.merge(b);
            }
        };

        final Joiner joiner = collect(supplier, accumulator, combiner);

        return joiner.toString();
    }

    @Override
    public <R> R collect(Supplier<R> supplier, ObjByteConsumer<R> accumulator) {
        final BiConsumer<R, R> combiner = collectingCombiner;

        return collect(supplier, accumulator, combiner);
    }

    @Override
    public Stream<IndexedByte> indexed() {
        final MutableLong idx = new MutableLong();

        return newStream(this.sequential().mapToObj(new ByteFunction<IndexedByte>() {
            @Override
            public IndexedByte apply(byte t) {
                return IndexedByte.of(idx.getAndIncrement(), t);
            }
        }).iterator(), true, INDEXED_BYTE_COMPARATOR);
    }

    @Override
    public ByteStream append(ByteStream stream) {
        return ByteStream.concat(this, stream);
    }

    @Override
    public ByteStream prepend(ByteStream stream) {
        return ByteStream.concat(stream, this);
    }

    @Override
    public ByteStream merge(ByteStream b, ByteBiFunction<Nth> nextSelector) {
        return ByteStream.merge(this, b, nextSelector);
    }

    @Override
    public ByteStream zipWith(ByteStream b, ByteBiFunction<Byte> zipFunction) {
        return ByteStream.zip(this, b, zipFunction);
    }

    @Override
    public ByteStream zipWith(ByteStream b, ByteStream c, ByteTriFunction<Byte> zipFunction) {
        return ByteStream.zip(this, b, c, zipFunction);
    }

    @Override
    public ByteStream zipWith(ByteStream b, byte valueForNoneA, byte valueForNoneB, ByteBiFunction<Byte> zipFunction) {
        return ByteStream.zip(this, b, valueForNoneA, valueForNoneB, zipFunction);
    }

    @Override
    public ByteStream zipWith(ByteStream b, ByteStream c, byte valueForNoneA, byte valueForNoneB, byte valueForNoneC, ByteTriFunction<Byte> zipFunction) {
        return ByteStream.zip(this, b, c, valueForNoneA, valueForNoneB, valueForNoneC, zipFunction);
    }

    @Override
    public ByteStream cached() {
        return this.newStream(toArray(), sorted);
    }
}
