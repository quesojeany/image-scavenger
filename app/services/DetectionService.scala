package services

import akka.http.scaladsl.model.MediaTypes
import com.google.inject.ImplementedBy
import models.{Detectable, Storable}
import play.api.Logger
import play.api.libs.json.{JsObject, Json}
import play.api.libs.ws.WSClient
import services.DetectionService.{Annotation, DetectionResource}
import services.GoogleDetectionService.{GoogleDetectionResponse, Responses, logger}

import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.{Duration, DurationInt}
import scala.concurrent.{ExecutionContext, Future}

object DetectionService {
  case class DetectionResource(annotations: Seq[Annotation])
  case class Annotation(name: String)
  object Annotation {
    implicit val annotationFormat = Json.using[Json.WithDefaultValues].format[Annotation]
  }
}

/**
 * Generic detection service. I went ahead and leveraged Guice Runtime dependency injection,
 * which is sorta gross:
 * 1. abstract class instead of trait (have to have a constructor for DI)
 * 2. Generics are tough- you have to use TypeLiteral for binding, so didnt do that in constructor.
 * 3. I wonder if this whole trying to separate out specific implementations is a moot point with
 *    @ImplementBy - Unsure if we can do it for multiple implementations.
 *
 *  NOTE: I am sorry, this file is embarrassingly long, i probably would split up and clean
 *  TODO: Caching, more logging, of course unit testing with mocks.
 */
@ImplementedBy(classOf[GoogleDetectionService])
abstract class DetectionService(ws: WSClient)(implicit ec: ExecutionContext) {
  protected def targetUrl: String
  protected def requestTimeout: Duration
  protected def limit: Int

  /**
   * Set of tags or annotations of objects found in the image
   * TODO: return a Future of a case class and recover with Error (or use an either)
   * @param entity
   * @tparam T
   * @return
   */
  def detect[T <: Storable with Detectable](entity: T):  Future[DetectionResource]
}

/**
 * Just a placeholder to put all our deser/ser implicits for dealing with google requests/responses
 * TODO: I suspect we should put this elsewhere as this file is getting FAT
 */
object GoogleDetectionService {
  val logger = Logger(getClass)
  /* Response case classes: Needed really just for reading/deser, so oformat is overkill
  but whatever - its less typing, so yay! */
  case class NormalizedVertices(x: Double, y: Double)
  object NormalizedVertices {
    implicit val normalizedVertexFormat = Json.using[Json.WithDefaultValues].format[NormalizedVertices]
  }

  case class BoundingPoly(normalizedVertices: Seq[NormalizedVertices])
  object BoundingPoly {
    implicit val boundingPolyFormat = Json.using[Json.WithDefaultValues].format[BoundingPoly]
  }

  case class LocalizedObjectAnnotations(mid: String, name: String, score: Double, boundingPoly: BoundingPoly)
  object LocalizedObjectAnnotations {
    implicit val localizedObjectAnnotationFormat = Json.using[Json.WithDefaultValues].format[LocalizedObjectAnnotations]
  }

  case class Responses(localizedObjectAnnotations: Seq[LocalizedObjectAnnotations])
  object Responses {
    implicit val responseFormat = Json.using[Json.WithDefaultValues].format[Responses]
  }

  case class GoogleDetectionResponse(responses: Seq[Responses])
  object GoogleDetectionResponse {
    implicit val responseFormat = Json.using[Json.WithDefaultValues].format[GoogleDetectionResponse]
  }
}

/**
 * Google specific implementation of DetectionService
 * @param ws WSClient (injected by play- gives us all the goodies and cleansup itself automagically)
 * @param ec ExecutionContext The play injected main/global execution context.
 *           TODO: I think it would
 *           be better practice to provide our own, named and what not, but for another time.
 */
@Singleton
class GoogleDetectionService @Inject() (ws: WSClient)(implicit ec: ExecutionContext) extends DetectionService(ws) {
  protected val requestTimeout = 5000.millis
  protected val limit = 10
  protected val targetUrl = "https://vision.googleapis.com/v1/images:annotate"
  //TODO: BAD!!! Naughty security violation, hardcoding apikey --> use Configuration and encode
  private val apiKey = "AIzaSyAqUj5ckT1Dr8hQKJma3AcukbeR0Qq19Rc"

  private lazy val request = ws.url(targetUrl)
    .addQueryStringParameters("key" -> apiKey)
    .addHttpHeaders("Content-Type" -> MediaTypes.`application/json`.value)

  /**
   * Detects for any objects in a file and returns the annotations
   *  TODO: return a Future of a case class and recover with Error (or use an either) - exceptions and bad status codes
   *  Google does return error objects that i could deser, but TIME, where is the time?
   * @param entity Storable and detectable entity
   * @return Future[DetectionResource]
   */
  override def detect[T <: Storable with Detectable](entity: T): Future[DetectionResource] = {
    val data = imageRequest(entity)
    logger.debug(s"Is my google payload good? $data")
    request.post(data).map { response =>
      val stringResponse = response.json.toString()
      logger.debug(s"YEEEEHAW Google: this is the json response $stringResponse")

      val gResponse = response.json.as[GoogleDetectionResponse]

      val annotations = gResponse.responses.flatMap((responses: Responses) => {
        responses.localizedObjectAnnotations.map(a => Annotation(a.name))
      })
      DetectionResource(annotations)
    }
  }

  /**
   * TODO: yuck, use cleaner json play implicit writes/reads with case classes
   * @param entity
   * @return
   */
  private def imageRequest[T <: Storable with Detectable](entity: T): JsObject = {
    val imageUri = Json.obj("imageUri" -> entity.storedPath)
    val source = Json.obj("source" -> imageUri)
    //hardcoded yuck. could create valid google type enumerations
    val feature = Json.obj("maxResults" -> limit, "type" -> "OBJECT_LOCALIZATION")
    val imageAndFeatures = Json.obj("image" -> source, "features" -> Json.arr(feature))
    Json.obj("requests" -> Json.arr(imageAndFeatures))
  }
}





