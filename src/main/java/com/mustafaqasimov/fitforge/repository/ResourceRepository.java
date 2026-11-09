package com.mustafaqasimov.fitforge.repository;

import com.mustafaqasimov.fitforge.entity.Resource;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ResourceRepository extends JpaRepository<Resource, UUID> {
  List<Resource> findByKindAndOwner(String kind, UUID owner);

  List<Resource> findByKind(String kind);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from Resource r where r.id=:id")
  Optional<Resource> lock(@Param("id") UUID id);

  @Query(
      value = "select * from resources where kind='user' and data->>'email'=:email",
      nativeQuery = true)
  Optional<Resource> byEmail(@Param("email") String email);

  @Query(
      value = "select * from resources where kind='refresh' and data->>'hash'=:hash",
      nativeQuery = true)
  Optional<Resource> byHash(@Param("hash") String hash);
}
