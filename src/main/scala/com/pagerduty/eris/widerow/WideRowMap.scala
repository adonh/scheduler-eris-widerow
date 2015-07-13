package com.pagerduty.eris.widerow

import com.pagerduty.eris._

import scala.concurrent.ExecutionContextExecutor

/**
 * Implements basic WideRowMap.
 */
class WideRowMap[RowKey, ColName, ColValue](
    val columnFamilyModel: ColumnFamilyModel[RowKey, ColName, ColValue],
    pageSize: Int = 25)(implicit executor: ExecutionContextExecutor)
  extends com.pagerduty.widerow.WideRowMap[RowKey, ColName, ColValue](
    new WideRowDriverImpl(columnFamilyModel, executor),
    pageSize)
