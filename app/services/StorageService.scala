package services

import akka.stream.scaladsl.{FileIO, Source}
import com.google.inject.ImplementedBy
import models.Storable
import play.api.Logger
import play.api.libs.Files
import play.api.libs.Files.TemporaryFile
import play.api.libs.ws._
import play.api.mvc.MultipartFormData.{DataPart, FilePart}

import java.io.File
import java.nio.file.Files
import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.{Duration, DurationInt}
import scala.concurrent.{ExecutionContext, Future}

/**
 * handle generic StorageFiles or models that are "uploadable" and "downloadable" etc.
 * NOTE: we are using the default execution context and wsclient plugged from play.  We probably
 * could create a separate custom execution context, but not going to right now.
 * use generics--> have to use TypeLiteral bindings with Guice
 * this abstraction might be unnecessary
 */
@ImplementedBy(classOf[GoogleStorageService])
abstract class StorageService(ws: WSClient, ec: ExecutionContext)  {
  //TODO: this could be accessible via Configuration, but just hardcoding for now.
  protected def server: String
  def upload(storedFile: FilePart[File]): Future[WSResponse]
  def download(storedFile: Storable): Future[WSResponse]
  protected def requestTimeout: Duration
  protected def uploadTimeout: Duration
}

//consider using cache as well, but hell with it for now. - same files being accessed etc.
//could have used Alpakka Google cloud storage client (akka folks) but was playing around

@Singleton
class GoogleStorageService @Inject() (ws: WSClient, ec: ExecutionContext) extends StorageService(ws, ec) {
  protected val server = "https://storage.googleapis.com"
  private val bucket = "quesojean-playhouse"
  private val storagePath = s"/storage/v1/b/${bucket}/o/"
  private val storageUrl = s"$server${storagePath}"
  private val uploadUrl = s"${server}/upload$storagePath"
  // id guess requests shouldn't take longer than a few seconds (one second was too long unfortunately)
  protected val requestTimeout = 5000.millis
  protected val uploadTimeout = 5000.millis
  private val logger = Logger(getClass)

  override def upload(tmpFile: FilePart[File]): Future[WSResponse] = {
    logger.info(s"key = ${tmpFile.key}, filename = ${tmpFile.filename}, contentType = ${tmpFile.contentType}, file = $tmpFile.ref, fileSize = ${tmpFile.fileSize}, dispositionType = ${tmpFile.dispositionType}")

    val request = ws.url(uploadUrl)
      .addQueryStringParameters("uploadType" -> "media", "name" -> tmpFile.filename)
      .addHttpHeaders("Content-Type" -> tmpFile.contentType.get, "Content-Length" -> tmpFile.fileSize.toString)
      .withRequestTimeout(requestTimeout)

    request.post(
      Source(
        FilePart(tmpFile.key, tmpFile.filename, tmpFile.contentType, FileIO.fromPath(tmpFile.ref.toPath)) :: DataPart(
          "key",
          "value"
        ) :: List()
      )
    )
  }



  def download(storedFile: Storable): Future[WSResponse] = {
    val downloadUrl = s"${storageUrl}demo-img.jpg"
    val request: WSRequest = ws.url(downloadUrl)
      .addQueryStringParameters("alt" -> "media")
      .withRequestTimeout(requestTimeout)
    request.get()
  }



}
