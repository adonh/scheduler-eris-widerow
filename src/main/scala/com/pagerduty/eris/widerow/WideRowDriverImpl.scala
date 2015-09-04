package com.pagerduty.eris.widerow

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
            column.getValue(columnFamilyModel.colValueSerializer),
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
