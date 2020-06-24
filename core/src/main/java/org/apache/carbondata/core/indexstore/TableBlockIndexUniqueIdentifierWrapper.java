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

package org.apache.carbondata.core.indexstore;

import java.io.Serializable;

import org.apache.carbondata.core.datastore.impl.FileFactory;
import org.apache.carbondata.core.metadata.schema.table.CarbonTable;

import org.apache.hadoop.conf.Configuration;

/**
 * Class holds reference to TableBlockIndexUniqueIdentifier and carbonTable related info
 * This is just a wrapper passed between methods like a context, This object must never be cached.
 *
 */
public class TableBlockIndexUniqueIdentifierWrapper implements Serializable {

  private static final long serialVersionUID = 1L;

  // holds the reference to tableBlockIndexUniqueIdentifier
  private TableBlockIndexUniqueIdentifier tableBlockIndexUniqueIdentifier;

  // holds the reference to CarbonTable
  private CarbonTable carbonTable;

  private transient Configuration configuration;
  /**
   * flag to specify whether to load table block metadata in unsafe or safe and whether to add the
   * table block metadata in LRU cache. Default value is true
   */
  private boolean addTableBlockToUnsafeAndLRUCache = true;

  private boolean addToUnsafe = true;

  private boolean serializeDmStore = false;

  public TableBlockIndexUniqueIdentifierWrapper(
      TableBlockIndexUniqueIdentifier tableBlockIndexUniqueIdentifier, CarbonTable carbonTable) {
    this.tableBlockIndexUniqueIdentifier = tableBlockIndexUniqueIdentifier;
    this.carbonTable = carbonTable;
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-2909
    this.configuration = FileFactory.getConfiguration();
  }

  private TableBlockIndexUniqueIdentifierWrapper(
      TableBlockIndexUniqueIdentifier tableBlockIndexUniqueIdentifier, CarbonTable carbonTable,
      Configuration configuration) {
    this.tableBlockIndexUniqueIdentifier = tableBlockIndexUniqueIdentifier;
    this.carbonTable = carbonTable;
    this.configuration = configuration;
  }

  // Note: The constructor is getting used in extensions with other functionalities.
  // Kindly do not remove
  public TableBlockIndexUniqueIdentifierWrapper(
      TableBlockIndexUniqueIdentifier tableBlockIndexUniqueIdentifier, CarbonTable carbonTable,
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-3337
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-3306
      Configuration configuration, boolean addTableBlockToUnsafeAndLRUCache) {
    this(tableBlockIndexUniqueIdentifier, carbonTable, configuration);
    this.addTableBlockToUnsafeAndLRUCache = addTableBlockToUnsafeAndLRUCache;
  }

  // Note: The constructor is getting used in extensions with other functionalities.
  // Kindly do not remove
  public TableBlockIndexUniqueIdentifierWrapper(
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-3680
      TableBlockIndexUniqueIdentifier tableBlockIndexUniqueIdentifier, CarbonTable carbonTable,
      boolean addTableBlockToUnsafeAndLRUCache, boolean addToUnsafe, boolean serializeDmStore) {
    this(tableBlockIndexUniqueIdentifier, carbonTable);
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-2909
    this.configuration = FileFactory.getConfiguration();
    this.addTableBlockToUnsafeAndLRUCache = addTableBlockToUnsafeAndLRUCache;
    this.addToUnsafe = addToUnsafe;
    this.serializeDmStore = serializeDmStore;
  }

  public TableBlockIndexUniqueIdentifier getTableBlockIndexUniqueIdentifier() {
    return tableBlockIndexUniqueIdentifier;
  }

  public CarbonTable getCarbonTable() {
    return carbonTable;
  }

  public boolean isAddTableBlockToUnsafeAndLRUCache() {
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-2924
    return addTableBlockToUnsafeAndLRUCache;
  }

  public Configuration getConfiguration() {
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-2909
    return configuration;
  }

  public boolean isAddToUnsafe() {
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-3680
    return addToUnsafe;
  }

  public boolean isSerializeDmStore() {
    return serializeDmStore;
  }
}
