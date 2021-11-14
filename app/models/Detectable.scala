package models

/**
 * Whether something can undergo detection process (like images or pdfs, etc)
 */
trait Detectable {
  def detectionEnabled: Boolean
}
