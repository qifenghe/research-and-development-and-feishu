package com.lhr.rnd.service;

import com.lhr.rnd.model.UserAccount;

public record AuthLoginResult(
        UserAccount user,
        String accessToken
) {
}
