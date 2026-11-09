package com.mustafaqasimov.fitforge.service.interfaces;

import com.mustafaqasimov.fitforge.dto.request.*;
import com.mustafaqasimov.fitforge.dto.response.AuthResponse;

public interface AuthService {
  AuthResponse register(RegisterRequest request);

  AuthResponse login(LoginRequest request);
}
