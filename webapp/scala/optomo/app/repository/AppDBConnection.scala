package repository

import com.google.inject.{Inject, Singleton}

import play.api.inject.ApplicationLifecycle
import scalikejdbc._
import scalikejdbc.config.DBs

import scala.concurrent.Future

trait AppDBConnection {
  def db: DBConnection
}

@Singleton
class AppDBConnectionImpl @Inject()(applicationLifecycle: ApplicationLifecycle) extends AppDBConnection {
  DBs.setup('default)
  applicationLifecycle.addStopHook(() => Future.successful(DBs.closeAll()))
  override def db = NamedDB('default)
}