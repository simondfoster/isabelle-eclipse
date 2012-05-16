package isabelle.eclipse.core.resource

import isabelle.Document
import isabelle.Isabelle_System
import isabelle.Path
import isabelle.Thy_Header
import isabelle.Thy_Load
import isabelle.eclipse.core.IsabelleCorePlugin

import java.net.URI
import java.net.URISyntaxException

import org.eclipse.core.filesystem.EFS
import org.eclipse.core.filebuffers.FileBuffers
import org.eclipse.core.runtime.CoreException
import org.eclipse.emf.common.CommonPlugin

import org.eclipse.core.{runtime => erun}
import org.eclipse.core.{filesystem => efs}
import org.eclipse.emf.common.util.{URI => EmfURI}


/** A theory loader (and a companion object) that uses URIs to enable file-system operations. 
  * This goes in line with Eclipse's {@link EFS} functionality, which also employs URIs.
  * <p>
  * Every document reference is referenced with a URI, and relative theories are resolved against the
  * URIs. Absolute imported theories also have their paths converted to appropriate URIs. For
  * workspace files, the {@code platform:} URI scheme is used as provided by
  * {@link org.eclipse.emf.common.util.URI}.
  * </p>
  * <p>
  * As a workaround for a limitation in Isabelle with allowing URIs, they are encoded/decoded where
  * needed to "path" style to be allowed in Isabelle. See {@link URIPathEncoder} for details.
  * </p>
  * 
  * @author Andrius Velykis
  */
object URIThyLoad {

  /** Appends (resolves) the source path against the given base URI */
  def resolveURI(base: URI, source_path: Path): URI = {
    val path = source_path.expand

    if (path.is_absolute) {
      // path is absolute file system path - use Isabelle's libraries
      // to resolve (e.g. it has some cygwin considerations)
      val platformPath = Isabelle_System.platform_path(path);
      // encode as file system URI
      efs.URIUtil.toURI(platformPath);
    } else {
      // assume relative URI and resolve it against the base URI
      val pathStr = path.implode 

      try {
        val sourceUri = new URI(pathStr);
        base.resolve(sourceUri);
      } catch {
        case e: URISyntaxException => {
          IsabelleCorePlugin.log(e);
          // at the worst case, just append the path (expecting a directory here)
          erun.URIUtil.append(base, pathStr);
        }
      }
    }
  }

  def resolveDocumentUri(name: Document.Node.Name): URI = {

    val uriStr = name.node
    val platformUri = URIPathEncoder.decodePath(uriStr);

    // resolve platform URI if needed (gives filesystem URI for platform: (workspace) URIs)
    resolvePlatformUri(platformUri.toString());
  }

  /** Resolves the given URI String: if it was workspace-based ({@code platform:} scheme),
    * then it gets resolved to local filesystem.
    *
    * @param uriStr
    * @return
    */
  def resolvePlatformUri(uriStr: String): URI = {
    // use EMF URI to resolve
    val uri = EmfURI.createURI(uriStr);
    val resolvedUri = CommonPlugin.resolve(uri);
    URI.create(resolvedUri.toString());
  }

  /** Creates a workspace-based ({@code platform:} scheme) URI.
    *
    * @param path
    * @return
    */
  def createPlatformUri(path: String): URI = {
    // use EMF common libraries to construct "platform:" URI
    val uri = EmfURI.createPlatformResourceURI(path.toString(), true)
    URI.create(uri.toString())
  }
}

class URIThyLoad extends Thy_Load {

  /* Appends the (possibly) relative path to the base directory, thus resolving relative paths if
   * needed.
   * 
   * (non-Javadoc)
   * @see isabelle.Thy_Load#append(java.lang.String, isabelle.Path)
   */
  override def append(dir: String, source_path: Path): String = {
//    val dirUri = URI.create(dir);
    val dirUri = URIPathEncoder.decodePath(dir);
    val resolvedUri = URIThyLoad.resolveURI(dirUri, source_path);

//    resolvedUri.toString();
    URIPathEncoder.encodeAsPath(resolvedUri);    
  }

  /* Reads theory header for the given document reference. The implementation resolves the URI and
   * loads the file contents using Eclipse EFS, thus benefiting from the support for non-local
   * filesystems.
   * 
   * (non-Javadoc)
   * 
   * @see isabelle.Thy_Load#check_thy(Document.Node.Name)
   */
  override def check_thy(name: Document.Node.Name): Thy_Header = {
    // resolve the document URI to load its contents
    val uri = URIThyLoad.resolveDocumentUri(name);

    try {
      val store = EFS.getStore(uri);

      // Load the file contents using FileBuffers. In this way if the file is already open by
      // some editor, we may avoid the need to reopen it, as the file buffer may be cached.
      val manager = FileBuffers.getTextFileBufferManager();
      manager.connectFileStore(store, null);
      val buffer = manager.getFileStoreTextFileBuffer(store);
      val fileText = buffer.getDocument().get();

      manager.disconnectFileStore(store, null);

      Thy_Header.read(fileText);

    } catch {
      case e: CoreException => {
        IsabelleCorePlugin.log(e);
        // in case of failure, perform default loading
        super.check_thy(name);
      }
    }
  }
  
}