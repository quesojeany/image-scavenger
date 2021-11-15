package models

import play.api.libs.json.Json
import repos.{AnnotationData, FullImageData, ImageData, ImageId, ImageRepository}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

/**
 * Only needed for Image, which is our main DTO here.
 *
 * @param id Long TODO: Gross --> I hate the way i use 0 to mean it doesnt exist, trait will be nicer than opt and zero
 * @param name
 * @param imageId Long TODO: ditto
 */
case class Annotation(id: Long = 0, name: String, imageId: Long = 0)
object Annotation {
    implicit val annotationFormat = Json.using[Json.WithDefaultValues].format[Annotation]
}

/**
 * DTO for displaying image information
 * @param id Long TODO: Gross --> I hate the way i use 0 to mean it doesnt exist, trait will be nicer than opt and zero
 * @param name String name of image
 * @param path String path of image
 * @param detectionEnabled whether to turn on detection for image
 */
case class ImageResource(id: Long = 0,
                         name: String = "defaultPoop",
                         storedPath: String = "defaultStoredPath",
                         detectionEnabled: Boolean = false,
                         annotations: Seq[Annotation] = Seq(),
                         mediaType: String = "",
                         fileName: String = "defaultFileNamePoop",
                         downloadUrl: String = "thisshouldnotbeaccesibleDownloadUrl",
                        ) extends Storable with Detectable {

    //override def isMediaTypeSupported: Boolean = mediaType.isImage
}

object ImageResource {
    implicit val imageFormat = Json.using[Json.WithDefaultValues].format[ImageResource]
}

/**
 * Controls access to the backend data, returning [[ImageResource]]
 * TODO: figure out caching - play CacheAPI?
 * TODO: update?
 */
class ImageResourceHandler @Inject()(imageRepository: ImageRepository)(implicit ec: ExecutionContext) {

    /**
     * Create an image
     * TODO: error handling with Form
     * @param image ImageResource
     * @return
     */
    def create (image: ImageResource): Future[ImageResource] = {
        //TODO: yuck null (do this later ExistingImage versus Image, with Existing trait maybe)
        //val id = image.id.map(thisId => ImageId(thisId))
        val imageData = ImageData(ImageId(image.id), path = image.storedPath, name = image.name, detectionEnabled = image.detectionEnabled)
        val annotationsData = image.annotations.map(a => AnnotationData(a.id, a.name, ImageId(a.imageId)))
        imageRepository.create(imageData, annotationsData).map(toImageResource)
    }

    def list(): Future[Seq[ImageResource]] = imageRepository.list()
      .map(imageRows => {
        imageRows.map(toImageResource)
      })

    def get(id: String): Future[Option[ImageResource]] = imageRepository.findById(ImageId(id))
      .map(imageRow => imageRow.map(toImageResource))

    def exists(id: String): Future[Boolean] = get(id).map(_.isDefined)

    def remove(id: String): Future[Int] = imageRepository.remove(ImageId(id))

    /**
     * Remove an image.
     * TODO: throws generic illegal argument exception should probably handle more elegantly
     * @param image ImageResource the dto
     * @return
     */
    def remove(image: ImageResource): Future[Int] = {
        require(image.id != 0) //todo; lazy way handle handling id is 0 which is the shitty way of indicating
        val imageToDelete = ImageData(ImageId(image.id), image.name, image.storedPath, image.detectionEnabled)
        imageRepository.remove(imageToDelete)
    }
    // probably better way to apply and unapply these models, but for right now blargh time.
    // todo: get rid of hardcoded media type
    private def toImageResource(data: (ImageData, Seq[AnnotationData])): ImageResource = {
        val (image: ImageData, annotationsData) = data
        ImageResource(image.id.value, image.name, image.path, image.detectionEnabled, mediaType = "", annotations = annotationsData.map(toAnnotation))
    }

    private def toImageResource(fullData: FullImageData): ImageResource = {
        val image = fullData.imageData
        val annotations = fullData.annotationData
        ImageResource(image.id.value, image.name, image.path, image.detectionEnabled, annotations = annotations.map(toAnnotation))
    }

    private def toImageResource(image: ImageData): ImageResource =
        ImageResource(image.id.value, image.name, image.path, image.detectionEnabled, annotations = Seq())

    private def toAnnotation(annotation: AnnotationData): Annotation =
        Annotation(annotation.id, annotation.name, annotation.imageId.value)

}