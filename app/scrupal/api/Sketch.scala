package scrupal.api

import scala.slick.driver._
import scala.slick.jdbc.{StaticQuery, StaticQuery0}
import scala.slick.session.{Session, Database}
import play.api.{Configuration, Logger}
import java.util.Properties
import com.typesafe.config.ConfigObject

object SupportedDatabases extends Enumeration {
  type Kind = Value
  val H2 = Value
  val MySQL = Value
  val SQLite = Value
  val Postgres = Value

  def forJDBCUrl(url: String) : Option[Kind] = {
    url match {
      case s if s.startsWith("jdbc:h2") => Some(H2)
      case s if s.startsWith("jdbc:mysql") => Some(MySQL)
      case s if s.startsWith("jdbc:sqllite:") => Some(SQLite)
      case s if s.startsWith("jdbc:postgresql:") => Some(Postgres)
      case _ => None
    }
  }

  def defaultDriverFor(kind: Kind) : String = {
    kind match {
      case H2 =>  "org.h2.Driver"
      case MySQL => "com.mysql.jdbc.Driver"
      case SQLite =>  "org.sqlite.JDBC"
      case Postgres => "org.postgresql.Driver"
      case _ => Logger.warn("Unrecognized SupportedDatabase.Kind !"); "not.a.db.driver"
    }
  }
  def defaultDriverFor(kind: Option[Kind]) : String = {
    kind match {
      case Some(x) => defaultDriverFor(x)
      case None => "org.h2.Driver" // Just because that's the default one Play uses
    }
  }
}

/**
 * A Sketch is a simple trait that sketches out some basic things we need to know about a particular database
 * implementation. This is similar to Slick's Profiles, but we call them Sketches here because they're a related but
 * different concept. The subclasses of this trait are concrete, one for each kind of database. Every Sketch
 * has a member that is the Slick ExtendedProfile to be used, specifies  the schema name to be used
 * for the scrupal tables, knows the driver class name, can instantiate that driver, and knows how to create the
 * schema in which the Scrupal tables live. This allows administrators to keep scrupal's tables separate from other
 * modules added to scrupal, while keeping all the data in the same database
 */
abstract class Sketch (
  val kind: SupportedDatabases.Kind,
  val url: String,
  val driver: String,
  val profile : ExtendedProfile,
  val user : Option[String] = None,
  val pass : Option[String] = None,
  val schema : Option[String] = None,
  val properties : Option[Properties] = None
) {
  val database: Database = Database.forURL(url, user.getOrElse(""), pass.getOrElse(""),
    properties.getOrElse(new Properties()), driver)

  def driverClass = Class.forName(driver).newInstance()

  def makeSession = {
    Logger.debug("Creating DB Session for " + driver)
    database.createSession()
  }
  def makeSchema : StaticQuery0[Int] = throw new NotImplementedError("Making DB Schema for " + driver )
  override def toString = { kind + ":{" + driver + ", " + schema + ", " + profile + ", " + url + "}"}

  def withSession[T](f: Session => T): T = database.withSession(f)

}

/**
 * The Sketch for H2 Database
 * @param schema - optional schema name for the profile
 */
case class H2Sketch(
  override val kind: SupportedDatabases.Kind,
  override val url: String,
  override val driver: String,
  override val user : Option[String] = None,
  override val pass : Option[String] = None,
  override val schema : Option[String] = None,
  override val properties : Option[Properties] = None
) extends Sketch(kind, url, driver, H2Driver, user, pass, schema, properties) {
  override def makeSchema : StaticQuery0[Int] = StaticQuery.u + "SET TRACE_LEVEL_FILE 4; CREATE SCHEMA IF NOT EXISTS " + schema.get
}

/**
 * The Sketch for H2 Database
 * @param schema - optional schema name for the profile
 */
case class MySQLSketch (
  override val kind: SupportedDatabases.Kind,
  override val url: String,
  override val driver: String,
  override val user : Option[String] = None,
  override val pass : Option[String] = None,
  override val schema : Option[String] = None,
  override val properties : Option[Properties] = None
) extends Sketch(kind, url, driver, MySQLDriver, user, pass, schema, properties) {
}

/**
 * The Sketch for SQLite Database
 * @param schema - optional schema name for the profile
 */
class SQLiteSketch (
  override val kind: SupportedDatabases.Kind,
  override val url: String,
  override val driver: String,
  override val user : Option[String] = None,
  override val pass : Option[String] = None,
  override val schema : Option[String] = None,
  override val properties : Option[Properties] = None
) extends Sketch(kind, url, driver, SQLiteDriver, user, pass, schema, properties) {
}

/**
 * The Sketch for Postgres Database
 * @param schema - optional schema name for the profile
 */
class PostgresSketch (
  override val kind: SupportedDatabases.Kind,
  override val url: String,
  override val driver: String,
  override val user : Option[String] = None,
  override val pass : Option[String] = None,
  override val schema : Option[String] = None,
  override val properties : Option[Properties] = None
) extends Sketch(kind, url, driver, PostgresDriver, user, pass, schema, properties) {
}

object Sketch {

  /** Make a Sketch From a ConfigObject
    * @param config A ConfigObject presumably extracted with `config.getObject("db")`
    * @return The corrspondingly configured Sketch
    */
   def apply(config: Configuration) : Sketch = {
    val url = config.getString("url").getOrElse("")
    val driver = config.getString("driver")
    val user = config.getString("user")
    val pass = config.getString("pass")
    val schemaName = config.getString("schema")
    Sketch(url, user, pass, schemaName, driver)
  }

  /** Convert a URL into a Database Sketch by examining its prefix
    * @param url - The JDBC Connection URL for the database we should connect to
    * @return A Sketch for the corresponding database type
    */
  def apply(url: String, user: Option[String] = None, pass: Option[String] = None, schema: Option[String] = None,
    driver: Option[String] = None, properties: Option[Properties] = None) : Sketch = {
    Logger.debug("Creating Sketch With: " + url )

    val kind = SupportedDatabases.forJDBCUrl(url)

    val d = driver.getOrElse(SupportedDatabases.defaultDriverFor(kind))

    import SupportedDatabases._
    kind match {
      case Some(H2) => new H2Sketch(kind.get, url, d,  user, pass, schema, properties)
      case Some(MySQL) => new MySQLSketch(kind.get, url, d,  user, pass, schema, properties)
      case Some(SQLite) => new SQLiteSketch(kind.get, url, d,  user, pass, schema, properties)
      case Some(Postgres) => new PostgresSketch(kind.get, url, d,  user, pass, schema, properties)
      case Some(_) => throw new UnsupportedOperationException("JDBC Url (" + url + ") is not for a supported database.")
      case None => throw new UnsupportedOperationException("JDBC Url (" + url + ") is not for a supported database.")
    }
  }
}

