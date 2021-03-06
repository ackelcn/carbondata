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

package org.apache.carbondata.core.datastore.chunk.store;

import org.apache.carbondata.core.constants.CarbonCommonConstants;
import org.apache.carbondata.core.datastore.chunk.store.impl.LocalDictDimensionDataChunkStore;
import org.apache.carbondata.core.datastore.chunk.store.impl.safe.SafeFixedLengthDimensionDataChunkStore;
import org.apache.carbondata.core.datastore.chunk.store.impl.safe.SafeVariableIntLengthDimensionDataChunkStore;
import org.apache.carbondata.core.datastore.chunk.store.impl.safe.SafeVariableShortLengthDimensionDataChunkStore;
import org.apache.carbondata.core.datastore.chunk.store.impl.unsafe.UnsafeFixedLengthDimensionDataChunkStore;
import org.apache.carbondata.core.datastore.chunk.store.impl.unsafe.UnsafeVariableIntLengthDimensionDataChunkStore;
import org.apache.carbondata.core.datastore.chunk.store.impl.unsafe.UnsafeVariableShortLengthDimensionDataChunkStore;
import org.apache.carbondata.core.scan.result.vector.CarbonDictionary;
import org.apache.carbondata.core.util.CarbonProperties;

/**
 * Below class will be used to get the dimension store type
 */
public class DimensionChunkStoreFactory {

  /**
   * store factory instance
   */
  public static final DimensionChunkStoreFactory INSTANCE = new DimensionChunkStoreFactory();

  /**
   * is unsafe
   */
  private static final boolean isUnsafe;

  static {
    isUnsafe = Boolean.parseBoolean(CarbonProperties.getInstance()
        .getProperty(CarbonCommonConstants.ENABLE_UNSAFE_IN_QUERY_EXECUTION,
            CarbonCommonConstants.ENABLE_UNSAFE_IN_QUERY_EXECUTION_DEFAULTVALUE));
  }

  private DimensionChunkStoreFactory() {

  }

  /**
   * Below method will be used to get the dimension store type
   *
   * @param columnValueSize column value size
   * @param isInvertedIndex is inverted index
   * @param numberOfRows    number of rows
   * @param totalSize       total size of data
   * @param storeType       store type
   * @return dimension store type
   */
  public DimensionDataChunkStore getDimensionChunkStore(int columnValueSize,
      boolean isInvertedIndex, int numberOfRows, long totalSize, DimensionStoreType storeType,
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-3113
      CarbonDictionary dictionary, boolean fillDirectVector, int dataLength) {
    if (isUnsafe && !fillDirectVector) {
      switch (storeType) {
        case FIXED_LENGTH:
          return new UnsafeFixedLengthDimensionDataChunkStore(totalSize, columnValueSize,
              isInvertedIndex, numberOfRows, dataLength);
        case VARIABLE_SHORT_LENGTH:
          return new UnsafeVariableShortLengthDimensionDataChunkStore(totalSize, isInvertedIndex,
              numberOfRows, dataLength);
        case VARIABLE_INT_LENGTH:
          return new UnsafeVariableIntLengthDimensionDataChunkStore(totalSize, isInvertedIndex,
              numberOfRows, dataLength);
        case LOCAL_DICT:
          return new LocalDictDimensionDataChunkStore(
              new UnsafeFixedLengthDimensionDataChunkStore(totalSize, 3, isInvertedIndex,
                  numberOfRows, dataLength), dictionary, dataLength);
        default:
          throw new UnsupportedOperationException("Invalid dimension store type");
      }
    } else {
      switch (storeType) {
        case FIXED_LENGTH:
          return new SafeFixedLengthDimensionDataChunkStore(isInvertedIndex, columnValueSize,
              numberOfRows);
        case VARIABLE_SHORT_LENGTH:
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-3113
          return new SafeVariableShortLengthDimensionDataChunkStore(isInvertedIndex, numberOfRows,
              dataLength);
        case VARIABLE_INT_LENGTH:
          return new SafeVariableIntLengthDimensionDataChunkStore(isInvertedIndex, numberOfRows,
              dataLength);
        case LOCAL_DICT:
          return new LocalDictDimensionDataChunkStore(
              new SafeFixedLengthDimensionDataChunkStore(isInvertedIndex, 3, numberOfRows),
              dictionary, dataLength);
        default:
          throw new UnsupportedOperationException("Invalid dimension store type");
      }
    }
  }

  /**
   * dimension store type enum
   */
  public enum DimensionStoreType {
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-2589
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-2590
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-2602
    FIXED_LENGTH, VARIABLE_SHORT_LENGTH, VARIABLE_INT_LENGTH, LOCAL_DICT;
  }
}
