// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.cutoverflow;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.json.JsonMapper;

/** 冻结方案、演练门禁、依赖执行、独立核验与逆序补偿事务。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class CutoverService {
  final Store db;
  final AccessService access;
  final Clock clock;
  final JsonMapper json = JsonMapper.builder().findAndAddModules().build();

  public CutoverService(Store d, AccessService a, Clock c) {
    db = d;
    access = a;
    clock = c;
  }

  /** 方案输入不能携带放行结论。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record PlanInput(
      Long version,
      String requestKey,
      String code,
      String title,
      String category,
      Long departmentId,
      Long directorId,
      String systemName,
      String scope,
      String decisionCriteria,
      String recoveryCriteria) {}

  /** 任务定义及同一方案的前置任务。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record StepInput(
      Long version,
      String requestKey,
      String title,
      Long ownerId,
      Long reviewerId,
      Integer minutes,
      String instructions,
      String verification,
      String rollback,
      Set<Long> dependencies) {}

  /** 幂等命令携带被阅读的基线或执行修订。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Command(Long version, String requestKey, String note) {}

  /** 每次运行独立保存执行期限与变更/演练凭证编号。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record RunInput(
      Long version, String requestKey, String mode, String reference, Instant deadline) {}

  /** 仅给当前部门范围内人员及不含凭证的管理选项。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object options() {
    access.require("plan.read");
    return Map.of(
        "accounts",
        db.all(Account.class).stream()
            .filter(a -> a.enabled && access.visible(a.departmentId))
            .map(
                a ->
                    Map.of(
                        "id",
                        a.id,
                        "displayName",
                        a.displayName,
                        "departmentId",
                        a.departmentId,
                        "permissions",
                        db.get(AccessRole.class, a.roleId).permissions))
            .toList(),
        "departments",
        db.all(Department.class).stream().filter(d -> access.visible(d.id)).toList(),
        "dictionaries",
        db.all(DictionaryEntry.class),
        "settings",
        db.all(SystemSetting.class));
  }

  /** 数据库分页、范围、搜索、状态及固定排序。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object list(String search, String status, int page, int size, String sort) {
    access.require("plan.read");
    if (search == null
        || search.length() > 100
        || page < 0
        || page > 100000
        || size < 1
        || size > 100) throw new Problem(400, "INVALID_INPUT");
    if (!Set.of("", "DRAFT", "REVIEW", "READY", "RETIRED").contains(status))
      throw new Problem(400, "INVALID_STATUS");
    String order =
        switch (sort) {
          case "newest" -> "p.id desc";
          case "oldest" -> "p.id asc";
          case "code" -> "p.code asc";
          default -> throw new Problem(400, "INVALID_INPUT");
        };
    String where =
        scopeQuery()
            + " and (lower(p.code) like :search escape '!' or lower(p.title) like :search escape '!')"
            + (status.isEmpty() ? "" : " and p.status=:status");
    var count = db.jpql(Long.class, "select count(p) from CutoverPlan p where " + where);
    var q = db.jpql(CutoverPlan.class, "from CutoverPlan p where " + where + " order by " + order);
    for (var x : List.of(count, q)) {
      bindScope(x);
      x.setParameter(
          "search",
          "%"
              + search
                  .toLowerCase(Locale.ROOT)
                  .replace("!", "!!")
                  .replace("%", "!%")
                  .replace("_", "!_")
              + "%");
      if (!status.isEmpty()) x.setParameter("status", status);
    }
    return Map.of(
        "total",
        count.getSingleResult(),
        "items",
        q.setFirstResult(page * size).setMaxResults(size).getResultList().stream()
            .map(this::planView)
            .toList());
  }

  /** 基线、任务、历次运行与最多500条近期事件。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object detail(Long id) {
    access.require("plan.read");
    var p = plan(id);
    return Map.of(
        "record",
        planView(p),
        "steps",
        steps(id).stream().map(this::stepView).toList(),
        "runs",
        db.query(CutoverRun.class, "from CutoverRun where planId=?1 order by id desc", id).stream()
            .map(this::runView)
            .toList(),
        "events",
        events("plan", id));
  }

  /** 创建/编辑本人草稿，编号与部门不能更改；修订阻止旧页面覆盖。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object save(Long id, PlanInput v) {
    gate("plan.write");
    if (self() || v == null) throw new Problem(400, "INVALID_INPUT");
    String payload = payload("plan-save", id, v);
    Long retry = replay(v.requestKey, payload);
    if (retry != null) return planView(plan(retry));
    var p = id == null ? new CutoverPlan() : plan(id);
    if (id != null) {
      assigned(p.authorId);
      state(p.status, "DRAFT");
      version(p.version, v.version);
    }
    String code = text(v.code, 60).toUpperCase(Locale.ROOT);
    if (!code.matches("[A-Z0-9_.-]{3,60}")) throw new Problem(400, "INVALID_CODE");
    if (v.departmentId == null) throw new Problem(400, "INVALID_INPUT");
    access.department(v.departmentId);
    db.get(Department.class, v.departmentId);
    if (id != null && (!p.code.equals(code) || !p.departmentId.equals(v.departmentId)))
      throw new Problem(409, "IMMUTABLE_IDENTITY");
    var director = eligible(v.directorId, v.departmentId, "plan.direct");
    if (director.id.equals(access.current().id)) throw new Problem(400, "INDEPENDENCE_REQUIRED");
    if (db.query(
            DictionaryEntry.class,
            "from DictionaryEntry where type='cutover' and code=?1",
            v.category)
        .isEmpty()) throw new Problem(400, "INVALID_CATEGORY");
    p.code = code;
    p.title = text(v.title, 160);
    p.category = v.category;
    p.departmentId = v.departmentId;
    p.directorId = director.id;
    p.systemName = text(v.systemName, 160);
    p.scope = evidence(v.scope);
    p.decisionCriteria = evidence(v.decisionCriteria);
    p.recoveryCriteria = evidence(v.recoveryCriteria);
    if (id == null) {
      p.authorId = access.current().id;
      p.createdAt = clock.instant();
      db.save(p);
    } else p.version++;
    event("plan", p.id, p.departmentId, "SAVE_PLAN", "维护方案草稿");
    stamp(v.requestKey, payload, p.id);
    return planView(p);
  }

  /** 草稿内维护任务，检测跨方案引用和循环，所有编辑增加方案修订。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object saveStep(Long planId, Long stepId, StepInput v) {
    gate("plan.write");
    var p = plan(planId);
    assigned(p.authorId);
    if (v == null) throw new Problem(400, "INVALID_INPUT");
    String payload = payload("step-save", List.of(planId, stepId == null ? 0L : stepId), v);
    Long retry = replay(v.requestKey, payload);
    if (retry != null) return detail(planId);
    state(p.status, "DRAFT");
    version(p.version, v.version);
    var all = steps(planId);
    if (stepId == null && all.size() >= 100) throw new Problem(400, "STEP_LIMIT");
    var s = stepId == null ? new CutoverStep() : step(p, stepId);
    s.planId = p.id;
    s.title = text(v.title, 160);
    s.ownerId = eligible(v.ownerId, p.departmentId, "task.execute").id;
    s.reviewerId = eligible(v.reviewerId, p.departmentId, "task.verify").id;
    if (s.ownerId.equals(s.reviewerId) || s.ownerId.equals(p.directorId))
      throw new Problem(400, "INDEPENDENCE_REQUIRED");
    if (v.minutes == null
        || v.minutes < 1
        || v.minutes > 1440
        || v.dependencies == null
        || v.dependencies.size() > 100) throw new Problem(400, "INVALID_INPUT");
    s.minutes = v.minutes;
    s.instructions = evidence(v.instructions);
    s.verification = evidence(v.verification);
    s.rollback = evidence(v.rollback);
    var known = new HashSet<>(all.stream().map(x -> x.id).toList());
    if (!known.containsAll(v.dependencies)) throw new Problem(400, "INVALID_DEPENDENCY");
    s.dependencies = new HashSet<>(v.dependencies);
    if (stepId == null) db.save(s);
    var graph = graph(steps(planId));
    graph.put(s.id, s.dependencies);
    RunbookPolicy.validate(graph);
    p.version++;
    event("plan", p.id, p.departmentId, "SAVE_STEP", "任务 #" + s.id + "：" + s.title);
    stamp(v.requestKey, payload, p.id);
    return detail(p.id);
  }

  /** 删除未提交的本人草稿或无引用的任务，不删除提交历史。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void delete(Long planId, Long stepId, Long revision) {
    gate("plan.write");
    var p = plan(planId);
    assigned(p.authorId);
    state(p.status, "DRAFT");
    version(p.version, revision);
    if (stepId != null) {
      var s = step(p, stepId);
      if (steps(planId).stream().anyMatch(x -> x.dependencies.contains(stepId)))
        throw new Problem(409, "DEPENDENCY_REFERENCED");
      db.delete(s);
      p.version++;
      event("plan", p.id, p.departmentId, "DELETE_STEP", "删除未执行任务 #" + stepId);
    } else {
      if (p.submitted) throw new Problem(409, "HISTORY_PROTECTED");
      for (var s : steps(planId)) s.dependencies.clear();
      db.flush();
      for (var s : steps(planId)) db.delete(s);
      for (var e :
          db.query(FlowEvent.class, "from FlowEvent where kind='plan' and objectId=?1", p.id))
        db.delete(e);
      db.delete(p);
      access.audit("DELETE_DRAFT", planId, p.departmentId);
    }
  }

  /** 提交、独立冻结放行、退回或退役；管理员不能代替指定指挥。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object planCommand(Long id, String action, Command v) {
    gate(action.equals("submit") ? "plan.write" : "plan.direct");
    var p = plan(id);
    if (v == null) throw new Problem(400, "INVALID_INPUT");
    String payload = payload("plan-" + action, id, v);
    Long retry = replay(v.requestKey, payload);
    if (retry != null) return detail(id);
    version(p.version, v.version);
    String note = evidence(v.note);
    switch (action) {
      case "submit" -> {
        assigned(p.authorId);
        state(p.status, "DRAFT");
        validatePlan(p);
        p.status = "REVIEW";
        p.submitted = true;
      }
      case "approve" -> {
        assigned(p.directorId);
        state(p.status, "REVIEW");
        validatePlan(p);
        p.status = "READY";
      }
      case "return" -> {
        assigned(p.directorId);
        state(p.status, "REVIEW");
        p.status = "DRAFT";
      }
      case "retire" -> {
        assigned(p.directorId);
        state(p.status, "READY");
        if (runs(id).stream().anyMatch(this::active)) throw new Problem(409, "ACTIVE_RUN");
        p.status = "RETIRED";
      }
      default -> throw new Problem(400, "INVALID_ACTION");
    }
    p.version++;
    event("plan", id, p.departmentId, action.toUpperCase(Locale.ROOT), note);
    stamp(v.requestKey, payload, id);
    return detail(id);
  }

  /** 基于同一冻结方案启动独立运行，正式切换要求已验收演练且无活动运行。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object createRun(Long id, RunInput v) {
    gate("plan.direct");
    var p = plan(id);
    assigned(p.directorId);
    if (v == null) throw new Problem(400, "INVALID_INPUT");
    String payload = payload("run-create", id, v);
    Long retry = replay(v.requestKey, payload);
    if (retry != null) return runDetail(retry);
    state(p.status, "READY");
    version(p.version, v.version);
    validatePlan(p);
    if (!Set.of("REHEARSAL", "LIVE").contains(v.mode == null ? "" : v.mode))
      throw new Problem(400, "INVALID_INPUT");
    var history = runs(id);
    if (history.size() >= 200) throw new Problem(400, "RUN_LIMIT");
    if (history.stream().anyMatch(this::active)) throw new Problem(409, "ACTIVE_RUN");
    if (v.mode.equals("LIVE")
        && history.stream()
            .noneMatch(r -> r.mode.equals("REHEARSAL") && r.status.equals("ACCEPTED")))
      throw new Problem(409, "REHEARSAL_REQUIRED");
    if (v.deadline == null
        || !v.deadline.isAfter(clock.instant())
        || v.deadline.isAfter(clock.instant().plus(Duration.ofHours(windowHours()))))
      throw new Problem(400, "INVALID_DEADLINE");
    var r = new CutoverRun();
    r.planId = id;
    r.departmentId = p.departmentId;
    r.mode = v.mode;
    r.reference = text(v.reference, 100);
    r.deadline = v.deadline;
    r.startedAt = clock.instant();
    db.save(r);
    for (var s : steps(id)) {
      var t = new RunTask();
      t.runId = r.id;
      t.stepId = s.id;
      db.save(t);
    }
    p.version++;
    event("run", r.id, p.departmentId, "START_RUN", r.mode + " / " + r.reference);
    stamp(v.requestKey, payload, r.id);
    return runDetail(r.id);
  }

  /** 当前范围的执行任务和事件；所有任务引用不可变基线。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object runDetail(Long id) {
    access.require("plan.read");
    var r = db.get(CutoverRun.class, id);
    var p = plan(r.planId);
    return Map.of(
        "record",
        runView(r),
        "plan",
        planView(p),
        "tasks",
        tasks(id).stream().map(t -> taskView(t, step(p, t.stepId))).toList(),
        "events",
        events("run", id));
  }

  /** 指定人员执行/核验；失败阻断，补偿须按依赖逆序并再次独立核验。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object taskCommand(Long runId, Long taskId, String action, Command v) {
    boolean verification =
        Set.of("pass", "reject", "rollback-pass", "rollback-reject").contains(action);
    gate(verification ? "task.verify" : "task.execute");
    var r = db.get(CutoverRun.class, runId);
    var p = plan(r.planId);
    var t = db.get(RunTask.class, taskId);
    if (!t.runId.equals(runId)) throw new Problem(404, "NOT_FOUND");
    var s = step(p, t.stepId);
    assigned(verification ? s.reviewerId : s.ownerId);
    eligible(
        verification ? s.reviewerId : s.ownerId,
        p.departmentId,
        verification ? "task.verify" : "task.execute");
    if (v == null) throw new Problem(400, "INVALID_INPUT");
    String payload = payload("task-" + action, List.of(runId, taskId), v);
    Long retry = replay(v.requestKey, payload);
    if (retry != null) return runDetail(runId);
    version(r.version, v.version);
    String note = evidence(v.note);
    switch (action) {
      case "start" -> {
        state(r.status, "RUNNING");
        deadline(r);
        state(t.status, "PENDING");
        var states = new HashMap<Long, String>();
        for (var x : tasks(runId)) states.put(x.stepId, x.status);
        if (!RunbookPolicy.ready(s.dependencies, states))
          throw new Problem(409, "DEPENDENCY_BLOCKED");
        t.status = "RUNNING";
        t.executionEvidence = note;
      }
      case "complete" -> {
        state(r.status, "RUNNING");
        deadline(r);
        state(t.status, "RUNNING");
        t.status = "VERIFYING";
        t.executionEvidence = note;
      }
      case "pass" -> {
        state(r.status, "RUNNING");
        deadline(r);
        state(t.status, "VERIFYING");
        t.status = "DONE";
        t.reviewEvidence = note;
        if (tasks(runId).stream().allMatch(x -> x.status.equals("DONE"))) r.status = "VERIFYING";
      }
      case "fail" -> {
        state(r.status, "RUNNING");
        state(t.status, "RUNNING");
        t.status = "FAILED";
        t.executionEvidence = note;
        r.status = "BLOCKED";
      }
      case "reject" -> {
        state(r.status, "RUNNING");
        state(t.status, "VERIFYING");
        t.status = "FAILED";
        t.reviewEvidence = note;
        r.status = "BLOCKED";
      }
      case "rollback" -> {
        state(r.status, "ROLLING_BACK");
        state(t.rollbackStatus, "PENDING");
        var remaining = new HashSet<Long>();
        for (var x : tasks(runId))
          if (!Set.of("NONE", "DONE").contains(x.rollbackStatus)) remaining.add(x.stepId);
        if (!RunbookPolicy.canRollback(s.id, graph(steps(p.id)), remaining))
          throw new Problem(409, "ROLLBACK_ORDER");
        t.rollbackStatus = "VERIFYING";
        t.rollbackEvidence = note;
      }
      case "rollback-pass" -> {
        state(r.status, "ROLLING_BACK");
        state(t.rollbackStatus, "VERIFYING");
        t.rollbackStatus = "DONE";
        t.rollbackReview = note;
      }
      case "rollback-reject" -> {
        state(r.status, "ROLLING_BACK");
        state(t.rollbackStatus, "VERIFYING");
        t.rollbackStatus = "PENDING";
        t.rollbackReview = note;
      }
      default -> throw new Problem(400, "INVALID_ACTION");
    }
    r.version++;
    event(
        "run", runId, p.departmentId, action.toUpperCase(Locale.ROOT), "任务 #" + s.id + "：" + note);
    stamp(v.requestKey, payload, runId);
    return runDetail(runId);
  }

  /** 指挥人验收完整运行或启动补偿并核对最终恢复依据。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object runCommand(Long id, String action, Command v) {
    gate("plan.direct");
    var r = db.get(CutoverRun.class, id);
    var p = plan(r.planId);
    assigned(p.directorId);
    eligible(p.directorId, p.departmentId, "plan.direct");
    if (v == null) throw new Problem(400, "INVALID_INPUT");
    String payload = payload("run-" + action, id, v);
    Long retry = replay(v.requestKey, payload);
    if (retry != null) return runDetail(id);
    version(r.version, v.version);
    String note = evidence(v.note);
    switch (action) {
      case "accept" -> {
        state(r.status, "VERIFYING");
        deadline(r);
        if (tasks(id).stream().anyMatch(t -> !t.status.equals("DONE")))
          throw new Problem(409, "INCOMPLETE_RUN");
        r.status = "ACCEPTED";
      }
      case "abort" -> {
        if (!Set.of("RUNNING", "BLOCKED", "VERIFYING").contains(r.status))
          throw new Problem(409, "INVALID_STATE");
        r.status = "ROLLING_BACK";
        for (var t : tasks(id)) if (!t.status.equals("PENDING")) t.rollbackStatus = "PENDING";
      }
      case "finish-rollback" -> {
        state(r.status, "ROLLING_BACK");
        if (tasks(id).stream().anyMatch(t -> !Set.of("NONE", "DONE").contains(t.rollbackStatus)))
          throw new Problem(409, "INCOMPLETE_ROLLBACK");
        r.status = "ROLLED_BACK";
      }
      default -> throw new Problem(400, "INVALID_ACTION");
    }
    r.decision = note;
    r.version++;
    event("run", id, p.departmentId, action.toUpperCase(Locale.ROOT), note);
    stamp(v.requestKey, payload, id);
    return runDetail(id);
  }

  /** 本人负责/核验的未结任务及本人起草或待指挥方案。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object workbench() {
    access.require("plan.read");
    var id = access.current().id;
    var ps = scoped();
    var todo = new ArrayList<Object>();
    for (var p : ps)
      for (var r : runs(p.id))
        if (active(r))
          for (var t : tasks(r.id)) {
            var s = step(p, t.stepId);
            if ((s.ownerId.equals(id) || s.reviewerId.equals(id))
                && (!t.status.equals("DONE") || !Set.of("NONE", "DONE").contains(t.rollbackStatus)))
              todo.add(
                  Map.of(
                      "runId",
                      r.id,
                      "planCode",
                      p.code,
                      "mode",
                      r.mode,
                      "runStatus",
                      r.status,
                      "task",
                      taskView(t, s)));
          }
    return Map.of(
        "plans",
        ps.stream()
            .filter(
                p ->
                    p.authorId.equals(id) && p.status.equals("DRAFT")
                        || p.directorId.equals(id) && Set.of("REVIEW", "READY").contains(p.status))
            .map(this::planView)
            .toList(),
        "tasks",
        todo);
  }

  /** 当前数据范围的实际计划、演练及运行统计。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object dashboard() {
    access.require("dashboard");
    var ps = scoped();
    var counts = new TreeMap<String, Long>();
    var modes = new TreeMap<String, Long>();
    var states = new TreeMap<String, Long>();
    long taskCount = 0, done = 0;
    for (var p : ps) {
      counts.merge(p.status, 1L, Long::sum);
      for (var r : runs(p.id)) {
        modes.merge(r.mode, 1L, Long::sum);
        states.merge(r.status, 1L, Long::sum);
        for (var t : tasks(r.id)) {
          taskCount++;
          if (t.status.equals("DONE")) done++;
        }
      }
    }
    return Map.of(
        "plans", counts, "modes", modes, "runs", states, "taskCount", taskCount, "verified", done);
  }

  /** 业务JSON报告，沿用相同权限与数据范围且不插入广告。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object report(Long id) {
    access.require("export");
    return runDetail(id);
  }

  /** 操作审计按部门限制，SELF账号不开放部门审计。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object audit() {
    access.require("audit");
    if (self()) throw new Problem(403, "FORBIDDEN");
    return db.all(AuditEvent.class).stream().filter(e -> access.visible(e.departmentId)).toList();
  }

  /** 放行和运行前重验人员资格及冻结图。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  private void validatePlan(CutoverPlan p) {
    eligible(p.directorId, p.departmentId, "plan.direct");
    if (p.authorId.equals(p.directorId)) throw new Problem(400, "INDEPENDENCE_REQUIRED");
    var all = steps(p.id);
    if (all.isEmpty()) throw new Problem(400, "NO_STEPS");
    for (var s : all) {
      eligible(s.ownerId, p.departmentId, "task.execute");
      eligible(s.reviewerId, p.departmentId, "task.verify");
      if (s.ownerId.equals(s.reviewerId) || s.ownerId.equals(p.directorId))
        throw new Problem(400, "INDEPENDENCE_REQUIRED");
    }
    RunbookPolicy.validate(graph(all));
  }

  private Map<Long, Set<Long>> graph(List<CutoverStep> ss) {
    var g = new HashMap<Long, Set<Long>>();
    for (var s : ss) g.put(s.id, s.dependencies);
    return g;
  }

  private List<CutoverStep> steps(Long id) {
    return db.query(CutoverStep.class, "from CutoverStep where planId=?1 order by id", id);
  }

  private List<CutoverRun> runs(Long id) {
    return db.query(CutoverRun.class, "from CutoverRun where planId=?1 order by id desc", id);
  }

  private List<RunTask> tasks(Long id) {
    return db.query(RunTask.class, "from RunTask where runId=?1 order by id", id);
  }

  private List<FlowEvent> events(String kind, Long id) {
    return db.jpql(
            FlowEvent.class, "from FlowEvent where kind=:kind and objectId=:id order by id desc")
        .setParameter("kind", kind)
        .setParameter("id", id)
        .setMaxResults(500)
        .getResultList();
  }

  private CutoverStep step(CutoverPlan p, Long id) {
    var s = db.get(CutoverStep.class, id);
    if (!s.planId.equals(p.id)) throw new Problem(400, "INVALID_DEPENDENCY");
    return s;
  }

  private boolean active(CutoverRun r) {
    return !Set.of("ACCEPTED", "ROLLED_BACK").contains(r.status);
  }

  private boolean self() {
    return access.role().scope.equals("SELF");
  }

  private String scopeQuery() {
    if (access.role().scope.equals("ALL")) return "1=1";
    if (self())
      return "(p.authorId=:actor or p.directorId=:actor or exists (select s.id from CutoverStep s where s.planId=p.id and (s.ownerId=:actor or s.reviewerId=:actor)))";
    return "p.departmentId=:department";
  }

  private void bindScope(jakarta.persistence.Query q) {
    if (self()) q.setParameter("actor", access.current().id);
    else if (!access.role().scope.equals("ALL"))
      q.setParameter("department", access.current().departmentId);
  }

  private List<CutoverPlan> scoped() {
    var q =
        db.jpql(
            CutoverPlan.class, "from CutoverPlan p where " + scopeQuery() + " order by p.id desc");
    bindScope(q);
    var ps = q.setMaxResults(1001).getResultList();
    if (ps.size() > 1000) throw new Problem(409, "REPORT_LIMIT");
    return ps;
  }

  private CutoverPlan plan(Long id) {
    if (id == null) throw new Problem(400, "INVALID_INPUT");
    var p = db.get(CutoverPlan.class, id);
    if (self()) {
      var actor = access.current().id;
      if (!p.authorId.equals(actor)
          && !p.directorId.equals(actor)
          && steps(id).stream()
              .noneMatch(s -> s.ownerId.equals(actor) || s.reviewerId.equals(actor)))
        throw new Problem(403, "OUT_OF_SCOPE");
    } else access.department(p.departmentId);
    return p;
  }

  private Account eligible(Long id, Long dept, String permission) {
    if (id == null) throw new Problem(400, "INVALID_INPUT");
    var a = db.get(Account.class, id);
    var role = db.get(AccessRole.class, a.roleId);
    if (!a.enabled
        || !role.permissions.contains(permission)
        || !role.scope.equals("ALL") && !a.departmentId.equals(dept)
        || permission.startsWith("plan.") && role.scope.equals("SELF"))
      throw new Problem(400, "INELIGIBLE_ACCOUNT");
    return a;
  }

  private void gate(String permission) {
    db.lock(Department.class, 1L);
    var a = access.current();
    db.refresh(a);
    db.refresh(db.get(AccessRole.class, a.roleId));
    access.require(permission);
  }

  private void assigned(Long id) {
    if (!access.current().id.equals(id)) throw new Problem(403, "NOT_ASSIGNED");
  }

  private void version(Long actual, Long expected) {
    if (!Objects.equals(actual, expected)) throw new Problem(409, "STALE_VERSION");
  }

  private void state(String actual, String expected) {
    if (!actual.equals(expected)) throw new Problem(409, "INVALID_STATE");
  }

  private void deadline(CutoverRun r) {
    if (!clock.instant().isBefore(r.deadline)) throw new Problem(409, "DEADLINE_REACHED");
  }

  private String text(String s, int max) {
    return AdminService.text(s, max);
  }

  private String evidence(String s) {
    var v = text(s, 2000);
    if (v.length() < 10) throw new Problem(400, "EVIDENCE_REQUIRED");
    return v;
  }

  private String name(Long id) {
    return db.get(Account.class, id).displayName;
  }

  private Map<String, Object> planView(CutoverPlan p) {
    var m = json.convertValue(p, Map.class);
    m.put("authorName", name(p.authorId));
    m.put("directorName", name(p.directorId));
    return m;
  }

  private Map<String, Object> stepView(CutoverStep s) {
    var m = json.convertValue(s, Map.class);
    m.put("ownerName", name(s.ownerId));
    m.put("reviewerName", name(s.reviewerId));
    return m;
  }

  private Map<String, Object> runView(CutoverRun r) {
    var m = json.convertValue(r, Map.class);
    m.put("overdue", active(r) && !clock.instant().isBefore(r.deadline));
    return m;
  }

  private Map<String, Object> taskView(RunTask t, CutoverStep s) {
    return Map.of("record", t, "step", stepView(s));
  }

  private void event(String kind, Long id, Long dept, String action, String note) {
    var e = new FlowEvent();
    e.kind = kind;
    e.objectId = id;
    e.departmentId = dept;
    e.actorId = access.current().id;
    e.action = action;
    e.note = note;
    e.createdAt = clock.instant();
    db.save(e);
    access.audit(action, id, dept);
  }

  /** 完整结构序列化，避免分隔符载荷碰撞。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  private String payload(String action, Object id, Object input) {
    return json.writeValueAsString(Arrays.asList(action, id, input));
  }

  private long windowHours() {
    return Long.parseLong(
        db.query(SystemSetting.class, "from SystemSetting where code='runWindowHours'")
            .getFirst()
            .value);
  }

  /** 幂等指纹绑定当前操作人。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  private String fingerprint(String p) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest((access.current().id + ":" + p).getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  /** 精确重试不重复业务事件，异载荷拒绝。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  private Long replay(String key, String p) {
    if (key == null || !key.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
      throw new Problem(400, "INVALID_REQUEST_KEY");
    var rows = db.query(CommandRecord.class, "from CommandRecord where requestKey=?1", key);
    if (rows.isEmpty()) return null;
    if (!rows.getFirst().fingerprint.equals(fingerprint(p)))
      throw new Problem(409, "IDEMPOTENCY_CONFLICT");
    return rows.getFirst().resultId;
  }

  private void stamp(String key, String p, Long id) {
    var c = new CommandRecord();
    c.requestKey = key;
    c.fingerprint = fingerprint(p);
    c.resultId = id;
    db.save(c);
  }
}
