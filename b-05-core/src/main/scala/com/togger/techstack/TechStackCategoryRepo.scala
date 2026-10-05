package com.togger.techstack

import doobie.ConnectionIO

import java.time.Instant
import java.util.UUID

trait TechStackCategoryRepo {
  def findById(id: UUID): ConnectionIO[TechStackCategory]
  def insert(techStackCategory: TechStackCategory): ConnectionIO[TechStackCategory]
  def deleteById(id: UUID): ConnectionIO[Int]
  def update(techStackCategory: TechStackCategory, updatedAt: Instant): ConnectionIO[TechStackCategory]
  def findAll: ConnectionIO[List[TechStackCategory]]
}
