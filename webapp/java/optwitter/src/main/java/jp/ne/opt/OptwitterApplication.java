package jp.ne.opt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.org.apache.xerces.internal.impl.dv.util.HexBin;
import jp.ne.opt.model.Tweet;
import jp.ne.opt.model.User;
import jp.ne.opt.repository.TweetRepository;
import jp.ne.opt.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.format.DateTimeFormatter;
import java.util.*;

@SpringBootApplication
@Controller
public class OptwitterApplication {

	public static void main(String[] args) {
		SpringApplication.run(OptwitterApplication.class, args);
	}

	public static final int PER_PAGE = 50;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	UserRepository userRepository;

	@Autowired
	TweetRepository tweetRepository;

	@Autowired
	HttpSession session;

	@RequestMapping(value = "/initialize", method = RequestMethod.GET)
	public ResponseEntity initialize() {
		if (!initializer()){
			throw new InternalServerError("error");
		}
		HashMap<String,String> result = new HashMap<>();
		result.put("result", "ok");

		return ResponseEntity.ok(result);
	}

	@RequestMapping(value = "/", method = RequestMethod.GET)
	public String index(Model model,
	 					@RequestParam(name = "until", defaultValue = "") String until,
						@RequestParam(name = "append", defaultValue = "0") Integer append) {

		String name = getUserName(Integer.class.cast(session.getAttribute("user_id")));
		if (name == null) {
			String flush = String.class.cast(session.getAttribute("flush"));
			session.setAttribute("flush", null);
			if (flush != null) {
				model.addAttribute("flush", flush);
			}
			return "login";
		}

		List<String> friends = loadFriend(name);

		List<Tweet> rows;
		if (until.length() == 0){
			rows = tweetRepository.findOrderByCreatedAtDesc();
		} else {
			rows = tweetRepository.findOrderByCreatedAtDesc(until);
		}

		List<Tweet> tweets = new ArrayList<>();
		for (Tweet row: rows){
			Tweet tweet = new Tweet();
			tweet.setHtml(htmlify(row.getText()));
			tweet.setTime(row.getCretedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
			String friendName = getUserName(row.getUserId());
			tweet.setUserName(friendName);

			if (friends.contains(friendName)) {
				tweets.add(tweet);
			}

			if (tweets.size() == PER_PAGE){
				break;
			}
		}
		model.addAttribute("tweets", tweets);

		if (append != 0) {
			return "_tweet :: append";
		}

		model.addAttribute("name", name);
		return "index";
	}

	@RequestMapping(value = "/", method = RequestMethod.POST)
	public String tweet(@RequestParam("text") String text) {
		String name = getUserName(Integer.class.cast(session.getAttribute("user_id")));
		if (name == null || text.length() == 0) {
			return "redirect:/";
		}

		tweetRepository.create(Integer.class.cast(session.getAttribute("user_id")), text);

		return "redirect:/";
	}

	@RequestMapping(value = "/login", method = RequestMethod.POST)
	public String login(@RequestParam("name") String name,
						@RequestParam("password") String password) {
		User user = userRepository.findByUserName(name);
		if (user == null) {
			throw new ContentNotFound("not found");
		}

		MessageDigest md = null;
		byte[] digest = null;
		try {
			md = MessageDigest.getInstance("SHA-1");
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		digest = md.digest((user.getSalt()+password).getBytes());

		if (!user.getPassword().equals(HexBin.encode(digest).toLowerCase())) {
			session.setAttribute("flush", "ログインエラー");
			return "redirect:/";
		}

		session.setAttribute("user_id", user.getUserId());
		return "redirect:/";
	}

	@RequestMapping(value = "/logout", method = RequestMethod.POST)
	public String logout() {
		session.setAttribute("user_id", null);
		return "redirect:/";
	}

	@RequestMapping(value = "/follow", method = RequestMethod.POST)
	public String follow(@RequestParam("user") String user) {
		String name = getUserName(Integer.class.cast(session.getAttribute("user_id")));
		if (name == null) {
			return "redirect:/";
		}

		Map<String, String> map = new HashMap<>();
		map.put("user", user);
		HttpEntity<Map> entity = new HttpEntity<>(map);

		RestTemplate restTemplate = new RestTemplate();
		ResponseEntity<String> response = restTemplate.exchange(
				 "http://localhost:8081/"+name,
				HttpMethod.POST, entity, String.class);
		if (response.getStatusCode() != HttpStatus.OK) {
			throw new InternalServerError("error");
		}
		return "redirect:/"+user;
	}

	@RequestMapping(value = "/unfollow", method = RequestMethod.POST)
	public String unfollow(@RequestParam("user") String user) {
		String name = getUserName(Integer.class.cast(session.getAttribute("user_id")));
		if (name == null) {
			return "redirect:/";
		}

		Map<String, String> map = new HashMap<>();
		map.put("user", user);
		HttpEntity<Map> entity = new HttpEntity<>(map);

		RestTemplate restTemplate = new RestTemplate();
		ResponseEntity<String> response = restTemplate.exchange(
				"http://localhost:8081/"+name,
				HttpMethod.DELETE, entity, String.class);
		if (response.getStatusCode() != HttpStatus.OK) {
			throw new InternalServerError("error");
		}
		return "redirect:/"+user;
	}

	@RequestMapping(value = {"/search", "hashtag/{tag}"}, method = RequestMethod.GET)
	public String search(Model model,
						 @RequestParam(name = "q", defaultValue = "") String query,
						 @PathVariable("tag") Optional<String> tag,
						 @RequestParam(name = "until", defaultValue = "") String until,
						 @RequestParam(name = "append", defaultValue = "0") Integer append) {
		String name = getUserName(Integer.class.cast(session.getAttribute("user_id")));
		if (tag.isPresent()) {
			query = "#" + tag.get();
		}

		List<Tweet> rows;
		if (until.length() == 0){
			rows = tweetRepository.findOrderByCreatedAtDesc();
		} else {
			rows = tweetRepository.findOrderByCreatedAtDesc(until);
		}

		List<Tweet> tweets = new ArrayList<>();
		for (Tweet row: rows){
			Tweet tweet = new Tweet();
			tweet.setHtml(htmlify(row.getText()));
			tweet.setTime(row.getCretedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
			tweet.setUserName(getUserName(row.getUserId()));

			if (row.getText().contains(query)) {
				tweets.add(tweet);
			}

			if (tweets.size() == PER_PAGE){
				break;
			}
		}
		model.addAttribute("tweets", tweets);

		if (append != 0) {
			return "_tweet :: append";
		}

		model.addAttribute("name", name);
		model.addAttribute("query", query);
		return "search";
	}


	@RequestMapping(value = "/{user}", method = RequestMethod.GET)
	public String user(Model model,
					   @PathVariable("user") String user,
					   @RequestParam(name = "until", defaultValue = "") String until,
					   @RequestParam(name = "append", defaultValue = "0") Integer append) {
		String name = getUserName(Integer.class.cast(session.getAttribute("user_id")));
		boolean mypage = false;
		if(user.equals(name)){
			mypage = true;
		}
		Integer userId = getUserId(user);
		if (userId <= 0) {
			throw new ContentNotFound("not found");
		}

		boolean isFriend = false;

		if (name != null){
			List<String> friends = loadFriend(name);
			if (friends.contains(user)){
				isFriend = true;
			}
		}

		List<Tweet> rows;
		if (until.length() == 0){
			rows = tweetRepository.findByUserIdOrderByCreatedAtDesc(userId);
		} else {
			rows = tweetRepository.findByUserIdOrderByCreatedAtDesc(userId, until);
		}

		List<Tweet> tweets = new ArrayList<>();
		for (Tweet row: rows){
			Tweet tweet = new Tweet();
			tweet.setHtml(htmlify(row.getText()));
			tweet.setTime(row.getCretedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
			tweet.setUserName(user);
			tweets.add(tweet);

			if (tweets.size() == PER_PAGE){
				break;
			}
		}
		model.addAttribute("tweets", tweets);

		if (append != 0) {
			return "_tweet :: append";
		}

		model.addAttribute("name", name);
		model.addAttribute("mypage", mypage);
		model.addAttribute("isFriend", isFriend);
		return "user";
	}

	@ResponseStatus(HttpStatus.BAD_REQUEST)
	@ExceptionHandler(BadRequest.class)
	ResponseEntity<String> badRequest(RuntimeException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
	}

	@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
	@ExceptionHandler(InternalServerError.class)
	ResponseEntity<String> internalServerError(RuntimeException ex) {
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
	}

	@ResponseStatus(HttpStatus.NOT_FOUND)
	@ExceptionHandler(ContentNotFound.class)
	ResponseEntity<String> contentNotFound(RuntimeException ex) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
	}

	public static class BadRequest extends RuntimeException {
		public BadRequest(String errorMessage) {
			super(errorMessage);
		}
	}

	public static class InternalServerError extends RuntimeException {
		public InternalServerError(String errorMessage) {
			super(errorMessage);
		}
	}

	public static class ContentNotFound extends RuntimeException {
		public ContentNotFound(String errorMessage) {
			super(errorMessage);
		}
	}

	String getUserName(Integer userId) {
		if (userId == null) {
			return null;
		}
		User user = userRepository.findByUserId(userId);
		return user.getUsername();
	}

	Integer getUserId(String userName) {
		if (userName == null) {
			return null;
		}
		User user = userRepository.findByUserName(userName);
		return user.getUserId();
	}

	String htmlify(String text){
		if (text == null) {
			return "";
		}
		return text.replaceAll("&", "&amp;")
				.replaceAll("<", "&lt;")
				.replaceAll(">", "&gt;")
				.replaceAll("'", "&apos;")
				.replaceAll("\"", "&quot;")
				.replaceAll("#(\\S+)(\\s|$)", "<a class=\"hashtag\" href=\"/hashtag/$1\">#$1</a>$2");
	}

	List<String> loadFriend(String name){
		ObjectMapper mapper = new ObjectMapper();
		List<String> friends = new ArrayList<>();
		RestTemplate restTemplate = new RestTemplate();
		try {
			String response = restTemplate.getForObject("http://localhost:8081/"+name, String.class);
			JsonNode root = mapper.readTree(response);
			String friendList = root.get("friends").toString()
					.replaceAll("\"", "")
					.replaceAll("\\[", "")
					.replaceAll("\\]", "");
			friends.addAll(Arrays.asList(friendList.split(",")));
		} catch (ResourceAccessException e ){
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		return friends;
	}

	@Transactional
	boolean initializer() {
		jdbcTemplate.update("DELETE FROM tweets WHERE id > 100000");
		jdbcTemplate.update("DELETE FROM users WHERE id > 1000");

		RestTemplate restTemplate = new RestTemplate();
		ResponseEntity<String> response = restTemplate.getForEntity("http://localhost:8081/initialize", String.class);
		if (response.getStatusCode() != HttpStatus.OK) {
				return false;
		}
		return true;
	}

}
