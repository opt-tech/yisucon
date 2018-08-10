import com.google.inject.AbstractModule
import repository._

class Module extends AbstractModule {

  override def configure() = {
    bind(classOf[UserRepository]).to(classOf[UserRepositoryImpl])
    bind(classOf[AppDBConnection]).to(classOf[AppDBConnectionImpl])
  }

}
