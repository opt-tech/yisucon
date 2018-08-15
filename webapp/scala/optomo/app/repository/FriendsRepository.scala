package repository

import com.google.inject.{Inject, Singleton}
import model.Friends
import scalikejdbc._

trait FriendsRepository {
  def findByUserName(name: String): Friends
  def update(friends: String, name: String): Boolean
}

@Singleton
class FriendsRepositoryImpl @Inject()(appDBConnection: AppDBConnection) extends FriendsRepository {

  def convert(rs: WrappedResultSet): Friends = Friends(rs.get[Int](1), rs.get[String](2), rs.get[String](3))

  override def findByUserName(name: String): Friends = {
    appDBConnection.db.localTx{ implicit session =>
      sql"SELECT * FROM friends WHERE me = ${name}"
        .map(rs => convert(rs)).single.apply().get
    }
  }

  override def update(friends: String, name: String): Boolean = {
    appDBConnection.db.localTx{ implicit session =>
      val res = sql"UPDATE friends SET friends = ${friends} WHERE me = ${name}"
        .map(rs => convert(rs)).update.apply()
      res != 0
    }
  }
}
