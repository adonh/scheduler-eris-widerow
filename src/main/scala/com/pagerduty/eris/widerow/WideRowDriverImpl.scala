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

import com.netflix.astyanax.model.Column
import com.netflix.astyanax.serializers.LongSerializer
import com.pagerduty.eris._
import com.netflix.astyanax.util.RangeBuilder
import com.pagerduty.widerow.{ Entry, EntryColumn, WideRowDriver }
import scala.collection.JavaConversions._
import com.pagerduty.eris.FutureConversions._
import scala.concurrent.{ExecutionContextExecutor, Future}


/**
 * Eris implementation of WideRowDriver interface.
 *
 * Example:
 * {{{
 * import com.pagerduty.eris.ColumnFamilyModel
 * import com.pagerduty.eris.widerow.WideRowDriverImpl
 *
 * class WideRowIndex[RowKey, ColName, ColValue](
 *    val columnFamilyModel: ColumnFamilyModel[RowKey, ColName, ColValue],
 *    pageSize: Int = 25)(implicit executor: ExecutionContextExecutor)
 *  extends com.pagerduty.widerow.WideRowIndex[RowKey, ColName, ColValue](
 *    new WideRowDriverImpl(columnFamilyModel, executor),
 *    pageSize)
 * }}}
 *
 * @param columnFamilyModel target column family model
 * @param executor executor context for async tasks
 * @tparam RowKey column family row key
 * @tparam ColName column family column name
 * @tparam ColValue column family columna value
 */
class WideRowDriverImpl[RowKey, ColName, ColValue](
    val columnFamilyModel: ColumnFamilyModel[RowKey, ColName, ColValue],
    implicit val executor: ExecutionContextExecutor)
  extends WideRowDriver[RowKey, ColName, ColValue]
{

  // This is a workaround to support counter columns that only implement getLongValue()
  private def readValue(column: Column[ColName]): ColValue = {
    if (columnFamilyModel.colValueSerializer.isInstanceOf[LongSerializer]) {
      column.getLongValue().asInstanceOf[ColValue]
    }
    else {
      column.getValue(columnFamilyModel.colValueSerializer)
    }
  }

  def fetchData(
      rowKey: RowKey,
      ascending: Boolean,
      from: Option[ColName],
      to: Option[ColName],
      limit: Int)
  : Future[IndexedSeq[Entry[RowKey, ColName, ColValue]]] = {
    val range = {
      val builder = new RangeBuilder().setLimit(limit).setReversed(!ascending)
      if (from.isDefined) builder.setStart(from.get, columnFamilyModel.colNameSerializer)
      if (to.isDefined) builder.setEnd(to.get, columnFamilyModel.colNameSerializer)
      builder.build()
    }

    val futureResult = columnFamilyModel.keyspace
      .prepareQuery(columnFamilyModel.columnFamily)
      .getKey(rowKey)
      .autoPaginate(false)
      .withColumnRange(range)
      .executeAsync()

    futureResult.map { operationResult =>
      val result = operationResult.getResult.toIndexedSeq
      for (column <- result) yield {
        Entry(
          rowKey,
          EntryColumn(
            column.getName,
            readValue(column),
            Option(column.getTtl).filter(_ != 0)))
      }
    }
  }

  def update(
      rowKey: RowKey,
      remove: Iterable[ColName],
      insert: Iterable[EntryColumn[ColName, ColValue]])
  : Future[Unit] = {
    val serializer = columnFamilyModel.colValueSerializer
    val batch = columnFamilyModel.keyspace.prepareMutationBatch()
    val rowBatch = batch.withRow(columnFamilyModel.columnFamily, rowKey)
    for (colName <- remove) {
      rowBatch.deleteColumn(colName)
    }
    for (column <- insert) {
      val ttl = column.ttlSeconds.map(seconds => seconds: java.lang.Integer).orNull
      rowBatch.putColumn(column.name, serializer.toByteBuffer(column.value), ttl)
    }
    batch.executeAsync().map(_ => Unit)
  }

  override def deleteRow(rowKey: RowKey): Future[Unit] = {
    val batch = columnFamilyModel.keyspace.prepareMutationBatch()
    batch.withRow(columnFamilyModel.columnFamily, rowKey).delete()
    batch.executeAsync().map(_ => Unit)
  }
}
