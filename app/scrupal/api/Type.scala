/**********************************************************************************************************************
 * This file is part of Scrupal a Web Application Framework.                                                          *
 *                                                                                                                    *
 * Copyright (c) 2013, Reid Spencer and viritude llc. All Rights Reserved.                                            *
 *                                                                                                                    *
 * Scrupal is free software: you can redistribute it and/or modify it under the terms                                 *
 * of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License,   *
 * or (at your option) any later version.                                                                             *
 *                                                                                                                    *
 * Scrupal is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied      *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more      *
 * details.                                                                                                           *
 *                                                                                                                    *
 * You should have received a copy of the GNU General Public License along with Scrupal. If not, see either:          *
 * http://www.gnu.org/licenses or http://opensource.org/licenses/GPL-3.0.                                             *
 **********************************************************************************************************************/

package scrupal.api

import scala.util.matching.Regex
import scala.collection.immutable.HashMap
import play.api.libs.json._
import play.api.data.validation.ValidationError
import java.net.{URI, URISyntaxException}
import scrupal.utils.Pluralizer
import scala.collection.mutable


/** A trait to define the validate method for objects that can validate a JsValue */
trait ValueValidator {
  def validate(value : JsValue) : JsResult[Boolean] = {
    value match {
      case JsNull => JsSuccess(true) // JsNull is always valid
      case x: JsUndefined => JsError("JsUndefined can never validate successfully.")
      case x: JsValue => JsError("Unexpected JsValue type: " + x.getClass.getSimpleName )
    }
  }
}

/** Validation trait for JsArray
  * Several of the types defined below expect a JsArray with a particular element type. This trait just provides the
  * method to validate the JsArray against the expected element type.
  */
trait ArrayValidator {

  /** The validation method for validating a JsArray
    * This traverses the array and validates that each element conforms to the `elemType`
    * @param value The JsArray to be validated
    * @param elemType The Type each element of the array should have. By default
    * @return JsSuccess(true) when valid, JsError otherwise
    */
  def validate(value : JsArray, elemType: Type) : JsResult[Boolean] = {
    /// TODO: Improve this to collect and return all the validation issues
    if (value.value exists { elem => !elemType.validate(elem).asOpt.isDefined } )
      JsError("Not all elements match element type")
    else JsSuccess(true)
  }
}

/** Validation trait for JsObject
  * Several of the types defined below expect a JsObject with a particular structure to them. This trait just
  * provides the method to validate the JsObject against a Map of what is expected in that JsObject.
  */
trait ObjectValidator {
  /** The validation method for validating a JsObject
    * This traverses a JsObject and for each field, looks up the associated type in the "structure" and validates that
    * field with the type.
    * @return JsSuccess(true) if all the fields of the JsObject value validate correctly
    */
   def validate( value: JsObject, structure: Map[Symbol,Type]) : JsResult[Boolean] = {
    if (value.value exists {
      case (key,data) => !(structure.contains(Symbol(key)) &&
        structure.get(Symbol(key)).get.validate(data).asOpt.isDefined)
    })
      JsError(JsPath(), ValidationError("Not all elements validate"))
    else
      JsSuccess(true)
  }
}

/** A generic Type used as a placeholder for subclasses that compose types.
  * Note that the name of the Type is a Symbol. Symbols are interned so there is only ever one copy of the name of
  * the type. This is important because type linkage is done by indirectly referencing the name, not the actual type
  * object and name reference equality is the same as type equality.  Scrupal also interns the Type objects that
  * modules provide so they can be looked up by name quickly. This allows inter-module integration without sharing
  * code and provides rapid determination of type equality.
  * @param name The name of the type
  */
abstract class Type (
  val name : Symbol,
  val description: String
) extends ValueValidator {
  require(!name.name.isEmpty)
  require(!description.isEmpty)

  /** By default the validate method returns an error
    * @param value The JSON value to be validated
    * @return JsError, always -- subclasses should override
    */
  override def validate(value : JsValue) : JsResult[Boolean] = JsError("Unimplemented validator.")

  /** The plural of the name of the type.
    * The name given to the type should be the singular form (Color not Colors) but things associated with this type
    * may wish to use the plural form. To ensure that everyone uses the same rules for pluralization,
    * we use our utility [[scrupal.util.Pluralizer]] to make it consistent. This is lazy constructed so there's no
    * cost for it unless it gets used.
    */
  lazy val plural = Pluralizer.pluralize(name)
}

/** The type for indirectly referencing another value of a specific type.
  * By default, all nested types are stored in JSON by value. In this way, complex data structures can be created and
  * validated by composing the various subclasses of Type --- with one exception, this `ReferenceType` class. This
  * Type refers to some other value stored outside of the object in which the `ReferenceType` occurs.
  * @param name The name of the reference type
  * @param description A description of the reference type
  * @param typ The type of the value to which the this object refers
  */
case class ReferenceType (
  override val name : Symbol,
  override val description: String,
  typ : Type
) extends Type(name, description) {
  override def validate(value : JsValue) = {
    value match {
      case v : JsObject => {
        val map = v.value
        if (map.size != 1)
          JsError("Reference object should only have one entry.")
        else {
          val (typ,id) = map.head
          // TODO: validate that typ is a symbol/identifier legal for types and id is correct for an identifier
          JsSuccess(true)
        }
      }
      case x => super.validate(value)
    }
  }
}

/** A String type constrains a string by defining its content with a regular expression and a maximum length.
  *
  * @param name THe name of the string type
  * @param description A brief expression of the string type
  * @param regex The regular expression that specifies legal values for the string type
  * @param maxLen The maximum length of this string type
  */
case class StringType (
  override val name : Symbol,
  override val description : String,
  regex : Regex,
  maxLen : Int = Int.MaxValue
) extends Type(name, description) {
  require(maxLen >= 0)

  override def validate(value : JsValue) = {
    value match {
      case v: JsString => {
        if (v.value.length <= maxLen && regex.findFirstIn(v.value).isDefined) JsSuccess(true)
        else JsError("Value does not match pattern " + regex.pattern.pattern())
      }
      case x => super.validate(value)
    }
  }
}

/** A Range type constrains Long Integers between a minimum and maximum value
  *
  * @param name
  * @param description
  * @param min
  * @param max
  */
case class RangeType (
  override val name : Symbol,
  override val description : String,
  min : Long = Int.MinValue,
  max : Long = Int.MaxValue
) extends Type(name, description) {

  override def validate(value : JsValue) = {
    value match {
      case v: JsNumber => {
        if (v.value < min) JsError("Value smaller than minimum: " + min)
        else if (v.value > max) JsError("Value greater than maximum: " + max)
        else JsSuccess(true)
      }
      case x => super.validate(value)
    }
  }
}

/** A Real type constrains Double values between a minimum and maximum value
  *
  * @param name
  * @param description
  * @param min
  * @param max
  */
case class RealType (
  override val name : Symbol,
  override val description : String,
  min : Double = Double.MinValue,
  max : Double = Double.MaxValue
) extends Type(name, description) {

  override def validate(value : JsValue) = {
    value match {
      case v: JsNumber => {
        if (v.value < min) JsError("Value smaller than minimum: " + min)
        else if (v.value > max) JsError("Value greater than maximum: " + max)
        else JsSuccess(true)
      }
      case x => super.validate(value)
    }
  }
}

/** A BLOB type has a specified MIME content type and a minimum and maximum length
  *
  * @param name
  * @param description
  * @param mime
  * @param minLen
  * @param maxLen
  */
case class BLOBType  (
  override val name : Symbol,
  override val description : String,
  mime : String,
  minLen : Integer = 0,
  maxLen : Long = Long.MaxValue
) extends Type(name, description) {
  assert(minLen >= 0)
  assert(maxLen >= 0)
  assert(mime.contains("/"))

  /** Validation of BLOBs is done via indirection.
    * JSON doesn't permit (efficient) representation of BLOBs so instead of attempting to place the BLOB in the JSON
    * we simply put a URI to the blob in the JSON. This has the benefit of allowing the BLOB loading to occur
    * asynchronously and in a binary manner.
    * @param value The JsString containing the URI of the BLOB
    * @return JsSuccess(true) if the BLOB validates correctly, JsError otherwise
    */
  override def validate(value: JsValue) : JsResult[Boolean] = {
    value match {
      case s: JsString => try { validate(new URI(s.value)) }
        catch { case x: URISyntaxException => JsError(ValidationError("URI Syntax Error: " + x.getMessage())) }
      case x => super.validate(value)
    }
  }

  /** Subclass validator for BLOBs
    * Subclasses are expected to implement this method to validate the content of the BLOB at the provided `uri`. This
    * base implementation just return success which is a suitable assumption for many blobs. For others,
    * its not. The default implementation makes it possible to instantiate this case class instead of it being
    * abstract.
    * @param uri The Uniform Resource Indicator where the BLOB can be loaded from.
    * @return JsSuccess(true)
    */
  def validate(uri: URI) : JsResult[Boolean] = JsSuccess(true)
}

/** An Enum type allows a selection of one enumerator from a list of enumerators.
  * Each enumerator is assigned an integer value.
  */
case class EnumType  (
  override val name : Symbol,
  override val description : String,
  enumerators : HashMap[String, Int]
) extends Type(name, description) {
  require(!enumerators.isEmpty)

  override def validate(value : JsValue) = {
    value match {
      case v: JsString => {
        if (enumerators.contains( v.value )) JsSuccess(true)
        else JsError("Invalid enumeration value")
      }
      case x => super.validate(value)
    }
  }
  def valueOf(enum: String) = enumerators.get(enum)
}

/** Abstract base class of Types that refer to another Type that is the element type of the compound type
  *
  * @param name
  * @param description
  * @param elemType
  */
abstract class CompoundType (
  name : Symbol,
  description : String,
  val elemType : Type
) extends Type(name, description)

/** A List type allows a non-exclusive list of elements of other types to be constructed
  *
  * @param name
  * @param description
  * @param elemType
  */
case class ListType  (
  override val name : Symbol,
  override val description : String,
  override val elemType : Type
) extends CompoundType(name, description, elemType) with ArrayValidator {

  override def validate(value : JsValue) = {
    value match {
      case v: JsArray => validate(v, elemType)
      case x => super.validate(value)
    }
  }
}


/** A Set type allows an exclusive Set of elements of other types to be constructed
  *
  * @param name
  * @param description
  * @param elemType
  */
case class SetType  (
  override val name : Symbol,
  override val description : String,
  override val elemType : Type
) extends CompoundType(name, description, elemType) with ArrayValidator {

  override def validate(value : JsValue) = {
    value match {
      case v: JsArray => {
        val s = v.value
        if (s.distinct.size != s.size)
          JsError("Elements of set are not distinct")
        else
          validate(v, elemType)
      }
      case x => super.validate(value)
    }
  }
}

/** A Map is a set whose elements are named with an arbitrary string
  *
  * @param name
  * @param description
  * @param elemType
  */
case class MapType  (
  override val name : Symbol,
  override val description : String,
  override val elemType : Type
) extends CompoundType(name, description, elemType) {

  override def validate(value : JsValue) = {
    value match {
      case v: JsObject => {
        if (v.value exists { case ( key: String, elem: JsValue ) => key.isEmpty || !elemType.validate(elem).asOpt.isDefined } )
          JsError("Not all elements match element type")
        else
          JsSuccess(true)
      }
      case x => super.validate(value)
    }
  }
}

/** A group of named and typed fields that are related in some way.
  * A trait is the fundamental unit of construction in Scrupal. Traits are analogous to tables in
  * relational databases. That is, they define the names and types of a group of fields (columns) that are in some
  * way related. For example, a street name, city name, country and postal code are fields of an address trait
  * because they are related by the common purpose of specifying a location.
  *
  * Note that Traits, along with List, Set and Map types make the type system fully composable. You can create
  * an arbitrarily complex data structure (not that we recommend that you do so).
  *
  * Traits also have actions associated with them. Actions can
  * - transform the fields of the Trait into arbitrary JSON (map)
  * - access the fields of the Trait to reduce its content (reduce)
  * - mutate the fields of the Trait to produce a new instance of the Trait (mutate)
  * @param name The name of the trait
  * @param description A description of the trait in terms of its purpose or utility
  * @param fields A map of the field name symbols to their Type
  */
case class TraitType (
  override val name : Symbol,
  override val description : String,
  fields : HashMap[Symbol, Type],
  actions: HashMap[Symbol, Action]
) extends Type(name, description) with ObjectValidator {
  require(!fields.isEmpty)

  override def validate(value: JsValue) : JsResult[Boolean] = {
    value match {
      case v: JsObject => validate(v, fields)
      case x => super.validate(value)
    }
  }
}

/** A set of named traits that is the unit of storage.
  * Bundles are simply assemblies of traits. While Traits have tight cohesion, bundles of traits are not cohesive but
  * simply coupled together to describe different aspects of some thing. If Traits can represent an address then a
  * bundle permits a business, for example, to have an address for each of its locations,
  * as well as traits for employees, products, etc. Every Entity is a Bundle and Bundles are the unit of storage
  * @param name The name of the type
  * @param description
  * @param traits
  */
case class BundleType(
  override val name: Symbol,
  override val description: String,
  traits : HashMap[Symbol,TraitType],
  actions : HashMap[Symbol,Action] = HashMap()
) extends Type(name, description) with ObjectValidator {

  override def validate(value: JsValue) : JsResult[Boolean] = {
    value match {
      case v: JsObject => validate(v, traits)
      case x => super.validate(value)
    }
  }
}

/** A utility object for accessing the types registered by modules */
object Type {

  /** Lookup Type by name
    * This handy application allows constructs like `Type('foo)` to return the Scrupal Type for `foo`
    * @param name The Symbol for the type you want to look up
    * @return The corresponding Type object
    */
  def apply(name: Symbol) : Option[Type] = {
    types.get(name) match {
      case Some((ty,mod)) => Some(ty)
      case None => None
    }
  }

  def exists(name: Symbol) : Boolean = types.contains(name)

  /** Allow only objects in the scrupal.api package to insert Type instances into the list of types
    *
    * @param ty
    * @param mod
    * @return The previous incarnation of the type
    */
  private[api] def apply(ty: Type, mod: Module) : Option[(Type,Module)] = {
    this.types.put(ty.name, (ty,mod))
  }

  /** Retrieve the module in which a Type was defined */
  def moduleOf(name: Symbol) : Option[Module] = {
    types.get(name) match {
      case Some((ty,mod)) => Some(mod)
      case _ => None
    }
  }

  /** The mutable list of Types that Scrupal can operate on */
  private val types = mutable.HashMap[Symbol,(Type,Module)]()

}

