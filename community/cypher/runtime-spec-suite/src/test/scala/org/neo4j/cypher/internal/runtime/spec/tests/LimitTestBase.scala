/*
 * Copyright (c) 2002-2019 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.cypher.internal.runtime.spec.tests

import org.neo4j.cypher.internal.logical.plans.Ascending
import org.neo4j.cypher.internal.runtime.spec._
import org.neo4j.cypher.internal.{CypherRuntime, RuntimeContext}

abstract class LimitTestBase[CONTEXT <: RuntimeContext](edition: Edition[CONTEXT],
                                                        runtime: CypherRuntime[CONTEXT],
                                                        sizeHint: Int
                                                       ) extends RuntimeTestSuite[CONTEXT](edition, runtime) {

  test("limit 0") {
    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("x")
      .limit(0)
      .input(variables = Seq("x"))
      .build()

    val input = inputColumns(100000, 3, identity).stream()

    // then
    val runtimeResult = execute(logicalQuery, runtime, input)
    runtimeResult should beColumns("x").withNoRows()

    input.hasMore should be(true)
  }

  test("limit -1") {
    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("x")
      .limit(-1)
      .input(variables = Seq("x"))
      .build()

    val input = inputColumns(100000, 3, identity).stream()

    // then
    val runtimeResult = execute(logicalQuery, runtime, input)
    runtimeResult should beColumns("x").withNoRows()

    input.hasMore should be(true)
  }

  test("limit higher than amount of rows") {
    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("x")
      .limit(Int.MaxValue)
      .input(variables = Seq("x"))
      .build()

    val input = inputColumns(sizeHint, 3, identity)

    // then
    val runtimeResult = execute(logicalQuery, runtime, input)
    runtimeResult should beColumns("x").withRows(input.flatten)
  }

  test("should support limit") {
    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("x")
      .limit(10)
      .input(variables = Seq("x"))
      .build()

    val input = inputColumns(100000, 3, identity).stream()

    // then
    val runtimeResult = execute(logicalQuery, runtime, input)
    runtimeResult should beColumns("x").withRows(rowCount(10))

    input.hasMore should be(true)
  }

  test("should support apply-limit") {
    // given
    val nodesPerLabel = 100
    val (aNodes, _) = bipartiteGraph(nodesPerLabel, "A", "B", "R")

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("x")
      .apply()
      .|.limit(10)
      .|.expandAll("(x)-->(y)")
      .|.argument()
      .allNodeScan("x")
      .build()

    // then
    val runtimeResult = execute(logicalQuery, runtime)

    runtimeResult should beColumns("x").withRows(singleColumn(aNodes.flatMap(n => List().padTo(10, n))))
  }

  test("should support reduce -> limit on the RHS of apply") {
    // given
    val nodesPerLabel = 100
    val (aNodes, bNodes) = bipartiteGraph(nodesPerLabel, "A", "B", "R")

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("x", "y")
      .apply()
      .|.limit(10)
      .|.sort(Seq(Ascending("y")))
      .|.expandAll("(x)-->(y)")
      .|.argument()
      .allNodeScan("x")
      .build()

    // then
    val runtimeResult = execute(logicalQuery, runtime)

    val expected = for{
      x <- aNodes
      y <- bNodes.sortBy(_.getId).take(10)
    } yield Array[Any](x, y)

    runtimeResult should beColumns("x", "y").withRows(expected)
  }

  test("should support limit -> reduce on the RHS of apply") {
    // given
    val NODES_PER_LABEL = 100
    val LIMIT = 10
    val (aNodes, bNodes) = bipartiteGraph(NODES_PER_LABEL, "A", "B", "R")

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("x", "y")
      .apply()
      .|.sort(Seq(Ascending("y")))
      .|.limit(LIMIT)
      .|.expandAll("(x)-->(y)")
      .|.argument()
      .allNodeScan("x")
      .build()

    // then
    val runtimeResult = execute(logicalQuery, runtime)
    runtimeResult should beColumns("x", "y").withRows(groupedBy(NODES_PER_LABEL, LIMIT, "x").asc("y"))
  }

  test("should support chained limits") {
    // given
    val nodesPerLabel = 100
    bipartiteGraph(nodesPerLabel, "A", "B", "R")

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("a2")
      .limit(10)
      .expandAll("(b2)<--(a2)")
      .limit(10)
      .expandAll("(a1)-->(b2)")
      .limit(10)
      .expandAll("(b1)<--(a1)")
      .limit(10)
      .expandAll("(x)-->(b1)")
      .allNodeScan("x")
      .build()

    // then
    val runtimeResult = execute(logicalQuery, runtime)
    runtimeResult should beColumns("a2").withRows(rowCount(10))
  }
}
