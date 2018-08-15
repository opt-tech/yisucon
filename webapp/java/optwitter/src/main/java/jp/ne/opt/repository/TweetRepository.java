package jp.ne.opt.repository;

import jp.ne.opt.model.Tweet;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public class TweetRepository {
    @Autowired
    NamedParameterJdbcTemplate jdbcTemplate;

    RowMapper<Tweet> rowMapper = (rs, i) -> {
        Tweet tweet = new Tweet();
        tweet.setUserId(rs.getInt("user_id"));
        tweet.setText(rs.getString("text"));
        tweet.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        return tweet;
    };

    public List<Tweet> findOrderByCreatedAtDesc(String createdAt) {
        SqlParameterSource source = new MapSqlParameterSource().addValue("created_at", createdAt);
        return jdbcTemplate.query(
                "SELECT * FROM tweets WHERE created_at < :created_at ORDER BY created_at DESC",
                source, rowMapper);
    }

    public List<Tweet> findOrderByCreatedAtDesc(){
        return jdbcTemplate.query(
                "SELECT * FROM tweets ORDER BY created_at DESC",
                rowMapper);
    }

    public List<Tweet> findByUserIdOrderByCreatedAtDesc(Integer userId) {
        SqlParameterSource source = new MapSqlParameterSource().addValue("user_id", userId);
        return jdbcTemplate.query(
                "SELECT * FROM tweets WHERE user_id = :user_id ORDER BY created_at DESC",
                source, rowMapper);
    }

    public List<Tweet> findByUserIdOrderByCreatedAtDesc(Integer userId, String createdAt) {
        SqlParameterSource source = new MapSqlParameterSource()
                .addValue("user_id", userId)
                .addValue("created_at", createdAt);
        return jdbcTemplate.query(
                "SELECT * FROM tweets WHERE user_id = :user_id AND created_at < :created_at ORDER BY created_at DESC",
                source, rowMapper);
    }

    @Transactional
    public Integer create(Integer userId, String text) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        SqlParameterSource source = new MapSqlParameterSource()
                .addValue("user_id", userId)
                .addValue("text", text);
        return jdbcTemplate.update(
                "INSERT INTO tweets (user_id, text, created_at) VALUES (:user_id, :text, NOW())",
                source, keyHolder);
    }
}
