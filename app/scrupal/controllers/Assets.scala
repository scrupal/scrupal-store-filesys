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

import play.api.mvc.{Action,AnyContent}
import play.api.Play.current
import controllers.WebJarAssets
import play.api.{http, Play, Mode}
import java.lang.IllegalArgumentException

/**
 * Asset controller for core assets. This one gets used by the templates
 */
object Assets extends WebJarAssets(controllers.Assets)
{
  // Save the Play AssetBuilder object under a new name so we can refer to it without referring to ourself!
  val assetBuilder = controllers.Assets
  def fallback(path : String, file : String) : Action[AnyContent] = {
    assetBuilder.at(path, file)
  }

  val root = "/public"
  val javascripts = root + "/javascripts"
  val stylesheets = root + "/stylesheets"
  val images = root + "/images"
  val themes = root + "/themes"

  /** Resolve a path/file combination from either WebJars or the static/compiled resources Scrupal provides
    * Attempts to resolve the path/file combination using WebJars but if the file could not be located there then it
    * falls back to using Play's AssetBuilder to locate the resource inherent to Scrupal.
    * @param path The static path at which the resource might be located (if not found b WebJars)
    * @param file The basic file name with path and basename but without suffixes or versions
    * @return The Enumeratee of the resource
    */
  def resolve(path: String, file: String) : Action[AnyContent] = {
    try {
      val expanded_file_path = super.locate(file)
      super.at(expanded_file_path)
    }
    catch {
      case x: IllegalArgumentException => fallback(path, file)
    }
  }

  def misc(file: String) = resolve(root, file)

  /** Get a Javascript from a Jar file
    * Uses WebJarAssets to locate and return the Jar file corresponding to the argument which must end with .js. T
    * @param file
    * @return
    */
  def js(file: String, min : Boolean = true) = resolve(javascripts, minify(file, ".js", min))

  /** Get a Javascript from public/javascripts (static or compiled)
    * Just uses the Play AssetBuilder to extract the javascript file.
    * @param file The name of the script with partial path after "javascripts" and no version or suffix.
    * @param min Whether or not to minify the resulting file name (always off for Dev mode)
    * @return The Content of the file as an Action
    */
  def js_s(file: String, min : Boolean = true) = fallback(javascripts, minify(file, ".js", min))


  /** Get a Stylesheet from a Jar file
    * Uses WebJarAssets to locate and return the `file` from within a ClassLoaded Jar file.
    *
    * @param file The name of the file without path prefix, version nor suffix, just the basename
    * @param min Whether or not to minify the resulting file name (always off for Dev mode)
    * @return The Content of the file as an Action
    */
  def css(file: String, min : Boolean = true) = resolve(stylesheets, minify(file, ".css", min))

  /** Get a Stylesheet from public/stylesheets (static or compiled)
    *
    * @param file The partial path with no suffix
    * @param min Whether or not to minify the resulting file name (always off for Dev mode)
    * @return The Content of the file as an Action
    */
  def css_s(file: String, min: Boolean = true) = fallback(stylesheets, minify(file, ".css", min))

  /** Get a PNG (Portable Network Graphic) file with extension .png from the static assets
    *
    * @param file name of the file to fetch with any partial path (after /public/images) and without the suffix
    * @return
    */
  def img(file: String) = fallback(images, file)

  /** Get the correct favicon for the context
    * TODO: Defaulted for now to a static result :(
    * @return The /public/images/favicon.png file
    */
  def favicon = fallback(images, "favicon.png")

	/**
	 * A way to obtain a theme css file just by the name of the theme
   * @param source The web source for the theme
	 * @param name Name of the theme
	 * @return path to the theme's .css file
	 */
	def theme(name: String, min: Boolean = true) = {
    resolve(themes, minify(name,".css", min))
  }

  def themeURL(source: String, name: String) : String = {
    source.toLowerCase() match {
      case "bootswatch" => {
        "http://bootswatch.com/" + name + "/bootstrap.min.css"
      }
      case _ => {
        "/assets/stylesheets/bootstrap.min.css"
      }
    }
  }

  /** Serve AngularJS Partial Chunks of HTML
   * An accessor for getting Angular's partial/fragment/chunks of HTML for composed views. We store the HTML files in
   * a directory named "chunks" underneath the javascript module's directory. But, we ask for it with a path like
   * /chunks/module/file.html and the router routes such requests HERE.
   * @param module The name of the Angular module
   * @param file The name of the file being requested
   * @return The content of the file
   */
  def chunks(module: String, file: String) = fallback(root + "/chunks/" + module, file)


  /** The pattern for extracting the suffix from a file name */
  private lazy val suffix_r = "(\\.[^.]*)$".r

  private def minify(file: String, suffix: String, min: Boolean ) = {
    (min && Play.mode != Mode.Dev, file.endsWith(suffix)) match {
      case (false, false) => file + suffix
      case (false, true) => file
      case (true, false) => file + ".min" + suffix
      case (true, true) => suffix_r.replaceFirstIn(file, ".min$1")
    }
  }
}
