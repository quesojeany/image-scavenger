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
 *
 * TODO: This model needs a little cleanup (default params, etc)
 * TODO: Rethink how we handline either url or path --> maybe have a Source object
 * @param id Long TODO: Gross --> I hate the way i use 0 to mean it doesnt exist, trait will be nicer than opt and zero
 * @param name label of image TODO: this should be optional and autogenerated if nothing provided
 * @param storedPath path of where file will be stored
 * @param detectionEnabled whether to turn on object detection for image
 * @param annotations list of tags that represent an object detected in the image
 * @param mediaType a valid content type
 * @param fileName file name
 * @param downloadUrl autogenerated url based on where stuff is stored TODO: remove this and move autogenerated implementation in Storable
 */
case class ImageResource(id: Long = 0,
                         name: String,
                         storedPath: String = "defaultStoredPath", //TODO: remove default
                         detectionEnabled: Boolean = false,
                         annotations: Seq[Annotation] = Seq(),
                         mediaType: String = "",
                         fileName: String = "defaultFileNamePoop", //TODO: remove default, just done for now for poc demo to ignore
                         downloadUrl: String = "defaultDownLoadUrl", //TODO: remove and private implementation should be autogenerated based on where its stored
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

    def list(name: Option[String] = None): Future[Seq[ImageResource]] = {
        val fullData = name.map(n => {
            imageRepository.findByAnnotation(AnnotationData(0, n, null)) //todo: 0, null barf!
        }).getOrElse(imageRepository.list())

        fullData.map(imageRows => {
            imageRows.map(toImageResource)
        })
    }

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
        require(image.id != 0) //todo; lazy way handle handling id is 0 which is the shitty way of indicating existence
        val imageToDelete = ImageData(ImageId(image.id), image.name, image.storedPath, image.detectionEnabled)
        imageRepository.remove(imageToDelete)
    }

    /*  Transform methods: more concise way to apply and unapply these models, but for right now blargh time.
     */
    private def toImageResource(data: (ImageData, Seq[AnnotationData])): ImageResource = {
        val (image: ImageData, annotationsData) = data
        ImageResource(image.id.value, image.name, image.path, image.detectionEnabled, annotations = annotationsData.map(toAnnotation))
    }

    private def toImageResource(fullData: FullImageData): ImageResource = {
        val image = fullData.imageData
        val annotations = fullData.annotationData
        ImageResource(image.id.value, image.name, image.path, image.detectionEnabled, annotations = annotations.map(toAnnotation))
    }

    // Learning/Reminder: keeping this here as a reminder why this DTO transfer logic was a godsend when refactoring to FullDataImage
    private def toImageResource(image: ImageData): ImageResource =
        ImageResource(image.id.value, image.name, image.path, image.detectionEnabled, annotations = Seq())

    private def toAnnotation(annotation: AnnotationData): Annotation =
        Annotation(annotation.id, annotation.name, annotation.imageId.value)

}