package models

trait Identifiable[T] extends Serializable {
  def id:T
}
