package com.mustafaqasimov.fitforge.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class RateLimitService {
  private final JdbcTemplate jdbc;

  public RateLimitService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean allow(String scope, int limit) {
    int count =
        jdbc.queryForObject(
            """
            INSERT INTO auth_rate_limits(scope,window_start,attempts) VALUES (?,now(),1)
            ON CONFLICT(scope) DO UPDATE SET
            attempts=CASE WHEN auth_rate_limits.window_start < now()-interval '15 minutes' THEN 1 ELSE auth_rate_limits.attempts+1 END,
            window_start=CASE WHEN auth_rate_limits.window_start < now()-interval '15 minutes' THEN now() ELSE auth_rate_limits.window_start END
            RETURNING attempts
            """,
            Integer.class,
            scope);
    return count <= limit;
  }
}
