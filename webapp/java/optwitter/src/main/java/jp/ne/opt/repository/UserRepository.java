package jp.ne.opt.repository;

import jp.ne.opt.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class UserRepository {
    @Autowired
    NamedParameterJdbcTemplate jdbcTemplate;

    RowMapper<User> rowMapper = (rs, i) -> {
        User user = new User();
        user.setUserId(rs.getInt("id"));
        user.setUsername(rs.getString("name"));
        user.setSalt(rs.getString("salt"));
        user.setPassword(rs.getString("password"));
        return user;
    };

    public User findByUserName(String name) {
        SqlParameterSource source = new MapSqlParameterSource().addValue("name", name);
        List<User> user = jdbcTemplate.query(
                "SELECT * FROM users WHERE name = :name",
                source, rowMapper);
        if (user != null && user.size() > 0) {
            return user.get(0);
        } else {
            return null;
        }
    }

    public User findByUserId(Integer id) {
        SqlParameterSource source = new MapSqlParameterSource().addValue("id", id);
        List<User> user = jdbcTemplate.query(
                "SELECT * FROM users WHERE id = :id",
                source, rowMapper);
        if (user != null && user.size() > 0) {
            return user.get(0);
        } else {
            return null;
        }
    }

}
