package com.pagerduty.eris.widerow


import com.netflix.astyanax.serializers.ComparatorType
import com.pagerduty.eris.{ColumnFamilySettings, ColumnFamilyModel, TestClusterCtx}
import com.pagerduty.eris.schema.SchemaLoader
import com.pagerduty.eris.serializers._
import com.pagerduty.widerow.EntryColumn
import org.scalatest.{Matchers, FreeSpec}
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration


class LongAndCounterSpec extends FreeSpec with Matchers {
  val cluster = TestClusterCtx.cluster

  "Correctly read from column families with long values" in {
    val keyspace = cluster.getKeyspace("LongAndCounterSpec" + Thread.currentThread.getId)
    val columnFamily = ColumnFamilyModel[String, String, Long](keyspace, "longTest")
    val schemaLoader = new SchemaLoader(cluster, Set(columnFamily.columnFamilyDef(cluster)))
    schemaLoader.dropSchema()
    schemaLoader.loadSchema()
    val driver = new WideRowDriverImpl(columnFamily, global)

    val rowKey = "longTestRow"
    val data = Set(EntryColumn("col1", 1L), EntryColumn("col2", 2L))
    Await.result(driver.update(rowKey, Set.empty, data), Duration.Inf)

    val res = Await.result(driver.fetchData(rowKey, true, None, None, 100), Duration.Inf)
    res.map(_.column).toSet shouldBe data
  }

  "Correctly read from column families with counter values" in {
    val keyspace = cluster.getKeyspace("LongAndCounterSpec" + Thread.currentThread.getId)
    val columnFamily = ColumnFamilyModel[String, String, Long](
      keyspace, "counterTest",
      ColumnFamilySettings(
        colValueValidatorOverride = Some(ComparatorType.COUNTERTYPE.getClassName)
      )
    )
    val schemaLoader = new SchemaLoader(cluster, Set(columnFamily.columnFamilyDef(cluster)))
    schemaLoader.dropSchema()
    schemaLoader.loadSchema()
    val driver = new WideRowDriverImpl(columnFamily, global)

    val rowKey = "longTestRow"
    val data = Set(EntryColumn("col1", 1L), EntryColumn("col2", 2L))

    val batch = keyspace.prepareMutationBatch()
    val batchWithRow = batch.withRow(columnFamily.columnFamily, rowKey)
    data.foreach(d => batchWithRow.incrementCounterColumn(d.name, d.value))
    batch.execute()

    val res = Await.result(driver.fetchData(rowKey, true, None, None, 100), Duration.Inf)
    res.map(_.column).toSet shouldBe data
  }
}
