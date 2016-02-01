/*
 * Copyright (c) 2015, PagerDuty
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted
 * provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions
 * and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided with
 * the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY
 * WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.pagerduty.eris.widerow

import com.netflix.astyanax.serializers.ComparatorType
import com.pagerduty.eris.{ ColumnFamilySettings, ColumnFamilyModel, TestClusterCtx }
import com.pagerduty.eris.schema.SchemaLoader
import com.pagerduty.eris.serializers._
import com.pagerduty.widerow.EntryColumn
import org.scalatest.{ Matchers, FreeSpec }
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
