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

package org.apache.carbondata.processing.loading;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.carbondata.common.logging.LogServiceFactory;
import org.apache.carbondata.core.datastore.filesystem.CarbonFile;
import org.apache.carbondata.core.datastore.filesystem.CarbonFileFilter;
import org.apache.carbondata.core.datastore.impl.FileFactory;
import org.apache.carbondata.core.metadata.schema.table.CarbonTable;
import org.apache.carbondata.core.statusmanager.LoadMetadataDetails;
import org.apache.carbondata.core.statusmanager.SegmentStatusManager;
import org.apache.carbondata.core.util.CarbonProperties;
import org.apache.carbondata.core.util.CarbonThreadFactory;
import org.apache.carbondata.core.util.CarbonUtil;
import org.apache.carbondata.core.util.path.CarbonTablePath;
import org.apache.carbondata.processing.loading.model.CarbonLoadModel;
import org.apache.carbondata.processing.util.CarbonDataProcessorUtil;
import org.apache.carbondata.processing.util.CarbonLoaderUtil;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

public class TableProcessingOperations {
  private static final Logger LOGGER =
      LogServiceFactory.getLogService(CarbonLoaderUtil.class.getName());

  /**
   *
   * @param carbonTable
   * @param isCompactionFlow
   * @throws IOException
   */
  public static void deletePartialLoadDataIfExist(CarbonTable carbonTable,
      final boolean isCompactionFlow) throws IOException {
    String metaDataLocation = carbonTable.getMetadataPath();
    final LoadMetadataDetails[] details = SegmentStatusManager.readLoadMetadata(metaDataLocation);

    //delete folder which metadata no exist in tablestatus
    String partitionPath = CarbonTablePath.getPartitionDir(carbonTable.getTablePath());
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-2863
    if (FileFactory.isFileExist(partitionPath)) {
      CarbonFile carbonFile = FileFactory.getCarbonFile(partitionPath);
      CarbonFile[] listFiles = carbonFile.listFiles(new CarbonFileFilter() {
        @Override
        public boolean accept(CarbonFile path) {
          String segmentId =
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-2428
              CarbonTablePath.DataFileUtil.getSegmentIdFromPath(path.getAbsolutePath() + "/dummy");
          boolean found = false;
          for (int j = 0; j < details.length; j++) {
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-1886
            if (details[j].getLoadName().equals(segmentId)) {
              found = true;
              break;
            }
          }
          return !found;
        }
      });
      for (int k = 0; k < listFiles.length; k++) {
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-2428
        String segmentId = CarbonTablePath.DataFileUtil
            .getSegmentIdFromPath(listFiles[k].getAbsolutePath() + "/dummy");
        if (isCompactionFlow) {
          if (segmentId.contains(".")) {
            CarbonLoaderUtil.deleteStorePath(listFiles[k].getAbsolutePath());
          }
        } else {
          if (!segmentId.contains(".")) {
            CarbonLoaderUtil.deleteStorePath(listFiles[k].getAbsolutePath());
          }
        }
      }
    }
  }

  /**
   *
   * This method will delete the local data load folder location after data load is complete
   *
   * @param loadModel
   * @param isCompactionFlow COMPACTION keyword will be added to path to make path unique if true
   * @param isAltPartitionFlow Alter_Partition keyword will be added to path to make path unique if
   *                           true
   */
  public static void deleteLocalDataLoadFolderLocation(CarbonLoadModel loadModel,
      boolean isCompactionFlow, boolean isAltPartitionFlow) {
    String tableName = loadModel.getTableName();
    String databaseName = loadModel.getDatabaseName();
    String tempLocationKey = CarbonDataProcessorUtil
        .getTempStoreLocationKey(databaseName, tableName, loadModel.getSegmentId(),
            loadModel.getTaskNo(), isCompactionFlow, isAltPartitionFlow);
    deleteLocalDataLoadFolderLocation(tempLocationKey, tableName);
  }

  /**
   *
   * This method will delete the local data load folder location after data load is complete
   *
   * @param tempLocationKey temporary location set in carbon properties
   * @param tableName
   */
  public static void deleteLocalDataLoadFolderLocation(String tempLocationKey, String tableName) {

    // form local store location
    final String localStoreLocations = CarbonProperties.getInstance().getProperty(tempLocationKey);
    if (localStoreLocations == null) {
      throw new RuntimeException("Store location not set for the key " + tempLocationKey);
    }
    // submit local folder clean up in another thread so that main thread execution is not blocked
    ExecutorService localFolderDeletionService = Executors
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-3304
        .newFixedThreadPool(1, new CarbonThreadFactory("LocalFolderDeletionPool:" + tableName,
                true));
    try {
      localFolderDeletionService.submit(new Callable<Void>() {
        @Override
        public Void call() {
          long startTime = System.currentTimeMillis();
          String[] locArray = StringUtils.split(localStoreLocations, File.pathSeparator);
          for (String loc : locArray) {
            try {
              CarbonUtil.deleteFoldersAndFiles(new File(loc));
            } catch (IOException | InterruptedException e) {
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-3024
              LOGGER.error("Failed to delete local data load folder location: " + loc, e);
            }
          }
          LOGGER.info(
              "Deleted the local store location: " + localStoreLocations + " : Time taken: " + (
                  System.currentTimeMillis() - startTime));
          return null;
        }
      });
    } finally {
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-3026
      CarbonProperties.getInstance().removeProperty(tempLocationKey);
      if (null != localFolderDeletionService) {
        localFolderDeletionService.shutdown();
      }
    }

  }
}
