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

package org.apache.carbondata.core.scan.complextypes;

import java.io.IOException;

import org.apache.carbondata.core.datastore.chunk.DimensionColumnPage;
import org.apache.carbondata.core.datastore.chunk.impl.DimensionRawColumnChunk;
import org.apache.carbondata.core.scan.processor.RawBlockletColumnChunks;

public class ComplexQueryType {
  protected String name;

  protected String parentName;

  protected int columnIndex;

  public ComplexQueryType(String name, String parentName, int columnIndex) {
    this.name = name;
    this.parentName = parentName;
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-3684
    this.columnIndex = columnIndex;
  }

  /**
   * Method will copy the block chunk holder data and return the cloned value.
   * This method is also used by child.
   */
  protected byte[] copyBlockDataChunk(DimensionRawColumnChunk[] rawColumnChunks,
      DimensionColumnPage[][] dimensionColumnPages, int rowNumber, int pageNumber) {
    byte[] data =
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-3684
        getDecodedDimensionPage(dimensionColumnPages, rawColumnChunks[columnIndex], pageNumber)
            .getChunkData(rowNumber);
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-1400
    byte[] output = new byte[data.length];
    System.arraycopy(data, 0, output, 0, output.length);
    return output;
  }

  /*
   * This method will read the block data chunk from the respective block
   */
  protected void readBlockDataChunk(RawBlockletColumnChunks blockChunkHolder) throws IOException {
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-3684
    if (null == blockChunkHolder.getDimensionRawColumnChunks()[columnIndex]) {
      blockChunkHolder.getDimensionRawColumnChunks()[columnIndex] = blockChunkHolder.getDataBlock()
          .readDimensionChunk(blockChunkHolder.getFileReader(), columnIndex);
    }
  }

  private DimensionColumnPage getDecodedDimensionPage(DimensionColumnPage[][] dimensionColumnPages,
      DimensionRawColumnChunk dimensionRawColumnChunk, int pageNumber) {
    if (dimensionColumnPages == null || null == dimensionColumnPages[columnIndex]) {
      return dimensionRawColumnChunk.decodeColumnPage(pageNumber);
    }
    return dimensionColumnPages[columnIndex][pageNumber];
  }
}
