package jp.ne.opt.repository;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import jp.ne.opt.model.Friends;

@Repository
public class FriendsRepository {
    @Autowired
    NamedParameterJdbcTemplate jdbcTemplate;

    RowMapper<Friends> rowMapper = (rs, i) -> {
        Friends friends = new Friends();
        friends.setFriends(rs.getString("friends"));
        return friends;
    };

    public Friends findByUserName(String name) {
        SqlParameterSource source = new MapSqlParameterSource().addValue("me", name);
        List<Friends> friends = jdbcTemplate.query(
                "SELECT * FROM friends WHERE me = :me",
                source, rowMapper);
        return friends.get(0);
    }

    @Transactional
    public boolean update(String friends, String name) {
        SqlParameterSource source = new MapSqlParameterSource()
                .addValue("friends", friends)
                .addValue("me", name);
        return 1 == jdbcTemplate.update(
                "UPDATE friends SET friends = :friends WHERE me = :me",
                source);
    }
}
