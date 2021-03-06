/*
 * Copyright (c) 2018, Haiyang Li.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.landawn.abacus.util;

import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.RandomAccess;
import java.util.Set;

/**
 * It's an extension and wrapper for Google Guava.
 * 
 * @since 1.2.7
 * 
 * @author Haiyang Li 
 */
public final class Iterables {

    private Iterables() {
        // singleton.
    }

    /**
     * 
     * @param a
     * @param b
     * @return
     */
    public static <T> Set<T> differentSet(final Collection<? extends T> a, final Collection<?> b) {
        if (N.isNullOrEmpty(a)) {
            return new HashSet<>();
        } else if (N.isNullOrEmpty(b)) {
            return new HashSet<>(a);
        }

        final Set<T> result = new HashSet<>(a);

        Iterables.removeAll(a, b);

        return result;
    }

    /**
     * 
     * @param a
     * @param b
     * @return
     */
    public static <T> Set<T> symmetricDifferentSet(final Collection<? extends T> a, final Collection<? extends T> b) {
        if (N.isNullOrEmpty(a)) {
            return N.isNullOrEmpty(b) ? new HashSet<T>() : new HashSet<>(b);
        } else if (N.isNullOrEmpty(b)) {
            return N.isNullOrEmpty(a) ? new HashSet<T>() : new HashSet<>(a);
        }

        final Set<T> commonSet = Iterables.commonSet(a, b);
        final Set<T> result = new HashSet<>(a);

        for (T e : a) {
            if (!commonSet.contains(e)) {
                result.add(e);
            }
        }

        for (T e : b) {
            if (!commonSet.contains(e)) {
                result.add(e);
            }
        }

        return result;
    }

    /**
     * 
     * @param a
     * @param b
     * @return
     */
    public static <T> Set<T> commonSet(final Collection<? extends T> a, final Collection<?> b) {
        if (N.isNullOrEmpty(a) || N.isNullOrEmpty(b)) {
            return new HashSet<>();
        }

        return commonSet(N.asList(a, (Collection<? extends T>) b));
    }

    public static <T> Set<T> commonSet(final Collection<? extends Collection<? extends T>> c) {
        if (N.isNullOrEmpty(c)) {
            return new HashSet<>();
        } else if (c.size() == 1) {
            return N.newHashSet(c.iterator().next());
        }

        Collection<? extends T> smallest = null;

        for (Collection<? extends T> e : c) {
            if (N.isNullOrEmpty(e)) {
                return new HashSet<>();
            }

            if (smallest == null || e.size() < smallest.size()) {
                smallest = e;
            }
        }

        final Map<T, MutableInt> map = new HashMap<>();

        for (T e : smallest) {
            map.put(e, new MutableInt(1));
        }

        int cnt = 1;
        MutableInt val = null;

        for (Collection<? extends T> ec : c) {
            if (ec == smallest) {
                continue;
            }

            for (T e : ec) {
                val = map.get(e);

                if (val == null) {
                    // do nothing.
                } else if (val.intValue() < cnt) {
                    // map.remove(e);
                } else if (val.intValue() == cnt) {
                    val.increment();
                }
            }

            cnt++;
        }

        final Set<T> result = new HashSet<>(map.size());

        for (Map.Entry<T, MutableInt> entry : map.entrySet()) {
            if (entry.getValue().intValue() == cnt) {
                result.add(entry.getKey());
            }
        }

        return result;
    }

    public static boolean removeAll(Collection<?> c, Collection<?> objsToRemove) {
        if (N.isNullOrEmpty(c) || N.isNullOrEmpty(objsToRemove)) {
            return false;
        }

        if (c instanceof HashSet && !(objsToRemove instanceof Set)) {
            boolean result = false;

            for (Object e : objsToRemove) {
                result |= c.remove(e);

                if (c.size() == 0) {
                    break;
                }
            }

            return result;
        } else {
            return c.removeAll(objsToRemove);
        }
    }

    public static boolean retainAll(Collection<?> c, Collection<?> objsToKeep) {
        if (N.isNullOrEmpty(c)) {
            return false;
        } else if (N.isNullOrEmpty(objsToKeep)) {
            c.clear();
            return true;
        }

        if (c instanceof HashSet && !(objsToKeep instanceof Set) && (c.size() > 9 || objsToKeep.size() > 9)) {
            return c.retainAll(new HashSet<>(objsToKeep));
        } else {
            return c.retainAll(objsToKeep);
        }
    }

    /**
     * 
     * @param iterable
     * @return
     * throws DuplicatedResultException if there are more than one elements in the specified {@code iterable}.
     */
    public static <T> Nullable<T> getOnlyElement(Iterable<? extends T> iterable) {
        if (iterable == null) {
            return Nullable.empty();
        }

        return Iterators.getOnlyElement(iterable.iterator());
    }

    /**
     * 
     * @param c
     * @param objToFind
     * @return
     */
    public static OptionalInt indexOf(final Collection<?> c, final Object objToFind) {
        if (N.isNullOrEmpty(c)) {
            return OptionalInt.empty();
        }

        int idx = 0;

        for (Object e : c) {
            if (N.equals(e, objToFind)) {
                return OptionalInt.of(idx);
            }

            idx++;
        }

        return OptionalInt.empty();
    }

    /**
     * 
     * @param c
     * @param objToFind
     * @return
     */
    public static OptionalInt lastIndexOf(final Collection<?> c, final Object objToFind) {
        if (N.isNullOrEmpty(c)) {
            return OptionalInt.empty();
        }

        final int size = c.size();

        if (c instanceof List) {
            final List<Object> list = (List<Object>) c;

            if (c instanceof RandomAccess) {
                for (int i = size - 1; i >= 0; i--) {
                    if (N.equals(list.get(i), objToFind)) {
                        return OptionalInt.of(i);
                    }
                }
            } else {
                final ListIterator<Object> iter = list.listIterator(list.size());

                for (int i = size - 1; iter.hasPrevious(); i--) {
                    if (N.equals(iter.previous(), objToFind)) {
                        return OptionalInt.of(i);
                    }
                }
            }

            return OptionalInt.empty();
        } else if (c instanceof Deque) {
            final Iterator<Object> iter = ((Deque<Object>) c).descendingIterator();

            for (int i = size - 1; iter.hasNext(); i--) {
                if (N.equals(iter.next(), objToFind)) {
                    return OptionalInt.of(i);
                }
            }

            return OptionalInt.empty();
        } else {
            final Object[] a = c.toArray();

            for (int i = a.length - 1; i >= 0; i--) {
                if (N.equals(a[i], objToFind)) {
                    return OptionalInt.of(i);
                }
            }

            return OptionalInt.empty();
        }
    }

    public static <T, E extends Exception> OptionalInt findFirstIndex(final Collection<? extends T> c, final Try.Predicate<? super T, E> predicate) throws E {
        if (N.isNullOrEmpty(c)) {
            return OptionalInt.empty();
        }

        int idx = 0;

        for (T e : c) {
            if (predicate.test(e)) {
                return OptionalInt.of(idx);
            }

            idx++;
        }

        return OptionalInt.empty();
    }

    public static <T, E extends Exception> OptionalInt findLastIndex(final Collection<? extends T> c, final Try.Predicate<? super T, E> predicate) throws E {
        if (N.isNullOrEmpty(c)) {
            return OptionalInt.empty();
        }

        final int size = c.size();

        if (c instanceof List) {
            final List<T> list = (List<T>) c;

            if (c instanceof RandomAccess) {
                for (int i = size - 1; i >= 0; i--) {
                    if (predicate.test(list.get(i))) {
                        return OptionalInt.of(i);
                    }
                }
            } else {
                final ListIterator<T> iter = list.listIterator(list.size());

                for (int i = size - 1; iter.hasPrevious(); i--) {
                    if (predicate.test(iter.previous())) {
                        return OptionalInt.of(i);
                    }
                }
            }

            return OptionalInt.empty();
        } else if (c instanceof Deque) {
            final Iterator<T> iter = ((Deque<T>) c).descendingIterator();

            for (int i = size - 1; iter.hasNext(); i--) {
                if (predicate.test(iter.next())) {
                    return OptionalInt.of(i);
                }
            }

            return OptionalInt.empty();
        } else {
            final T[] a = (T[]) c.toArray();

            for (int i = a.length - 1; i >= 0; i--) {
                if (predicate.test(a[i])) {
                    return OptionalInt.of(i);
                }
            }

            return OptionalInt.empty();
        }
    }
}
