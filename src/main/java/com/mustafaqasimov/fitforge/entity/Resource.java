package com.mustafaqasimov.fitforge.entity;

import jakarta.persistence.*;
import java.util.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "resources")
public class Resource {
  @Id public UUID id;

  @Column(nullable = false)
  public String kind;

  public UUID owner;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb", nullable = false)
  public Map<String, Object> data = new LinkedHashMap<>();

  public Resource() {}

  public Resource(String kind, UUID owner, Map<String, Object> data) {
    this.id = UUID.randomUUID();
    this.kind = kind;
    this.owner = owner;
    this.data = data;
  }
}
