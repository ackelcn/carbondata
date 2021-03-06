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

package org.apache.carbondata.core.indexstore.row;

import java.io.Serializable;

import org.apache.carbondata.core.indexstore.schema.CarbonRowSchema;

/**
 * It is just a normal row to store data. Implementation classes could be safe and unsafe.
 * TODO move this class a global row and use across loading after DataType is changed class
 */
public abstract class IndexRow implements Serializable {

  /**
   * This is made transient as it is temporary and should not be serialized
   */
  protected transient CarbonRowSchema[] schemas;

  public IndexRow(CarbonRowSchema[] schemas) {
    this.schemas = schemas;
  }

  public abstract byte[] getByteArray(int ordinal);

  public abstract IndexRow getRow(int ordinal);

  public abstract void setRow(IndexRow row, int ordinal);

  public abstract void setByteArray(byte[] byteArray, int ordinal);

  public abstract int getInt(int ordinal);

  public abstract void setInt(int value, int ordinal);

  public abstract void setByte(byte value, int ordinal);

  public abstract byte getByte(int ordinal);

  public abstract void setShort(short value, int ordinal);

  public abstract short getShort(int ordinal);

  public abstract void setLong(long value, int ordinal);

  public abstract long getLong(int ordinal);

  public abstract void setFloat(float value, int ordinal);

  public abstract float getFloat(int ordinal);

  public abstract void setDouble(double value, int ordinal);

  public abstract double getDouble(int ordinal);

  public abstract int getLengthInBytes(int ordinal);

  public abstract void setBoolean(boolean value, int ordinal);

  public abstract boolean getBoolean(int ordinal);

  public int getTotalSizeInBytes() {
    int len = 0;
    for (int i = 0; i < schemas.length; i++) {
      len += getSizeInBytes(i);
    }
    // for last offset in unsafe index row
    len += 4;
    return len;
  }

  public int getSizeInBytes(int ordinal) {
    switch (schemas[ordinal].getSchemaType()) {
      case FIXED:
        return schemas[ordinal].getLength();
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-2420
      case VARIABLE_SHORT:
      case VARIABLE_INT:
        return getLengthInBytes(ordinal) + 4;
      case STRUCT:
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-3704
        IndexRow row = getRow(ordinal);
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-2648
        CarbonRowSchema[] childSchemas =
            ((CarbonRowSchema.StructCarbonRowSchema) schemas[ordinal]).getChildSchemas();
        // set the child schema. Because schema is transient it can be null
        row.setSchemas(childSchemas);
        return getRow(ordinal).getTotalSizeInBytes();
      default:
        throw new UnsupportedOperationException("wrong type");
    }
  }

  public int getColumnCount() {
    return schemas.length;
  }

  public void setSchemas(CarbonRowSchema[] schemas) {
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-2701
    if (null == this.schemas) {
      this.schemas = schemas;
    }
  }
}
