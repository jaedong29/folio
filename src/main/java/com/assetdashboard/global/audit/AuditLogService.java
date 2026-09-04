package com.assetdashboard.global.audit;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 감사 기록이 업무 변경과 같은 커밋 경계를 쓰도록 강제한다. */
@Service
@RequiredArgsConstructor
public class AuditLogService {

  private final AuditLogRepository repository;

  /** 호출자의 트랜잭션 안에서 성공 액션을 기록한다. */
  @Transactional(propagation = Propagation.MANDATORY)
  public void record(Long subjectUserId, AuditAction action) {
    repository.save(AuditLog.record(subjectUserId, action));
  }
}
