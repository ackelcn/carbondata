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

package org.apache.carbondata.presto.readers;

import org.apache.carbondata.core.metadata.datatype.DataType;
import org.apache.carbondata.core.scan.result.vector.impl.CarbonColumnVectorImpl;

import com.facebook.presto.spi.block.Block;
import com.facebook.presto.spi.block.BlockBuilder;
import com.facebook.presto.spi.type.AbstractType;
import com.facebook.presto.spi.type.RealType;

/**
 * Class for Reading the Float(real) value and setting it in Block
 */
public class FloatStreamReader extends CarbonColumnVectorImpl implements PrestoVectorBlockBuilder {

  protected int batchSize;

  protected AbstractType type = RealType.REAL;

  protected BlockBuilder builder;

  public FloatStreamReader(int batchSize, DataType dataType) {
    super(batchSize, dataType);
    this.batchSize = batchSize;
    this.builder = type.createBlockBuilder(null, batchSize);
  }

  @Override
  public Block buildBlock() {
    return builder.build();
  }

  @Override
  public void setBatchSize(int batchSize) {
    this.batchSize = batchSize;
  }

  @Override
  public void putFloat(int rowId, float value) {
    type.writeLong(builder, (long)Float.floatToRawIntBits(value));
  }

  @Override
  public void putFloats(int rowId, int count, float[] src, int srcIndex) {
    for (int i = srcIndex; i < count; i++) {
      type.writeLong(builder, (long)Float.floatToRawIntBits(src[i]));
    }
  }

  @Override
  public void putNull(int rowId) {
    builder.appendNull();
  }

  @Override
  public void putNulls(int rowId, int count) {
    for (int i = 0; i < count; i++) {
      builder.appendNull();
    }
  }

  @Override
  public void reset() {
    builder = type.createBlockBuilder(null, batchSize);
  }

  @Override
  public void putObject(int rowId, Object value) {
    if (value == null) {
      putNull(rowId);
    } else {
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-3605
      putFloat(rowId, (float) value);
    }
  }
}
