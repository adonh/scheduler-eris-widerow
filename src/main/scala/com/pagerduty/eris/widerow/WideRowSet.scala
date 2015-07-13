package com.pagerduty.eris.widerow

import com.pagerduty.eris._

import scala.concurrent.ExecutionContextExecutor


/**
 * Implements basic WideRowSet.
 */
class WideRowSet[RowKey, ColName, ColValue](
    val columnFamilyModel: ColumnFamilyModel[RowKey, ColName, Array[Byte]],
    pageSize: Int = 25)(implicit executor: ExecutionContextExecutor)
  extends com.pagerduty.widerow.WideRowSet[RowKey, ColName](
    new WideRowDriverImpl(columnFamilyModel, executor),
    pageSize)
