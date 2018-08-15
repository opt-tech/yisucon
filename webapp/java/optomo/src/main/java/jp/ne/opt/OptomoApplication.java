package jp.ne.opt;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jp.ne.opt.model.Friends;
import jp.ne.opt.repository.FriendsRepository;


@SpringBootApplication
@RestController
public class OptomoApplication {

	public static void main(String[] args) {
		SpringApplication.run(OptomoApplication.class, args);
	}

	@Autowired
	FriendsRepository friendRepository;

	@RequestMapping(value = "/initialize", method = RequestMethod.GET)
	public ResponseEntity<String> initialize() {
		try{
			initializer();
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
			throw new InternalServerError("error");
		}
		return ResponseEntity.ok("ok");
	}

	@RequestMapping(value = "/{me}", method = RequestMethod.GET)
	public HashMap<String, List<String>> myFriends(@PathVariable("me") String me) {
		HashMap<String, List<String>> map = new HashMap<>();
		map.put("friends", get_friends(me));
		return map;
	}

	@RequestMapping(value = "/{me}", method = RequestMethod.POST)
	public HashMap<String, List<String>> addFriend(@PathVariable("me") String me, @RequestBody HashMap<String, String> formData) {
		List<String> friends = new LinkedList<>(get_friends(me));
		String user = formData.get("user");

		if (friends.contains(user)) {
			throw new BadRequest(user + " is already your friend.");
		}
		friends.add(user);
		if (!update_friends(me, String.join(",", friends))) {
			throw new InternalServerError("error");
		}
		HashMap<String, List<String>> map = new HashMap<>();
		map.put("friends", friends);
		return map;
	}

	@RequestMapping(value = "/{me}", method = RequestMethod.DELETE)
	public HashMap<String, List<String>> deleteFriend(@PathVariable("me") String me, @RequestBody HashMap<String, String> formData) {
		List<String> friends = new LinkedList<>(get_friends(me));
		String user = formData.get("user");

		if (!friends.contains(user)) {
			throw new BadRequest(user + " not your friend.");
		}

		friends.remove(user);
		if (!update_friends(me, String.join(",", friends))) {
			throw new InternalServerError("error");
		}
		HashMap<String, List<String>> map = new HashMap<>();
		map.put("friends", friends);
		return map;
	}

	List<String> get_friends(String name) {
		Friends friends = friendRepository.findByUserName(name);
		return Arrays.asList(friends.getFriends().split(","));
	}

	boolean update_friends(String me, String friends) {
		return friendRepository.update(friends, me);
	}

	void initializer() throws IOException, InterruptedException {
		String[] cmd = new String[]{"mysql", "-u", "root", "-D", "optomo"};

		ProcessBuilder builder = new ProcessBuilder(cmd);
		builder.redirectInput(ProcessBuilder.Redirect.from(new File("../../sql/seed_optomo.sql")));

		try {
			Process p = builder.start();
			p.waitFor();
			System.out.println(builder.redirectInput());
		} catch (IOException | InterruptedException e) {
			throw e;
		}
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
}
