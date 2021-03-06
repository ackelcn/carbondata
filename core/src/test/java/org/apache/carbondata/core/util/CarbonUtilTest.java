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

package org.apache.carbondata.core.util;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.carbondata.core.index.Segment;
import org.apache.carbondata.core.datastore.block.TableBlockInfo;
import org.apache.carbondata.core.datastore.chunk.impl.FixedLengthDimensionColumnPage;
import org.apache.carbondata.core.datastore.filesystem.LocalCarbonFile;
import org.apache.carbondata.core.datastore.impl.FileFactory;
import org.apache.carbondata.core.metadata.ColumnarFormatVersion;
import org.apache.carbondata.core.metadata.blocklet.DataFileFooter;
import org.apache.carbondata.core.metadata.datatype.DataType;
import org.apache.carbondata.core.metadata.datatype.DataTypes;
import org.apache.carbondata.core.metadata.encoder.Encoding;
import org.apache.carbondata.core.metadata.schema.table.column.CarbonDimension;
import org.apache.carbondata.core.metadata.schema.table.column.CarbonMeasure;
import org.apache.carbondata.core.metadata.schema.table.column.ColumnSchema;
import org.apache.carbondata.core.scan.model.ProjectionDimension;

import mockit.Mock;
import mockit.MockUp;
import org.apache.hadoop.security.UserGroupInformation;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class CarbonUtilTest {

  @BeforeClass public static void setUp() throws Exception {
    new File("../core/src/test/resources/testFile.txt").createNewFile();
    new File("../core/src/test/resources/testDatabase").mkdirs();
  }

  @Test public void testGetBitLengthForDimensionGiveProperValue() {
    int[] cardinality = { 200, 1, 10000, 1, 10, 3 };
    int[] dimensionBitLength =
        CarbonUtil.getDimensionBitLength(cardinality, new int[] { 1, 1, 3, 1 });
    int[] expectedOutPut = { 8, 8, 14, 2, 8, 8 };
    for (int i = 0; i < dimensionBitLength.length; i++) {
      assertEquals(expectedOutPut[i], dimensionBitLength[i]);
    }
  }

  @Test(expected = IOException.class) public void testCloseStreams() throws IOException {
    FileReader stream = new FileReader("../core/src/test/resources/testFile.txt");
    BufferedReader br = new BufferedReader(stream);
    CarbonUtil.closeStreams(br);
    br.ready();
  }

  @Test public void testToGetCardinalityForPerIncrLessThan0() {
    int result = CarbonUtil.getIncrementedCardinality(6);
    assertEquals(result, 3);
  }

  @Test public void testToGetCardinality() {
    int result = CarbonUtil.getIncrementedCardinality(10);
    assertEquals(result, 4);
  }

  @Test public void testToGetCardinalityForArray() {
    int[] cardinality = { 10, 20, 0, 6 };
    int[] actualResult = CarbonUtil.getIncrementedCardinality(cardinality);
    int[] expectedResult = { 4, 5, 1, 3 };
    for (int i = 0; i < cardinality.length; i++) {
      assertEquals(actualResult[i], expectedResult[i]);
    }
  }

  @Test public void testToGetIncrementedCardinalityFullyFilled() {
    int[] cardinality = { 200, 20, 0, 10 };
    int[] actualResult = CarbonUtil.getIncrementedCardinalityFullyFilled(cardinality);
    int[] expectedResult = { 8, 8, 64, 8 };
    for (int i = 0; i < cardinality.length; i++) {
      assertEquals(actualResult[i], expectedResult[i]);
    }
  }

  @Test public void testToDeleteFolderForValidPath()
      throws InterruptedException, IOException {
    File testDir = new File("../core/src/test/resources/testDir");
    testDir.mkdirs();
    CarbonUtil.deleteFoldersAndFiles(testDir);
    assertTrue(!testDir.isDirectory());
  }

  @Test(expected = IOException.class) public void testToDeleteFolderWithIOException()
      throws InterruptedException, IOException {
    File testDir = new File("../core/src/test/resources/testDir");
    new MockUp<UserGroupInformation>() {
      @SuppressWarnings("unused") @Mock public UserGroupInformation getLoginUser()
          throws IOException {
        throw new IOException();
      }
    };
    CarbonUtil.deleteFoldersAndFiles(testDir);
  }

  @Test(expected = InterruptedException.class)
  public void testToDeleteFolderWithInterruptedException()
      throws InterruptedException, IOException {
    File testDir = new File("../core/src/test/resources/testDir");
    new MockUp<UserGroupInformation>() {
      @SuppressWarnings("unused") @Mock public UserGroupInformation getLoginUser()
          throws InterruptedException {
        throw new InterruptedException();
      }
    };
    CarbonUtil.deleteFoldersAndFiles(testDir);
  }

  @Test public void testToDeleteFileForValidPath()
      throws InterruptedException, IOException {
    File testDir = new File("../core/src/test/resources/testDir/testFile.csv");
    testDir.mkdirs();
    CarbonUtil.deleteFoldersAndFiles(testDir);
    assertTrue(!testDir.isFile());
  }

  @Test public void testToDeleteFoldersAndFilesForValidFolder()
      throws InterruptedException, IOException {
    String folderPath = "../core/src/test/resources/testDir/carbonDir";
    new File(folderPath).mkdirs();
    LocalCarbonFile testDir = new LocalCarbonFile(folderPath);
    CarbonUtil.deleteFoldersAndFiles(testDir);
    assertTrue(!testDir.exists());
  }

  @Test(expected = IOException.class) public void testToDeleteFoldersAndFilesWithIOException()
      throws InterruptedException, IOException {
    LocalCarbonFile testDir = new LocalCarbonFile("../core/src/test/resources/testDir/carbonDir");
    new MockUp<UserGroupInformation>() {
      @SuppressWarnings("unused") @Mock public UserGroupInformation getLoginUser()
          throws IOException {
        throw new IOException();
      }
    };
    CarbonUtil.deleteFoldersAndFiles(testDir);
  }

  @Test(expected = InterruptedException.class) public void testToDeleteFoldersAndFilesWithInterruptedException()
      throws InterruptedException, IOException {
    LocalCarbonFile testDir = new LocalCarbonFile("../core/src/test/resources/testDir/carbonDir");
    new MockUp<UserGroupInformation>() {
      @SuppressWarnings("unused") @Mock public UserGroupInformation getLoginUser()
          throws InterruptedException {
        throw new InterruptedException();
      }
    };
    CarbonUtil.deleteFoldersAndFiles(testDir);
  }

  @Test public void testToDeleteFoldersAndFilesForValidCarbonFile()
      throws InterruptedException, IOException {
    LocalCarbonFile testDir =
        new LocalCarbonFile("../core/src/test/resources/testDir/testCarbonFile");
    testDir.createNewFile();
    CarbonUtil.deleteFoldersAndFiles(testDir);
    assertTrue(!testDir.exists());
  }

  @Test public void testToDeleteFoldersAndFilesForCarbonFileSilently()
      throws IOException, InterruptedException {
    LocalCarbonFile testDir = new LocalCarbonFile("../core/src/test/resources/testDir");
    testDir.createNewFile();
    CarbonUtil.deleteFoldersAndFilesSilent(testDir);
    assertTrue(!testDir.exists());
  }

  @Test(expected = IOException.class)
  public void testToDeleteFoldersAndFilesSintlyWithIOException()
      throws IOException, InterruptedException {
    new MockUp<UserGroupInformation>() {
      @SuppressWarnings("unused") @Mock public UserGroupInformation getLoginUser()
          throws IOException {
        throw new IOException();
      }
    };
    LocalCarbonFile testDir =
        new LocalCarbonFile("../unibi-solutions/system/carbon/badRecords/badLogPath");
    CarbonUtil.deleteFoldersAndFilesSilent(testDir);
  }

  @Test(expected = InterruptedException.class)
  public void testToDeleteFoldersAndFilesSintlyWithInterruptedException()
      throws IOException, InterruptedException {
    new MockUp<UserGroupInformation>() {
      @SuppressWarnings("unused") @Mock public UserGroupInformation getLoginUser()
          throws InterruptedException {
        throw new InterruptedException();
      }
    };
    LocalCarbonFile testDir =
        new LocalCarbonFile("../unibi-solutions/system/carbon/badRecords/badLogPath");
    CarbonUtil.deleteFoldersAndFilesSilent(testDir);
  }

  @Test public void testToDeleteFiles() throws IOException {
    String baseDirectory = "../core/src/test/resources/";
    File file1 = new File(baseDirectory + "File1.txt");
    File file2 = new File(baseDirectory + "File2.txt");
    file1.createNewFile();
    file2.createNewFile();
    File[] files = { file1, file2 };
    CarbonUtil.deleteFiles(files);
    assertTrue(!file1.exists());
    assertTrue(!file2.exists());
  }

  @Test public void testToGetNextLesserValue() {
    byte[] dataChunks = { 5, 6, 7, 8, 9 };
    byte[] compareValues = { 7 };
    FixedLengthDimensionColumnPage fixedLengthDataChunk =
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-3113
        new FixedLengthDimensionColumnPage(dataChunks, null, null, 5, 1, dataChunks.length);
    int result = CarbonUtil.nextLesserValueToTarget(2, fixedLengthDataChunk, compareValues);
    assertEquals(result, 1);
  }

  @Test public void testToGetNextLesserValueToTarget() {
    byte[] dataChunks = { 7, 7, 7, 8, 9 };
    byte[] compareValues = { 7 };
    FixedLengthDimensionColumnPage fixedLengthDataChunk =
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-3113
        new FixedLengthDimensionColumnPage(dataChunks, null, null, 5, 1, dataChunks.length);
    int result = CarbonUtil.nextLesserValueToTarget(2, fixedLengthDataChunk, compareValues);
    assertEquals(result, -1);
  }

  @Test public void testToGetnextGreaterValue() {
    byte[] dataChunks = { 5, 6, 7, 8, 9 };
    byte[] compareValues = { 7 };
    FixedLengthDimensionColumnPage fixedLengthDataChunk =
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-3113
        new FixedLengthDimensionColumnPage(dataChunks, null, null, 5, 1, dataChunks.length);
    int result = CarbonUtil.nextGreaterValueToTarget(2, fixedLengthDataChunk, compareValues, 5);
    assertEquals(result, 3);
  }

  @Test public void testToConvertToIntegerList() {
    int[] integerArray = { 10, 20, 30, 40 };
    List<Integer> integerList = CarbonUtil.convertToIntegerList(integerArray);
    for (int i = 0; i < integerArray.length; i++) {
      assertEquals(integerArray[i], (int) integerList.get(i));
    }
  }

  @Test public void testToGetnextGreaterValueToTarget() {
    byte[] dataChunks = { 5, 6, 7, 7, 7 };
    byte[] compareValues = { 7 };
    FixedLengthDimensionColumnPage fixedLengthDataChunk =
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-3113
        new FixedLengthDimensionColumnPage(dataChunks, null, null, 5, 1, dataChunks.length);
    int result = CarbonUtil.nextGreaterValueToTarget(2, fixedLengthDataChunk, compareValues, 5);
    assertEquals(result, 5);
  }

  @Test public void testToUnescapeChar() {
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-3740
    String[] input = { "\\001", "\\t", "\\r", "\\b", "\\n", "\\f", "\\r\\n", "\\\\" };
    String[] output = { "\001", "\t", "\r", "\b", "\n", "\f", "\r\n", "\\" };
    for (int i = 0; i < input.length; i++) {
      assertEquals(CarbonUtil.unescapeChar(input[i]), output[i]);
    }
  }

  @Test public void testForDelimiterConverter() {
    String[] input =
        { "|", "*", ".", ":", "^", "\\", "$", "+", "?", "(", ")", "{", "}", "[", "]", "'" };
    String[] expectedResult =
        { "\\|", "\\*", "\\.", "\\:", "\\^", "\\\\", "\\$", "\\+", "\\?", "\\(", "\\)", "\\{",
            "\\}", "\\[", "\\]", "'" };
    for (int i = 0; i < input.length; i++) {
      assertEquals(CarbonUtil.delimiterConverter(input[i]), expectedResult[i]);
    }
  }

  @Test public void testToCheckAndAppendHDFSUrlWithNoBlackSlash() {
    new MockUp<FileFactory>() {
      @SuppressWarnings("unused") @Mock public FileFactory.FileType getFileType(String path) {
        return FileFactory.FileType.LOCAL;
      }
    };
    new MockUp<CarbonProperties>() {
      @SuppressWarnings("unused") @Mock public String getProperty(String key) {
        return "BASE_URL";
      }
    };
    String hdfsURL = CarbonUtil.checkAndAppendHDFSUrl("../core/src/test/resources/testDatabase");
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-1236
    assertEquals(hdfsURL, "file:///BASE_URL/../core/src/test/resources/testDatabase");
  }

  @Test public void testToCheckAndAppendHDFSUrlWithBlackSlash() {
    new MockUp<FileFactory>() {
      @SuppressWarnings("unused") @Mock public FileFactory.FileType getFileType(String path) {
        return FileFactory.FileType.LOCAL;
      }
    };
    new MockUp<CarbonProperties>() {
      @SuppressWarnings("unused") @Mock public String getProperty(String key) {
        return "BASE_URL/";
      }
    };
    String hdfsURL = CarbonUtil.checkAndAppendHDFSUrl("../core/src/test/resources/testDatabase");
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-1236
    assertEquals(hdfsURL, "file:///BASE_URL/../core/src/test/resources/testDatabase");
  }

  @Test public void testToCheckAndAppendHDFSUrlWithNull() {
    new MockUp<FileFactory>() {
      @SuppressWarnings("unused") @Mock public FileFactory.FileType getFileType(String path) {
        return FileFactory.FileType.LOCAL;
      }
    };
    new MockUp<CarbonProperties>() {
      @SuppressWarnings("unused") @Mock public String getProperty(String key) {
        return null;
      }
    };
    String hdfsURL = CarbonUtil.checkAndAppendHDFSUrl("../core/src/test/resources/testDatabase");
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-1236
    assertEquals(hdfsURL, "file:////../core/src/test/resources/testDatabase");
  }

  @Test public void testToCheckAndAppendHDFSUrlWithHdfs() {
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-1236
    new MockUp<FileFactory>() {
      @SuppressWarnings("unused") @Mock public FileFactory.FileType getFileType(String path) {
        return FileFactory.FileType.HDFS;
      }
    };
    new MockUp<org.apache.hadoop.conf.Configuration>() {
      @SuppressWarnings("unused") @Mock public String get(String name) {
        return "hdfs://";
      }
    };
    String hdfsURL = CarbonUtil.checkAndAppendHDFSUrl("hdfs://ha/core/src/test/resources/testDatabase");
    assertEquals(hdfsURL, "hdfs://ha/core/src/test/resources/testDatabase");
  }

  @Test public void testToCheckAndAppendHDFSUrlWithDoubleSlashLocal() {
    new MockUp<FileFactory>() {
      @SuppressWarnings("unused") @Mock public FileFactory.FileType getFileType(String path) {
        return FileFactory.FileType.LOCAL;
      }
    };
    new MockUp<CarbonProperties>() {
      @SuppressWarnings("unused") @Mock public String getProperty(String key) {
        return "/opt/";
      }
    };
    String hdfsURL = CarbonUtil.checkAndAppendHDFSUrl("/core/src/test/resources/testDatabase");
    assertEquals(hdfsURL, "file:////opt/core/src/test/resources/testDatabase");
  }

  @Test public void testToCheckAndAppendHDFSUrlWithDoubleSlashHDFS() {
    new MockUp<FileFactory>() {
      @SuppressWarnings("unused") @Mock public FileFactory.FileType getFileType(String path) {
        return FileFactory.FileType.HDFS;
      }
    };
    new MockUp<org.apache.hadoop.conf.Configuration>() {
      @SuppressWarnings("unused") @Mock public String get(String name) {
        return "hdfs://";
      }
    };
    new MockUp<CarbonProperties>() {
      @SuppressWarnings("unused") @Mock public String getProperty(String key) {
        return "/opt/";
      }
    };
    String hdfsURL = CarbonUtil.checkAndAppendHDFSUrl("/core/src/test/resources/testDatabase");
    assertEquals(hdfsURL, "hdfs:///opt/core/src/test/resources/testDatabase");
  }

  @Test public void testToCheckAndAppendHDFSUrlWithBaseURLPrefix() {
    new MockUp<FileFactory>() {
      @SuppressWarnings("unused") @Mock public FileFactory.FileType getFileType(String path) {
        return FileFactory.FileType.HDFS;
      }
    };
    new MockUp<CarbonProperties>() {
      @SuppressWarnings("unused") @Mock public String getProperty(String key) {
        return "hdfs://ha/opt/";
      }
    };
    String hdfsURL = CarbonUtil.checkAndAppendHDFSUrl("/core/src/test/resources/testDatabase");
    assertEquals(hdfsURL, "hdfs://ha/opt/core/src/test/resources/testDatabase");
  }

  @Test public void testToCheckAndAppendHDFSUrlWithBaseURLFile() {
    new MockUp<FileFactory>() {
      @SuppressWarnings("unused") @Mock public FileFactory.FileType getFileType(String path) {
        return FileFactory.FileType.HDFS;
      }
    };
    new MockUp<CarbonProperties>() {
      @SuppressWarnings("unused") @Mock public String getProperty(String key) {
        return "file:///";
      }
    };
    String hdfsURL = CarbonUtil.checkAndAppendHDFSUrl("/core/src/test/resources/testDatabase");
    assertEquals(hdfsURL, "file:///core/src/test/resources/testDatabase");
  }

  @Test public void testToCheckAndAppendHDFSUrlWithFilepathPrefix() {
    String hdfsURL = CarbonUtil.checkAndAppendHDFSUrl("file:///core/src/test/resources/testDatabase");
    assertEquals(hdfsURL, "file:///core/src/test/resources/testDatabase");
  }

  @Test public void testForisFileExists() {
    assertTrue(CarbonUtil.isFileExists("../core/src/test/resources/testFile.txt"));
  }

  @Test public void testForisFileExistsWithException() {
    new MockUp<FileFactory>() {
      @SuppressWarnings("unused") @Mock public FileFactory.FileType getFileType(String path)
          throws IOException {
        throw new IOException();
      }
    };
    assertTrue(!CarbonUtil.isFileExists("../core/src/test/resources/testFile.txt"));
  }

  @Test public void testToCheckAndCreateFolder() {
    boolean exists = CarbonUtil.checkAndCreateFolder("../core/src/test/resources/testDatabase");
    boolean created = CarbonUtil.checkAndCreateFolder("../core/src/test/resources/newDatabase");
    assertTrue(exists);
    assertTrue(created);
  }

  @Test public void testToCheckAndCreateFolderWithException() {
    new MockUp<FileFactory>() {
      @SuppressWarnings("unused") @Mock public FileFactory.FileType getFileType(String path)
          throws IOException {
        throw new IOException();
      }
    };
    boolean exists = CarbonUtil.checkAndCreateFolder("../core/src/test/resources/testDatabase1");
    assertTrue(!exists);
  }

  @Test public void testToGetFileSize() {
    assertEquals(CarbonUtil.getFileSize("../core/src/test/resources/testFile.txt"), 0);
  }

  @Test public void testForHasEncoding() {
    List<Encoding> encodingList = new ArrayList<>();
    encodingList.add(Encoding.INVERTED_INDEX);
    encodingList.add(Encoding.DICTIONARY);
    encodingList.add(Encoding.DELTA);
    assertTrue(CarbonUtil.hasEncoding(encodingList, Encoding.DICTIONARY));
    assertTrue(!CarbonUtil.hasEncoding(encodingList, Encoding.BIT_PACKED));
  }

  @Test public void testForHasDataTypes() {
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-1594
    DataType[] dataTypes = {DataTypes.createDefaultDecimalType(), DataTypes.BOOLEAN, DataTypes.INT };
    assertTrue(CarbonUtil.hasDataType(DataTypes.BOOLEAN, dataTypes));
    assertTrue(!CarbonUtil.hasDataType(DataTypes.DATE, dataTypes));
  }

  @Test public void testForHasComplexDataTypes() {
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-1662
    assertTrue(DataTypes.createDefaultArrayType().isComplexType());
    assertTrue(!DataTypes.DATE.isComplexType());
  }

  @Test public void testToGetComplexDataTypeArray() {
    ColumnSchema column1Schema = new ColumnSchema();
    ColumnSchema column2Schema = new ColumnSchema();
    column1Schema.setColumnName("Column1");
    column1Schema.setDataType(DataTypes.DATE);
    ProjectionDimension
        column1 = new ProjectionDimension(new CarbonDimension(column1Schema, 1, 1, 1));
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-2720
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-2720
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-2720

    column2Schema.setColumnName("Column2");
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-1662
    column2Schema.setDataType(DataTypes.createDefaultArrayType());
    ProjectionDimension
        column2 = new ProjectionDimension(new CarbonDimension(column2Schema, 1, 1, 1));

    ProjectionDimension[] queryDimensions = { column1, column2 };

    boolean[] dictionaryEncoding = CarbonUtil.getComplexDataTypeArray(queryDimensions);
    boolean[] expectedDictionaryEncoding = { false, true };
    for (int i = 0; i < dictionaryEncoding.length; i++) {
      assertEquals(dictionaryEncoding[i], expectedDictionaryEncoding[i]);
    }
  }

  @Test public void testToReadMetadataFile() throws IOException {
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-3650
    new MockUp<DataFileFooterConverterV3>() {
      @SuppressWarnings("unused") @Mock
      public DataFileFooter readDataFileFooter(TableBlockInfo info) {
        DataFileFooter fileFooter = new DataFileFooter();
        fileFooter.setVersionId(ColumnarFormatVersion.V3);
        return fileFooter;
      }
    };
    TableBlockInfo info =
        new TableBlockInfo("file:/", 1, "0", new String[0], 1, ColumnarFormatVersion.V3, null);

    assertEquals(CarbonUtil.readMetadataFile(info).getVersionId().number(), 3);
  }

  @Test(expected = IOException.class)
  public void testToReadMetadataFileWithException()
      throws Exception {
    TableBlockInfo info =
        new TableBlockInfo("file:/", 1, "0", new String[0], 1, ColumnarFormatVersion.V3, null);
    CarbonUtil.readMetadataFile(info);
  }

  @Test public void testToFindDimension() {
    ColumnSchema column1Schema = new ColumnSchema();
    ColumnSchema column2Schema = new ColumnSchema();
    column1Schema.setColumnName("Column1");
    column2Schema.setColumnName("Column2");
    List<CarbonDimension> carbonDimension = new ArrayList<>();
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-2720
    carbonDimension.add(new CarbonDimension(column1Schema, 1, 1, 1));
    carbonDimension.add(new CarbonDimension(column2Schema, 2, 1, 1));
    assertEquals(CarbonUtil.findDimension(carbonDimension, "Column1"),
        new CarbonDimension(column1Schema, 1, 1, 1));
  }

  @Test public void testToGetColumnSchemaList() {
    ColumnSchema column1Schema = new ColumnSchema();
    ColumnSchema column2Schema = new ColumnSchema();
    column1Schema.setColumnName("Column1");
    column2Schema.setColumnName("Column2");
    List<CarbonDimension> carbonDimension = new ArrayList<>();
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-2720
    carbonDimension.add(new CarbonDimension(column1Schema, 1, 1, 1));
    carbonDimension.add(new CarbonDimension(column2Schema, 2, 2, 1));

    List<CarbonMeasure> carbonMeasure = new ArrayList<>();
    carbonMeasure.add(new CarbonMeasure(column1Schema, 1));
    carbonMeasure.add(new CarbonMeasure(column2Schema, 2));

    List<ColumnSchema> columnSchema =
        CarbonUtil.getColumnSchemaList(carbonDimension, carbonMeasure);
    for (int i = 0; i < carbonMeasure.size(); i++) {
      assertEquals(columnSchema.get(i), carbonMeasure.get(i).getColumnSchema());
    }
  }

  @Test public void testToReadHeader() throws IOException {
    File file = new File("../core/src/test/resources/sampleCSV.csv");
    FileWriter writer = new FileWriter(file);
    writer.write("id,name");
    writer.flush();
    String headers = CarbonUtil.readHeader("../core/src/test/resources/sampleCSV.csv");
    assertEquals(headers, "id,name");
    file.deleteOnExit();
  }

  @Test(expected = IOException.class)
  public void testToReadHeaderWithFileNotFoundException() throws IOException {
    new MockUp<FileFactory>() {
      @SuppressWarnings("unused") @Mock
      public DataInputStream getDataInputStream(String path)
          throws FileNotFoundException {
        throw new FileNotFoundException();
      }
    };
    String result = CarbonUtil.readHeader("../core/src/test/resources/sampleCSV");
    assertEquals(null, result);
  }

  @Test(expected = IOException.class)
  public void testToReadHeaderWithIOException() throws IOException {
    new MockUp<FileFactory>() {
      @SuppressWarnings("unused") @Mock
      public DataInputStream getDataInputStream(String path)
          throws IOException {
        throw new IOException();
      }
    };
    String result = CarbonUtil.readHeader("../core/src/test/resources/sampleCSV.csv");
    assertEquals(null, result);
  }

  @Test public void testToPrintLine() {
    String line = CarbonUtil.printLine("*", 2);
    assertEquals(line, "**");
  }

  @Test public void testToGetSegmentString() {
    List<Segment> list = new ArrayList<>();
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-2909
    list.add(new Segment("1", null, null));
    list.add(new Segment("2", null, null));
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-1858
    String segments = CarbonUtil.convertToString(list);
    assertEquals(segments, "1,2");
  }

  @Test public void testToGetSegmentStringWithEmptySegmentList() {
    List<Segment> list = new ArrayList<>();
    String segments = CarbonUtil.convertToString(list);
    assertEquals(segments, "");
  }

  @Test public void testToGetSurrogateKey() {
    byte[] data = { 1, 1 };
    ByteBuffer byteBuffer = ByteBuffer.allocate(8);
    int a = CarbonUtil.getSurrogateKey(data, byteBuffer);
    assertEquals(a, 257);
  }

  @Test public void testToGetDictionaryChunkSize() {
    new MockUp<CarbonProperties>() {
      @SuppressWarnings("unused") @Mock public CarbonProperties getInstance()
          throws NumberFormatException {
        throw new NumberFormatException();
      }
    };
    int expectedResult = CarbonUtil.getDictionaryChunkSize();
    assertEquals(expectedResult, 10000);
  }

  @Test public void testToPackByteBufferIntoSingleByteArrayWithNull() {
    byte[] byteArray = CarbonUtil.packByteBufferIntoSingleByteArray(null);
    assertEquals(null, byteArray);
  }

  @Test public void testToPackByteBufferIntoSingleByteArray() {
    ByteBuffer[] byteBuffers = { ByteBuffer.allocate(1), ByteBuffer.allocate(2) };
    byte[] byteArray = CarbonUtil.packByteBufferIntoSingleByteArray(byteBuffers);
    byte[] expectedResult = { 0, 4, 0, 5, 0, 0, 0, 0 };
    for (int i = 0; i < byteArray.length; i++) {
      assertEquals(expectedResult[i], byteArray[i]);
    }
  }

  @Test public void testToIdentifyDimensionType() {
    ColumnSchema column1Schema = new ColumnSchema();
    ColumnSchema column2Schema = new ColumnSchema();
    ColumnSchema column3Schema = new ColumnSchema();
    column1Schema.setColumnName("Column1");
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-3674
    column1Schema.setDataType(DataTypes.DATE);
    column1Schema.setEncodingList(Arrays.asList(Encoding.DELTA, Encoding.DICTIONARY));
    column2Schema.setColumnName("Column2");
    column2Schema.setDataType(DataTypes.DATE);
    column2Schema.setEncodingList(Arrays.asList(Encoding.DELTA, Encoding.DICTIONARY));
    column3Schema.setColumnName("Column3");
    column3Schema.setEncodingList(Arrays.asList(Encoding.DELTA, Encoding.INVERTED_INDEX));
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-2720
    CarbonDimension carbonDimension = new CarbonDimension(column1Schema, 1, 1, 1);
    CarbonDimension carbonDimension2 = new CarbonDimension(column2Schema, 2, 2, 2);
    CarbonDimension carbonDimension3 = new CarbonDimension(column3Schema, 3, 3, 3);
    List<CarbonDimension> carbonDimensions =
        Arrays.asList(carbonDimension, carbonDimension2, carbonDimension3);
    boolean[] result = CarbonUtil.identifyDimensionType(carbonDimensions);
    assertThat(result, is(equalTo(new boolean[] { true, true, false })));
  }

  @Test public void testToGetFirstIndexUsingBinarySearchWithCompareTo1() {
    byte[] dataChunks = { 10, 20, 30, 40, 50, 60 };
    byte[] compareValue = { 5 };
    FixedLengthDimensionColumnPage fixedLengthDimensionDataChunk =
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-3113
        new FixedLengthDimensionColumnPage(dataChunks, null, null, 6, 1, dataChunks.length);
    int result = CarbonUtil
        .getFirstIndexUsingBinarySearch(fixedLengthDimensionDataChunk, 1, 3, compareValue, false);
    assertEquals(-2, result);
  }

  @Test public void testToGetFirstIndexUsingBinarySearchWithCompareToLessThan0() {
    byte[] dataChunks = { 10, 20, 30, 40, 50, 60 };
    byte[] compareValue = { 30 };
    FixedLengthDimensionColumnPage fixedLengthDimensionDataChunk =
        new FixedLengthDimensionColumnPage(dataChunks, null, null, 6, 1, dataChunks.length);
    int result = CarbonUtil
        .getFirstIndexUsingBinarySearch(fixedLengthDimensionDataChunk, 1, 3, compareValue, false);
    assertEquals(2, result);
  }

  @Test public void testToGetFirstIndexUsingBinarySearchWithCompareTo0() {
    byte[] dataChunks = { 10, 10, 10, 40, 50, 60 };
    byte[] compareValue = { 10 };
    FixedLengthDimensionColumnPage fixedLengthDimensionDataChunk =
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-3113
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-3113
        new FixedLengthDimensionColumnPage(dataChunks, null, null, 6, 1, dataChunks.length);
    int result = CarbonUtil
        .getFirstIndexUsingBinarySearch(fixedLengthDimensionDataChunk, 1, 3, compareValue, false);
    assertEquals(0, result);
  }

  @Test public void testToGetFirstIndexUsingBinarySearchWithMatchUpLimitTrue() {
    byte[] dataChunks = { 10, 10, 10, 40, 50, 60 };
    byte[] compareValue = { 10 };
    FixedLengthDimensionColumnPage fixedLengthDimensionDataChunk =
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-3113
        new FixedLengthDimensionColumnPage(dataChunks, null, null, 6, 1, dataChunks.length);
    int result = CarbonUtil
        .getFirstIndexUsingBinarySearch(fixedLengthDimensionDataChunk, 1, 3, compareValue, true);
    assertEquals(2, result);
  }

  @Test
  public void testBinaryRangeSearch() {

    byte[] dataChunk = new byte[10];
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-2099
    FixedLengthDimensionColumnPage fixedLengthDimensionDataChunk;
    byte[] keyWord = new byte[1];
    int[] range;

    dataChunk = "abbcccddddeffgggh".getBytes();
    byte[][] dataArr = new byte[dataChunk.length / keyWord.length][keyWord.length];
    fixedLengthDimensionDataChunk = new FixedLengthDimensionColumnPage(dataChunk, null, null,
        dataChunk.length / keyWord.length, keyWord.length, dataChunk.length);

    for (int ii = 0; ii < dataChunk.length / keyWord.length; ii++) {
      dataArr[ii] = fixedLengthDimensionDataChunk.getChunkData(ii);
    }

    keyWord[0] = Byte.valueOf("97");
    int[] expectRangeIndex = new int[2];
    expectRangeIndex[0] = 0;
    expectRangeIndex[1] = 0;
    assertRangeIndex(dataArr, dataChunk, fixedLengthDimensionDataChunk, keyWord, expectRangeIndex);

    keyWord[0] = Byte.valueOf("104");
    expectRangeIndex = new int[2];
    expectRangeIndex[0] = 16;
    expectRangeIndex[1] = 16;
    assertRangeIndex(dataArr, dataChunk, fixedLengthDimensionDataChunk, keyWord, expectRangeIndex);

    keyWord[0] = Byte.valueOf("101");
    expectRangeIndex = new int[2];
    expectRangeIndex[0] = 10;
    expectRangeIndex[1] = 10;
    assertRangeIndex(dataArr, dataChunk, fixedLengthDimensionDataChunk, keyWord, expectRangeIndex);

    keyWord[0] = Byte.valueOf("99");
    expectRangeIndex = new int[2];
    expectRangeIndex[0] = 3;
    expectRangeIndex[1] = 5;
    assertRangeIndex(dataArr, dataChunk, fixedLengthDimensionDataChunk, keyWord, expectRangeIndex);

    dataChunk = "ab".getBytes();
    fixedLengthDimensionDataChunk = new FixedLengthDimensionColumnPage(dataChunk, null, null,
        dataChunk.length / keyWord.length, keyWord.length, dataChunk.length);

    keyWord[0] = Byte.valueOf("97");
    range = CarbonUtil.getRangeIndexUsingBinarySearch(fixedLengthDimensionDataChunk, 0, dataChunk.length - 1, keyWord);
    assertEquals(0, range[0]);
    assertEquals(0, range[1]);

    keyWord[0] = Byte.valueOf("98");
    range = CarbonUtil.getRangeIndexUsingBinarySearch(fixedLengthDimensionDataChunk, 0, dataChunk.length - 1, keyWord);
    assertEquals(1, range[0]);
    assertEquals(1, range[1]);

    dataChunk = "aabb".getBytes();
    fixedLengthDimensionDataChunk = new FixedLengthDimensionColumnPage(dataChunk, null, null,
        dataChunk.length / keyWord.length, keyWord.length, dataChunk.length);

    keyWord[0] = Byte.valueOf("97");
    range = CarbonUtil.getRangeIndexUsingBinarySearch(fixedLengthDimensionDataChunk, 0, dataChunk.length - 1, keyWord);
    assertEquals(0, range[0]);
    assertEquals(1, range[1]);

    keyWord[0] = Byte.valueOf("98");
    range = CarbonUtil.getRangeIndexUsingBinarySearch(fixedLengthDimensionDataChunk, 0, dataChunk.length - 1, keyWord);
    assertEquals(2, range[0]);
    assertEquals(3, range[1]);

    dataChunk = "a".getBytes();
    fixedLengthDimensionDataChunk = new FixedLengthDimensionColumnPage(dataChunk, null, null,
        dataChunk.length / keyWord.length, keyWord.length, dataChunk.length);

    keyWord[0] = Byte.valueOf("97");
    range = CarbonUtil.getRangeIndexUsingBinarySearch(fixedLengthDimensionDataChunk, 0, dataChunk.length - 1, keyWord);
    assertEquals(0, range[0]);
    assertEquals(0, range[1]);

    dataChunk = "aa".getBytes();
    fixedLengthDimensionDataChunk = new FixedLengthDimensionColumnPage(dataChunk, null, null,
        dataChunk.length / keyWord.length, keyWord.length, dataChunk.length);
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-3113
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-3113
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-3113
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-3113

    keyWord[0] = Byte.valueOf("97");
    range = CarbonUtil.getRangeIndexUsingBinarySearch(fixedLengthDimensionDataChunk, 0, dataChunk.length - 1, keyWord);
    assertEquals(0, range[0]);
    assertEquals(1, range[1]);

    dataChunk = "aabbbbbbbbbbcc".getBytes();
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-2099
    fixedLengthDimensionDataChunk = new FixedLengthDimensionColumnPage(dataChunk, null, null,
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-3113
        dataChunk.length / keyWord.length, keyWord.length, dataChunk.length);
    keyWord[0] = Byte.valueOf("98");
    range = CarbonUtil.getRangeIndexUsingBinarySearch(fixedLengthDimensionDataChunk, 0, dataChunk.length - 1, keyWord);
    assertEquals(2, range[0]);
    assertEquals(11, range[1]);

  }

  @Test
  public void IndexUsingBinarySearchLengthTwo() {

    byte[] dataChunk = new byte[10];
    FixedLengthDimensionColumnPage fixedLengthDimensionDataChunk;
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-2099

    byte[] keyWord = new byte[2];

    dataChunk = "aabbbbbbbbbbcc".getBytes();
    byte[][] dataArr = new byte[dataChunk.length / keyWord.length][keyWord.length];

//IC see: https://issues.apache.org/jira/browse/CARBONDATA-2099
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-2099
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-2099
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-2099
    fixedLengthDimensionDataChunk = new FixedLengthDimensionColumnPage(dataChunk, null, null,
        dataChunk.length / keyWord.length, keyWord.length, dataChunk.length);

    for (int ii = 0; ii < dataChunk.length / keyWord.length; ii++) {
      dataArr[ii] = fixedLengthDimensionDataChunk.getChunkData(ii);
    }

    keyWord[0] = Byte.valueOf("98");
    keyWord[1] = Byte.valueOf("98");
    int[] expectRangeIndex = new int[2];
    expectRangeIndex[0] = 1;
    expectRangeIndex[1] = 5;
    assertRangeIndex(dataArr, dataChunk, fixedLengthDimensionDataChunk, keyWord, expectRangeIndex);

    keyWord[0] = Byte.valueOf("97");
    keyWord[1] = Byte.valueOf("97");

    expectRangeIndex = new int[2];
    expectRangeIndex[0] = 0;
    expectRangeIndex[1] = 0;
    assertRangeIndex(dataArr, dataChunk, fixedLengthDimensionDataChunk, keyWord, expectRangeIndex);

    keyWord[0] = Byte.valueOf("99");
    keyWord[1] = Byte.valueOf("99");
    expectRangeIndex = new int[2];
    expectRangeIndex[0] = 6;
    expectRangeIndex[1] = 6;
    assertRangeIndex(dataArr, dataChunk, fixedLengthDimensionDataChunk, keyWord, expectRangeIndex);

  }

  @Test
  public void IndexUsingBinarySearchLengthThree() {

    byte[] dataChunk = new byte[10];
    FixedLengthDimensionColumnPage fixedLengthDimensionDataChunk;
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-2099

    byte[] keyWord = new byte[3];

    dataChunk = "aaabbbbbbbbbccc".getBytes();
    byte[][] dataArr = new byte[dataChunk.length / keyWord.length][keyWord.length];

    fixedLengthDimensionDataChunk = new FixedLengthDimensionColumnPage(dataChunk, null, null,
        dataChunk.length / keyWord.length, keyWord.length, dataChunk.length);
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-3113
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-3113
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-3113

    for (int ii = 0; ii < dataChunk.length / keyWord.length; ii++) {
      dataArr[ii] = fixedLengthDimensionDataChunk.getChunkData(ii);
    }

    keyWord[0] = Byte.valueOf("98");
    keyWord[1] = Byte.valueOf("98");
    keyWord[2] = Byte.valueOf("98");
    int[] expectRangeIndex = new int[2];
    expectRangeIndex[0] = 1;
    expectRangeIndex[1] = 3;
    assertRangeIndex(dataArr, dataChunk, fixedLengthDimensionDataChunk, keyWord, expectRangeIndex);

  }

  @Test
  public void testSplitSchemaStringToMapWithLessThanSplitLen() {
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-1443
    String schema = generateString(399);
    Map<String, String> map = CarbonUtil.splitSchemaStringToMap(schema);
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-2038
    Assert.assertTrue(map.size() == 2);
    String schemaString = CarbonUtil.splitSchemaStringToMultiString(" ", "'", ",", schema);
    Assert.assertTrue(schemaString.length() > schema.length());
  }

  @Test
  public void testSplitSchemaStringToMapWithEqualThanSplitLen() {
    String schema = generateString(4000);
    Map<String, String> map = CarbonUtil.splitSchemaStringToMap(schema);
    Assert.assertTrue(map.size() == 2);
    String schemaString = CarbonUtil.splitSchemaStringToMultiString(" ", "'", ",", schema);
    Assert.assertTrue(schemaString.length() > schema.length());
  }

  @Test
  public void testSplitSchemaStringToMapWithMoreThanSplitLen() {
    String schema = generateString(7999);
    Map<String, String> map = CarbonUtil.splitSchemaStringToMap(schema);
    Assert.assertTrue(map.size() == 3);
    String schemaString = CarbonUtil.splitSchemaStringToMultiString(" ", "'", ",", schema);
    Assert.assertTrue(schemaString.length() > schema.length());
  }

  @Test
  public void testSplitSchemaStringToMapWithMultiplesOfSplitLen() {
    String schema = generateString(12000);
    Map<String, String> map = CarbonUtil.splitSchemaStringToMap(schema);
    Assert.assertTrue(map.size() == 4);
    String schemaString = CarbonUtil.splitSchemaStringToMultiString(" ", "'", ",", schema);
    Assert.assertTrue(schemaString.length() > schema.length());
  }

  private String generateString(int length) {
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < length; i++) {
      builder.append("a");
    }
    return builder.toString();
  }

  private void assertRangeIndex(byte[][] dataArr, byte[] dataChunk,
//IC see: https://issues.apache.org/jira/browse/CARBONDATA-2099
      FixedLengthDimensionColumnPage fixedLengthDimensionDataChunk, byte[] keyWord, int[] expectRangeIndex) {
    int[] range;
    range = CarbonUtil.getRangeIndexUsingBinarySearch(fixedLengthDimensionDataChunk, 0,
        (dataChunk.length - 1) / keyWord.length, keyWord);
    assertEquals(expectRangeIndex[0], range[0]);
    assertEquals(expectRangeIndex[1], range[1]);

//    int index = CarbonUtil.binarySearch(dataArr, 0, dataChunk.length / keyWord.length - 1, keyWord);
//    assertTrue(expectRangeIndex[0] <= index && index <= range[1]);
  }

 	
  @AfterClass public static void testcleanUp() {
    new File("../core/src/test/resources/testFile.txt").deleteOnExit();
    new File("../core/src/test/resources/testDatabase/levelmetadata_testTable.metadata")
        .deleteOnExit();
    new File("../core/src/test/resources/testDatabase").delete();
    new File("../core/src/test/resources/newDatabase").deleteOnExit();
  }

}
