package com.mustafaqasimov.fitforge.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor(onConstructor_ = @com.fasterxml.jackson.annotation.JsonCreator)
@AllArgsConstructor
public class LoginRequest {
  @NotBlank
  @Email
  @Size(max = 254)
  private String email;

  @NotBlank
  @Size(max = 128)
  private String password;

  @Size(max = 100)
  private String deviceName;

  @com.fasterxml.jackson.annotation.JsonAnySetter
  public void unknownField(String field, Object value) {
    throw new IllegalArgumentException("Unknown request field");
  }
}
