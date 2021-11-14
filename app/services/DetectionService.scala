package services

import models.{Detectable, ImageResource}

/**
 * TODO: this needs to be a singleton blah
 * probably confine this only to images right now with generics (files that can be detected- detectable)
 */
trait DetectionService[T <: Detectable] {

  //return a set of tags or "annotations" of objects found in the image (should be a StorageFileResource)
  def detect(entity: T): Unit
}

class GoogleVisionDetectionService extends DetectionService[ImageResource] {
  override def detect(image: ImageResource): Unit = ???
}
