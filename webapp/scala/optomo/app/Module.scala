import com.google.inject.AbstractModule
import repository.{AppDBConnection, AppDBConnectionImpl, FriendsRepository, FriendsRepositoryImpl}

class Module extends AbstractModule {

  override def configure() = {
    bind(classOf[FriendsRepository]).to(classOf[FriendsRepositoryImpl])
    bind(classOf[AppDBConnection]).to(classOf[AppDBConnectionImpl])
  }

}
