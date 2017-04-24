/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.hbase.client;

import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.testclassification.ClientTests;
import org.apache.hadoop.hbase.testclassification.MediumTests;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

@Category({ MediumTests.class, ClientTests.class })
public class TestAsyncSnapshotAdminApi extends TestAsyncAdminBase {

  @Test
  public void testTakeSnapshot() throws Exception {
    String snapshotName1 = "snapshotName1";
    String snapshotName2 = "snapshotName2";
    TableName tableName = TableName.valueOf("testTakeSnapshot");
    Admin syncAdmin = TEST_UTIL.getAdmin();

    try {
      Table table = TEST_UTIL.createTable(tableName, Bytes.toBytes("f1"));
      for (int i = 0; i < 3000; i++) {
        table.put(new Put(Bytes.toBytes(i)).addColumn(Bytes.toBytes("f1"), Bytes.toBytes("cq"),
          Bytes.toBytes(i)));
      }

      admin.snapshot(snapshotName1, tableName).get();
      admin.snapshot(snapshotName2, tableName).get();
      List<SnapshotDescription> snapshots = syncAdmin.listSnapshots();
      Collections.sort(snapshots, (snap1, snap2) -> {
        Assert.assertNotNull(snap1);
        Assert.assertNotNull(snap1.getName());
        Assert.assertNotNull(snap2);
        Assert.assertNotNull(snap2.getName());
        return snap1.getName().compareTo(snap2.getName());
      });

      Assert.assertEquals(snapshotName1, snapshots.get(0).getName());
      Assert.assertEquals(tableName, snapshots.get(0).getTableName());
      Assert.assertEquals(SnapshotType.FLUSH, snapshots.get(0).getType());
      Assert.assertEquals(snapshotName2, snapshots.get(1).getName());
      Assert.assertEquals(tableName, snapshots.get(1).getTableName());
      Assert.assertEquals(SnapshotType.FLUSH, snapshots.get(1).getType());
    } finally {
      syncAdmin.deleteSnapshot(snapshotName1);
      syncAdmin.deleteSnapshot(snapshotName2);
      TEST_UTIL.deleteTable(tableName);
    }
  }

  @Test
  public void testCloneSnapshot() throws Exception {
    String snapshotName1 = "snapshotName1";
    TableName tableName = TableName.valueOf("testCloneSnapshot");
    TableName tableName2 = TableName.valueOf("testCloneSnapshot2");
    Admin syncAdmin = TEST_UTIL.getAdmin();

    try {
      Table table = TEST_UTIL.createTable(tableName, Bytes.toBytes("f1"));
      for (int i = 0; i < 3000; i++) {
        table.put(new Put(Bytes.toBytes(i)).addColumn(Bytes.toBytes("f1"), Bytes.toBytes("cq"),
          Bytes.toBytes(i)));
      }

      admin.snapshot(snapshotName1, tableName).get();
      List<SnapshotDescription> snapshots = syncAdmin.listSnapshots();
      Assert.assertEquals(snapshots.size(), 1);
      Assert.assertEquals(snapshotName1, snapshots.get(0).getName());
      Assert.assertEquals(tableName, snapshots.get(0).getTableName());
      Assert.assertEquals(SnapshotType.FLUSH, snapshots.get(0).getType());

      // cloneSnapshot into a existed table.
      boolean failed = false;
      try {
        admin.cloneSnapshot(snapshotName1, tableName).get();
      } catch (Exception e) {
        failed = true;
      }
      Assert.assertTrue(failed);

      // cloneSnapshot into a new table.
      Assert.assertTrue(!syncAdmin.tableExists(tableName2));
      admin.cloneSnapshot(snapshotName1, tableName2).get();
      syncAdmin.tableExists(tableName2);
    } finally {
      syncAdmin.deleteSnapshot(snapshotName1);
      TEST_UTIL.deleteTable(tableName);
    }
  }

  private void assertResult(TableName tableName, int expectedRowCount) throws IOException {
    try (Table table = TEST_UTIL.getConnection().getTable(tableName)) {
      Scan scan = new Scan();
      try (ResultScanner scanner = table.getScanner(scan)) {
        Result result;
        int rowCount = 0;
        while ((result = scanner.next()) != null) {
          Assert.assertArrayEquals(result.getRow(), Bytes.toBytes(rowCount));
          Assert.assertArrayEquals(result.getValue(Bytes.toBytes("f1"), Bytes.toBytes("cq")),
            Bytes.toBytes(rowCount));
          rowCount += 1;
        }
        Assert.assertEquals(rowCount, expectedRowCount);
      }
    }
  }

  @Test
  public void testRestoreSnapshot() throws Exception {
    String snapshotName1 = "snapshotName1";
    String snapshotName2 = "snapshotName2";
    TableName tableName = TableName.valueOf("testRestoreSnapshot");
    Admin syncAdmin = TEST_UTIL.getAdmin();

    try {
      Table table = TEST_UTIL.createTable(tableName, Bytes.toBytes("f1"));
      for (int i = 0; i < 3000; i++) {
        table.put(new Put(Bytes.toBytes(i)).addColumn(Bytes.toBytes("f1"), Bytes.toBytes("cq"),
          Bytes.toBytes(i)));
      }
      Assert.assertEquals(admin.listSnapshots().get().size(), 0);

      admin.snapshot(snapshotName1, tableName).get();
      admin.snapshot(snapshotName2, tableName).get();
      Assert.assertEquals(admin.listSnapshots().get().size(), 2);

      admin.disableTable(tableName).get();
      admin.restoreSnapshot(snapshotName1, true).get();
      admin.enableTable(tableName).get();
      assertResult(tableName, 3000);

      admin.disableTable(tableName).get();
      admin.restoreSnapshot(snapshotName2, false).get();
      admin.enableTable(tableName).get();
      assertResult(tableName, 3000);
    } finally {
      syncAdmin.deleteSnapshot(snapshotName1);
      syncAdmin.deleteSnapshot(snapshotName2);
      TEST_UTIL.deleteTable(tableName);
    }
  }

  @Test
  public void testListSnapshots() throws Exception {
    String snapshotName1 = "snapshotName1";
    String snapshotName2 = "snapshotName2";
    String snapshotName3 = "snapshotName3";
    TableName tableName = TableName.valueOf("testListSnapshots");
    Admin syncAdmin = TEST_UTIL.getAdmin();

    try {
      Table table = TEST_UTIL.createTable(tableName, Bytes.toBytes("f1"));
      for (int i = 0; i < 3000; i++) {
        table.put(new Put(Bytes.toBytes(i)).addColumn(Bytes.toBytes("f1"), Bytes.toBytes("cq"),
          Bytes.toBytes(i)));
      }
      Assert.assertEquals(admin.listSnapshots().get().size(), 0);

      admin.snapshot(snapshotName1, tableName).get();
      admin.snapshot(snapshotName2, tableName).get();
      admin.snapshot(snapshotName3, tableName).get();
      Assert.assertEquals(admin.listSnapshots().get().size(), 3);

      Assert.assertEquals(admin.listSnapshots("(.*)").get().size(), 3);
      Assert.assertEquals(admin.listSnapshots("snapshotName(\\d+)").get().size(), 3);
      Assert.assertEquals(admin.listSnapshots("snapshotName[1|3]").get().size(), 2);
      Assert.assertEquals(admin.listSnapshots(Pattern.compile("snapshot(.*)")).get().size(), 3);
      Assert.assertEquals(admin.listTableSnapshots("testListSnapshots", "s(.*)").get().size(), 3);
      Assert.assertEquals(admin.listTableSnapshots("fakeTableName", "snap(.*)").get().size(), 0);
      Assert.assertEquals(admin.listTableSnapshots("test(.*)", "snap(.*)[1|3]").get().size(), 2);

    } finally {
      syncAdmin.deleteSnapshot(snapshotName1);
      syncAdmin.deleteSnapshot(snapshotName2);
      syncAdmin.deleteSnapshot(snapshotName3);
      TEST_UTIL.deleteTable(tableName);
    }
  }

  @Test
  public void testDeleteSnapshots() throws Exception {
    String snapshotName1 = "snapshotName1";
    String snapshotName2 = "snapshotName2";
    String snapshotName3 = "snapshotName3";
    TableName tableName = TableName.valueOf("testDeleteSnapshots");

    try {
      Table table = TEST_UTIL.createTable(tableName, Bytes.toBytes("f1"));
      for (int i = 0; i < 3000; i++) {
        table.put(new Put(Bytes.toBytes(i)).addColumn(Bytes.toBytes("f1"), Bytes.toBytes("cq"),
          Bytes.toBytes(i)));
      }
      Assert.assertEquals(admin.listSnapshots().get().size(), 0);

      admin.snapshot(snapshotName1, tableName).get();
      admin.snapshot(snapshotName2, tableName).get();
      admin.snapshot(snapshotName3, tableName).get();
      Assert.assertEquals(admin.listSnapshots().get().size(), 3);

      admin.deleteSnapshot(snapshotName1).get();
      Assert.assertEquals(admin.listSnapshots().get().size(), 2);

      admin.deleteSnapshots("(.*)abc").get();
      Assert.assertEquals(admin.listSnapshots().get().size(), 2);

      admin.deleteSnapshots("(.*)1").get();
      Assert.assertEquals(admin.listSnapshots().get().size(), 2);

      admin.deleteTableSnapshots("(.*)", "(.*)1").get();
      Assert.assertEquals(admin.listSnapshots().get().size(), 2);

      admin.deleteTableSnapshots("(.*)", "(.*)2").get();
      Assert.assertEquals(admin.listSnapshots().get().size(), 1);

      admin.deleteTableSnapshots("(.*)", "(.*)3").get();
      Assert.assertEquals(admin.listSnapshots().get().size(), 0);
    } finally {
      TEST_UTIL.deleteTable(tableName);
    }
  }
}