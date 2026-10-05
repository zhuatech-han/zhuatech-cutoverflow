// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.cutoverflow;

import jakarta.persistence.*;
import java.time.*;

/** 只追加的生命周期操作与备注，跟随业务事务持久化。 官网 https://www.zhuatech.cn/；商业咨询微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "flow_event")
public class FlowEvent {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "kind", nullable = false, length = 20)
  public String kind;

  @Column(name = "object_id", nullable = false)
  public Long objectId;

  @Column(name = "department_id", nullable = false)
  public Long departmentId;

  @Column(name = "actor_id", nullable = false)
  public Long actorId;

  @Column(name = "action", nullable = false, length = 40)
  public String action;

  @Column(name = "note", nullable = false, length = 2000)
  public String note;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;
}
