/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package viper.silicon.resources

import viper.silicon.state.terms
import viper.silicon.state.terms.Term
import viper.silicon.verifier.Verifier

abstract class PropertyInterpreter(verifier: Verifier) {
  
  protected type Info
  
  protected def buildPathCondition(expression: PropertyExpression, info: Info): Term = expression match {
    // Literals
    case True() => terms.True()
    case False() => terms.False()
    case PermissionLiteral(numerator, denominator) => buildPermissionLiteral(numerator, denominator)
    case Null() => terms.Null()

    // Boolean operators
    case Not(expr) => terms.Not(buildPathCondition(expr, info))
    case And(left, right) => buildAnd(left, right, info)
    case Or(left, right) => buildOr(left, right, info)
    case Implies(left, right) => buildImplies(left, right, info)

    // Universal operator
    case Equals(left, right) => buildEquals(left, right, info)

    // Permission operators
    case Plus(left, right) => buildBinary(terms.PermPlus, left, right, info)
    case Minus(left, right) => buildBinary(terms.PermMinus, left, right, info)
    case Times(left, right) => buildBinary(terms.PermTimes, left, right, info)
    case Div(left, right) => buildBinary(terms.Div, left, right, info)

    case GreaterThanEquals(left, right) => buildBinary(terms.PermAtMost, right, left, info)
    case GreaterThan(left, right) => buildBinary(terms.PermLess, right, left, info)
    case LessThanEquals(left, right) => buildBinary(terms.PermAtMost, left, right, info)
    case LessThan(left, right) => buildBinary(terms.PermLess, left, right, info)

    // Chunk accessors
    case PermissionAccess(cv) => buildPermissionAccess(cv, info)
    case ValueAccess(cv) => buildValueAccess(cv, info)

    // decider / heap interaction
    case Check(condition, thenDo, otherwise) => buildCheck(condition, thenDo, otherwise, info)
    case ForEach(chunkVariables, body) => buildForEach(chunkVariables, body, info)

    // If then else
    case PermissionIfThenElse(condition, thenDo, otherwise) => buildIfThenElse(condition, thenDo, otherwise, info)
    case ValueIfThenElse(condition, thenDo, otherwise) => buildIfThenElse(condition, thenDo, otherwise, info)

    // The only missing cases are chunk expressions which only happen in accessors, and location expressions which
    // only happen in equality expressions and are treated separately
    case e => sys.error( s"An expression of type ${e.getClass} is not allowed here.")
  }

  protected def buildPermissionAccess(chunkVariable: ChunkPlaceholder, info: Info): Term
  protected def buildValueAccess(chunkVariable: ChunkPlaceholder, info: Info): Term

  // Assures short-circuit evalutation of 'and'
  protected def buildAnd(left: PropertyExpression, right: PropertyExpression, info: Info) = {
    buildPathCondition(left, info) match {
      case leftTerm @ terms.False() => leftTerm
      case leftTerm @ _ => terms.And(leftTerm, buildPathCondition(right, info))
    }
  }

  protected def buildOr(left: PropertyExpression, right: PropertyExpression, info: Info) = {
    buildPathCondition(left, info) match {
      case leftTerm @ terms.True() => leftTerm
      case leftTerm @ _ => terms.Or(leftTerm, buildPathCondition(right, info))
    }
  }

  protected def buildImplies(left: PropertyExpression, right: PropertyExpression, info: Info) = {
    buildPathCondition(left, info) match {
      case terms.False() => terms.True()
      case leftTerm @ _ => terms.Implies(leftTerm, buildPathCondition(right, info))
    }
  }

  protected def buildEquals(left: PropertyExpression, right: PropertyExpression, info: Info) = {
    (left, right) match {
      case (Null(), Null()) => terms.True()
      case (ArgumentAccess(cv1), ArgumentAccess(cv2)) =>
        val args1 = extractArguments(cv1, info)
        val args2 = extractArguments(cv2, info)
        if (args1 == args2) {
          // if all arguments are the same, they are definitely equal
          terms.True()
        } else {
          // else return argument-wise equal
          terms.And(args1.zip(args2).map{ case (t1, t2) => t1 === t2 })
        }
      case (ArgumentAccess(cv), Null()) => terms.And(extractArguments(cv, info).map(terms.Equals(_, terms.Null())))
      case (Null(), ArgumentAccess(cv)) => terms.And(extractArguments(cv, info).map(terms.Equals(_, terms.Null())))
      case _ => terms.Equals(buildPathCondition(left, info), buildPathCondition(right, info))
    }
  }

  protected def extractArguments(chunkVariable: ChunkPlaceholder, info: Info): Seq[Term]

  protected def buildPermissionLiteral(numerator: BigInt, denominator: BigInt): Term = {
    require(denominator != 0, "Denominator of permission literal must not be 0")
    (numerator, denominator) match {
      case (n, _) if n == 0 => terms.NoPerm()
      case (n, d) if n == d => terms.FullPerm()
      case (n, d) => terms.FractionPerm(terms.IntLiteral(n), terms.IntLiteral(d))
    }
  }

  protected def buildBinary(builder: (Term, Term) => Term, left: PropertyExpression, right: PropertyExpression, pm: Info) = {
    val leftTerm = buildPathCondition(left, pm)
    val rightTerm = buildPathCondition(right, pm)
    builder(leftTerm, rightTerm)
  }

  protected def buildCheck(condition: BooleanExpression, thenDo: BooleanExpression, otherwise: BooleanExpression, info: Info): Term
  protected def buildForEach(chunkVariables: Seq[ChunkVariable], body: BooleanExpression, pm: Info): Term

  protected def buildIfThenElse(condition: PropertyExpression, thenDo: PropertyExpression, otherwise: PropertyExpression, pm: Info) = {
    val conditionTerm = buildPathCondition(condition, pm)
    val thenDoTerm = buildPathCondition(thenDo, pm)
    val otherwiseTerm = buildPathCondition(otherwise, pm)
    terms.Ite(conditionTerm, thenDoTerm, otherwiseTerm)
  }

}
