package net.petitviolet.prac.grpc.model

import java.util.UUID

import scala.concurrent.{ExecutionContext, Future}

case class User(id: String, name: String, age: Int)

object User {
  def create(name: String, age: Int): User = apply(UUID.randomUUID().toString, name, age)
}

trait UserRepository {
  def findAll()(implicit ec: ExecutionContext): Future[Seq[User]]

  def store(user: User)(implicit ec: ExecutionContext): Future[Boolean]
}

trait UsesUserRepository {
  val userRepository: UserRepository
}

trait MixInUserRepository {
  val userRepository: UserRepository = UserRepositoryImpl
}

private object UserRepositoryImpl extends UserRepository {

  import collection.mutable

  private val users: mutable.ListBuffer[User] = mutable.ListBuffer()

  def findAll()(implicit ec: ExecutionContext): Future[Seq[User]] = {
    Future.apply {
      users.toList
    }
  }

  def store(user: User)(implicit ec: ExecutionContext): Future[Boolean] = {
    Future.apply {
      users += user
      true
    }
  }
}