package com.zkp.my12306.ntc.service;

import com.zkp.my12306.ntc.dto.WorkTitleGenerateResponseDto;

public interface ScriptWorkTitleService {

    WorkTitleGenerateResponseDto generateTitle(String currentUser, String workId, String novelExcerpt);
}
