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

package scrupal.models.db

import scala.slick.lifted.DDL
import scrupal.api._
import scrupal.api.Component
import java.sql.Clob
import play.api.libs.json.{Json, JsObject}

trait CoreComponent extends Component {

  import sketch.profile.simple._

  // Get the TypeMapper for DateTime
  import CommonTypeMappers._

  // Need mappings for Version -> ID, Module -> ID, ENtitTpype -> ID

  implicit  val JsObjectMapper = MappedTypeMapper.base[JsObject,Clob] (
    { j => { val result = session.conn.createClob(); result.setString(1, Json.stringify(j)); result } },
    { c => Json.parse( c.getSubString(1, c.length().toInt)).asInstanceOf[JsObject] }
  )

  implicit val VersionMapper = MappedTypeMapper.base[Version,String] (
    { v:Version => v.toString },
    { s:String => {
      val parts= s.split("\\.")
      assert(parts.size==3)
      Version(parts(0).toInt, parts(1).toInt, parts(2).toInt)
    }}
  )

  object Types extends ScrupalTable[EssentialType]("types")  {
    def id = column[TypeIdentifier](nm("id"), O.PrimaryKey)
    def description = column[String](nm("description"))
    def moduleId = column[ModuleIdentifier](nm("moduleId"))
    def moduleId_fKey = foreignKey(fkn(Modules.tableName), moduleId, Modules)(_.id)
    def * = id ~ description ~ moduleId <> (EssentialType.tupled, EssentialType.unapply _)
    lazy val fetchByIDQuery = for { id <- Parameters[TypeIdentifier] ; ty <- this if ty.id === id } yield ty

    def fetch(id: TypeIdentifier) : Option[EssentialType] =  fetchByIDQuery(id).firstOption

    def findAll() : Seq[EssentialType] = Query(Types).list
    def insert(ty: EssentialType) : TypeIdentifier = { *  insert(ty); ty.id  }
  }

  object Modules extends ScrupalTable[EssentialModule]("modules") {
    def id = column[ModuleIdentifier](nm("id"), O.PrimaryKey)
    def description = column[String](nm("description"))
    def version = column[Version](nm("version"))
    def obsoletes = column[Version](nm("obsoletes"))
    def enabled = column[Boolean](nm("enabled"))
    def * = id ~ description ~ version ~ obsoletes ~ enabled  <>
      (EssentialModule.tupled , EssentialModule.unapply _ )

    lazy val fetchByIDQuery = for { id <- Parameters[ModuleIdentifier] ; ent <- this if ent.id === id } yield ent

    def fetch(id: ModuleIdentifier) : Option[EssentialModule] =  fetchByIDQuery(id).firstOption

    def findAll() : Seq[EssentialModule] = Query(Modules).list

    def insert(mod: EssentialModule) : ModuleIdentifier = { * insert(mod) ; mod.id }
  }

  object Entities extends ScrupalTable[Entity]("entities") with EnablableThingTable[Entity] {
    def typeId = column[TypeIdentifier](nm("typeId"))
    def typeId_fkey = foreignKey(fkn("typeId"), typeId, Types)(_.id)
    def payload = column[JsObject](nm("payload"), O.NotNull)
    def forInsert = name ~ description ~ typeId ~ payload ~ enabled
    def * = forInsert ~ modified.? ~ created.? ~ id.?  <>
      (Entity.tupled, Entity.unapply _)
  }

  object Sites extends ScrupalTable[Site]("sites") with EnablableThingTable[Site] {
    def listenPort = column[Short](nm("listenPort"))
    def listenPort_index = index(idx("listenPort"), listenPort, unique=true)
    def urlDomain = column[String](nm("urlDomain"))
    def urlPort = column[Short](nm("urlPort"))
    def urlHttps = column[Boolean](nm("urlHttps"))
    def * = name ~ description ~ listenPort ~ urlDomain ~ urlPort ~ urlHttps ~ enabled ~ modified.? ~ created.? ~
            id.? <> (Site.tupled, Site.unapply _)
  }

  def coreDDL : DDL = Types.ddl ++ Modules.ddl ++ Entities.ddl ++ Sites.ddl
}
