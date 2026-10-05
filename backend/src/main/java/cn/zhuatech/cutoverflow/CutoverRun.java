// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.cutoverflow;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;

/** 一次演练或正式切换的独立状态和期限。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "cutover_run")
public class CutoverRun {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "revision", nullable = false)
  public Long version = 0L;

  @Column(name = "plan_id", nullable = false)
  public Long planId;

  @Column(name = "department_id", nullable = false)
  public Long departmentId;

  @Column(name = "mode", nullable = false, length = 20)
  public String mode;

  @Column(name = "reference_no", nullable = false, length = 100)
  public String reference;

  @Column(name = "status", nullable = false, length = 20)
  public String status = "RUNNING";

  @Column(name = "started_at", nullable = false)
  public Instant startedAt;

  @Column(name = "deadline", nullable = false)
  public Instant deadline;

  @Column(name = "decision_note", nullable = false, length = 2000)
  public String decision = "";
}
