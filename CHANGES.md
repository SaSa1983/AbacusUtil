
### 1.1.1

* Replace `Predicate/Consumer/Function` in `Multiset/LongMultiset/Multimap/Matrix/IntMatrix/.../f` with `Try.Predicate/Consumer/Function`.

* Remove `IOUtil.parseInt(...)/parseLong(...)`.

* Remove `N.copy(Object entity, boolean ignoreUnknownProperty, Set<String> ignorePropNames)`.

* Remove `N.asConcurrentMap/asBiMap(...)`, Replace with `BiMap.of(...)`.

* Remove `Stream.biMap/triMap(...)`, replaced with `Stream.slidingMap(mapper, 2, ignoreNotPaired)/slidingMap(mapper, 3, ignoreNotPaired)`

* Refactoring `N.merge`: change `merge(sourceEntity, selectPropNames, targetEntity)` to `merge(sourceEntity, targetEntity, selectPropNames)`.

* Add `Fn.tuple1/tuple2/tuple3/tuple4(...)`.

* Add `BooleanPair/BytePair/ShortPair/BooleanTriple/ByteTriple/ShortTriple`.

* Add `Maps.removeIf/removeIfKey/removeIfValue(...)`.

* Add `Maps.map2Entity(targetClass, map, selectPropNames)`.

* Add `N.newTreeMap(...)`.

* Add `Joiner.concat(...)`.

* Add `Median`.

* Improvements and bug fix.


### 1.1.0

* `Clazz.of(Class<?>)` is marked to ‘Deprecated’ and will be removed in version 1.2.0 because it doesn’t work as expected.

* Replace `Predicate/Consumer/Function` in `N/Seq/IntList.../EntryStream/Stream/IntStream/.../DataSet.forEach(...)` and `N/JdbcUtil/IOUtil.parse(...)` with `Try.Predicate/Consumer/Function`.

* Add `EntryStream.collect(java.util.stream.Collector)` and `EntryStream.collectAndThen(java.util.stream.Collector, Function)`.

* Add `Math2.asinh(double)/acosh(double)/atanh(double)`. Copied from Apache Commons Math.

* Add `N.deleteRange(boolean[] a, int fromIndex, int toIndex)/deleteRange(char[]...)/deleteRange(byte[]...)/.../deleteRange(List<T>...)`.

* Remove `IndexedIntConsumer...` and `BooleanList/CharList/ByteList/.../IntList.forEach(IndexedIntConsumer...)`.

* Improvements and bug fix.



### Prior 1.1.0
* Refer to: [CHANGES.txt](https://github.com/landawn/AbacusUtil/blob/master/CHANGES.txt)