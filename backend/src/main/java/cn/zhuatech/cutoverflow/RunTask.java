// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.cutoverflow;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;

/** 执行与补偿证据分别保存，不覆盖原始执行事实。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "run_task")
public class RunTask {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "run_id", nullable = false)
  public Long runId;

  @Column(name = "step_id", nullable = false)
  public Long stepId;

  @Column(name = "status", nullable = false, length = 20)
  public String status = "PENDING";

  @Column(name = "rollback_status", nullable = false, length = 20)
  public String rollbackStatus = "NONE";

  @Column(name = "execution_evidence", nullable = false, length = 2000)
  public String executionEvidence = "";

  @Column(name = "review_evidence", nullable = false, length = 2000)
  public String reviewEvidence = "";

  @Column(name = "rollback_evidence", nullable = false, length = 2000)
  public String rollbackEvidence = "";

  @Column(name = "rollback_review", nullable = false, length = 2000)
  public String rollbackReview = "";
}
