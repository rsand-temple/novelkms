package com.richardsand.novelkms;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.dropwizard.core.Configuration;
import lombok.Getter;

@Getter
public class NovelKmsConfig extends Configuration {
    @Getter
    public static class Database {
        String url;
        String adminUser;
        String adminPwd;
    }
    
    @JsonProperty
    Database database;
}
