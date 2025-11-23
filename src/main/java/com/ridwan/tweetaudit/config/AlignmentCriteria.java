package com.ridwan.tweetaudit.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "alignment")
public class AlignmentCriteria {

    private List<String> forbiddenWords;
    private boolean checkProfessionalism;
    private String context;
    private String desiredTone;
    
}
