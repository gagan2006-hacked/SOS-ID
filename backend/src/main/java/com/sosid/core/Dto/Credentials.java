package com.sosid.core.Dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record Credentials(@Email @NotBlank String email, @NotBlank @Size(min = 8, max = 128) String password) {
}