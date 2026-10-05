// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.cutoverflow;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;

/** 冻结任务说明、执行与核验职责及前置依赖。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "cutover_step")
public class CutoverStep {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "plan_id", nullable = false)
  public Long planId;

  @Column(name = "title", nullable = false, length = 160)
  public String title;

  @Column(name = "owner_id", nullable = false)
  public Long ownerId;

  @Column(name = "reviewer_id", nullable = false)
  public Long reviewerId;

  @Column(name = "minutes", nullable = false)
  public Integer minutes;

  @Column(name = "instructions", nullable = false, length = 2000)
  public String instructions;

  @Column(name = "verification", nullable = false, length = 2000)
  public String verification;

  @Column(name = "rollback", nullable = false, length = 2000)
  public String rollback;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "step_dependency", joinColumns = @JoinColumn(name = "step_id"))
  @Column(name = "predecessor_id")
  public Set<Long> dependencies = new HashSet<>();
}
