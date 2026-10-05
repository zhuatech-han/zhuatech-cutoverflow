// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.cutoverflow;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;

/** 切换方案基线和独立指挥人。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "cutover_plan")
public class CutoverPlan {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "revision", nullable = false)
  public Long version = 0L;

  @Column(name = "code", nullable = false, length = 60)
  public String code;

  @Column(name = "title", nullable = false, length = 160)
  public String title;

  @Column(name = "category", nullable = false, length = 60)
  public String category;

  @Column(name = "department_id", nullable = false)
  public Long departmentId;

  @Column(name = "author_id", nullable = false)
  public Long authorId;

  @Column(name = "director_id", nullable = false)
  public Long directorId;

  @Column(name = "system_name", nullable = false, length = 160)
  public String systemName;

  @Column(name = "scope_text", nullable = false, length = 2000)
  public String scope;

  @Column(name = "decision_criteria", nullable = false, length = 2000)
  public String decisionCriteria;

  @Column(name = "recovery_criteria", nullable = false, length = 2000)
  public String recoveryCriteria;

  @Column(name = "status", nullable = false, length = 20)
  public String status = "DRAFT";

  @Column(name = "submitted", nullable = false)
  public boolean submitted = false;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;
}
