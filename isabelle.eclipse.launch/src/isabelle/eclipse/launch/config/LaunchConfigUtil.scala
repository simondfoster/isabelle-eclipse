package isabelle.eclipse.launch.config

import scala.collection.JavaConverters._
import scala.reflect.runtime.universe._

import org.eclipse.core.runtime.{CoreException, IPath, Path}
import org.eclipse.core.variables.VariablesPlugin
import org.eclipse.debug.core.{ILaunchConfiguration, ILaunchConfigurationWorkingCopy}

import isabelle.eclipse.launch.IsabelleLaunchPlugin.{error, log}


/**
 * Utilities for launch configurations.
 * 
 * @author Andrius Velykis
 */
object LaunchConfigUtil {

  def configValue[T: TypeTag](configuration: ILaunchConfiguration,
                              attributeName: String,
                              defaultValue: T): T =
    try {

      val res = typeOf[T] match {
        case t if t =:= typeOf[String] =>
          configuration.getAttribute(attributeName, defaultValue.asInstanceOf[String])
        case t if t =:= typeOf[Boolean] =>
          configuration.getAttribute(attributeName, defaultValue.asInstanceOf[Boolean])
        case t if t =:= typeOf[Int] =>
          configuration.getAttribute(attributeName, defaultValue.asInstanceOf[Int])
        case t if t <:< typeOf[List[String]] =>
          configuration.getAttribute(attributeName,
            defaultValue.asInstanceOf[List[String]].asJava).asScala.toList
        case _ =>
          throw new UnsupportedOperationException("unsupported config type")
      }

      res.asInstanceOf[T]

    } catch {
      case ce: CoreException => {
        log(error(Some(ce), Some("Error reading configuration")))
        // return the default
        defaultValue
      }
    }

  def setConfigValue(configuration: ILaunchConfigurationWorkingCopy,
                     attributeName: String,
                     value: Option[String]) =
    value match {
      case Some(value) =>
        configuration.setAttribute(attributeName, value)
      case None =>
        configuration.removeAttribute(attributeName)
    }

  
  def pathsConfigValue(configuration: ILaunchConfiguration, attributeName: String): Seq[IPath] = {
    
    val configVal = configValue(configuration, attributeName, List[String]())
    configVal map resolvePath
  }

  def resolvePath(location: String): IPath = {
    resolveWorkspacePath(location) getOrElse new Path(location)
  }

  private def resolveWorkspacePath(location: String): Option[IPath] = {
    try {
      val substitutedStr = VariablesPlugin.getDefault.getStringVariableManager.
        performStringSubstitution(location)

      Some(new Path(substitutedStr))
    } catch {
      case ce: CoreException => {
        log(error(Some(ce)))
        None
      }
    }
  }
  
}
