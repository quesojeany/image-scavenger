package models

import akka.http.scaladsl.model.MediaType


import java.util.UUID

//todo: better practice to do import java.util.UUID to guarantee uniqueness of primarykey globally
//todo dates- create/upload/etc storablefile
trait Storable extends Identifiable[Long] with Named {
  //def mediaType: MediaType
  //def storageService: StorageService
  def storedPath: String
  def fileName: String
  //in case files have same name
  def uniqueName = UUID.randomUUID.toString
  def fileSize: Int = 0
  //we could have stronger type here, but its fine for now.
  def downloadUrl: String

  //def isMediaTypeSupported: Boolean
}
