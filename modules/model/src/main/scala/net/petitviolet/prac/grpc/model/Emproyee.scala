package net.petitviolet.prac.grpc.model

import java.util.UUID

import scala.concurrent.{ExecutionContext, Future}

case class Employee(id: String, name: String, age: Int, organization: Organization)

object Employee {
  def create(name: String, age: Int, organization: Organization)(implicit ec: ExecutionContext): Employee = {
    apply(UUID.randomUUID().toString, name, age, organization)
  }

  sealed trait Post
  object Post {
    case object NoTitle extends Post
    case object Manager extends Post
    case object Officer extends Post
  }
}

trait EmployeeRepository {
  def findAll()(implicit ec: ExecutionContext): Future[Seq[Employee]]

  def findByOrganization(organization: Organization)(implicit ec: ExecutionContext): Future[Seq[Employee]]

  def store(employee: Employee)(implicit ec: ExecutionContext): Future[Boolean]
}

trait UsesEmployeeRepository {
  val employeeRepository: EmployeeRepository
}

trait MixInEmployeeRepository {
  val employeeRepository: EmployeeRepository = EmployeeRepositoryImpl
}

private object EmployeeRepositoryImpl extends EmployeeRepository {

  import collection.mutable

  private val employees: mutable.ListBuffer[Employee] = mutable.ListBuffer()

  def findAll()(implicit ec: ExecutionContext): Future[Seq[Employee]] = {
    Future.apply {
      employees.toList
    }
  }

  def findByOrganization(organization: Organization)(implicit ec: ExecutionContext): Future[Seq[Employee]] = {
    Future.apply {
      employees.filter { employee =>
        employee.organization == organization
      }
    }
  }

  def store(employee: Employee)(implicit ec: ExecutionContext): Future[Boolean] = {
    Future.apply {
      employees += employee
      true
    }
  }
}