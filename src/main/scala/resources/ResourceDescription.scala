/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package viper.silicon.resources

/**
  * A resource description contains the assumptions that are added at several points during verificaton.
  * <ul>
  *   <li>Instance properties are assumed when a new chunk is added to the heap. They describe properties of single chunks
  *   and may contain the <code>This()</code> literal. </li>
  *   <li>Static properties are also assumed when a new chunk is added to the heap. They descripe properties of the whole heap
  *   and are not allowed to contain the <code>This()</code> literal.</li>
  *   <li>Delayed properties are static properties that are only assumed after a state consolidation.</li>
  *  </ul>
  */
trait ResourceDescription {
  val instanceProperties: Seq[Property]
  val staticProperties: Seq[Property]
  val delayedProperties: Seq[Property]
}

abstract class BasicDescription extends ResourceDescription {
  override val instanceProperties = Seq(permAtLeastZero)
  override val staticProperties = Seq[Property]()
  override val delayedProperties = Seq(valNeqImpliesLocNeq)

  def permAtLeastZero: Property = {
    val description = "Permissions are non-negative"
    Property(GreaterThanEquals(PermissionAccess(This()), PermissionLiteral(0, 1)), "permAtLeastZero", description)
  }

  // TODO: this does not work for singleton quantified chunks for some reason
  def valNeqImpliesLocNeq: Property = {
    val c1 = ChunkVariable("c1")
    val c2 = ChunkVariable("c2")
    val condition = Not(Equals(ValueAccess(c1), ValueAccess(c2)))
    val expression = ForEach(Seq(c1, c2), Check(condition, Not(Equals(ArgumentAccess(c1), ArgumentAccess(c2))), True()))
    val description = "Different values imply different arguments"
    Property(expression, "valNeqImpliesLocNeq", description)
  }
}

class PredicateDescription extends BasicDescription {
  override def toString = "Predicate"
}

class FieldDescription extends BasicDescription {
  override val instanceProperties = Seq(permAtLeastZero, permAtMostOne, permImpliesNonNull)
  override val delayedProperties = Seq(permUpperBoundDiseq, valNeqImpliesLocNeq)

  def permAtMostOne: Property = {
    val description = "Field permissions are at most one"
    Property(LessThanEquals(PermissionAccess(This()), PermissionLiteral(1, 1)), "permAtMostOne", description)
  }

  def permImpliesNonNull: Property = {
    val exp = Implies(GreaterThan(PermissionAccess(This()), PermissionLiteral(0, 1)), Not(Equals(ArgumentAccess(This()), Null())))
    Property(exp, "permImpliesNonNull", "Permission implies non-null receiver")
  }

  def permUpperBoundDiseq: Property = {
    val c1 = ChunkVariable("c1")
    val c2 = ChunkVariable("c2")
    val perm1 = PermissionAccess(c1)
    val perm2 = PermissionAccess(c2)
    val greaterThan = GreaterThan(Plus(perm1, perm2), PermissionLiteral(1, 1))
    val neq = Not(Equals(ArgumentAccess(c1), ArgumentAccess(c2)))
    val description = "Added permissions greater than one imply non-equal receivers"
    Property(ForEach(Seq(c1, c2), Check(greaterThan, neq, True())), "permUpperBoundDiseq", description)
  }

  override def toString = "Field"
}

class MagicWandDescription extends ResourceDescription {
  override val instanceProperties = Seq(permAtLeastZero)
  override val staticProperties = Seq[Property]()
  override val delayedProperties = Seq[Property]()

  def permAtLeastZero: Property = {
    val description = "Permissons are non-negative"
    Property(GreaterThanEquals(PermissionAccess(This()), PermissionLiteral(0, 1)), "permAtLeastZero", description)
  }

  override def toString = "Magic Wand"
}

