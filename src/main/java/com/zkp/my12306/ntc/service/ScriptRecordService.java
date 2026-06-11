package com.zkp.my12306.ntc.service;

import com.zkp.my12306.ntc.dto.ScriptRecordResponseDto;
import com.zkp.my12306.ntc.dto.ScriptSaveRequestDto;
import com.zkp.my12306.ntc.dto.ScriptWorkSummaryDto;

import java.util.List;

public interface ScriptRecordService {

    ScriptRecordResponseDto save(String currentUser, ScriptSaveRequestDto request);

    List<ScriptRecordResponseDto> listByWorkId(String currentUser, String workId);

    List<ScriptWorkSummaryDto> listWorks(String currentUser);

    void deleteWork(String currentUser, String workId);

    ScriptRecordResponseDto getById(String currentUser, Long id);
}
