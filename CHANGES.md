### 1.5.7
 
* Add `Stream.split/splitAt/splitBy/sliding(..., Collector<? super T, A, R> collector)`.

* Add `Stream/IntStream/.../DoubleStream.scan(seed, accumulator, seedIncluded)`.

* Add `ByteStream/IntStream/.../DoubleStream.flatMappToObj(ByteFunction<T[]> mapper)`.

* Add `JdbcUtil.batchUpdate/batchInsert(PreparedQuery...)`.

* Improvements and bug fix.


### 1.5.6
 
* Move `f.copy(Class<T[][]>, Object[][]` to `N`.

* Remove `Collectors.pairing`, replaced by `Collectors.combine`.

* Change the implementation of `ParallelArrayStream/ParallelIteratorStream.collect(Collector)`: All `Collector` will be executed with multiple threads, regardless the `Characteristics`.

* Improvements and bug fix.


### 1.5.5
 
* Add `Multimap.forEachKey/forEachValue/flatForEachValue`.

* Add `Multiset/LongMultiset.forEach(Consumer<? super T> action)`.

* Add `IntObjPair/LongObjPair/DoubleObjPair`.

* Refactoring `Synchronized`.

* Improvements and bug fix.


### 1.5.4

* Rename `Stream/ByteStream/.../DoubleStream.of(T[][]/byte[][]/.../double[][])` to `Stream/ByteStream/.../DoubleStream.flat(T[][]/byte[][]/.../double[][])`.

* Rename `Stream.list(File)` to `Stream.listFiles(File)`.

* Change `Stream<Map.Entry<T, Integer>> Multiset/LongMultiset.stream()` to `Stream<T> Multiset/LongMultiset.stream()`.

* Add `Multiset/LongMultiset.flatStream()`.

* Add `N.formatJSON`.

* Add `Points`.

* Add `Files`, copied from Google Guava under Apache License v2. The purpose is to provide unified API.

* Refactoring `HttpHeaders/HttpClient`.

* Improvements and bug fix.


### 1.5.3

* Rename `Joiner.append(key, value)` to `Joiner.appendEntry(key, value)`.

* Refactoring `HttpHeaders/HttpClient`.

* Improvements and bug fix.


### 1.5.2
 
* Change the stream implementation: Streams will be automatically closed after execution(any terminal method is executed).

* Remove `Stream.try` because Streams will be automatically closed after execution.

* Remove `Stream.head/tail/headAndTail/cached`.

* Rename `Stream.of(File/Path...)` to `Stream.lines(File/Path...)`.

* Improvements and bug fix.


### 1.3.33
 
* Refactoring `HttpClient/HttpRequest/OKHttpClient/HttpSettings/HttpHeaders`.

* Replace `Charsets.DEFAULT` with `Charsets.UTF_8`.

* Add `OkHttpRequest`.

* Improvements and bug fix.


### 1.3.32
 
* Remove `Result.__()`.

* Remove `copyThen(...)` from `DataSet/IntList/LongList/DoubleList/...`.

* Rename `DataSet.__()` to `DataSet.builder()`.

* Switch method name: `DataSet.split(...)` to `DataSet.splitt(...)`.

* Refactoring: `Fn`.

* Remove/replace the source codes/docs copied from OpenJdk.

* Improvements and bug fix.


### 1.3.31

* Move `box/unbox/unboxOrDefault/unboxOrGet` from `Array` to `Primitives`.

* Move `unboxS/I/B/...` from `Fn` to `Fnn`.

* Remove `Collectors` related methods from `Fn`.

* Improvements and bug fix.


### 1.3.30

* Rename `ContinuableFuture.delayed(...)` to `ContinuableFuture.thenDelay(...)`.

* Rename `ContinuableFuture.with(Executor)` to `ContinuableFuture.thenUse(Executor)`.

* Rename `Futures.zip(...)` to `Futures.compose(...)`.

* Rename `Headers` to `HttpHeaders`.

* Rename `JdbcSettings.get/setColumnIndexes` to `JdbcSettings.get/setReturnedColumnIndexes`.

* Rename `JdbcSettings.get/setColumnNames` to `JdbcSettings.get/setReturnedColumnNames`.

* Improvements and bug fix.


### 1.3.29

* Add `Result<T, Throwable>`.

* Refactoring `ContinuableFuture` with `Result<T, Throwable`.

* Refactoring `HttpClient/OKHttpClient`.

* Refactoring `CodeGenerator`.

* Rename `NonUniqueResultException` to `DuplicatedResultException`.

* Improvements and bug fix.


### 1.3.28

* Remove `HttpClient.request`.

* Improvements and bug fix.


### 1.3.27

* Rename `PreparedQuery/PreparedCallableQuery.applyAsync/acceptAsync` to `PreparedQuery/PreparedCallableQuery.asyncApply/asyncAccept`.

* Rename `N.len(Collection/Map)` to `N.size(Collection/Map)`.

* Refactoring: `AsyncExecutor`.

* Improvements and bug fix.


### 1.3.26

* Refactoring `DataSet`.

* Improvements and bug fix.


### 1.3.25

* Add `ExceptionalStream`.

* Refactoring: `PreparedQuery.stream`.

* Remove `Stream.of(ResultSet...)`, replaced by `ExceptionalStream.of(ResultSet...)`.

* Improvements and bug fix.


### 1.3.24

* Refactoring: `JdbcUtil` and `SQLExecutor`.

* Improvements and bug fix.


### 1.3.23

* Improvements and bug fix.


### 1.3.22

* Improvements and bug fix.

* Rename `EntryStream.flatMap/flattMap/flatMapp`.


### 1.3.21

* Refactoring: `DateUtil`.

* Add more: `Mapper.findFirst/list/stream`.

* Improvements and bug fix.


### 1.3.20

* Improvements and bug fix.


### 1.3.19

* Improvements and bug fix.


### 1.3.18

* Improvements and bug fix.


### 1.3.17

* Rename `DateUtil.asXXX` to `DateUtil.parseXXX`.

* Rename `N.asBoolean/Char/Byte/.../Double` to `N.parseBoolean/Char/Byte/.../Double`.

* Rename `Fn.single` to `Fn.memoize`.

* Improvements and bug fix.


### 1.3.16

* Improvements and bug fix.


### 1.3.15

* Improvements and bug fix.


### 1.3.14

* Remove `SQLExcutor.queryForSingleResult(..., resultGet...)`.

* Improvements and bug fix.


### 1.3.13

* Add `SQLExecutor.list`.

* Remove `SQLExcutor.queryForList`, replaced by `SQLExecutor.list`.

* Improvements and bug fix.


### 1.3.12

* Improvements and bug fix.


### 1.3.11

* Refactoring: `PreparedQuery`.

* Rename `queryForEntity` to `findFirst`.

* Improvements and bug fix.


### 1.3.10

* Add `PreparedQuery`.

* Improvements and bug fix.


### 1.3.9

* Refactoring: `Iterators.skip`.

* Refactoring: `ResultSetExtractor`.

* Add `PreparedQuery`.

* Improvements and bug fix.


### 1.3.8

* Improvements and bug fix.


### 1.3.7

* Improvements and bug fix.


### 1.3.6

* Add `N.toBooleanArray/N.toCharArray/N.toByteArray/N.toShortArray/N.toIntArray/.../N.toDoubleArray`.

* Add `Seq.forEachNonNull/flterThenForEach/mapThenForEach/flatMapThenForEach`.

* Add `Primitives`.

* Refactoring `DataSetBuilder`.

* Improvements and bug fix.


### 1.3.5

* Refactoring `Stream.zip`, `SQLBuilder` and `SQLExecutor.Mapper`.

* Improvements and bug fix.


### 1.3.4

* Add `Stream.split/sliding(...collectionSupplier)`.

* Add `Iterators.skip/limit`.

* Improvements and bug fix.


### 1.3.3

* Refactoring `SQLExecutor.beginTransaction`.

* Improvements and bug fix.


### 1.3.2

* Rename `Multimap.totalCountOfValue` to `Multimap.totalCountOfValues`.

* Refactoring: `EntryStream.map`.

* Add: `EntryStream.removeIf/takeWhile/dropWhile/findFirst/findAny/anyMatch/allMatch/noneMatch/min/minBy/max/maxBy`.

* Improvements and bug fix.


### 1.3.1

* Refactoring `MongoDBExecutor`.

* Improvements and bug fix.


### 1.3

* Rename `CompletableFuture` to `ContinuableFuture`.

* Add `Maps.flatten/unflatten`.

* Refactoring `Collectors.minMax`.

* Improvements and bug fix.


### 1.2.20

* Add `SQLExecutor.Mapper.add(...Collection<String> insertPropNames)`.

* Add `Maps.getAndPutMapIfAbsent`.

* Add `Stream.flattMapToEntry(Function<? super T, ? extends Map<K, V>>)`.

* Refactoring: Change: `EntryStream.flattMap(final Function<? super Map.Entry<K, V>, Collection<Map.Entry<KK, VV>>> mapper)` 
				To :`EntryStream.flattMap(final Function<? super Map.Entry<K, V>, Map<KK, VV>> mapper)`.

* Improvements and bug fix.


### 1.2.19

* Add `N.first/last`, `Seq.firstNonNull/lastNonNull`.

* Add `MongoDBExecutor/CassandraExecutor/CouchbaseExecutor.queryForDate`.

* Add `SQLExecutor.Mapper.exists(conn, id)/batchGet(ids, selectPropNames, batchSize)`.

* Add `N.newBiMap(keyMapSupplier, valueMapSupplier)`.

* Add `N.newListMultimap/newSetMultimap(mapSupplier, valueSupplier)`.

* Add `N.newMultiset(mapSupplier)`.

* Add `Stream.peekFirst/peekLast`.

* Improvements and bug fix.


### 1.2.18

* Add `Seq/IntList/ByteLits/.../DoubleList/Multiset/LongMultiset.ifNotEmpty`.

* Add `Stream.combinations(int len, boolean repeat)`.

* Add `Tuple.anyNull/allNull`.

* Add `Tuple1.toOptional`.

* Add `IntFn/LongFn/DoubleFn/PairFn/TripleFn`.

* Add `SQLExecutor.Mapper.addOrUpdate`.

* Refactoring `SQLExecutor.Mapper` to support composite id.

* Improvements and bug fix.


### 1.2.17

* Add `Stream.countBy/countByToEntry`.

* Add `distinct(Predicate<? super Long> occurrencesFilter)`

* Add `distinctBy(Function<? super T, ?> keyExtractor, Predicate<? super Long> occurrencesFilter)`.

* Refactoring `Futures`.

* Improvements and bug fix.


### 1.2.16

* Support @Transient for `SQLExecutor.Mapper`.

* Improvements and bug fix.


### 1.2.15

* Refactoring: replace `Nullable` with `Optional` in `Collectors` and `Stream` related operations.

* Add `Stream.of(resultSet, columnIndex/columnName)`.

* Add `Collectors.onlyOne/first/last/distinct`.

* Improvements and bug fix.


### 1.2.14

* Add `Stream.ofKeys/ofValues(Map)`.

* Add `Matrix.forEach(Try.IntBiConsumer<E>)`.

* Add `SQLExecutor/Mapper.queryForList`.

* Add `Mapper.update(entity, updateNames)`.

* Improvements and bug fix.


### 1.2.13

* Add `N.swap(Pair/Triple)` and `N.swapIf(Pair/Triple, Predicate)`.

* Add `Seq.filterThenMap/filterThenFlatMap/mapThenFilter/flatMapThenFilter`.

* Remove static method from some functional interfaces in package `com.landawn.abacus.util.function`, replaced by the methods in `Fn`.

* Refactoring: Change `Nullable<T> Holder/Nullable.filterIfNotNull(...)` to `Optional<T> Holder/Nullable.filterIfNotNull(...)`.

* Improvements and bug fix.


### 1.2.12

* Rename `DataSet.join` to `DataSet.innerJoin`.

* Rename `RowIterator.getColumnCount/getColumnLabelList` to `RowIterator.columnCount/columnLabels`.

* Add `Fn.sp/sf/sc` to replace `Synchronized`.

* Improvements and bug fix.


### 1.2.11

* Rename `DataSet.updateColumn(Collection...)/updateRow(int[]...)` to `updateColumnAll/updateRowAll`.

* Rename `N/Iterators.repeatEle/repeatEleToSize` to `N/Iterators.repeatEach/repeatEachToSize`.

* Rename `Maths` to `Matth`.

* Refactoring: change `Stream<IntPair> IntPair.stream()/...` to `IntStream IntPair.stream()/...`.

* Add `DataSet.divideColumn(...BiConsumer)`.

* Add `Fn.not(predicate)/contains`.

* Improvements and bug fix.


### 1.2.10

* Add `Functions.pairToList/pairToSet/tripleToList/tripleToSet`.

* Move `Maps.join` to `StringUtil.join`.

* Move `Joiner.concat` to `StringUtil.concat`.

* Move `N.repeatt` to `StringUtil.repeat`.

* Move a lot of String related operations from `N` to `StringUtil`.

* Remove `N.checkNullOrEmpty`, replaced by `N.checkArgNotNullorEmpty`.

* Remove `N.requireNonNull`, replaced by `N.checkArgNotNull`.

* Remove `XXX.forEach(..., accumulator, conditionToBreak...)`.

* Refactoring: change `DataSet.join/leftJoin/RightJoin/fullJoin(...Class<? extends Collection> collClass)` to `DataSet.join/leftJoin/RightJoin/fullJoin(...IntFunction<? extends Collection> collSupplier)`.

* Stop releasing `abacus-util-all` and `abacus-util-all-jdk7`.

* Improvements and bug fix.


### 1.2.9

* Refactoring: change the return type of `Opitonal.map` from `Optional<U>` to `Nullable<U>`.

* Add `Fn/Collectors.flattMapping`.

* Improvements and bug fix.


### 1.2.8
 
* Add `ByteStream/.../DoubleStream/.flattMapToObj`.

* Add `N.checkArgPositive`.

* Rename `Maps.removeAll(collection)/(map)` to `removeKeys/removeEntries`.

* Rename `Synchronized.run(consumer)/call(function)` to `accept(consumer)/apply(function)`.

* Improvements and bug fix.


### 1.2.7
 
* Add `Optional/OptionalInt.orElseThrow()`.

* Add `Optional/Nullable.toImmutableList/toImmutableSet`. 

* Add `Iterators.forEachPair/forEachTriple`.

* Refactoring: replace `Seq/IntList/BooleanList/ByteList/.../DoubleList.toMap(...Supplier<M> mapFactory)` with `IntFunction<M>`.

* Refactoring: replace `Supplier<Boolean>` with `BooleanSupplier`.

* Refactoring: replace `toList/toSet(supplier)` with `toCollection(supplier)`.

* Refactoring: rename `Stream.removeWhile/remove(n, action)` to `Stream.dropWhile/skip(n, action)`.

* Improvements and bug fix.


### 1.2.6
 
* Add `Comparators.comparingIgnoreCase(final Function<? super T, String> keyExtractor)`.

* Add `Iterators.generate(...)`.

* Add `Fn/IntPredicate/.../Predicate.between(...)`.

* Add `N.toList/toSet/toCollection`.

* Refactoring: move `N.asJUDate/asDate/currentMillis/roll/format` to `DateUtil`.

* Refatroing: change the return type of `N.intersection/difference/distinct/distinctBy/top/...` from `T[]` to `List<T>`.

* Improvements and bug fix.


### 1.2.5

* Remove `Seq.iterate`.

* Rename `N.repeat` to `N.repeatt`.

* Move/Rename `Seq.repeat/repeatt/nRepeat/repeatToSize/nRepeatToSize` to `N.repeat/repeatAll/repeatEle/repeatAllToSize/repeatEleToSize`.

* Move `Seq.iterate/concat/disjoint/merge/zip/unzip/rollup/powset/permutations/orderedPermutations/cartesianProduct/reverse/rotate/shuffle` to `N`.

* Rename `Iterators.repeatt/nRepeat/repeatToSize/nRepeatToSize` to `repeatAll/repeatEle/repeatAllToSize/repeatEleToSize`.

* Add `N.minAll/maxAll`.

* Add `N.checkArgNotNegative`.

* Add `Iterators.forEach/forEachNonNull`.

* Improvements and bug fix.


### 1.2.4

* Remove `Fn.limited`.

* Add `Fn.limitThenFilter` and `Fn.filterThenLimit`.

* Refactoring `condition.In`.

* Refactoring `IF.Or`.

* Improve `LocalTimeType/LocalDateType/LocalDateTimeType`.

* Add `filter/map/mapToObj/flatMap` to `OptionalBoolean/OptionalChar/OptionalByte/.../OptionalDouble`.

* Add `Index`.

* Add `toJdkStream` to `Stream/IntStream/LongStream/DoubleStream`.

* Add `N.checkArgNotNullOrEmpty`.

* Improvements and bug fix.


### 1.2.3

* Add `queryForDate`.

* Add `DataSet.__()`.

* Improvements and bug fix.


### 1.2.2

* Rename `JdbcUtil.absolute` to `JdbcUtil.skip`.

* Add `JdbcUtil.getColumnLabelList`.

* Add `Nullable.mapToBoolean/Char/Byte/.../Double`.

* Add `Nullable.mapToBoolean/Char/Byte/.../DoubleIfNotNull`.

* Add `CassandraExecutor/MongoDBExecutor/CouchbaseExecutor.queryForBoolean/Char/Byte/.../Double/String`.

* Improvements and bug fix.


### 1.2.1

* Remove `Mapper/AsyncMapper.stream(Connection...)`.

* Remove `Try.reader/writer/stream/callable`.

* Remove `N.findAll(..., Function...)`.

* Rename `N.findAllIndices` to `findAllIndicesBetween`, `N.findAll` to `findAllSubstringsBetween`.

* Rename `Splitter.split(..., Function...)` to `Splitter.splitAndThen(..., Function...)`

* Add `N.wrap/unwrap`.

* Add `N.flatMap/flattMap`.

* Add `N.EMPTY_BOOLEAN/CHAR/BYTE/SHORT/INT/../DOUBLE_OBJ_ARRAY`.

* Add `N.sleep/run/callUninterruptibly`, copied from Google Guava.

* Add `Stopwatch/RateLimiter`, copied from Google Guava.

* Improvements and bug fix.


### 1.2.0

* Rename `flatCollection/flatArray` to `flattMap/flatMapp`.

* Add `Array.asList`.

* Add `N.newArrayDeque/isMixedCase/appendIfMissing/prependIfMissing/wrapIfMissing`.

* Rename `N.between` to `N.substringBetween`.

* Add `SafeInitializer`, copied from Apache Commons Lang.

* Improvements and bug fix.


### 1.1.9

* Rename `Mapper.WriteOnly/registerWriteOnlyProps` to `Mapper.NonUpdatable/registerNonUpdatableProps`.

* Rename `iteratee/mergee/parallelMergee/summarizee/summingDoublee/averagingDoublee/reshapee/invokee/onTextChangedd/beforeTextChangedd/afterTextChangedd` to `iteratte/mergge/parallelMergge/summarizze/summingDoubble/averagingDoubble/reshappe/invokke/onTextChangged/beforeTextChangged/afterTextChangged`.

* Add `Mapper.findAll/queryAll/streamAll`.

* Improvements and bug fix.


### 1.1.8

* Add `JdbcUtil.stream/executeBatchUpdate/absolute(...)`.

* Improvements and bug fix.


### 1.1.7

* Add `DataSetUtil`.

* Add `DataSet.rollup/cube`.

* Add `Optional/Nullable<T>.or(Try.Supplier<? extends Optional<T>, E> supplier)`.

* Rename `Math2` to `Maths`.

* Rename `xyz0/1/2(...)` to `xyzz(...)`.

* Rename `DataSet.retainAll/removeAll` to `DataSet.intersectAll/except`.

* Remove `DataSet.sum/averageInt/Long/Double/kthLargest/count/toMultiset/toArray/split`, replaced by `DataSet.stream(...).sum/averageInt/Long/Double/kthLargest/count/toMultiset/toArray/split`.

* Improvements and bug fix.


### 1.1.6

* Add `DataSet.groupBy(..., func)/getOrDefault(columnName)`.

* Add `Collectors.combine(...)`.

* Add `Comparators.comparingByLength/comparingBySize`.

* Rename `Seq/Iterators.repeat(Collection)` to `Seq/Iterators.repeatt(Collection)`.

* Improvements and bug fix.


### 1.1.5

* Remove `Maps.diff`.

* Remove `f.matrix(...)`.

* Remove `Stream.unzip/unzip3`, replaced by `Seq/Iterators.unzip/unzip3`.

* Rename `Fn.indexeD` to `Fn.indexedd`, `Fn.indeXed` to `Fn.indexeed`.

* Rename `SQLExecutor.geT` to `SQLExecutor.gett`.

* Add `Optional<T> MongoDBExecutor/CassandraExecutor/CouchbaseExecutor/SQLiteExecutor.gett(...)`.

* Add `Multiset/LongMultiset/Multimap/ListMultimap/SetMulitmap/BiMap.copy()`.

* Add `Multimap/ListMultimap/SetMultimap.concat(...)`.

* Add `Multimap.toMultiset()`.

* Add `Multimap.totalCountOfValue()`.

* Add `ListMultimap/SetMulitmap.toImmutableMap`.

* Add `N.forEach(int startInclusive, int endExclusive...)`.

* Add `Matrix/IntMatrix.extend(...)`.


### 1.1.4

* Add `Multiset/LongMultiset.toImmutableMap`.

* Add `ImmutableSortedSet/ImmutableNavigableSet/ImmutableSortedMap/ImmutableNavigableMap/ImmutableBiMap`.

* Remove `Multimap.set/setAll/setIf/setAllIf`.

* Rename `Multimap.putAllIfAbsent` to `Multimap.putAllIfKeyAbsent`.

* Add `Multimap.putIfKeyAbsent`.

* Add `OptionalBoolean/OptionalByte/.../OptionalDouble.ofNullable`.

* Add `Optional<T> SQLExecutor.geT(...)`.

* Improvements and bug fix.


### 1.1.3

* Add `N.sumInt/sumLong/sumDouble/averageInt/averageLong/averageDouble` for `Number` type.

* Add `Fn.numToInt/numToLong/numToDouble`.

* Add `Multimap/ListMultimap/SetMultimap.invertFrom/flatInvertFrom`

* Improve `Futures`.

* Improvements and bug fix.


### 1.1.2

* Replace `Predicate/Consumer/Function` in `Iterators` with `Try.Predicate/Consumer/Function`.

* Replace `Predicate/Consumer/Function` in `Stream/.../IntStream.anyMatch/.../findFirst/findLast/...` with `Try.Predicate/Consumer/Function`.

* Rename `Maps.inverse` to `Maps.flatInvert`

* Rename `Fn.Consumers/BiConsumers/TriConsumers/Functions/BiFunctions/TriFunctions/Predicates.of(...)` to `create(...)`

* Remove `Fn.BiFunctions/TriFunctions.ofTuple()`, replaced with `Fn.tuple2()/tuple3()`.

* Remove `MultimapBuilder.removeAll(Collection<? extends K> keysToRemove)`.

* Add `MultimapBuilder.removeAll(K key, Collection<?> valuesToRemove)`.

* Add `Stream.partitionBy/partitionByToEntry/partitionTo`.

* Add `EntryStream/Stream.sortedByInt/sortedByLong/sortedByDouble`.

* Add `EntryStream.flatCollectionKey/flatCollectionValue`.

* Add `N.ifOrEmpty`.

* Add `If/IF`.

* Improvements and bug fix.


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
