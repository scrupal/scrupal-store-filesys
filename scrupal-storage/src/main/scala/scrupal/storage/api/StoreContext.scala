/** ********************************************************************************************************************
  * This file is part of Scrupal, a Scalable Reactive Content Management System.                                       *
  *                                                                                                     *
  * Copyright © 2015 Reactific Software LLC                                                                            *
  *                                                                                                     *
  * Licensed under the Apache License, Version 2.0 (the "License");  you may not use this file                         *
  * except in compliance with the License. You may obtain a copy of the License at                                     *
  *                                                                                                     *
  * http://www.apache.org/licenses/LICENSE-2.0                                                                  *
  *                                                                                                     *
  * Unless required by applicable law or agreed to in writing, software distributed under the                          *
  * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,                          *
  * either express or implied. See the License for the specific language governing permissions                         *
  * and limitations under the License.                                                                                 *
  * ********************************************************************************************************************
  */

package scrupal.storage.api

import java.io.Closeable
import java.net.URI
import scrupal.utils.{ Registry, ScrupalComponent, Registrable }
import scala.concurrent.{Future, ExecutionContext}

/** Context For Storage
  *
  * When connecting to a storage provider, subclasses of this instance provide all the details
  */
case class StoreContext(
  id: Symbol,
  driver: StorageDriver,
  store: Store)(implicit val ec: ExecutionContext
) extends Registrable[StoreContext] with Closeable with ScrupalComponent {
  def registry = StoreContext
  def uri : URI = store.uri

  def withStore[T](f : (Store) ⇒ T) : T = { f(store) }

  def hasSchema(name: String) : Boolean = store.hasSchema(name)

  def addSchema(design: SchemaDesign)(implicit ec: ExecutionContext) : Future[Schema] = {
    store.addSchema(design)
  }

  def dropSchema(name : String)(implicit ec: ExecutionContext) : Future[WriteResult] = {
    store.dropSchema(name)
  }

  def withSchema[T](schema : String)(f : (Schema) ⇒ T) : T = {
    store.withSchema(schema)(f)
  }

  def withCollection[T, S <: Storable](schema : String, collection : String)(f : (Collection[S]) ⇒ T) : T = {
    store.withCollection(schema,collection)(f)
  }

  def close() = {
    // Nothing to do
  }
}

object StoreContext extends Registry[StoreContext] with ScrupalComponent {
  val registrantsName : String = "storageContext"
  val registryName : String = "StorageContexts"
}

