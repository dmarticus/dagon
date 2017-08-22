/*
 Copyright 2014 Twitter, Inc.
 Copyright 2017 Stripe, Inc.

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */

package com.stripe.dagon

/**
 * Expr[N, T] is an expression of a graph of container nodes N[_] with
 * result type N[T]. These expressions are like the Literal[T, N] graphs
 * except that functions always operate with an indirection of a Id[T]
 * where N[T] is the type of the input node.
 *
 * Nodes can be deleted from the graph by replacing an Expr at Id = idA
 * with Var(idB) pointing to some upstream node.
 *
 * To add nodes to the graph, add depth to the final node returned in
 * a Unary or Binary expression.
 *
 * TODO: see the approach here: https://gist.github.com/pchiusano/1369239
 * Which seems to show a way to do currying, so we can handle general
 * arity
 */
sealed trait Expr[N[_], T] {
  def evaluate(idToExp: HMap[Id, Expr[N, ?]]): N[T] =
    Expr.evaluate(idToExp, this)
}

object Expr {

  case class Const[N[_], T](value: N[T]) extends Expr[N, T] {
    override def evaluate(idToExp: HMap[Id, Expr[N, ?]]): N[T] =
      value
  }

  case class Var[N[_], T](name: Id[T]) extends Expr[N, T]

  case class Unary[N[_], T1, T2](arg: Id[T1], fn: N[T1] => N[T2]) extends Expr[N, T2]

  case class Binary[N[_], T1, T2, T3](arg1: Id[T1],
    arg2: Id[T2],
    fn: (N[T1], N[T2]) => N[T3]) extends Expr[N, T3]

  def evaluate[N[_], T](idToExp: HMap[Id, Expr[N, ?]], expr: Expr[N, T]): N[T] =
    evaluate(idToExp, HMap.empty[Expr[N, ?], N], expr)._2

  private def evaluate[N[_], T](idToExp: HMap[Id, Expr[N, ?]],
    cache: HMap[Expr[N, ?], N],
    expr: Expr[N, T]): (HMap[Expr[N, ?], N], N[T]) = cache.get(expr) match {
    case Some(node) => (cache, node)
    case None => expr match {
      case Const(n) =>
        (cache + (expr -> n), n)
      case Var(id) =>
        val (c1, n) = evaluate(idToExp, cache, idToExp(id))
        (c1 + (expr -> n), n)
      case Unary(id, fn) =>
        val (c1, n1) = evaluate(idToExp, cache, idToExp(id))
        val n2 = fn(n1)
        (c1 + (expr -> n2), n2)
      case Binary(id1, id2, fn) =>
        val (c1, n1) = evaluate(idToExp, cache, idToExp(id1))
        val (c2, n2) = evaluate(idToExp, c1, idToExp(id2))
        val n3 = fn(n1, n2)
        (c2 + (expr -> n3), n3)
    }
  }
}