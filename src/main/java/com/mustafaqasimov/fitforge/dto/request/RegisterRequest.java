package com.mustafaqasimov.fitforge.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor(onConstructor_ = @com.fasterxml.jackson.annotation.JsonCreator)
@AllArgsConstructor
public class RegisterRequest {
  @NotBlank
  @Email
  @Size(max = 254)
  private String email;

  @NotBlank
  @Size(min = 12, max = 128)
  private String password;

  @NotBlank
  @Size(max = 60)
  private String displayName;

  private String timezone = "UTC";

  @com.fasterxml.jackson.annotation.JsonAnySetter
  public void unknownField(String field, Object value) {
    throw new IllegalArgumentException("Unknown request field");
  }
}
