package controllers

import akka.stream.IOResult
import akka.stream.scaladsl.{FileIO, Sink}
import akka.util.ByteString
import models._
import play.api.Logger
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.json.Json
import play.api.libs.streams.Accumulator
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc._
import play.core.parsers.Multipart.{FileInfo, FilePartHandler}
import services.{DetectionService, StorageService}

import java.io.File
import java.nio.file.{Path, Files => JFiles}
import javax.inject._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object ImageController {
  private val logger = Logger(getClass)
}

/**
 * Main controller to handle CRUD Rest operations for IMAGES
 *
 * @param imageResourceHandler dto to handle operations on image resources
 * @param cc I18N support
 * @param ss storage service for files
 * @param ds object detection service
 * @param ec ec implicit execution context (default is a fork join in play)
 */
class ImageController @Inject()(imageResourceHandler: ImageResourceHandler,
                                  cc: MessagesControllerComponents,
                                  ss: StorageService,
                                  ds: DetectionService
                                )(implicit ec: ExecutionContext)
  extends MessagesAbstractController(cc) {

  import ImageController.logger

  // create a "Form" to take advantage of built in validation/error handling; TODO fix/update validation/fields
  Form(
    mapping(
      "name" -> nonEmptyText,
      "path" -> nonEmptyText,
      "detectionEnabled" -> default(boolean, false)
    )(NewImageFormInput.apply)(NewImageFormInput.unapply)
  )

  /**
   * TODO: handle failures and upload
   * @return
   */
  def create() = Action.async { implicit request =>
    val json = request.body.asJson.get
    //todo should use Form?
    val imageResource = json.as[ImageResource]
    imageResourceHandler.create(imageResource).map { newImage =>
      Ok(Json.toJson(newImage))
    }
  }

  def createAndDetect() = Action.async { implicit request =>
    val json = request.body.asJson.get
    //todo should use Form?
    val imageResource = json.as[ImageResource]
    val newPossiblyEnhancedImage = for {
      annotatedImage <- detect(imageResource)
      newImage  <- imageResourceHandler.create(annotatedImage)
    } yield newImage


    newPossiblyEnhancedImage.map { newImage =>
      Ok(Json.toJson(newImage))
    }
  }

  /**
   * If enabled, will return an ImageResource with annotations, otherwise just the original ImageResource
   * TODO: I feel like this could be in a better place, but fine.
   * @param image ImageResource
   * @return Future[ImageResource]
   */
  private def detect(image: ImageResource): Future[ImageResource] = Option(image.detectionEnabled)
    .filter(_ == true)
    .map(_ => {
      ds.detect(image).map(detectionResults => {
        val annotationsToSave = detectionResults.annotations.map(a => Annotation(name = a.name))
        logger.debug(s"Arrrggh!!, found us some golden treasure ${annotationsToSave.size}")
        image.copy(annotations = annotationsToSave)
      })
    }).getOrElse(Future.successful(image))


  /**
   * A REST endpoint that gets all the images as JSON or if an annotation name is provided
   * then will only returns those images that have that annotation.
   * If none, returns empty json array.
   *
   * @param name filtered images by annotation name (case insensitive)
   * @return
   */
  def list(name: Option[String] = None) = Action.async { implicit request =>
    imageResourceHandler.list(name).map { images =>
      Ok(Json.toJson(images))
    }
  }

  /**
   * Return an image given a valid id.  If not found, returns 404 with json error message
   * @param id String value that should be able to converted to an Int
   * @return
   */
  def get(id: String) = Action.async { implicit request =>
    imageResourceHandler.get(id).map { image =>
      image.map(img => Ok(Json.toJson(img)))
        .getOrElse(NotFound(Json.toJson(ErrorResource(s"Poop, the image with id $id could not be found. Perhaps you meant another id?"))))
    }
  }

  /**
   * Remove an image if exists.
   * If not found, returns 404 with json error message
   * @param id String value that should be able to converted to an Int
   * @return
   */
  def remove(id: String) = Action.async { implicit request =>
    imageResourceHandler.get(id).flatMap {
      case None => Future.successful(
        NotFound(Json.toJson(ErrorResource(s"Alas, image with id $id could not be found. If at first you don't succeed, try, try again!")))
      )
      case Some(found) => imageResourceHandler.remove(found).map(_ => NoContent)
    }
  }

  def upload() = Action(parse.multipartFormData(handleFilePartAsFile)) { implicit request =>
    request.body.file("file").map { filePart =>
      logger.info("I AM ABOUT TO UPLOAD TO GOOGLE")
        //TODO: handle errors here please and dont forget to delete
        val response = ss.upload(filePart).onComplete {
          case Success(whatever) => logger.debug(s"THE RESPONSE BITCH ${whatever.json}")
          deleteTempFile(filePart.ref)
          case Failure(e) => logger.error("Something went terrible wrong", e)
        }
        logger.debug(response.toString)
      Ok(s"Felicitaciones, your file ${filePart.filename} has been uploaded!!!")
    }.getOrElse(BadRequest(Json.toJson(ErrorResource("Dang, we are missing the file to upload"))))
  }

  def download(id: String) = Action.async { implicit request =>
    imageResourceHandler.get(id).flatMap {
      case None => Future.successful(
        NotFound(Json.toJson(ErrorResource(s"Alas, image with id $id could not be found to download. If at first you don't succeed, try, try again!")))
      )
      case Some(found) => ss.download(found).map(filePoop => Ok(filePoop.body))
    }
  }

  /**
   * Uses a custom FilePartHandler to return a type of "File" rather than
   * using Play's TemporaryFile class.  Deletion must happen explicitly on
   * completion, rather than TemporaryFile (which uses finalization to
   * delete temporary files).
   * Grabbed this from play sample code fyi. wanted to learn accumulator
   * @return
   */
  private def handleFilePartAsFile: FilePartHandler[File] = {
    case FileInfo(partName, filename, contentType, _) =>
      val path: Path = JFiles.createTempFile("poop", filename)
      val fileSink: Sink[ByteString, Future[IOResult]] = FileIO.toPath(path)
      val accumulator: Accumulator[ByteString, IOResult] = Accumulator(fileSink)
      accumulator.map {
        case IOResult(count, status) =>
          logger.info(s"count = $count, status = $status")
          FilePart(partName, filename, contentType, path.toFile)
      }
  }
  /**
   * A generic operation on the temporary file that deletes the temp file after completion.
   */
  private def deleteTempFile(file: File) = {
    val size = JFiles.size(file.toPath)
    logger.info(s"size = $size")
    JFiles.deleteIfExists(file.toPath)
    size
  }
}

// TODO: make name optional and figure out path for that matter as one can either upload file or use a url
case class NewImageFormInput(name: String, path: String, detectionEnabled: Boolean = false)