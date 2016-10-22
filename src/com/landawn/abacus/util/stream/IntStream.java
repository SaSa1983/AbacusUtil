/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.landawn.abacus.util.stream;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.Set;

import com.landawn.abacus.exception.AbacusException;
import com.landawn.abacus.util.Array;
import com.landawn.abacus.util.CompletableFuture;
import com.landawn.abacus.util.Holder;
import com.landawn.abacus.util.IntIterator;
import com.landawn.abacus.util.IntList;
import com.landawn.abacus.util.IntSummaryStatistics;
import com.landawn.abacus.util.LongMultiset;
import com.landawn.abacus.util.Multimap;
import com.landawn.abacus.util.Multiset;
import com.landawn.abacus.util.MutableInt;
import com.landawn.abacus.util.N;
import com.landawn.abacus.util.Nth;
import com.landawn.abacus.util.Optional;
import com.landawn.abacus.util.OptionalDouble;
import com.landawn.abacus.util.OptionalInt;
import com.landawn.abacus.util.function.BiConsumer;
import com.landawn.abacus.util.function.BinaryOperator;
import com.landawn.abacus.util.function.Function;
import com.landawn.abacus.util.function.IntBiFunction;
import com.landawn.abacus.util.function.IntBinaryOperator;
import com.landawn.abacus.util.function.IntConsumer;
import com.landawn.abacus.util.function.IntFunction;
import com.landawn.abacus.util.function.IntNFunction;
import com.landawn.abacus.util.function.IntPredicate;
import com.landawn.abacus.util.function.IntSupplier;
import com.landawn.abacus.util.function.IntToByteFunction;
import com.landawn.abacus.util.function.IntToCharFunction;
import com.landawn.abacus.util.function.IntToDoubleFunction;
import com.landawn.abacus.util.function.IntToFloatFunction;
import com.landawn.abacus.util.function.IntToLongFunction;
import com.landawn.abacus.util.function.IntToShortFunction;
import com.landawn.abacus.util.function.IntTriFunction;
import com.landawn.abacus.util.function.IntUnaryOperator;
import com.landawn.abacus.util.function.ObjIntConsumer;
import com.landawn.abacus.util.function.Supplier;
import com.landawn.abacus.util.function.ToIntFunction;

/**
 * Note: It's copied from OpenJDK at: http://hg.openjdk.java.net/jdk8u/hs-dev/jdk
 * <br />
 * 
 * A sequence of primitive int-valued elements supporting sequential and parallel
 * aggregate operations.  This is the {@code int} primitive specialization of
 * {@link Stream}.
 *
 * <p>The following example illustrates an aggregate operation using
 * {@link Stream} and {@link IntStream}, computing the sum of the weights of the
 * red widgets:
 *
 * <pre>{@code
 *     int sum = widgets.stream()
 *                      .filter(w -> w.getColor() == RED)
 *                      .mapToInt(w -> w.getWeight())
 *                      .sum();
 * }</pre>
 *
 * See the class documentation for {@link Stream} and the package documentation
 * for <a href="package-summary.html">java.util.stream</a> for additional
 * specification of streams, stream operations, stream pipelines, and
 * parallelism.
 *
 * @since 1.8
 * @see Stream
 * @see <a href="package-summary.html">java.util.stream</a>
 */
public abstract class IntStream implements BaseStream<Integer, IntStream> {

    private static final IntStream EMPTY = new ArrayIntStream(N.EMPTY_INT_ARRAY);

    /**
     * Returns a stream consisting of the elements of this stream that match
     * the given predicate.
     *
     * <p>This is an <a href="package-summary.html#StreamOps">intermediate
     * operation</a>.
     *
     * @param predicate a <a href="package-summary.html#NonInterference">non-interfering</a>,
     *                  <a href="package-summary.html#Statelessness">stateless</a>
     *                  predicate to apply to each element to determine if it
     *                  should be included
     * @return the new stream
     */
    public abstract IntStream filter(final IntPredicate predicate);

    /**
     * 
     * @param predicate
     * @param max the maximum elements number to the new Stream.
     * @return
     */
    public abstract IntStream filter(final IntPredicate predicate, final long max);

    /**
     * Keep the elements until the given predicate returns false. The stream should be sorted, which means if x is the first element: <code>predicate.text(x)</code> returns false, any element y behind x: <code>predicate.text(y)</code> should returns false.
     * 
     * In parallel Streams, the elements after the first element which <code>predicate</code> returns false may be tested by predicate too.
     * 
     * @param predicate
     * @return
     */
    public abstract IntStream takeWhile(final IntPredicate predicate);

    /**
     * Keep the elements until the given predicate returns false. The stream should be sorted, which means if x is the first element: <code>predicate.text(x)</code> returns false, any element y behind x: <code>predicate.text(y)</code> should returns false.
     * 
     * In parallel Streams, the elements after the first element which <code>predicate</code> returns false may be tested by predicate too.
     * 
     * @param predicate
     * @param max the maximum elements number to the new Stream.
     * @return
     */
    public abstract IntStream takeWhile(final IntPredicate predicate, final long max);

    /**
     * Remove the elements until the given predicate returns false. The stream should be sorted, which means if x is the first element: <code>predicate.text(x)</code> returns true, any element y behind x: <code>predicate.text(y)</code> should returns true.
     * 
     * In parallel Streams, the elements after the first element which <code>predicate</code> returns false may be tested by predicate too.
     * 
     * @param predicate
     * @return
     */
    public abstract IntStream dropWhile(final IntPredicate predicate);

    /**
     * Remove the elements until the given predicate returns false. The stream should be sorted, which means if x is the first element: <code>predicate.text(x)</code> returns true, any element y behind x: <code>predicate.text(y)</code> should returns true.
     * 
     * In parallel Streams, the elements after the first element which <code>predicate</code> returns false may be tested by predicate too.
     * 
     * @param predicate
     * @param max the maximum elements number to the new Stream.
     * @return
     */
    public abstract IntStream dropWhile(final IntPredicate predicate, final long max);

    /**
     * Returns a stream consisting of the results of applying the given
     * function to the elements of this stream.
     *
     * <p>This is an <a href="package-summary.html#StreamOps">intermediate
     * operation</a>.
     *
     * @param mapper a <a href="package-summary.html#NonInterference">non-interfering</a>,
     *               <a href="package-summary.html#Statelessness">stateless</a>
     *               function to apply to each element
     * @return the new stream
     */
    public abstract IntStream map(IntUnaryOperator mapper);

    public abstract CharStream mapToChar(IntToCharFunction mapper);

    public abstract ByteStream mapToByte(IntToByteFunction mapper);

    public abstract ShortStream mapToShort(IntToShortFunction mapper);

    /**
     * Returns a {@code LongStream} consisting of the results of applying the
     * given function to the elements of this stream.
     *
     * <p>This is an <a href="package-summary.html#StreamOps">intermediate
     * operation</a>.
     *
     * @param mapper a <a href="package-summary.html#NonInterference">non-interfering</a>,
     *               <a href="package-summary.html#Statelessness">stateless</a>
     *               function to apply to each element
     * @return the new stream
     */
    public abstract LongStream mapToLong(IntToLongFunction mapper);

    /**
     * Returns a {@code FloatStream} consisting of the results of applying the
     * given function to the elements of this stream.
     *
     * <p>This is an <a href="package-summary.html#StreamOps">intermediate
     * operation</a>.
     *
     * @param mapper a <a href="package-summary.html#NonInterference">non-interfering</a>,
     *               <a href="package-summary.html#Statelessness">stateless</a>
     *               function to apply to each element
     * @return the new stream
     */
    public abstract FloatStream mapToFloat(IntToFloatFunction mapper);

    /**
     * Returns a {@code DoubleStream} consisting of the results of applying the
     * given function to the elements of this stream.
     *
     * <p>This is an <a href="package-summary.html#StreamOps">intermediate
     * operation</a>.
     *
     * @param mapper a <a href="package-summary.html#NonInterference">non-interfering</a>,
     *               <a href="package-summary.html#Statelessness">stateless</a>
     *               function to apply to each element
     * @return the new stream
     */
    public abstract DoubleStream mapToDouble(IntToDoubleFunction mapper);

    /**
     * Returns an object-valued {@code Stream} consisting of the results of
     * applying the given function to the elements of this stream.
     *
     * <p>This is an <a href="package-summary.html#StreamOps">
     *     intermediate operation</a>.
     *
     * @param <U> the element type of the new stream
     * @param mapper a <a href="package-summary.html#NonInterference">non-interfering</a>,
     *               <a href="package-summary.html#Statelessness">stateless</a>
     *               function to apply to each element
     * @return the new stream
     */
    public abstract <U> Stream<U> mapToObj(IntFunction<? extends U> mapper);

    /**
     * Returns a stream consisting of the results of replacing each element of
     * this stream with the contents of a mapped stream produced by applying
     * the provided mapping function to each element.  Each mapped stream is
     * {@link java.util.stream.BaseStream#close() closed} after its contents
     * have been placed into this stream.  (If a mapped stream is {@code null}
     * an empty stream is used, instead.)
     *
     * <p>This is an <a href="package-summary.html#StreamOps">intermediate
     * operation</a>.
     *
     * @param mapper a <a href="package-summary.html#NonInterference">non-interfering</a>,
     *               <a href="package-summary.html#Statelessness">stateless</a>
     *               function to apply to each element which produces an
     *               {@code IntStream} of new values
     * @return the new stream
     * @see Stream#flatMap(Function)
     */
    public abstract IntStream flatMap(IntFunction<? extends IntStream> mapper);

    public abstract CharStream flatMapToChar(IntFunction<? extends CharStream> mapper);

    public abstract ByteStream flatMapToByte(IntFunction<? extends ByteStream> mapper);

    public abstract ShortStream flatMapToShort(IntFunction<? extends ShortStream> mapper);

    public abstract LongStream flatMapToLong(IntFunction<? extends LongStream> mapper);

    public abstract FloatStream flatMapToFloat(IntFunction<? extends FloatStream> mapper);

    public abstract DoubleStream flatMapToDouble(IntFunction<? extends DoubleStream> mapper);

    public abstract <T> Stream<T> flatMapToObj(IntFunction<? extends Stream<T>> mapper);

    /**
     * Returns Stream of IntStream with consecutive sub sequences of the elements, each of the same size (the final sequence may be smaller).
     * 
     * @param size
     * @return
     */
    public abstract Stream<IntStream> split(int size);

    /**
     * Split the stream by the specified predicate.
     * 
     * <pre>
     * <code>
     * // split the number sequence by window 5.
     * final MutableInt border = MutableInt.of(5);
     * IntStream.of(1, 2, 3, 5, 7, 9, 10, 11, 19).split(e -> {
     *     if (e <= border.intValue()) {
     *         return true;
     *     } else {
     *         border.addAndGet(5);
     *         return false;
     *     }
     * }).map(s -> s.toArray()).forEach(N::println);
     * </code>
     * </pre>
     * 
     * This stream should be sorted by value which is used to verify the border.
     * This method only run sequentially, even in parallel stream.
     * 
     * @param predicate
     * @return
     */
    public abstract Stream<IntStream> split(IntPredicate predicate);

    /**
     * Returns a stream consisting of the distinct elements of this stream.
     *
     * <p>This is a <a href="package-summary.html#StreamOps">stateful
     * intermediate operation</a>.
     *
     * @return the new stream
     */
    public abstract IntStream distinct();

    public abstract IntStream top(int n);

    public abstract IntStream top(final int n, Comparator<? super Integer> comparator);

    /**
     * Returns a stream consisting of the elements of this stream in sorted
     * order.
     *
     * <p>This is a <a href="package-summary.html#StreamOps">stateful
     * intermediate operation</a>.
     *
     * @return the new stream
     */
    public abstract IntStream sorted();

    // public abstract IntStream parallelSorted();

    /**
     * Returns a stream consisting of the elements of this stream, additionally
     * performing the provided action on each element as elements are consumed
     * from the resulting stream.
     *
     * <p>This is an <a href="package-summary.html#StreamOps">intermediate
     * operation</a>.
     *
     * <p>For parallel stream pipelines, the action may be called at
     * whatever time and in whatever thread the element is made available by the
     * upstream operation.  If the action modifies shared state,
     * it is responsible for providing the required synchronization.
     *
     * @apiNote This method exists mainly to support debugging, where you want
     * to see the elements as they flow past a certain point in a pipeline:
     * <pre>{@code
     *     IntStream.of(1, 2, 3, 4)
     *         .filter(e -> e > 2)
     *         .peek(e -> System.out.println("Filtered value: " + e))
     *         .map(e -> e * e)
     *         .peek(e -> System.out.println("Mapped value: " + e))
     *         .sum();
     * }</pre>
     *
     * @param action a <a href="package-summary.html#NonInterference">
     *               non-interfering</a> action to perform on the elements as
     *               they are consumed from the stream
     * @return the new stream
     */
    public abstract IntStream peek(IntConsumer action);

    /**
     * Returns a stream consisting of the elements of this stream, truncated
     * to be no longer than {@code maxSize} in length.
     *
     * <p>This is a <a href="package-summary.html#StreamOps">short-circuiting
     * stateful intermediate operation</a>.
     *
     * @apiNote
     * While {@code limit()} is generally a cheap operation on sequential
     * stream pipelines, it can be quite expensive on ordered parallel pipelines,
     * especially for large values of {@code maxSize}, since {@code limit(n)}
     * is constrained to return not just any <em>n</em> elements, but the
     * <em>first n</em> elements in the encounter order.  Using an unordered
     * stream source or removing the
     * ordering constraint with {@link #unordered()} may result in significant
     * speedups of {@code limit()} in parallel pipelines, if the semantics of
     * your situation permit.  If consistency with encounter order is required,
     * and you are experiencing poor performance or memory utilization with
     * {@code limit()} in parallel pipelines, switching to sequential execution
     * with {@link #sequential()} may improve performance.
     *
     * @param maxSize the number of elements the stream should be limited to
     * @return the new stream
     * @throws IllegalArgumentException if {@code maxSize} is negative
     */
    public abstract IntStream limit(long maxSize);

    /**
     * Returns a stream consisting of the remaining elements of this stream
     * after discarding the first {@code n} elements of the stream.
     * If this stream contains fewer than {@code n} elements then an
     * empty stream will be returned.
     *
     * <p>This is a <a href="package-summary.html#StreamOps">stateful
     * intermediate operation</a>.
     *
     * @apiNote
     * While {@code skip()} is generally a cheap operation on sequential
     * stream pipelines, it can be quite expensive on ordered parallel pipelines,
     * especially for large values of {@code n}, since {@code skip(n)}
     * is constrained to skip not just any <em>n</em> elements, but the
     * <em>first n</em> elements in the encounter order.  Using an unordered
     * stream source or removing the
     * ordering constraint with {@link #unordered()} may result in significant
     * speedups of {@code skip()} in parallel pipelines, if the semantics of
     * your situation permit.  If consistency with encounter order is required,
     * and you are experiencing poor performance or memory utilization with
     * {@code skip()} in parallel pipelines, switching to sequential execution
     * with {@link #sequential()} may improve performance.
     *
     * @param n the number of leading elements to skip
     * @return the new stream
     * @throws IllegalArgumentException if {@code n} is negative
     */
    public abstract IntStream skip(long n);

    /**
     * Performs an action for each element of this stream.
     *
     * <p>This is a <a href="package-summary.html#StreamOps">terminal
     * operation</a>.
     *
     * <p>For parallel stream pipelines, this operation does <em>not</em>
     * guarantee to respect the encounter order of the stream, as doing so
     * would sacrifice the benefit of parallelism.  For any given element, the
     * action may be performed at whatever time and in whatever thread the
     * library chooses.  If the action accesses shared state, it is
     * responsible for providing the required synchronization.
     *
     * @param action a <a href="package-summary.html#NonInterference">
     *               non-interfering</a> action to perform on the elements
     */
    public abstract void forEach(IntConsumer action);

    //    /**
    //     * In parallel Streams, the elements after the first element which <code>action</code> returns false may be executed by action too.
    //     * 
    //     * @param action break if the action returns false.
    //     * @return false if it breaks, otherwise true.
    //     */
    //    public abstract boolean forEach2(IntFunction<Boolean> action);

    /**
     * Returns an array containing the elements of this stream.
     *
     * <p>This is a <a href="package-summary.html#StreamOps">terminal
     * operation</a>.
     *
     * @return an array containing the elements of this stream
     */
    public abstract int[] toArray();

    public abstract IntList toIntList();

    public abstract List<Integer> toList();

    public abstract List<Integer> toList(Supplier<? extends List<Integer>> supplier);

    public abstract Set<Integer> toSet();

    public abstract Set<Integer> toSet(Supplier<? extends Set<Integer>> supplier);

    public abstract Multiset<Integer> toMultiset();

    public abstract Multiset<Integer> toMultiset(Supplier<? extends Multiset<Integer>> supplier);

    public abstract LongMultiset<Integer> toLongMultiset();

    public abstract LongMultiset<Integer> toLongMultiset(Supplier<? extends LongMultiset<Integer>> supplier);

    /**
     * 
     * @param classifier
     * @return
     * @see Collectors#groupingBy(Function)
     */
    public abstract <K> Map<K, List<Integer>> toMap(IntFunction<? extends K> classifier);

    /**
     * 
     * @param classifier
     * @param mapFactory
     * @return
     * @see Collectors#groupingBy(Function, Supplier)
     */
    public abstract <K, M extends Map<K, List<Integer>>> M toMap(final IntFunction<? extends K> classifier, final Supplier<M> mapFactory);

    /**
     * 
     * @param classifier
     * @param downstream
     * @return
     * @see Collectors#groupingBy(Function, Collector)
     */
    public abstract <K, A, D> Map<K, D> toMap(final IntFunction<? extends K> classifier, final Collector<Integer, A, D> downstream);

    /**
     * 
     * @param classifier
     * @param downstream
     * @param mapFactory
     * @return
     * @see Collectors#groupingBy(Function, Collector, Supplier)
     */
    public abstract <K, D, A, M extends Map<K, D>> M toMap(final IntFunction<? extends K> classifier, final Collector<Integer, A, D> downstream,
            final Supplier<M> mapFactory);

    /**
     * 
     * @param keyMapper
     * @param valueMapper
     * @return
     * @see Collectors#toMap(Function, Function)
     */
    public abstract <K, U> Map<K, U> toMap(IntFunction<? extends K> keyMapper, IntFunction<? extends U> valueMapper);

    /**
     * 
     * @param keyMapper
     * @param valueMapper
     * @param mapSupplier
     * @return
     * @see Collectors#toMap(Function, Function, Supplier)
     */
    public abstract <K, U, M extends Map<K, U>> M toMap(IntFunction<? extends K> keyMapper, IntFunction<? extends U> valueMapper, Supplier<M> mapSupplier);

    /**
     * 
     * @param keyMapper
     * @param valueMapper
     * @param mergeFunction
     * @return
     * @see Collectors#toMap(Function, Function, BinaryOperator)
     */
    public abstract <K, U> Map<K, U> toMap(IntFunction<? extends K> keyMapper, IntFunction<? extends U> valueMapper, BinaryOperator<U> mergeFunction);

    /**
     * 
     * @param keyMapper
     * @param valueMapper
     * @param mergeFunction
     * @param mapSupplier
     * @return
     * @see Collectors#toMap(Function, Function, BinaryOperator, Supplier)
     */
    public abstract <K, U, M extends Map<K, U>> M toMap(IntFunction<? extends K> keyMapper, IntFunction<? extends U> valueMapper,
            BinaryOperator<U> mergeFunction, Supplier<M> mapSupplier);

    /**
     * 
     * @param keyMapper
     * @param valueMapper
     * @return
     * @see Collectors#toMultimap(Function, Function)
     */
    public abstract <K, U> Multimap<K, U, List<U>> toMultimap(IntFunction<? extends K> keyMapper, IntFunction<? extends U> valueMapper);

    /**
     * 
     * @param keyMapper
     * @param valueMapper
     * @param mapSupplier
     * @return
     * @see Collectors#toMap(Function, Function, BinaryOperator, Supplier)
     */
    public abstract <K, U, V extends Collection<U>> Multimap<K, U, V> toMultimap(IntFunction<? extends K> keyMapper, IntFunction<? extends U> valueMapper,
            Supplier<Multimap<K, U, V>> mapSupplier);

    /**
     * Performs a <a href="package-summary.html#Reduction">reduction</a> on the
     * elements of this stream, using the provided identity value and an
     * <a href="package-summary.html#Associativity">associative</a>
     * accumulation function, and returns the reduced value.  This is equivalent
     * to:
     * <pre>{@code
     *     int result = identity;
     *     for (int element : this stream)
     *         result = accumulator.applyAsInt(result, element)
     *     return result;
     * }</pre>
     *
     * but is not constrained to execute sequentially.
     *
     * <p>The {@code identity} value must be an identity for the accumulator
     * function. This means that for all {@code x},
     * {@code accumulator.apply(identity, x)} is equal to {@code x}.
     * The {@code accumulator} function must be an
     * <a href="package-summary.html#Associativity">associative</a> function.
     *
     * <p>This is a <a href="package-summary.html#StreamOps">terminal
     * operation</a>.
     *
     * @apiNote Sum, min, max, and average are all special cases of reduction.
     * Summing a stream of numbers can be expressed as:
     *
     * <pre>{@code
     *     int sum = integers.reduce(0, (a, b) -> a+b);
     * }</pre>
     *
     * or more compactly:
     *
     * <pre>{@code
     *     int sum = integers.reduce(0, Integer::sum);
     * }</pre>
     *
     * <p>While this may seem a more roundabout way to perform an aggregation
     * compared to simply mutating a running total in a loop, reduction
     * operations parallelize more gracefully, without needing additional
     * synchronization and with greatly reduced risk of data races.
     *
     * @param identity the identity value for the accumulating function
     * @param op an <a href="package-summary.html#Associativity">associative</a>,
     *           <a href="package-summary.html#NonInterference">non-interfering</a>,
     *           <a href="package-summary.html#Statelessness">stateless</a>
     *           function for combining two values
     * @return the result of the reduction
     * @see #sum()
     * @see #min()
     * @see #max()
     * @see #average()
     */
    public abstract int reduce(int identity, IntBinaryOperator op);

    /**
     * Performs a <a href="package-summary.html#Reduction">reduction</a> on the
     * elements of this stream, using an
     * <a href="package-summary.html#Associativity">associative</a> accumulation
     * function, and returns an {@code OptionalInt} describing the reduced value,
     * if any. This is equivalent to:
     * <pre>{@code
     *     boolean foundAny = false;
     *     int result = null;
     *     for (int element : this stream) {
     *         if (!foundAny) {
     *             foundAny = true;
     *             result = element;
     *         }
     *         else
     *             result = accumulator.applyAsInt(result, element);
     *     }
     *     return foundAny ? OptionalInt.of(result) : OptionalInt.empty();
     * }</pre>
     *
     * but is not constrained to execute sequentially.
     *
     * <p>The {@code accumulator} function must be an
     * <a href="package-summary.html#Associativity">associative</a> function.
     *
     * <p>This is a <a href="package-summary.html#StreamOps">terminal
     * operation</a>.
     *
     * @param op an <a href="package-summary.html#Associativity">associative</a>,
     *           <a href="package-summary.html#NonInterference">non-interfering</a>,
     *           <a href="package-summary.html#Statelessness">stateless</a>
     *           function for combining two values
     * @return the result of the reduction
     * @see #reduce(int, IntBinaryOperator)
     */
    public abstract OptionalInt reduce(IntBinaryOperator op);

    /**
     * Performs a <a href="package-summary.html#MutableReduction">mutable
     * reduction</a> operation on the elements of this stream.  A mutable
     * reduction is one in which the reduced value is a mutable result container,
     * such as an {@code ArrayList}, and elements are incorporated by updating
     * the state of the result rather than by replacing the result.  This
     * produces a result equivalent to:
     * <pre>{@code
     *     R result = supplier.get();
     *     for (int element : this stream)
     *         accumulator.accept(result, element);
     *     return result;
     * }</pre>
     *
     * <p>Like {@link #reduce(int, IntBinaryOperator)}, {@code collect} operations
     * can be parallelized without requiring additional synchronization.
     *
     * <p>This is a <a href="package-summary.html#StreamOps">terminal
     * operation</a>.
     *
     * @param <R> type of the result
     * @param supplier a function that creates a new result container. For a
     *                 parallel execution, this function may be called
     *                 multiple times and must return a fresh value each time.
     * @param accumulator an <a href="package-summary.html#Associativity">associative</a>,
     *                    <a href="package-summary.html#NonInterference">non-interfering</a>,
     *                    <a href="package-summary.html#Statelessness">stateless</a>
     *                    function for incorporating an additional element into a result
     * @param combiner an <a href="package-summary.html#Associativity">associative</a>,
     *                    <a href="package-summary.html#NonInterference">non-interfering</a>,
     *                    <a href="package-summary.html#Statelessness">stateless</a>
     *                    function for combining two values, which must be
     *                    compatible with the accumulator function
     * @return the result of the reduction
     * @see Stream#collect(Supplier, BiConsumer, BiConsumer)
     */
    public abstract <R> R collect(Supplier<R> supplier, ObjIntConsumer<R> accumulator, BiConsumer<R, R> combiner);

    /**
     * This method is always executed sequentially, even in parallel stream.
     * 
     * @param supplier
     * @param accumulator
     * @return
     */
    public abstract <R> R collect(Supplier<R> supplier, ObjIntConsumer<R> accumulator);

    /**
     * Returns an {@code OptionalInt} describing the minimum element of this
     * stream, or an empty optional if this stream is empty.  This is a special
     * case of a <a href="package-summary.html#Reduction">reduction</a>
     * and is equivalent to:
     * <pre>{@code
     *     return reduce(Integer::min);
     * }</pre>
     *
     * <p>This is a <a href="package-summary.html#StreamOps">terminal operation</a>.
     *
     * @return an {@code OptionalInt} containing the minimum element of this
     * stream, or an empty {@code OptionalInt} if the stream is empty
     */
    public abstract OptionalInt min();

    /**
     * Returns an {@code OptionalInt} describing the maximum element of this
     * stream, or an empty optional if this stream is empty.  This is a special
     * case of a <a href="package-summary.html#Reduction">reduction</a>
     * and is equivalent to:
     * <pre>{@code
     *     return reduce(Integer::max);
     * }</pre>
     *
     * <p>This is a <a href="package-summary.html#StreamOps">terminal
     * operation</a>.
     *
     * @return an {@code OptionalInt} containing the maximum element of this
     * stream, or an empty {@code OptionalInt} if the stream is empty
     */
    public abstract OptionalInt max();

    /**
     * 
     * @param k
     * @return OptionalByte.empty() if there is no element or count less than k, otherwise the kth largest element.
     */
    public abstract OptionalInt kthLargest(int k);

    /**
     * Returns the sum of elements in this stream.  This is a special case
     * of a <a href="package-summary.html#Reduction">reduction</a>
     * and is equivalent to:
     * <pre>{@code
     *     return reduce(0, Integer::sum);
     * }</pre>
     *
     * <p>This is a <a href="package-summary.html#StreamOps">terminal
     * operation</a>.
     *
     * @return the sum of elements in this stream
     */
    public abstract Long sum();

    /**
     * Returns an {@code OptionalDouble} describing the arithmetic mean of elements of
     * this stream, or an empty optional if this stream is empty.  This is a
     * special case of a
     * <a href="package-summary.html#Reduction">reduction</a>.
     *
     * <p>This is a <a href="package-summary.html#StreamOps">terminal
     * operation</a>.
     *
     * @return an {@code OptionalDouble} containing the average element of this
     * stream, or an empty optional if the stream is empty
     */
    public abstract OptionalDouble average();

    /**
     * Returns the count of elements in this stream.  This is a special case of
     * a <a href="package-summary.html#Reduction">reduction</a> and is
     * equivalent to:
     * <pre>{@code
     *     return mapToLong(e -> 1L).sum();
     * }</pre>
     *
     * <p>This is a <a href="package-summary.html#StreamOps">terminal operation</a>.
     *
     * @return the count of elements in this stream
     */
    public abstract long count();

    public abstract IntSummaryStatistics summarize();

    public abstract Optional<Map<String, Integer>> distribution();

    /**
     * Returns whether any elements of this stream match the provided
     * predicate.  May not evaluate the predicate on all elements if not
     * necessary for determining the result.  If the stream is empty then
     * {@code false} is returned and the predicate is not evaluated.
     *
     * <p>This is a <a href="package-summary.html#StreamOps">short-circuiting
     * terminal operation</a>.
     *
     * @apiNote
     * This method evaluates the <em>existential quantification</em> of the
     * predicate over the elements of the stream (for some x P(x)).
     *
     * @param predicate a <a href="package-summary.html#NonInterference">non-interfering</a>,
     *                  <a href="package-summary.html#Statelessness">stateless</a>
     *                  predicate to apply to elements of this stream
     * @return {@code true} if any elements of the stream match the provided
     * predicate, otherwise {@code false}
     */
    public abstract boolean anyMatch(IntPredicate predicate);

    /**
     * Returns whether all elements of this stream match the provided predicate.
     * May not evaluate the predicate on all elements if not necessary for
     * determining the result.  If the stream is empty then {@code true} is
     * returned and the predicate is not evaluated.
     *
     * <p>This is a <a href="package-summary.html#StreamOps">short-circuiting
     * terminal operation</a>.
     *
     * @apiNote
     * This method evaluates the <em>universal quantification</em> of the
     * predicate over the elements of the stream (for all x P(x)).  If the
     * stream is empty, the quantification is said to be <em>vacuously
     * satisfied</em> and is always {@code true} (regardless of P(x)).
     *
     * @param predicate a <a href="package-summary.html#NonInterference">non-interfering</a>,
     *                  <a href="package-summary.html#Statelessness">stateless</a>
     *                  predicate to apply to elements of this stream
     * @return {@code true} if either all elements of the stream match the
     * provided predicate or the stream is empty, otherwise {@code false}
     */
    public abstract boolean allMatch(IntPredicate predicate);

    /**
     * Returns whether no elements of this stream match the provided predicate.
     * May not evaluate the predicate on all elements if not necessary for
     * determining the result.  If the stream is empty then {@code true} is
     * returned and the predicate is not evaluated.
     *
     * <p>This is a <a href="package-summary.html#StreamOps">short-circuiting
     * terminal operation</a>.
     *
     * @apiNote
     * This method evaluates the <em>universal quantification</em> of the
     * negated predicate over the elements of the stream (for all x ~P(x)).  If
     * the stream is empty, the quantification is said to be vacuously satisfied
     * and is always {@code true}, regardless of P(x).
     *
     * @param predicate a <a href="package-summary.html#NonInterference">non-interfering</a>,
     *                  <a href="package-summary.html#Statelessness">stateless</a>
     *                  predicate to apply to elements of this stream
     * @return {@code true} if either no elements of the stream match the
     * provided predicate or the stream is empty, otherwise {@code false}
     */
    public abstract boolean noneMatch(IntPredicate predicate);

    /**
     * Returns an {@link OptionalInt} describing the first element of this
     * stream, or an empty {@code OptionalInt} if the stream is empty.  If the
     * stream has no encounter order, then any element may be returned.
     *
     * <p>This is a <a href="package-summary.html#StreamOps">short-circuiting
     * terminal operation</a>.
     *
     * @return an {@code OptionalInt} describing the first element of this stream,
     * or an empty {@code OptionalInt} if the stream is empty
     */
    // public abstract OptionalInt findFirst();

    public abstract OptionalInt findFirst(IntPredicate predicate);

    // public abstract OptionalInt findLast();

    public abstract OptionalInt findLast(IntPredicate predicate);

    /**
     * Returns an {@link OptionalInt} describing some element of the stream, or
     * an empty {@code OptionalInt} if the stream is empty.
     *
     * <p>This is a <a href="package-summary.html#StreamOps">short-circuiting
     * terminal operation</a>.
     *
     * <p>The behavior of this operation is explicitly nondeterministic; it is
     * free to select any element in the stream.  This is to allow for maximal
     * performance in parallel operations; the cost is that multiple invocations
     * on the same source may not return the same result.  (If a stable result
     * is desired, use {@link #findFirst()} instead.)
     *
     * @return an {@code OptionalInt} describing some element of this stream, or
     * an empty {@code OptionalInt} if the stream is empty
     * @see #findFirst()
     */
    // public abstract OptionalInt findAny();

    public abstract OptionalInt findAny(IntPredicate predicate);

    /**
     * @param c
     * @return
     * @see IntList#except(IntList)
     */
    public abstract IntStream except(Collection<?> c);

    /**
     * @param c
     * @return
     * @see IntList#intersect(IntList)
     */
    public abstract IntStream intersect(Collection<?> c);

    //    /**
    //     * Skill All the elements in the specified collection.
    //     * 
    //     * @param c
    //     * @return
    //     * @see IntList#intersect(IntList)
    //     */
    //    public abstract IntStream exclude(Collection<?> c);

    /**
     * Append the specified stream to the tail of this stream.
     * @param stream
     * @return
     */
    @Override
    public abstract IntStream append(IntStream stream);

    /**
     * 
     * @param b
     * @param nextSelector first parameter is selected if <code>Nth.FIRST</code> is returned, otherwise the second parameter is selected.
     * @return
     */
    public abstract IntStream merge(final IntStream b, final IntBiFunction<Nth> nextSelector);

    /**
     * Returns a {@code LongStream} consisting of the elements of this stream,
     * converted to {@code long}.
     *
     * <p>This is an <a href="package-summary.html#StreamOps">intermediate
     * operation</a>.
     *
     * @return a {@code LongStream} consisting of the elements of this stream,
     * converted to {@code long}
     */
    public abstract LongStream asLongStream();

    /**
     * Returns a {@code FloatStream} consisting of the elements of this stream,
     * converted to {@code double}.
     *
     * <p>This is an <a href="package-summary.html#StreamOps">intermediate
     * operation</a>.
     *
     * @return a {@code FloatStream} consisting of the elements of this stream,
     * converted to {@code double}
     */
    public abstract FloatStream asFloatStream();

    /**
     * Returns a {@code DoubleStream} consisting of the elements of this stream,
     * converted to {@code double}.
     *
     * <p>This is an <a href="package-summary.html#StreamOps">intermediate
     * operation</a>.
     *
     * @return a {@code DoubleStream} consisting of the elements of this stream,
     * converted to {@code double}
     */
    public abstract DoubleStream asDoubleStream();

    /**
     * Returns a {@code Stream} consisting of the elements of this stream,
     * each boxed to an {@code Integer}.
     *
     * <p>This is an <a href="package-summary.html#StreamOps">intermediate
     * operation</a>.
     *
     * @return a {@code Stream} consistent of the elements of this stream,
     * each boxed to an {@code Integer}
     */
    public abstract Stream<Integer> boxed();

    @Override
    public abstract ImmutableIterator<Integer> iterator();

    public abstract ImmutableIntIterator intIterator();

    // Static factories

    public static IntStream empty() {
        return EMPTY;
    }

    public static IntStream of(final int... a) {
        return N.isNullOrEmpty(a) ? empty() : new ArrayIntStream(a);
    }

    public static IntStream of(final int[] a, final int startIndex, final int endIndex) {
        return N.isNullOrEmpty(a) && (startIndex == 0 && endIndex == 0) ? empty() : new ArrayIntStream(a, startIndex, endIndex);
    }

    public static IntStream of(final IntIterator iterator) {
        return iterator == null ? empty()
                : new IteratorIntStream(iterator instanceof ImmutableIntIterator ? (ImmutableIntIterator) iterator : new ImmutableIntIterator() {
                    @Override
                    public boolean hasNext() {
                        return iterator.hasNext();
                    }

                    @Override
                    public int next() {
                        return iterator.next();
                    }
                });
    }

    public static IntStream from(final byte... a) {
        return N.isNullOrEmpty(a) ? empty() : ArrayByteStream.of(a).asIntStream();
    }

    public static IntStream from(final byte[] a, final int startIndex, final int endIndex) {
        return N.isNullOrEmpty(a) && (startIndex == 0 && endIndex == 0) ? empty() : ArrayByteStream.of(a, startIndex, endIndex).asIntStream();
    }

    public static IntStream from(final short... a) {
        return N.isNullOrEmpty(a) ? empty() : ArrayShortStream.of(a).asIntStream();
    }

    public static IntStream from(final short[] a, final int startIndex, final int endIndex) {
        return N.isNullOrEmpty(a) && (startIndex == 0 && endIndex == 0) ? empty() : ArrayShortStream.of(a, startIndex, endIndex).asIntStream();
    }

    public static IntStream from(final char... a) {
        return N.isNullOrEmpty(a) ? empty() : ArrayCharStream.of(a).asIntStream();
    }

    public static IntStream from(final char[] a, final int startIndex, final int endIndex) {
        return N.isNullOrEmpty(a) && (startIndex == 0 && endIndex == 0) ? empty() : ArrayCharStream.of(a, startIndex, endIndex).asIntStream();
    }

    //    /**
    //     * Takes the chars in the specified String as the elements of the Stream
    //     * 
    //     * @param str
    //     * @return
    //     */
    //    public static IntStream from(final String str) {
    //        return N.isNullOrEmpty(str) ? empty() : CharStream.from(str).asIntStream();
    //    }
    //
    //    /**
    //     * Takes the chars in the specified String as the elements of the Stream
    //     * 
    //     * @param str
    //     * @param startIndex
    //     * @param endIndex
    //     * @return
    //     */
    //    public static IntStream from(final String str, final int startIndex, final int endIndex) {
    //        return N.isNullOrEmpty(str) && (startIndex == 0 && endIndex == 0) ? empty() : CharStream.from(str, startIndex, endIndex).asIntStream();
    //    }

    public static IntStream range(final int startInclusive, final int endExclusive) {
        return of(Array.range(startInclusive, endExclusive));
    }

    public static IntStream range(final int startInclusive, final int endExclusive, final int by) {
        return of(Array.range(startInclusive, endExclusive, by));
    }

    public static IntStream rangeClosed(final int startInclusive, final int endInclusive) {
        return of(Array.rangeClosed(startInclusive, endInclusive));
    }

    public static IntStream rangeClosed(final int startInclusive, final int endInclusive, final int by) {
        return of(Array.rangeClosed(startInclusive, endInclusive, by));
    }

    public static IntStream repeat(int element, int n) {
        return of(Array.repeat(element, n));
    }

    public static IntStream iterate(final Supplier<Boolean> hasNext, final IntSupplier next) {
        N.requireNonNull(hasNext);
        N.requireNonNull(next);

        return new IteratorIntStream(new ImmutableIntIterator() {
            private boolean hasNextVal = false;

            @Override
            public boolean hasNext() {
                if (hasNextVal == false) {
                    hasNextVal = hasNext.get().booleanValue();
                }

                return hasNextVal;
            }

            @Override
            public int next() {
                if (hasNextVal == false && hasNext() == false) {
                    throw new NoSuchElementException();
                }

                hasNextVal = false;
                return next.getAsInt();
            }
        });
    }

    public static IntStream iterate(final int seed, final Supplier<Boolean> hasNext, final IntUnaryOperator f) {
        N.requireNonNull(hasNext);
        N.requireNonNull(f);

        return new IteratorIntStream(new ImmutableIntIterator() {
            private int t = 0;
            private boolean isFirst = true;
            private boolean hasNextVal = false;

            @Override
            public boolean hasNext() {
                if (hasNextVal == false) {
                    hasNextVal = hasNext.get().booleanValue();
                }

                return hasNextVal;
            }

            @Override
            public int next() {
                if (hasNextVal == false && hasNext() == false) {
                    throw new NoSuchElementException();
                }

                hasNextVal = false;

                if (isFirst) {
                    isFirst = false;
                    t = seed;
                } else {
                    t = f.applyAsInt(t);
                }

                return t;
            }
        });
    }

    /**
     * 
     * @param seed
     * @param hasNext test if has next by hasNext.test(seed) for first time and hasNext.test(f.apply(previous)) for remaining.
     * @param f
     * @return
     */
    public static IntStream iterate(final int seed, final IntPredicate hasNext, final IntUnaryOperator f) {
        N.requireNonNull(hasNext);
        N.requireNonNull(f);

        return new IteratorIntStream(new ImmutableIntIterator() {
            private int t = 0;
            private int cur = 0;
            private boolean isFirst = true;
            private boolean noMoreVal = false;
            private boolean hasNextVal = false;

            @Override
            public boolean hasNext() {
                if (hasNextVal == false && noMoreVal == false) {
                    if (isFirst) {
                        isFirst = false;
                        hasNextVal = hasNext.test(cur = seed);
                    } else {
                        hasNextVal = hasNext.test(cur = f.applyAsInt(t));
                    }

                    if (hasNextVal == false) {
                        noMoreVal = true;
                    }
                }

                return hasNextVal;
            }

            @Override
            public int next() {
                if (hasNextVal == false && hasNext() == false) {
                    throw new NoSuchElementException();
                }

                t = cur;
                hasNextVal = false;
                return t;
            }
        });
    }

    public static IntStream iterate(final int seed, final IntUnaryOperator f) {
        N.requireNonNull(f);

        return new IteratorIntStream(new ImmutableIntIterator() {
            private int t = 0;
            private boolean isFirst = true;

            @Override
            public boolean hasNext() {
                return true;
            }

            @Override
            public int next() {
                if (isFirst) {
                    isFirst = false;
                    t = seed;
                } else {
                    t = f.applyAsInt(t);
                }

                return t;
            }
        });
    }

    public static IntStream iterate(final IntSupplier s) {
        N.requireNonNull(s);

        return new IteratorIntStream(new ImmutableIntIterator() {
            @Override
            public boolean hasNext() {
                return true;
            }

            @Override
            public int next() {
                return s.getAsInt();
            }
        });
    }

    public static IntStream concat(final int[]... a) {
        return N.isNullOrEmpty(a) ? empty() : new IteratorIntStream(new ImmutableIntIterator() {
            private final Iterator<int[]> iter = N.asList(a).iterator();
            private ImmutableIntIterator cur;

            @Override
            public boolean hasNext() {
                while ((cur == null || cur.hasNext() == false) && iter.hasNext()) {
                    cur = new ImmutableIntIterator() {
                        private final int[] cur = iter.next();
                        private int cursor = 0;

                        @Override
                        public boolean hasNext() {
                            return cursor < cur.length;
                        }

                        @Override
                        public int next() {
                            if (cursor >= cur.length) {
                                throw new NoSuchElementException();
                            }

                            return cur[cursor++];
                        }
                    };
                }
                return cur != null && cur.hasNext();
            }

            @Override
            public int next() {
                if ((cur == null || cur.hasNext() == false) && hasNext() == false) {
                    throw new NoSuchElementException();
                }

                return cur.next();
            }
        });
    }

    public static IntStream concat(final IntStream... a) {
        return N.isNullOrEmpty(a) ? empty() : new IteratorIntStream(new ImmutableIntIterator() {
            private final Iterator<IntStream> iter = N.asList(a).iterator();
            private ImmutableIntIterator cur;

            @Override
            public boolean hasNext() {
                while ((cur == null || cur.hasNext() == false) && iter.hasNext()) {
                    cur = iter.next().intIterator();
                }

                return cur != null && cur.hasNext();
            }

            @Override
            public int next() {
                if ((cur == null || cur.hasNext() == false) && hasNext() == false) {
                    throw new NoSuchElementException();
                }

                return cur.next();
            }
        }).onClose(new Runnable() {
            @Override
            public void run() {
                RuntimeException runtimeException = null;

                for (IntStream stream : a) {
                    try {
                        stream.close();
                    } catch (Throwable throwable) {
                        if (runtimeException == null) {
                            runtimeException = N.toRuntimeException(throwable);
                        } else {
                            runtimeException.addSuppressed(throwable);
                        }
                    }
                }

                if (runtimeException != null) {
                    throw runtimeException;
                }
            }
        });
    }

    public static IntStream concat(final IntIterator... a) {
        return N.isNullOrEmpty(a) ? empty() : concat(N.asList(a));
    }

    public static IntStream concat(final Collection<? extends IntIterator> c) {
        return N.isNullOrEmpty(c) ? empty() : new IteratorIntStream(new ImmutableIntIterator() {
            private final Iterator<? extends IntIterator> iter = c.iterator();
            private IntIterator cur;

            @Override
            public boolean hasNext() {
                while ((cur == null || cur.hasNext() == false) && iter.hasNext()) {
                    cur = iter.next();
                }

                return cur != null && cur.hasNext();
            }

            @Override
            public int next() {
                if ((cur == null || cur.hasNext() == false) && hasNext() == false) {
                    throw new NoSuchElementException();
                }

                return cur.next();
            }
        });
    }

    /**
     * Zip together the "a" and "b" arrays until one of them runs out of values.
     * Each pair of values is combined into a single value using the supplied combiner function.
     * 
     * @param a
     * @param b
     * @return
     */
    public static IntStream zip(final int[] a, final int[] b, final IntBiFunction<Integer> combiner) {
        final ToIntFunction<Integer> mapper = new ToIntFunction<Integer>() {
            @Override
            public int applyAsInt(Integer value) {
                return value.intValue();
            }
        };

        return Stream.zip(a, b, combiner).mapToInt(mapper);
    }

    /**
     * Zip together the "a", "b" and "c" arrays until one of them runs out of values.
     * Each triple of values is combined into a single value using the supplied combiner function.
     * 
     * @param a
     * @param b
     * @return
     */
    public static IntStream zip(final int[] a, final int[] b, final int[] c, final IntTriFunction<Integer> combiner) {
        final ToIntFunction<Integer> mapper = new ToIntFunction<Integer>() {
            @Override
            public int applyAsInt(Integer value) {
                return value.intValue();
            }
        };

        return Stream.zip(a, b, c, combiner).mapToInt(mapper);
    }

    /**
     * Zip together the "a" and "b" streams until one of them runs out of values.
     * Each pair of values is combined into a single value using the supplied combiner function.
     * 
     * @param a
     * @param b
     * @return
     */
    public static IntStream zip(final IntStream a, final IntStream b, final IntBiFunction<Integer> combiner) {
        final ToIntFunction<Integer> mapper = new ToIntFunction<Integer>() {
            @Override
            public int applyAsInt(Integer value) {
                return value.intValue();
            }
        };

        return Stream.zip(a, b, combiner).mapToInt(mapper);
    }

    /**
     * Zip together the "a", "b" and "c" streams until one of them runs out of values.
     * Each triple of values is combined into a single value using the supplied combiner function.
     * 
     * @param a
     * @param b
     * @return
     */
    public static IntStream zip(final IntStream a, final IntStream b, final IntStream c, final IntTriFunction<Integer> combiner) {
        final ToIntFunction<Integer> mapper = new ToIntFunction<Integer>() {
            @Override
            public int applyAsInt(Integer value) {
                return value.intValue();
            }
        };

        return Stream.zip(a, b, c, combiner).mapToInt(mapper);
    }

    /**
     * Zip together the "a" and "b" iterators until one of them runs out of values.
     * Each pair of values is combined into a single value using the supplied combiner function.
     * 
     * @param a
     * @param b
     * @return
     */
    public static IntStream zip(final IntIterator a, final IntIterator b, final IntBiFunction<Integer> combiner) {
        final ToIntFunction<Integer> mapper = new ToIntFunction<Integer>() {
            @Override
            public int applyAsInt(Integer value) {
                return value.intValue();
            }
        };

        return Stream.zip(a, b, combiner).mapToInt(mapper);
    }

    /**
     * Zip together the "a", "b" and "c" iterators until one of them runs out of values.
     * Each triple of values is combined into a single value using the supplied combiner function.
     * 
     * @param a
     * @param b
     * @return
     */
    public static IntStream zip(final IntIterator a, final IntIterator b, final IntIterator c, final IntTriFunction<Integer> combiner) {
        final ToIntFunction<Integer> mapper = new ToIntFunction<Integer>() {
            @Override
            public int applyAsInt(Integer value) {
                return value.intValue();
            }
        };

        return Stream.zip(a, b, c, combiner).mapToInt(mapper);
    }

    /**
     * Zip together the iterators until one of them runs out of values.
     * Each array of values is combined into a single value using the supplied combiner function.
     * 
     * @param c
     * @param combiner
     * @return
     */
    public static IntStream zip(final Collection<? extends IntIterator> c, final IntNFunction<Integer> combiner) {
        final ToIntFunction<Integer> mapper = new ToIntFunction<Integer>() {
            @Override
            public int applyAsInt(Integer value) {
                return value.intValue();
            }
        };

        return Stream.zip(c, combiner).mapToInt(mapper);
    }

    /**
     * Zip together the "a" and "b" iterators until all of them runs out of values.
     * Each pair of values is combined into a single value using the supplied combiner function.
     * 
     * @param a
     * @param b
     * @param valueForNoneA value to fill if "a" runs out of values first.
     * @param valueForNoneB value to fill if "b" runs out of values first.
     * @param combiner
     * @return
     */
    public static IntStream zip(final IntStream a, final IntStream b, final int valueForNoneA, final int valueForNoneB, final IntBiFunction<Integer> combiner) {
        final ToIntFunction<Integer> mapper = new ToIntFunction<Integer>() {
            @Override
            public int applyAsInt(Integer value) {
                return value.intValue();
            }
        };

        return Stream.zip(a, b, valueForNoneA, valueForNoneB, combiner).mapToInt(mapper);
    }

    /**
     * Zip together the "a", "b" and "c" iterators until all of them runs out of values.
     * Each triple of values is combined into a single value using the supplied combiner function.
     * 
     * @param a
     * @param b
     * @param c
     * @param valueForNoneA value to fill if "a" runs out of values.
     * @param valueForNoneB value to fill if "b" runs out of values.
     * @param valueForNoneC value to fill if "c" runs out of values.
     * @param combiner
     * @return
     */
    public static IntStream zip(final IntStream a, final IntStream b, final IntStream c, final int valueForNoneA, final int valueForNoneB,
            final int valueForNoneC, final IntTriFunction<Integer> combiner) {
        final ToIntFunction<Integer> mapper = new ToIntFunction<Integer>() {
            @Override
            public int applyAsInt(Integer value) {
                return value.intValue();
            }
        };

        return Stream.zip(a, b, c, valueForNoneA, valueForNoneB, valueForNoneC, combiner).mapToInt(mapper);
    }

    /**
     * Zip together the "a" and "b" iterators until all of them runs out of values.
     * Each pair of values is combined into a single value using the supplied combiner function.
     * 
     * @param a
     * @param b
     * @param valueForNoneA value to fill if "a" runs out of values first.
     * @param valueForNoneB value to fill if "b" runs out of values first.
     * @param combiner
     * @return
     */
    public static IntStream zip(final IntIterator a, final IntIterator b, final int valueForNoneA, final int valueForNoneB,
            final IntBiFunction<Integer> combiner) {
        final ToIntFunction<Integer> mapper = new ToIntFunction<Integer>() {
            @Override
            public int applyAsInt(Integer value) {
                return value.intValue();
            }
        };

        return Stream.zip(a, b, valueForNoneA, valueForNoneB, combiner).mapToInt(mapper);
    }

    /**
     * Zip together the "a", "b" and "c" iterators until all of them runs out of values.
     * Each triple of values is combined into a single value using the supplied combiner function.
     * 
     * @param a
     * @param b
     * @param c
     * @param valueForNoneA value to fill if "a" runs out of values.
     * @param valueForNoneB value to fill if "b" runs out of values.
     * @param valueForNoneC value to fill if "c" runs out of values.
     * @param combiner
     * @return
     */
    public static IntStream zip(final IntIterator a, final IntIterator b, final IntIterator c, final int valueForNoneA, final int valueForNoneB,
            final int valueForNoneC, final IntTriFunction<Integer> combiner) {
        final ToIntFunction<Integer> mapper = new ToIntFunction<Integer>() {
            @Override
            public int applyAsInt(Integer value) {
                return value.intValue();
            }
        };

        return Stream.zip(a, b, c, valueForNoneA, valueForNoneB, valueForNoneC, combiner).mapToInt(mapper);
    }

    /**
     * Zip together the iterators until all of them runs out of values.
     * Each array of values is combined into a single value using the supplied combiner function.
     * 
     * @param c
     * @param valuesForNone value to fill for any iterator runs out of values.
     * @param combiner
     * @return
     */
    public static IntStream zip(final Collection<? extends IntIterator> c, final int[] valuesForNone, final IntNFunction<Integer> combiner) {
        final ToIntFunction<Integer> mapper = new ToIntFunction<Integer>() {
            @Override
            public int applyAsInt(Integer value) {
                return value.intValue();
            }
        };

        return Stream.zip(c, valuesForNone, combiner).mapToInt(mapper);
    }

    /**
     * 
     * @param a
     * @param b
     * @param nextSelector first parameter is selected if <code>Nth.FIRST</code> is returned, otherwise the second parameter is selected.
     * @return
     */
    public static IntStream merge(final int[] a, final int[] b, final IntBiFunction<Nth> nextSelector) {
        if (N.isNullOrEmpty(a)) {
            return of(b);
        } else if (N.isNullOrEmpty(b)) {
            return of(a);
        }

        return new IteratorIntStream(new ImmutableIntIterator() {
            private final int lenA = a.length;
            private final int lenB = b.length;
            private int cursorA = 0;
            private int cursorB = 0;

            @Override
            public boolean hasNext() {
                return cursorA < lenA || cursorB < lenB;
            }

            @Override
            public int next() {
                if (cursorA < lenA) {
                    if (cursorB < lenB) {
                        if (nextSelector.apply(a[cursorA], b[cursorB]) == Nth.FIRST) {
                            return a[cursorA++];
                        } else {
                            return b[cursorB++];
                        }
                    } else {
                        return a[cursorA++];
                    }
                } else if (cursorB < lenB) {
                    return b[cursorB++];
                } else {
                    throw new NoSuchElementException();
                }
            }
        });
    }

    /**
     * 
     * @param a
     * @param b
     * @param nextSelector first parameter is selected if <code>Nth.FIRST</code> is returned, otherwise the second parameter is selected.
     * @return
     */
    public static IntStream merge(final IntStream a, final IntStream b, final IntBiFunction<Nth> nextSelector) {
        final IntIterator iterA = a.intIterator();
        final IntIterator iterB = b.intIterator();

        if (iterA.hasNext() == false) {
            return b;
        } else if (iterB.hasNext() == false) {
            return a;
        }

        return merge(iterA, iterB, nextSelector).onClose(new Runnable() {
            @Override
            public void run() {
                RuntimeException runtimeException = null;

                for (IntStream stream : N.asList(a, b)) {
                    try {
                        stream.close();
                    } catch (Throwable throwable) {
                        if (runtimeException == null) {
                            runtimeException = N.toRuntimeException(throwable);
                        } else {
                            runtimeException.addSuppressed(throwable);
                        }
                    }
                }

                if (runtimeException != null) {
                    throw runtimeException;
                }
            }
        });
    }

    /**
     * 
     * @param a
     * @param b
     * @param nextSelector first parameter is selected if <code>Nth.FIRST</code> is returned, otherwise the second parameter is selected.
     * @return
     */
    public static IntStream merge(final IntIterator a, final IntIterator b, final IntBiFunction<Nth> nextSelector) {
        if (a.hasNext() == false) {
            return of(b);
        } else if (b.hasNext() == false) {
            return of(a);
        }

        return new IteratorIntStream(new ImmutableIntIterator() {
            private int nextA = 0;
            private int nextB = 0;
            private boolean hasNextA = false;
            private boolean hasNextB = false;

            @Override
            public boolean hasNext() {
                return a.hasNext() || b.hasNext() || hasNextA || hasNextB;
            }

            @Override
            public int next() {
                if (hasNextA) {
                    if (b.hasNext()) {
                        if (nextSelector.apply(nextA, (nextB = b.next())) == Nth.FIRST) {
                            hasNextA = false;
                            hasNextB = true;
                            return nextA;
                        } else {
                            return nextB;
                        }
                    } else {
                        hasNextA = false;
                        return nextA;
                    }
                } else if (hasNextB) {
                    if (a.hasNext()) {
                        if (nextSelector.apply((nextA = a.next()), nextB) == Nth.FIRST) {
                            return nextA;
                        } else {
                            hasNextA = true;
                            hasNextB = false;
                            return nextB;
                        }
                    } else {
                        hasNextB = false;
                        return nextB;
                    }
                } else if (a.hasNext()) {
                    if (b.hasNext()) {
                        if (nextSelector.apply((nextA = a.next()), (nextB = b.next())) == Nth.FIRST) {
                            hasNextB = true;
                            return nextA;
                        } else {
                            hasNextA = true;
                            return nextB;
                        }
                    } else {
                        return a.next();
                    }
                } else if (b.hasNext()) {
                    return b.next();
                } else {
                    throw new NoSuchElementException();
                }
            }
        });
    }

    /**
     * 
     * @param a
     * @param nextSelector first parameter is selected if <code>Nth.FIRST</code> is returned, otherwise the second parameter is selected.
     * @return
     */
    public static IntStream merge(final IntStream[] a, final IntBiFunction<Nth> nextSelector) {
        if (N.isNullOrEmpty(a)) {
            return empty();
        } else if (a.length == 1) {
            return a[0];
        } else if (a.length == 2) {
            return merge(a[0], a[1], nextSelector);
        }

        final IntIterator[] iters = new IntIterator[a.length];

        for (int i = 0, len = a.length; i < len; i++) {
            iters[i] = a[i].intIterator();
        }

        return merge(iters, nextSelector).onClose(new Runnable() {
            @Override
            public void run() {
                RuntimeException runtimeException = null;

                for (IntStream stream : a) {
                    try {
                        stream.close();
                    } catch (Throwable throwable) {
                        if (runtimeException == null) {
                            runtimeException = N.toRuntimeException(throwable);
                        } else {
                            runtimeException.addSuppressed(throwable);
                        }
                    }
                }

                if (runtimeException != null) {
                    throw runtimeException;
                }
            }
        });
    }

    /**
     * 
     * @param a
     * @param nextSelector first parameter is selected if <code>Nth.FIRST</code> is returned, otherwise the second parameter is selected.
     * @return
     */
    public static IntStream merge(final IntIterator[] a, final IntBiFunction<Nth> nextSelector) {
        if (N.isNullOrEmpty(a)) {
            return empty();
        } else if (a.length == 1) {
            return of(a[0]);
        } else if (a.length == 2) {
            return merge(a[0], a[1], nextSelector);
        }

        return merge(Arrays.asList(a), nextSelector);
    }

    /**
     * 
     * @param c
     * @param nextSelector first parameter is selected if <code>Nth.FIRST</code> is returned, otherwise the second parameter is selected.
     * @return
     */
    public static IntStream merge(final Collection<? extends IntIterator> c, final IntBiFunction<Nth> nextSelector) {
        if (N.isNullOrEmpty(c)) {
            return empty();
        } else if (c.size() == 1) {
            return of(c.iterator().next());
        } else if (c.size() == 2) {
            final Iterator<? extends IntIterator> iter = c.iterator();
            return merge(iter.next(), iter.next(), nextSelector);
        }

        final Iterator<? extends IntIterator> iter = c.iterator();
        IntStream result = merge(iter.next(), iter.next(), nextSelector);

        while (iter.hasNext()) {
            result = merge(result.intIterator(), iter.next(), nextSelector);
        }

        return result;
    }

    /**
     * 
     * @param a
     * @param nextSelector first parameter is selected if <code>Nth.FIRST</code> is returned, otherwise the second parameter is selected.
     * @return
     */
    public static IntStream parallelMerge(final IntStream[] a, final IntBiFunction<Nth> nextSelector) {
        return parallelMerge(a, nextSelector, Stream.DEFAULT_MAX_THREAD_NUM);
    }

    /**
     * 
     * @param a
     * @param nextSelector first parameter is selected if <code>Nth.FIRST</code> is returned, otherwise the second parameter is selected.
     * @param maxThreadNum
     * @return
     */
    public static IntStream parallelMerge(final IntStream[] a, final IntBiFunction<Nth> nextSelector, final int maxThreadNum) {
        if (maxThreadNum < 1) {
            throw new IllegalArgumentException("maxThreadNum can be less than 1");
        }

        if (N.isNullOrEmpty(a)) {
            return empty();
        } else if (a.length == 1) {
            return a[0];
        } else if (a.length == 2) {
            return merge(a[0], a[1], nextSelector);
        }

        final IntIterator[] iters = new IntIterator[a.length];

        for (int i = 0, len = a.length; i < len; i++) {
            iters[i] = a[i].intIterator();
        }

        return parallelMerge(iters, nextSelector, maxThreadNum).onClose(new Runnable() {
            @Override
            public void run() {
                RuntimeException runtimeException = null;

                for (IntStream stream : a) {
                    try {
                        stream.close();
                    } catch (Throwable throwable) {
                        if (runtimeException == null) {
                            runtimeException = N.toRuntimeException(throwable);
                        } else {
                            runtimeException.addSuppressed(throwable);
                        }
                    }
                }

                if (runtimeException != null) {
                    throw runtimeException;
                }
            }
        });
    }

    /**
     * 
     * @param a
     * @param nextSelector first parameter is selected if <code>Nth.FIRST</code> is returned, otherwise the second parameter is selected.
     * @return
     */
    public static IntStream parallelMerge(final IntIterator[] a, final IntBiFunction<Nth> nextSelector) {
        return parallelMerge(a, nextSelector, Stream.DEFAULT_MAX_THREAD_NUM);
    }

    /**
     * 
     * @param a
     * @param nextSelector first parameter is selected if <code>Nth.FIRST</code> is returned, otherwise the second parameter is selected.
     * @param maxThreadNum
     * @return
     */
    public static IntStream parallelMerge(final IntIterator[] a, final IntBiFunction<Nth> nextSelector, final int maxThreadNum) {
        if (maxThreadNum < 1) {
            throw new IllegalArgumentException("maxThreadNum can be less than 1");
        }

        if (N.isNullOrEmpty(a)) {
            return empty();
        } else if (a.length == 1) {
            return of(a[0]);
        } else if (a.length == 2) {
            return merge(a[0], a[1], nextSelector);
        }

        return parallelMerge(Arrays.asList(a), nextSelector, maxThreadNum);
    }

    /**
     * 
     * @param c
     * @param nextSelector first parameter is selected if <code>Nth.FIRST</code> is returned, otherwise the second parameter is selected.
     * @return
     */
    public static IntStream parallelMerge(final Collection<? extends IntIterator> c, final IntBiFunction<Nth> nextSelector) {
        return parallelMerge(c, nextSelector, Stream.DEFAULT_MAX_THREAD_NUM);
    }

    /**
     * 
     * @param c
     * @param nextSelector first parameter is selected if <code>Nth.FIRST</code> is returned, otherwise the second parameter is selected.
     * @param maxThreadNum
     * @return
     */
    public static IntStream parallelMerge(final Collection<? extends IntIterator> c, final IntBiFunction<Nth> nextSelector, final int maxThreadNum) {
        if (maxThreadNum < 1) {
            throw new IllegalArgumentException("maxThreadNum can be less than 1");
        }

        if (N.isNullOrEmpty(c)) {
            return empty();
        } else if (c.size() == 1) {
            return of(c.iterator().next());
        } else if (c.size() == 2) {
            final Iterator<? extends IntIterator> iter = c.iterator();
            return merge(iter.next(), iter.next(), nextSelector);
        } else if (maxThreadNum <= 1) {
            return merge(c, nextSelector);
        }

        final Queue<IntIterator> queue = new LinkedList<>();
        queue.addAll(c);
        final Holder<Throwable> eHolder = new Holder<>();
        final MutableInt cnt = MutableInt.of(c.size());
        final List<CompletableFuture<Void>> futureList = new ArrayList<>(c.size() - 1);

        for (int i = 0, n = N.min(maxThreadNum, c.size() / 2 + 1); i < n; i++) {
            futureList.add(Stream.asyncExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    IntIterator a = null;
                    IntIterator b = null;
                    IntIterator c = null;

                    try {
                        while (eHolder.value() == null) {
                            synchronized (queue) {
                                if (cnt.intValue() > 2 && queue.size() > 1) {
                                    a = queue.poll();
                                    b = queue.poll();

                                    cnt.decrement();
                                } else {
                                    break;
                                }
                            }

                            c = ImmutableIntIterator.of(merge(a, b, nextSelector).toArray());

                            synchronized (queue) {
                                queue.offer(c);
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

        // Should never happen.
        if (queue.size() != 2) {
            throw new AbacusException("Unknown error happened.");
        }

        return merge(queue.poll(), queue.poll(), nextSelector);
    }
}
