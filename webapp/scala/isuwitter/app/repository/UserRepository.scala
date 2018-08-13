package repository

import com.google.inject.{Inject, Singleton}
import model.User
import scalikejdbc._

trait UserRepository {
  def findByUserName(name: String): Option[User]
  def findByUserId(id: Int): Option[User]
}

@Singleton
class UserRepositoryImpl @Inject()(appDBConnection: AppDBConnection) extends UserRepository {

  def convert(rs: WrappedResultSet): User = User(rs.get[Int](1), rs.get[String](2), rs.get[String](3), rs.get[String](4))

  override def findByUserName(name: String): Option[User] = {
    appDBConnection.db.localTx{ implicit session =>
      sql"SELECT * FROM users WHERE name = ${name}"
        .map(rs => convert(rs)).single.apply()
    }
  }

  override def findByUserId(id: Int): Option[User] = {
    appDBConnection.db.localTx{ implicit session =>
      sql"SELECT * FROM users WHERE id = ${id}"
        .map(rs => convert(rs)).single.apply()
    }
  }
}
