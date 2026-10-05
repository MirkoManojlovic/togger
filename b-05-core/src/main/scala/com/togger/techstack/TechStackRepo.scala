package com.togger.techstack

import doobie.ConnectionIO

import java.time.Instant
import java.util.UUID

trait TechStackRepo {
  def findById(id: UUID): ConnectionIO[TechStack]
  def insert(techStack: TechStack): ConnectionIO[TechStack]
  def deleteById(id: UUID): ConnectionIO[Int]
  def update(techStack: TechStack, updatedAt: Instant): ConnectionIO[TechStack]
  def findAll: ConnectionIO[List[TechStack]]
}
