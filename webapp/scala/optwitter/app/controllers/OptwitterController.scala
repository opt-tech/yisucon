package controllers

import java.security.{MessageDigest, NoSuchAlgorithmException}
import java.time.format.DateTimeFormatter

import scalikejdbc._
import com.sun.org.apache.xerces.internal.impl.dv.util.HexBin
import javax.inject.{Inject, Singleton}
import model.Tweet
import play.api.data.Form
import play.api.data.Forms.{mapping, _}
import play.api.libs.json.Json
import play.api.libs.ws.{WSClient, WSRequest}
import play.api.mvc.{AbstractController, AnyContent, ControllerComponents, Request}
import repository.{AppDBConnection, TweetRepository, UserRepository}

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.control.Breaks

case class LoginRequest(name: String, password: String)

object LoginRequest {
  val formMapping = mapping(
    "name" -> text,
    "password" -> text,
  )(LoginRequest.apply)(LoginRequest.unapply)
  val form = Form(formMapping)
}

case class FollowRequest(user: String)

object FollowRequest {
  val formMapping = mapping(
    "user" -> text
  )(FollowRequest.apply)(FollowRequest.unapply)
  val form = Form(formMapping)
}

case class TweetRequest(text: String)

object TweetRequest {
  val formMapping = mapping(
    "text" -> text
  )(TweetRequest.apply)(TweetRequest.unapply)
  val form = Form(formMapping)
}

@Singleton
class OptwitterController @Inject()(cc: ControllerComponents, userRepository: UserRepository, tweetRepository: TweetRepository, ws: WSClient, appDBConnection: AppDBConnection)(implicit assetsFinder: AssetsFinder) extends AbstractController(cc) {

  val PER_PAGE = 50

  def tweet() = Action(parse.form(TweetRequest.form)) { implicit request =>
    val userIdOpt = request.session.get("user_id")
    if (userIdOpt.isEmpty) {
      Found("/")
    } else {
      val text = request.body.text
      tweetRepository.create(userIdOpt.get.toInt, text)
      Found("/")
    }
  }

  def follow() = Action(parse.form(FollowRequest.form)) { implicit request =>
    val userIdOpt = request.session.get("user_id")
    if (userIdOpt.isEmpty) {
      Found("/")
    } else {
      val name = getUserName(userIdOpt.map(_.toInt))
      val user = request.body.user

      val complexRequest = ws.url(s"http://localhost:8081/${name}").addHttpHeaders("Accept" -> "application/json")
      val data = Json.obj("user" -> user)
      val response = Await.result(complexRequest.post(data), Duration.Inf)
      if (response.status != 200) {
        InternalServerError("error")
      } else {
        Found(s"/${user}")
      }
    }
  }

  def unfollow() = Action(parse.form(FollowRequest.form)) { implicit request =>
    val userIdOpt = request.session.get("user_id")
    if (userIdOpt.isEmpty) {
      Found("/")
    } else {
      val name = getUserName(userIdOpt.map(_.toInt))
      val user = request.body.user

      val complexRequest = ws.url(s"http://localhost:8081/${name}").addHttpHeaders("Accept" -> "application/json")
      val data = Json.obj("user" -> user)
      val response = Await.result(complexRequest.withBody(data).delete(), Duration.Inf)
      if (response.status != 200) {
        InternalServerError("error")
      } else {
        Found(s"/${user}")
      }
    }
  }

  def logout() = Action { implicit request =>
    Found("/").removingFromSession("user_id")
  }

  def initialize() = Action {
    if (!initializer()) {
      InternalServerError("error")
    } else {
      Ok(Json.obj("result" -> "ok"))
    }
  }

  def initializer(): Boolean = {
    appDBConnection.db.localTx{ implicit session =>
      sql"DELETE FROM tweets WHERE id > 100000".update.apply()
      sql"DELETE FROM users WHERE id > 1000".update.apply()
    }
    val request: WSRequest = ws.url("http://localhost:8081/initialize")
    val complexRequest: WSRequest = request.addHttpHeaders("Accept" -> "application/json")
    val response = Await.result(complexRequest.get(), Duration.Inf)
    if (response.status != 200) return false
    true
  }

  def index(_until: Option[String], _append: Option[Int]) = Action { implicit request =>
    val until = _until.getOrElse("")
    val append = _append.getOrElse(0)
    request.session.get("user_id").map { id =>
      val name = getUserName(Some(id.toInt))
      val friends = loadFriend(name)
      val rows = if (until.length == 0) {
        tweetRepository.findOrderByCreatedAtDesc()
      } else {
        tweetRepository.findOrderByCreatedAtDesc(until)
      }

      var tweets = Seq[Tweet]()
      val b = new Breaks
      b.breakable {
        for (row <- rows) {
          var tweet = new Tweet(row.userId)
          tweet = tweet.copy(html = htmlify(row.text))
          tweet = tweet.copy(time = row.createdAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
          val friendName = getUserName(Some(row.userId))
          tweet = tweet.copy(userName = friendName)
          if (friends.contains(friendName)) {
            tweets = tweets :+ tweet
          }
          if (tweets.size == PER_PAGE) b.break
        }
      }
      if (append == 0) {
        Ok(views.html.index(name, tweets)).removingFromSession("flush")
      } else {
        Ok(views.html._tweet(tweets)).removingFromSession("flush")
      }
    }.getOrElse {
      request.session.get("flush")
        .map { flush =>
          Ok(views.html.login(flush, null)).removingFromSession("flush")
        }.getOrElse {
        Ok(views.html.login(null, null)).removingFromSession("flush")
      }
    }
  }

  def htmlify(text: String): String = {
    if (text == null) return ""
    text.replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;").replaceAll("'", "&apos;").replaceAll("\"", "&quot;").replaceAll("#(\\S+)(\\s|$)", "<a class=\"hashtag\" href=\"/hashtag/$1\">#$1</a>$2")
  }

  def getUserName(userId: Option[Int]): String = {
    if (userId.isEmpty) return null
    val user = userRepository.findByUserId(userId.get)
    user.get.userName
  }

  def search(query: String, _until: Option[String], _append: Option[Int]) = Action { implicit request =>
    val until = _until.getOrElse("")
    val append = _append.getOrElse(0)
    searchInternal(request, query, until, append)
  }

  def searchInternal(request: Request[AnyContent], query: String, until: String, append: Int) = {
    val id = request.session.get("user_id").map(_.toInt)
    val name = getUserName(id)

    val rows = if (until.length == 0) {
      tweetRepository.findOrderByCreatedAtDesc()
    } else {
      tweetRepository.findOrderByCreatedAtDesc(until)
    }

    var tweets = Seq[Tweet]()
    val b = new Breaks
    b.breakable {
      for (row <- rows) {
        var tweet = new Tweet(row.userId)
        tweet = tweet.copy(html = htmlify(row.text))
        tweet = tweet.copy(time = row.createdAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
        tweet = tweet.copy(userName = getUserName(Some(row.userId)))
        if (row.text.contains(query)) {
          tweets = tweets :+ tweet
        }
        if (tweets.size == PER_PAGE) b.break
      }
    }

    if (append == 0) {
      Ok(views.html.search(name, query, tweets))
    } else {
      Ok(views.html._tweet(tweets))
    }
  }

  def searchTag(tag: String, _until: Option[String], _append: Option[Int]) = Action { implicit request =>
    val until = _until.getOrElse("")
    val append = _append.getOrElse(0)
    val query = "#" + tag
    searchInternal(request, query, until, append)
  }

  def user(user: String, _until: Option[String], _append: Option[Int]) = Action { implicit request =>
    val until = _until.getOrElse("")
    val append = _append.getOrElse(0)
    val id = request.session.get("user_id").map(_.toInt)
    val name = getUserName(id)
    var mypage: Boolean = false
    if (user == name) mypage = true
    val userId_ = getUserId(user)
    if (userId_.isEmpty) {
      NotFound("not found")
    } else {
      val userId = userId_.get
      var isFriend: Boolean = false
      if (name != null) {
        val friends = loadFriend(name)
        if (friends.contains(user)) isFriend = true
      }
      val rows = if (until.length == 0) {
        tweetRepository.findByUserIdOrderByCreatedAtDesc(userId)
      } else {
        tweetRepository.findByUserIdOrderByCreatedAtDesc(userId, until)
      }

      var tweets = Seq[Tweet]()
      val b = new Breaks
      b.breakable {
        for (row <- rows) {
          var tweet = new Tweet(row.userId)
          tweet = tweet.copy(html = htmlify(row.text))
          tweet = tweet.copy(time = row.createdAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
          tweet = tweet.copy(userName = user)
          tweets = tweets :+ tweet
          if (tweets.size == PER_PAGE) b.break
        }
      }
      if (append == 0) {
        Ok(views.html.user(name, user, mypage, isFriend, tweets))
      } else {
        Ok(views.html._tweet(tweets))
      }
    }
  }

  private def getUserId(userName: String): Option[Int] = {
    if (userName == null) return None
    val user = userRepository.findByUserName(userName)
    user.map(_.userId)
  }

  def login() = Action(parse.form(LoginRequest.form)) { implicit request =>
    val userForm = request.body
    val userOp = userRepository.findByUserName(userForm.name)
    if (userOp.isEmpty) {
      NotFound("not found")
    } else {
      val user = userOp.get
      var md: MessageDigest = null
      var digest: Array[Byte] = null
      try
        md = MessageDigest.getInstance("SHA-1")
      catch {
        case e: NoSuchAlgorithmException =>
          e.printStackTrace()
      }
      digest = md.digest((user.salt + userForm.password).getBytes)
      if (!(user.password == HexBin.encode(digest).toLowerCase)) {
        Found("/").withSession("flush" -> "ログインエラー")
      } else {
        Found("/").withSession("user_id" -> user.userId.toString)
      }
    }
  }

  def loadFriend(name: String): Seq[String] = {
    val request: WSRequest = ws.url("http://localhost:8081/" + name)
    val complexRequest: WSRequest = request.addHttpHeaders("Accept" -> "application/json")
    val response = Await.result(complexRequest.get(), Duration.Inf)
    val json = response.json
    (json \ "friends").as[Seq[String]]
  }
}
