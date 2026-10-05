package com.togger.projectlabel

import enumeratum._

sealed abstract class DefaultDomain(val i: Int, val domainName: String, val description: String) extends EnumEntry

object DefaultDomain extends Enum[DefaultDomain] {
  case object Medicine extends DefaultDomain(0, "Medicine", "Healthcare, clinical, and medical technology")
  case object Energy extends DefaultDomain(1, "Energy", "Power generation, utilities, and energy management")
  case object Finance extends DefaultDomain(2, "Finance", "Banking, trading, and financial services")
  case object Telecommunications
      extends DefaultDomain(3, "Telecommunications", "Voice, data, and network communications infrastructure")
  case object Insurance extends DefaultDomain(4, "Insurance", "Risk assessment, underwriting, and claims management")
  case object Retail extends DefaultDomain(5, "Retail", "E-commerce, point-of-sale, and inventory management")
  case object Logistics extends DefaultDomain(6, "Logistics", "Supply chain, shipping, and fleet management")
  case object Manufacturing extends DefaultDomain(7, "Manufacturing", "Industrial production and factory automation")
  case object Automotive extends DefaultDomain(8, "Automotive", "Vehicle systems, embedded software, and mobility")
  case object Aerospace extends DefaultDomain(9, "Aerospace", "Aviation, spacecraft, and avionics systems")
  case object Education extends DefaultDomain(10, "Education", "Learning management and academic software")
  case object Government extends DefaultDomain(11, "Government", "Public sector and civic administration systems")
  case object Legal extends DefaultDomain(12, "Legal", "Case management and legal practice software")
  case object RealEstate extends DefaultDomain(13, "Real Estate", "Property management and real estate platforms")
  case object Agriculture extends DefaultDomain(14, "Agriculture", "Farming, crop management, and agritech")
  case object Hospitality
      extends DefaultDomain(15, "Hospitality", "Hotels, travel, and guest experience platforms")
  case object Gaming extends DefaultDomain(16, "Gaming", "Video games and interactive entertainment")
  case object Media extends DefaultDomain(17, "Media", "Broadcasting, publishing, and content distribution")
  case object Ecommerce extends DefaultDomain(18, "E-commerce", "Online marketplaces and digital storefronts")
  case object Pharmaceutical
      extends DefaultDomain(19, "Pharmaceutical", "Drug development and pharmaceutical research")
  case object Biotechnology
      extends DefaultDomain(20, "Biotechnology", "Genomics, lab research, and biotech platforms")
  case object Defense extends DefaultDomain(21, "Defense", "Military and defense systems")
  case object Construction
      extends DefaultDomain(22, "Construction", "Building, architecture, and construction management")
  case object CyberSecurity extends DefaultDomain(23, "Cybersecurity", "Information security and threat protection")

  override def values: IndexedSeq[DefaultDomain] = findValues
}
