package net.petitviolet.prac.grpc.model

import scala.concurrent.{ ExecutionContext, Future }

case class Organization(id: Int, name: String)

trait OrganizationRepository {
  def findById(id: Int)(implicit ec: ExecutionContext): Future[Option[Organization]]
}

private object OrganizationRepositoryImpl extends OrganizationRepository {
  private val organizations: Seq[Organization] = List(
    Organization(1, "org-1"),
    Organization(2, "org-2"),
    Organization(3, "org-3"),
    Organization(4, "org-4"),
    Organization(5, "org-5"),
  )

  def findById(id: Int)(implicit ec: ExecutionContext): Future[Option[Organization]] =
    Future.apply {
      organizations.find { _.id == id }
    }
}


trait UsesOrganizationRepository {
  val organizationRepository: OrganizationRepository
}

trait MixInOrganizationRepository {
  val organizationRepository: OrganizationRepository = OrganizationRepositoryImpl
}

