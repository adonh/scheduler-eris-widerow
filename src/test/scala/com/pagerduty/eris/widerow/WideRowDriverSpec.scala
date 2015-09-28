package com.pagerduty.eris.widerow

import com.pagerduty.eris.{ColumnFamilyModel, TestClusterCtx}
import com.pagerduty.eris.schema.SchemaLoader
import com.pagerduty.eris.serializers._
import com.pagerduty.widerow.{Entry, EntryColumn, WideRowDriver}
import org.scalatest.{Outcome, Matchers}
import org.scalatest.fixture.FreeSpec
import scala.concurrent.{Future, Await}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration


class WideRowDriverSpec extends FreeSpec with Matchers {
  type FixtureParam = WideRowDriver[String, Int, String]

  override def withFixture(test: OneArgTest): Outcome = {
    val cluster = TestClusterCtx.cluster
    val keyspace = cluster.getKeyspace("WideRowDriverSpec" + Thread.currentThread.getId)
    val columnFamily = ColumnFamilyModel[String, Int, String](keyspace, "driverTest")
    val schemaLoader = new SchemaLoader(cluster, Set(columnFamily.columnFamilyDef(cluster)))

    try {
      schemaLoader.dropSchema()
      schemaLoader.loadSchema()
      val driver = new WideRowDriverImpl(columnFamily, global)
      withFixture(test.toNoArgTest(driver))
    }
    finally {
      schemaLoader.dropSchema()
    }
  }

  def wait[T](future: Future[T]): T = Await.result(future, Duration.Inf)

  val rowKey = "testRow"
  val columns = Seq(2, 3, 5, 7, 11).map(i => EntryColumn(i, s"v$i"))
  val entries = columns.map(c => Entry(rowKey, c))

  "Eris wide row driver should" - {
    "insert, delete, and drop correctly" in { driver =>
      def reloaded() = wait(driver.fetchData(rowKey, true, None, None, columns.size*10))

      // Sanity check.
      reloaded() shouldBe empty

      // Insert.
      wait(driver.update(rowKey, Set.empty, columns))
      reloaded() shouldBe entries

      // Remove.
      val removing = columns.take(columns.size / 2).map(_.name).toSet
      wait(driver.update(rowKey, removing, Set.empty))
      reloaded() shouldBe entries.filterNot(entry => removing.contains(entry.column.name))

      // Drop
      wait(driver.deleteRow(rowKey))
      reloaded() shouldBe empty
    }

    "fetch data correctly" in { driver =>
      assert(columns.size == 5) // Hardcoded for this test.
      wait(driver.update(rowKey, Set.empty, columns))

      def expecting(ascending: Boolean, from: Option[Int], to: Option[Int], limit: Int) = {
        val bounded =
          if (ascending) {
            val init = columns
            val lowerBound = if (from.isDefined) init.dropWhile(_.name < from.get) else init
            if (to.isDefined) lowerBound.takeWhile(_.name <= to.get) else lowerBound
          }
          else {
            val init = columns.reverse
            val lowerBound = if (from.isDefined) init.dropWhile(_.name > from.get) else init
            if (to.isDefined) lowerBound.takeWhile(_.name >= to.get) else lowerBound
          }
        bounded.take(limit).map(Entry(rowKey, _))
      }

      object combinations { // Unfiltered total is 2*9*9*5 = 810
        val ascending = Seq(true, false)
        assert(ascending.size == 2)

        val from =
          None +: {
            val last = columns.size - 1
            val beforeFirst = columns(0).name - 1
            val afterLast = columns(last).name + 1

            Seq(Int.MinValue, Int.MaxValue, beforeFirst, afterLast) ++
            Seq(columns(0), columns(1), columns(last - 1), columns(last)).map(_.name)
          }.map(Some(_))

        assert(from.size == 9)

        val to = from
        assert(to.size == 9)

        val limit = Seq(0, 1, 4, 5, 6)
        assert(limit.size == 5)

        // Number of valid combinations when both bounds are defined.
        def definedBoundCount(n: Int) = {
          val first = n
          val last = 1
          val count = n
          (first + last)*count/2 // Arithmetic progression sum.
        }

        // 8*2 are combinations formed with None as one of the bounds,
        // + 1 is when both bounds are None.
        val boundCombinations = definedBoundCount(8) + 8*2 + 1

        // 2 ascending/descending directions
        // 5 limit values
        val total = 2*boundCombinations*5
      }

      def validBounds(ascending: Boolean, fromOp: Option[Int], toOp: Option[Int]) :Boolean = {
        (fromOp, toOp) match {
          case (Some(from), Some(to)) if ascending => to >= from
          case (Some(from), Some(to)) if !ascending => to <= from
          case _ => true
        }
      }

      var count = 0
      for {
        ascending <- combinations.ascending
        from <- combinations.from
        to <- combinations.to
        if (validBounds(ascending, from, to))
        limit <- combinations.limit
      } {
        count += 1
        val loaded = wait(driver.fetchData(rowKey, ascending, from, to, limit))
        val expected = expecting(ascending, from, to, limit)

        if (loaded != expected) {
          fail(
            "Test failed with" +
            " ascending=" + ascending +
            ", from=" + from +
            ", to=" + to +
            ", limit=" + limit +
            ".")
        }
      }

      count shouldBe combinations.total
    }
  }
}
