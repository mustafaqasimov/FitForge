package com.mustafaqasimov.fitforge.entity.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.mustafaqasimov.fitforge.entity.base.BaseEntity;
import com.mustafaqasimov.fitforge.enums.WeightUnit;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

import java.time.ZoneId;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@FieldDefaults(level = lombok.AccessLevel.PRIVATE)
@Table(name = "users")
public class User extends BaseEntity {

    @Column(name = "user_name", unique = true, nullable = false)
    String userName;

    @Column(name = "email", unique = true, nullable = false)
    String email;

    @Column(name = "password", nullable = false)
    @JsonIgnore
    String password;

    @Column(name = "timezone", nullable = false, length = 64)
    ZoneId timezone = ZoneId.of("UTC");

    @Enumerated(EnumType.STRING)
    @Column(name = "weight_unit", nullable = false, length = 2)
    WeightUnit weightUnit = WeightUnit.KG;

    @Column(name = "weekly_goal", nullable = false)
    int weeklyGoal = 3;
}
