/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.carbondata.core.scan.filter.executer;

import java.io.IOException;
import java.util.BitSet;

import org.apache.carbondata.core.constants.CarbonCommonConstants;
import org.apache.carbondata.core.datastore.block.SegmentProperties;
import org.apache.carbondata.core.datastore.chunk.DimensionColumnPage;
import org.apache.carbondata.core.datastore.chunk.impl.DimensionRawColumnChunk;
import org.apache.carbondata.core.datastore.chunk.impl.MeasureRawColumnChunk;
import org.apache.carbondata.core.datastore.page.ColumnPage;
import org.apache.carbondata.core.metadata.datatype.DataType;
import org.apache.carbondata.core.metadata.datatype.DataTypes;
import org.apache.carbondata.core.scan.filter.FilterExecutorUtil;
import org.apache.carbondata.core.scan.filter.FilterUtil;
import org.apache.carbondata.core.scan.filter.intf.FilterExecuterType;
import org.apache.carbondata.core.scan.filter.intf.RowIntf;
import org.apache.carbondata.core.scan.filter.resolver.resolverinfo.DimColumnResolvedFilterInfo;
import org.apache.carbondata.core.scan.filter.resolver.resolverinfo.MeasureColumnResolvedFilterInfo;
import org.apache.carbondata.core.scan.processor.RawBlockletColumnChunks;
import org.apache.carbondata.core.util.BitSetGroup;
import org.apache.carbondata.core.util.ByteUtil;
import org.apache.carbondata.core.util.CarbonUtil;
import org.apache.carbondata.core.util.DataTypeUtil;
import org.apache.carbondata.core.util.comparator.Comparator;
import org.apache.carbondata.core.util.comparator.SerializableComparator;

public class IncludeFilterExecuterImpl implements FilterExecuter {

  protected DimColumnResolvedFilterInfo dimColumnEvaluatorInfo;
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-2099
  DimColumnExecuterFilterInfo dimColumnExecuterInfo;
  private MeasureColumnResolvedFilterInfo msrColumnEvaluatorInfo;
  private MeasureColumnExecuterFilterInfo msrColumnExecutorInfo;
  protected SegmentProperties segmentProperties;
  private boolean isDimensionPresentInCurrentBlock = false;
  private boolean isMeasurePresentInCurrentBlock = false;
  protected SerializableComparator comparator;
  /**
   * is dimension column data is natural sorted
   */
  private boolean isNaturalSorted = false;

  private byte[][] filterValues;

  private FilterBitSetUpdater filterBitSetUpdater;

  public IncludeFilterExecuterImpl(byte[][] filterValues, boolean isNaturalSorted) {
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-2589
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-2590
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-2602
    this.filterValues = filterValues;
    this.isNaturalSorted = isNaturalSorted;
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-3373
    this.filterBitSetUpdater =
        BitSetUpdaterFactory.INSTANCE.getBitSetUpdater(FilterExecuterType.INCLUDE);
  }

  public IncludeFilterExecuterImpl(DimColumnResolvedFilterInfo dimColumnEvaluatorInfo,
      MeasureColumnResolvedFilterInfo msrColumnEvaluatorInfo, SegmentProperties segmentProperties,
      boolean isMeasure) {
    this.filterBitSetUpdater =
        BitSetUpdaterFactory.INSTANCE.getBitSetUpdater(FilterExecuterType.INCLUDE);
    this.segmentProperties = segmentProperties;
    if (!isMeasure) {
      this.dimColumnEvaluatorInfo = dimColumnEvaluatorInfo;
      dimColumnExecuterInfo = new DimColumnExecuterFilterInfo();
      FilterUtil
          .prepareKeysFromSurrogates(dimColumnEvaluatorInfo.getFilterValues(), segmentProperties,
              dimColumnEvaluatorInfo.getDimension(), dimColumnExecuterInfo, null, null);
      isDimensionPresentInCurrentBlock = true;
      isNaturalSorted =
          dimColumnEvaluatorInfo.getDimension().isUseInvertedIndex() && dimColumnEvaluatorInfo
              .getDimension().isSortColumn();

    } else {
      this.msrColumnEvaluatorInfo = msrColumnEvaluatorInfo;
      msrColumnExecutorInfo = new MeasureColumnExecuterFilterInfo();
      comparator =
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-2948
          Comparator.getComparatorByDataTypeForMeasure(
              FilterUtil.getMeasureDataType(msrColumnEvaluatorInfo));
      FilterUtil
          .prepareKeysFromSurrogates(msrColumnEvaluatorInfo.getFilterValues(), segmentProperties,
              null, null, msrColumnEvaluatorInfo.getMeasure(), msrColumnExecutorInfo);
      isMeasurePresentInCurrentBlock = true;
    }

  }

  @Override
  public BitSetGroup applyFilter(RawBlockletColumnChunks rawBlockletColumnChunks,
      boolean useBitsetPipeLine) throws IOException {
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-2099
    if (isDimensionPresentInCurrentBlock) {
      int chunkIndex = segmentProperties.getDimensionOrdinalToChunkMapping()
          .get(dimColumnEvaluatorInfo.getColumnIndex());
      if (null == rawBlockletColumnChunks.getDimensionRawColumnChunks()[chunkIndex]) {
        rawBlockletColumnChunks.getDimensionRawColumnChunks()[chunkIndex] =
            rawBlockletColumnChunks.getDataBlock().readDimensionChunk(
                rawBlockletColumnChunks.getFileReader(), chunkIndex);
      }
      DimensionRawColumnChunk dimensionRawColumnChunk =
          rawBlockletColumnChunks.getDimensionRawColumnChunks()[chunkIndex];
      BitSetGroup bitSetGroup = new BitSetGroup(dimensionRawColumnChunk.getPagesCount());
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-2760
      filterValues = dimColumnExecuterInfo.getFilterKeys();
      boolean isDecoded = false;
      for (int i = 0; i < dimensionRawColumnChunk.getPagesCount(); i++) {
        if (dimensionRawColumnChunk.getMaxValues() != null) {
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-3013
          if (isScanRequired(dimensionRawColumnChunk, i)) {
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-2589
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-2590
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-2602
            DimensionColumnPage dimensionColumnPage = dimensionRawColumnChunk.decodeColumnPage(i);
            if (!isDecoded) {
              filterValues =  FilterUtil
                  .getEncodedFilterValues(dimensionRawColumnChunk.getLocalDictionary(),
                      dimColumnExecuterInfo.getFilterKeys());
              isDecoded = true;
            }
            BitSet bitSet = getFilteredIndexes(dimensionColumnPage,
                dimensionRawColumnChunk.getRowCount()[i], useBitsetPipeLine,
                rawBlockletColumnChunks.getBitSetGroup(), i);
            bitSetGroup.setBitSet(bitSet, i);
          }
        } else {
          BitSet bitSet = getFilteredIndexes(dimensionRawColumnChunk.decodeColumnPage(i),
              dimensionRawColumnChunk.getRowCount()[i], useBitsetPipeLine,
              rawBlockletColumnChunks.getBitSetGroup(), i);
          bitSetGroup.setBitSet(bitSet, i);
        }
      }
      return bitSetGroup;
    } else if (isMeasurePresentInCurrentBlock) {
      int chunkIndex = segmentProperties.getMeasuresOrdinalToChunkMapping()
          .get(msrColumnEvaluatorInfo.getColumnIndex());
      if (null == rawBlockletColumnChunks.getMeasureRawColumnChunks()[chunkIndex]) {
        rawBlockletColumnChunks.getMeasureRawColumnChunks()[chunkIndex] =
            rawBlockletColumnChunks.getDataBlock().readMeasureChunk(
                rawBlockletColumnChunks.getFileReader(), chunkIndex);
      }
      MeasureRawColumnChunk measureRawColumnChunk =
          rawBlockletColumnChunks.getMeasureRawColumnChunks()[chunkIndex];
      BitSetGroup bitSetGroup = new BitSetGroup(measureRawColumnChunk.getPagesCount());
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-2948
      DataType msrType = FilterUtil.getMeasureDataType(msrColumnEvaluatorInfo);
      for (int i = 0; i < measureRawColumnChunk.getPagesCount(); i++) {
        if (measureRawColumnChunk.getMaxValues() != null) {
          if (isScanRequired(measureRawColumnChunk.getMaxValues()[i],
              measureRawColumnChunk.getMinValues()[i], msrColumnExecutorInfo.getFilterKeys(),
              msrColumnEvaluatorInfo.getType())) {
            BitSet bitSet =
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-2099
                getFilteredIndexesForMeasure(measureRawColumnChunk.decodeColumnPage(i),
                    measureRawColumnChunk.getRowCount()[i], useBitsetPipeLine,
                    rawBlockletColumnChunks.getBitSetGroup(), i, msrType);
            bitSetGroup.setBitSet(bitSet, i);
          }
        } else {
          BitSet bitSet =
              getFilteredIndexesForMeasure(measureRawColumnChunk.decodeColumnPage(i),
                  measureRawColumnChunk.getRowCount()[i], useBitsetPipeLine,
                  rawBlockletColumnChunks.getBitSetGroup(), i, msrType);
          bitSetGroup.setBitSet(bitSet, i);
        }
      }
      return bitSetGroup;
    }
    return null;
  }

  private boolean isScanRequired(DimensionRawColumnChunk dimensionRawColumnChunk, int columnIndex) {
    boolean scanRequired;
    // for no dictionary measure column comparison can be done
    // on the original data as like measure column
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-3013
    if (DataTypeUtil.isPrimitiveColumn(dimColumnEvaluatorInfo.getDimension().getDataType())
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-3674
        && dimColumnEvaluatorInfo.getDimension().getDataType() != DataTypes.DATE) {
      scanRequired = isScanRequired(dimensionRawColumnChunk.getMaxValues()[columnIndex],
          dimensionRawColumnChunk.getMinValues()[columnIndex],
          dimColumnExecuterInfo.getFilterKeys(),
          dimColumnEvaluatorInfo.getDimension().getDataType());
    } else {
      scanRequired = isScanRequired(dimensionRawColumnChunk.getMaxValues()[columnIndex],
          dimensionRawColumnChunk.getMinValues()[columnIndex],
          dimColumnExecuterInfo.getFilterKeys(),
          dimensionRawColumnChunk.getMinMaxFlagArray()[columnIndex]);
    }
    return scanRequired;
  }

  @Override
  public BitSet prunePages(RawBlockletColumnChunks rawBlockletColumnChunks)
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-3575
      throws IOException {
    if (isDimensionPresentInCurrentBlock) {
      int chunkIndex = segmentProperties.getDimensionOrdinalToChunkMapping()
          .get(dimColumnEvaluatorInfo.getColumnIndex());
      if (null == rawBlockletColumnChunks.getDimensionRawColumnChunks()[chunkIndex]) {
        rawBlockletColumnChunks.getDimensionRawColumnChunks()[chunkIndex] =
            rawBlockletColumnChunks.getDataBlock()
                .readDimensionChunk(rawBlockletColumnChunks.getFileReader(), chunkIndex);
      }
      DimensionRawColumnChunk dimensionRawColumnChunk =
          rawBlockletColumnChunks.getDimensionRawColumnChunks()[chunkIndex];
      filterValues = dimColumnExecuterInfo.getFilterKeys();
      BitSet bitSet = new BitSet(dimensionRawColumnChunk.getPagesCount());
      for (int i = 0; i < dimensionRawColumnChunk.getPagesCount(); i++) {
        if (dimensionRawColumnChunk.getMaxValues() != null) {
          if (isScanRequired(dimensionRawColumnChunk, i)) {
            bitSet.set(i);
          }
        } else {
          bitSet.set(i);
        }
      }
      return bitSet;
    } else if (isMeasurePresentInCurrentBlock) {
      int chunkIndex = segmentProperties.getMeasuresOrdinalToChunkMapping()
          .get(msrColumnEvaluatorInfo.getColumnIndex());
      if (null == rawBlockletColumnChunks.getMeasureRawColumnChunks()[chunkIndex]) {
        rawBlockletColumnChunks.getMeasureRawColumnChunks()[chunkIndex] =
            rawBlockletColumnChunks.getDataBlock()
                .readMeasureChunk(rawBlockletColumnChunks.getFileReader(), chunkIndex);
      }
      MeasureRawColumnChunk measureRawColumnChunk =
          rawBlockletColumnChunks.getMeasureRawColumnChunks()[chunkIndex];
      BitSet bitSet = new BitSet(measureRawColumnChunk.getPagesCount());
      for (int i = 0; i < measureRawColumnChunk.getPagesCount(); i++) {
        if (measureRawColumnChunk.getMaxValues() != null) {
          if (isScanRequired(measureRawColumnChunk.getMaxValues()[i],
              measureRawColumnChunk.getMinValues()[i], msrColumnExecutorInfo.getFilterKeys(),
              msrColumnEvaluatorInfo.getType())) {
            bitSet.set(i);
          }
        } else {
          bitSet.set(i);
        }
      }
      return bitSet;
    }
    return null;
  }

  @Override
  public boolean applyFilter(RowIntf value, int dimOrdinalMax) {
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-1572
    if (isDimensionPresentInCurrentBlock) {
      byte[][] filterValues = dimColumnExecuterInfo.getFilterKeys();
      byte[] col = (byte[])value.getVal(dimColumnEvaluatorInfo.getDimension().getOrdinal());
      for (int i = 0; i < filterValues.length; i++) {
        if (0 == ByteUtil.UnsafeComparer.INSTANCE.compareTo(col, 0, col.length,
            filterValues[i], 0, filterValues[i].length)) {
          return true;
        }
      }
    } else if (isMeasurePresentInCurrentBlock) {
      Object[] filterValues = msrColumnExecutorInfo.getFilterKeys();
      Object col = value.getVal(msrColumnEvaluatorInfo.getMeasure().getOrdinal() + dimOrdinalMax);
      for (int i = 0; i < filterValues.length; i++) {
        if (filterValues[i] == null) {
          if (null == col) {
            return true;
          }
          continue;
        }
        if (comparator.compare(col, filterValues[i]) == 0) {
          return true;
        }
      }
    }
    return false;
  }

  private BitSet getFilteredIndexesForMeasures(ColumnPage columnPage,
      int rowsInPage, DataType msrType) {
    // Here the algorithm is
    // Get the measure values from the chunk. compare sequentially with the
    // the filter values. The one that matches sets it Bitset.
    BitSet bitSet = new BitSet(rowsInPage);
    FilterExecutorUtil.executeIncludeExcludeFilterForMeasure(columnPage, bitSet,
        msrColumnExecutorInfo, msrColumnEvaluatorInfo, filterBitSetUpdater);
    return bitSet;
  }

  /**
   * Below method will be used to apply filter on measure column
   * @param measureColumnPage
   * @param numberOfRows
   * @param useBitsetPipeLine
   * @param prvBitSetGroup
   * @param pageNumber
   * @param msrDataType
   * @return filtered indexes bitset
   */
  private BitSet getFilteredIndexesForMeasure(ColumnPage measureColumnPage, int numberOfRows,
      boolean useBitsetPipeLine, BitSetGroup prvBitSetGroup, int pageNumber, DataType msrDataType) {
    // check whether previous indexes can be optimal to apply filter on measure column
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-1538
    if (CarbonUtil.usePreviousFilterBitsetGroup(useBitsetPipeLine, prvBitSetGroup, pageNumber,
        msrColumnExecutorInfo.getFilterKeys().length)) {
      return getFilteredIndexesForMsrUsingPrvBitSet(measureColumnPage, prvBitSetGroup, pageNumber,
          numberOfRows, msrDataType);
    } else {
      return getFilteredIndexesForMeasures(measureColumnPage, numberOfRows, msrDataType);
    }
  }

  /**
   * Below method will be used to apply filter on measure column based on previous filtered indexes
   * @param measureColumnPage
   * @param prvBitSetGroup
   * @param pageNumber
   * @param numberOfRows
   * @param msrDataType
   * @return filtred indexes bitset
   */
  private BitSet getFilteredIndexesForMsrUsingPrvBitSet(ColumnPage measureColumnPage,
      BitSetGroup prvBitSetGroup, int pageNumber, int numberOfRows, DataType msrDataType) {
    BitSet bitSet = new BitSet(numberOfRows);
    Object[] filterValues = msrColumnExecutorInfo.getFilterKeys();
    BitSet nullBitSet = measureColumnPage.getNullBits();
    BitSet prvPageBitSet = prvBitSetGroup.getBitSet(pageNumber);
    SerializableComparator comparator = Comparator.getComparatorByDataTypeForMeasure(msrDataType);
    for (int i = 0; i < filterValues.length; i++) {
      if (filterValues[i] == null) {
        for (int j = nullBitSet.nextSetBit(0); j >= 0; j = nullBitSet.nextSetBit(j + 1)) {
          bitSet.set(j);
        }
        continue;
      }
      for (int index = prvPageBitSet.nextSetBit(0);
           index >= 0; index = prvPageBitSet.nextSetBit(index + 1)) {
        if (!nullBitSet.get(index)) {
          // Check if filterValue[i] matches with measure Values.
          Object msrValue = DataTypeUtil
              .getMeasureObjectBasedOnDataType(measureColumnPage, index,
                  msrDataType, msrColumnEvaluatorInfo.getMeasure());

          if (comparator.compare(msrValue, filterValues[i]) == 0) {
            // This is a match.
            bitSet.set(index);
          }
        }
      }
    }
    return bitSet;
  }

  /**
   * Below method will be used to apply filter on dimension column
   * @param dimensionColumnPage
   * @param numberOfRows
   * @param useBitsetPipeLine
   * @param prvBitSetGroup
   * @param pageNumber
   * @return filtered indexes bitset
   */
  protected BitSet getFilteredIndexes(DimensionColumnPage dimensionColumnPage,
      int numberOfRows, boolean useBitsetPipeLine, BitSetGroup prvBitSetGroup, int pageNumber) {
    // check whether previous indexes can be optimal to apply filter on dimension column
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-2589
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-2590
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-2602
    if (filterValues.length > 0 && CarbonUtil
        .usePreviousFilterBitsetGroup(useBitsetPipeLine, prvBitSetGroup, pageNumber,
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-2589
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-2590
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-2602
            filterValues.length)) {
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-2099
      return getFilteredIndexesUisngPrvBitset(dimensionColumnPage, prvBitSetGroup, pageNumber,
          numberOfRows);
    } else {
      return getFilteredIndexes(dimensionColumnPage, numberOfRows);
    }
  }

  private BitSet getFilteredIndexes(DimensionColumnPage dimensionColumnPage,
      int numberOfRows) {
    if (dimensionColumnPage.isExplicitSorted()) {
      return setFilterdIndexToBitSetWithColumnIndex(dimensionColumnPage, numberOfRows);
    }
    return setFilterdIndexToBitSet(dimensionColumnPage, numberOfRows);
  }

  /**
   * Below method will be used to apply filter on dimension
   * column based on previous filtered indexes
   * @param dimensionColumnPage
   * @param prvBitSetGroup
   * @param pageNumber
   * @param numberOfRows
   * @return filtered bitset
   */
  private BitSet getFilteredIndexesUisngPrvBitset(DimensionColumnPage dimensionColumnPage,
      BitSetGroup prvBitSetGroup, int pageNumber, int numberOfRows) {
    BitSet prvPageBitSet = prvBitSetGroup.getBitSet(pageNumber);
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-1544
    if (prvPageBitSet == null || prvPageBitSet.isEmpty()) {
      return prvPageBitSet;
    }
    BitSet bitSet = new BitSet(numberOfRows);
    int compareResult = 0;
    // if dimension data was natural sorted then get the index from previous bitset
    // and use the same in next column data, otherwise use the inverted index reverse
    if (!dimensionColumnPage.isExplicitSorted()) {
      for (int index = prvPageBitSet.nextSetBit(0);
           index >= 0; index = prvPageBitSet.nextSetBit(index + 1)) {
        compareResult = CarbonUtil
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-2589
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-2590
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-2602
            .isFilterPresent(filterValues, dimensionColumnPage, 0, filterValues.length - 1, index);
        if (compareResult == 0) {
          bitSet.set(index);
        }
      }
    } else {
      for (int index = prvPageBitSet.nextSetBit(0);
           index >= 0; index = prvPageBitSet.nextSetBit(index + 1)) {
        compareResult = CarbonUtil
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-2589
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-2590
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-2602
            .isFilterPresent(filterValues, dimensionColumnPage, 0, filterValues.length - 1,
                dimensionColumnPage.getInvertedReverseIndex(index));
        if (compareResult == 0) {
          bitSet.set(index);
        }
      }
    }
    return bitSet;
  }

  private BitSet setFilterdIndexToBitSetWithColumnIndex(
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-2099
      DimensionColumnPage dimensionColumnPage, int numerOfRows) {
    BitSet bitSet = new BitSet(numerOfRows);
    if (filterValues.length == 0) {
      return bitSet;
    }
    int startIndex = 0;
    for (int i = 0; i < filterValues.length; i++) {
      if (startIndex >= numerOfRows) {
        break;
      }
      int[] rangeIndex = CarbonUtil
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-2099
          .getRangeIndexUsingBinarySearch(dimensionColumnPage, startIndex, numerOfRows - 1,
              filterValues[i]);
      for (int j = rangeIndex[0]; j <= rangeIndex[1]; j++) {
        bitSet.set(dimensionColumnPage.getInvertedIndex(j));
      }
      if (rangeIndex[1] >= 0) {
        startIndex = rangeIndex[1] + 1;
      }
    }
    return bitSet;
  }

  private BitSet setFilterdIndexToBitSet(DimensionColumnPage dimensionColumnPage,
      int numerOfRows) {
    BitSet bitSet = new BitSet(numerOfRows);
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-2589
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-2590
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-2602
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-2589
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-2590
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-2602
    if (filterValues.length == 0) {
      return bitSet;
    }
    // binary search can only be applied if column is sorted and
    // inverted index exists for that column
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-2896
    if (isNaturalSorted && dimensionColumnPage.isExplicitSorted()) {
      int startIndex = 0;
      for (int i = 0; i < filterValues.length; i++) {
        if (startIndex >= numerOfRows) {
          break;
        }
        int[] rangeIndex = CarbonUtil
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-2099
            .getRangeIndexUsingBinarySearch(dimensionColumnPage, startIndex, numerOfRows - 1,
                filterValues[i]);
        for (int j = rangeIndex[0]; j <= rangeIndex[1]; j++) {
          bitSet.set(j);
        }
        if (rangeIndex[1] >= 0) {
          startIndex = rangeIndex[1] + 1;
        }
      }
    } else {
      if (filterValues.length > 1) {
        for (int i = 0; i < numerOfRows; i++) {
          int index = CarbonUtil.binarySearch(filterValues, 0, filterValues.length - 1,
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-2589
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-2590
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-2602
              dimensionColumnPage, i);
          if (index >= 0) {
            bitSet.set(i);
          }
        }
      } else {
        for (int j = 0; j < numerOfRows; j++) {
          if (dimensionColumnPage.compareTo(j, filterValues[0]) == 0) {
            bitSet.set(j);
          }
        }
      }
    }
    return bitSet;
  }

  @Override
  public BitSet isScanRequired(byte[][] blkMaxVal, byte[][] blkMinVal, boolean[] isMinMaxSet) {
    BitSet bitSet = new BitSet(1);
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-2589
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-2590
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-2602
    byte[][] filterValues;
    int chunkIndex = 0;
    boolean isScanRequired = false;

//IC see: https://issues.apache.org/jira/browse/CARBONDATA-1353
    if (isDimensionPresentInCurrentBlock) {
      filterValues = dimColumnExecuterInfo.getFilterKeys();
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-2649
      chunkIndex = dimColumnEvaluatorInfo.getColumnIndexInMinMaxByteArray();
      // for no dictionary measure column comparison can be done
      // on the original data as like measure column
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-3674
      if (DataTypeUtil.isPrimitiveColumn(dimColumnEvaluatorInfo.getDimension().getDataType()) &&
          dimColumnEvaluatorInfo.getDimension().getDataType() != DataTypes.DATE) {
        isScanRequired = isScanRequired(blkMaxVal[chunkIndex], blkMinVal[chunkIndex], filterValues,
            dimColumnEvaluatorInfo.getDimension().getDataType());
      } else {
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-2942
        isScanRequired = isScanRequired(blkMaxVal[chunkIndex], blkMinVal[chunkIndex], filterValues,
          isMinMaxSet[chunkIndex]);
      }
    } else if (isMeasurePresentInCurrentBlock) {
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-3353
      chunkIndex = msrColumnEvaluatorInfo.getColumnIndexInMinMaxByteArray();
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-3353
      if (isMinMaxSet[chunkIndex]) {
        isScanRequired = isScanRequired(blkMaxVal[chunkIndex], blkMinVal[chunkIndex],
            msrColumnExecutorInfo.getFilterKeys(), msrColumnEvaluatorInfo.getType());
      } else {
        isScanRequired = true;
      }
    }

    if (isScanRequired) {
      bitSet.set(0);
    }
    return bitSet;
  }

  private boolean isScanRequired(byte[] blkMaxVal, byte[] blkMinVal, byte[][] filterValues,
      boolean isMinMaxSet) {
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-2942
    if (!isMinMaxSet) {
      // scan complete data if min max is not written for a given column
      return true;
    }
    boolean isScanRequired = false;
    for (int k = 0; k < filterValues.length; k++) {
      // filter value should be in range of max and min value i.e
      // max>filtervalue>min
      // so filter-max should be negative
      int maxCompare =
          ByteUtil.UnsafeComparer.INSTANCE.compareTo(filterValues[k], blkMaxVal);
      // and filter-min should be positive
      int minCompare =
          ByteUtil.UnsafeComparer.INSTANCE.compareTo(filterValues[k], blkMinVal);

      // if any filter value is in range than this block needs to be
      // scanned
      if (maxCompare <= 0 && minCompare >= 0) {
        isScanRequired = true;
        break;
      }
    }
    return isScanRequired;
  }

  private boolean isScanRequired(byte[] blkMaxVal, byte[] blkMinVal, byte[][] filterValues,
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-2896
      DataType dataType) {
    boolean isScanRequired = false;
    Object minValue = DataTypeUtil.getDataBasedOnDataTypeForNoDictionaryColumn(blkMinVal, dataType);
    Object maxValue = DataTypeUtil.getDataBasedOnDataTypeForNoDictionaryColumn(blkMaxVal, dataType);
    for (int k = 0; k < filterValues.length; k++) {
      if (ByteUtil.UnsafeComparer.INSTANCE
          .compareTo(filterValues[k], CarbonCommonConstants.EMPTY_BYTE_ARRAY) == 0) {
        return true;
      }
      // filter value should be in range of max and min value i.e
      // max>filtervalue>min
      // so filter-max should be negative
      Object data =
          DataTypeUtil.getDataBasedOnDataTypeForNoDictionaryColumn(filterValues[k], dataType);
      SerializableComparator comparator = Comparator.getComparator(dataType);
      int maxCompare = comparator.compare(data, maxValue);
      int minCompare = comparator.compare(data, minValue);
      // if any filter value is in range than this block needs to be
      // scanned
      if (maxCompare <= 0 && minCompare >= 0) {
        isScanRequired = true;
        break;
      }
    }
    return isScanRequired;
  }

  private boolean isScanRequired(byte[] maxValue, byte[] minValue, Object[] filterValue,
      DataType dataType) {
    Object maxObject = DataTypeUtil.getMeasureObjectFromDataType(maxValue, dataType);
    Object minObject = DataTypeUtil.getMeasureObjectFromDataType(minValue, dataType);
    for (int i = 0; i < filterValue.length; i++) {
      // TODO handle min and max for null values.
      if (filterValue[i] == null) {
        return true;
      }
      if (comparator.compare(filterValue[i], maxObject) <= 0
          && comparator.compare(filterValue[i], minObject) >= 0) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void readColumnChunks(RawBlockletColumnChunks rawBlockletColumnChunks) throws IOException {
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-2099
    if (isDimensionPresentInCurrentBlock) {
      int chunkIndex = segmentProperties.getDimensionOrdinalToChunkMapping()
          .get(dimColumnEvaluatorInfo.getColumnIndex());
      if (null == rawBlockletColumnChunks.getDimensionRawColumnChunks()[chunkIndex]) {
        rawBlockletColumnChunks.getDimensionRawColumnChunks()[chunkIndex] =
            rawBlockletColumnChunks.getDataBlock().readDimensionChunk(
                rawBlockletColumnChunks.getFileReader(), chunkIndex);
      }
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-1353
    } else if (isMeasurePresentInCurrentBlock) {
      int chunkIndex = segmentProperties.getMeasuresOrdinalToChunkMapping()
          .get(msrColumnEvaluatorInfo.getColumnIndex());
      if (null == rawBlockletColumnChunks.getMeasureRawColumnChunks()[chunkIndex]) {
        rawBlockletColumnChunks.getMeasureRawColumnChunks()[chunkIndex] =
            rawBlockletColumnChunks.getDataBlock().readMeasureChunk(
                rawBlockletColumnChunks.getFileReader(), chunkIndex);
      }
    }
  }
}
