package com.slack.bot.domain.link.repository;

import com.slack.bot.domain.link.dto.AccessLinkSequenceBlockDto;

public interface AccessLinkSequenceRepository {

    AccessLinkSequenceBlockDto allocateBlock(Long size, Long initialValue);
}
