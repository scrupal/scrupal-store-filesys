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

package scrupal.controllers

import scala.slick.session.Session
import play.api.mvc._
import scrupal.api.{Site, Module}
import play.api.Configuration
import play.api.Play.current
import scala.concurrent.Future
import scrupal.db.{Sketch, CoreSchema, Alert}
import play.api.mvc.SimpleResult

/** A generic Context trait with just enough defaulted information to render a BasicPage.
  * This allows us, regardless of the error condition of a page, to render custom errors at least in some
  * default way. Classes that mix in Context will override and extend what's available in their context.
  */
trait Context extends RequestHeader {
  val config : Configuration = current.configuration
  val appName : String = "Scrupal"
  val themeProvider : String = "scrupal"
  val themeName : String = "amelia"
  val user : String = "guest"
  def alerts : Seq[Alert] = Seq()
  def suggestURL : String = routes.Home.index.url
}

/** A Basic context which just mixes the Context trait with the WrappedRequest.
  * This information is generally overridden by subclasses, but this is the minimum we need to render a page. Note that
  * because this is a WrappedRequest[A], all the fields of Request are fields of this class too.
  * @param request The request upon which the context is based
  * @tparam A The type of content for the request
  */
class BasicContext[A](request: Request[A]) extends WrappedRequest[A](request) with Context

/** A Site context which pulls the information necessary to render something for a site.
  * SiteContext is presumed to be created with a SiteAction from the ContextProvider which will only create on if the
  * conditions are right, otherwise a BasicContext is created and an error returned.
  * @param site The site that this request should be processed by
  * @param request The request upon which the context is based
  * @tparam A The type of content for the request
  */
class SiteContext[A](val schema: CoreSchema, val site: Site, request: Request[A]) extends BasicContext[A](request) {
  override val appName : String = site.id.name
  override val themeProvider : String = "scrupal" // FIXME: Should be default theme provider for site
  override val themeName: String = "cyborg" // FIXME: Should be default theme for site
  val modules: Seq[Module] = site.modules
  val sketch : Sketch = schema.sketch
  val dbSession : Session = schema.session
}

/** A User context which extends SiteContext by adding specific user information.
  * Details TBD.
  * @param user The user that initiated the request.
  * @param site The site that this request should be processed by
  * @param request The request upon which the context is based
  * @tparam A The type of content for the request
  */
class UserContext[A](override val user: String, schema: CoreSchema, site: Site, request: Request[A])
    extends SiteContext[A](schema, site, request) {
  val principal  = Nil // TODO: Finish UserContext implementation
}

/** Some utility applicators for constructing the various Contexts */
object Context {
  def apply[A](request: Request[A]) = new BasicContext[A](request)
  def apply[A](schema: CoreSchema, site: Site, request: Request[A]) = new SiteContext(schema, site, request)
  def apply[A](user: String, schema: CoreSchema, site: Site, request: Request[A]) =
    new UserContext[A](user, schema, site, request)

  /** This one is degenerate, mostly for testing */
  def apply[A]() = new AnyRef with Context {
    def headers: play.api.mvc.Headers = new Headers { val data = Seq() }
    def id: Long = 0
    def method: String = "GET"
    def path: String = "/"
    def queryString: Map[String,Seq[String]] = Map()
    def remoteAddress: String = ""
    def tags: Map[String,String] = Map()
    def uri: String = "/"
    def version: String = "1.1"
  }

}


/** A trait for producing a Request's context via ContextualAction action builder
  * This trait simply defines the ContextualAction object which is an ActionBuilder with a ConcreteContext type. We
  * construct the ConcreteContext using the request provided to invokeBlock[A] and then invoke the block or generate
  * an error if this is more appropriate. Note that we derive from RichResults so the error factories there can be
  * used. This ContextualAction is used by controllers wherever they need Scrupal relevant contextual information.
  * Their action block is provided a ConcreteContext from which information can be derived.
  */
trait ContextProvider extends RichResults {

  type BasicActionBlock[A] = (BasicContext[A]) => Future[SimpleResult]

  class BasicAction extends ActionBuilder[BasicContext] {
    def invokeBlock[A](request: Request[A], block: BasicActionBlock[A] ) : Future[SimpleResult] = {
      block( new BasicContext[A](request) )
    }
  }
  object BasicAction extends BasicAction
  type AnyBasicContext = BasicContext[AnyContent]

  type SiteActionBlock[A] = (SiteContext[A]) => Future[SimpleResult]

  class SiteAction extends ActionBuilder[SiteContext] {
    def invokeBlock[A](request: Request[A], block: SiteActionBlock[A]) : Future[SimpleResult] = {
      if (Global.ScrupalIsConfigured) {
        val parts : Array[String] = request.host.split(":")
        // HTTP Requires the Host Header, but we guard anyway and assume "localhost" if its empty
        val host: String = if (parts.length > 0) parts(0) else "localhost" ;
        // Look up the Site by host requested in the request and deal with what we find
        {
          Global.DataYouShouldNotModify.sites.get(host) map { site: Site =>
            if (site.enabled) {
              if (site.requireHttps) {
                request.headers.get("X-Forwarded-Proto").collect {
                  case "https" =>
                    site.withCoreSchema { schema: CoreSchema =>
                      block( Context(schema, site, request) )
                    }
                  case _ => {
                    implicit val ctxt = Context(request)
                    Future.successful(Forbidden("request to site '" + site.id.name + "'", "it requires https."))
                  }
                } getOrElse {
                  implicit val ctxt = Context(request)
                  Future.successful(Forbidden("request to site '" + site.id.name + "'", "it requires https."))
                }
              } else {
                site.withCoreSchema { schema: CoreSchema =>
                  block( Context(schema, site, request) )
                }
              }
            } else {
              implicit val ctxt = Context(request)
              Future.successful(Forbidden("browse site '" + site.id.name + "'", "it is disabled"))
            }
          }
        }  getOrElse {
          implicit val ctxt = Context(request)
          Future.successful(NotFound("content for site '" + host + "'"))
        }
      } else {
        implicit val ctxt = Context(request)
        Future.successful(Redirect(routes.ConfigWizard.configure))
      }
    }
  }
  object SiteAction extends SiteAction

  /** A simple rename of the unwieldy ConcreteContext[AnyContent] that gets used frequently. */
  type AnySiteContext = SiteContext[AnyContent]

  type UserActionBlock[A] = (UserContext[A]) => Future[SimpleResult]

  class UserAction extends ActionBuilder[UserContext] {
    def invokeBlock[A](request: Request[A], block: UserActionBlock[A]) : Future[SimpleResult] = {
      SiteAction.invokeBlock(request, {
        context: SiteContext[A] => {
          val user = "guest" // FIXME: Need to look up the actual user
          block( Context(user, context.schema, context.site, request))
        }
      })
    }
  }
  object UserAction extends UserAction

  type AnyUserContext = UserContext[AnyContent]
}

