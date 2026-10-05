// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.cutoverflow;

import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** 切换业务HTTP接口，权限和状态在事务服务中复核。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@RestController
@RequestMapping("/api")
public class ApiController {
  final CutoverService service;
  final AdminService admin;

  public ApiController(CutoverService s, AdminService a) {
    service = s;
    admin = a;
  }

  /** 业务选择目录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/options")
  public Object options() {
    return service.options();
  }

  /** 本人任务工作台。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/workbench")
  public Object workbench() {
    return service.workbench();
  }

  /** 范围内真实统计。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/dashboard")
  public Object dashboard() {
    return service.dashboard();
  }

  /** 部门操作审计。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/audit")
  public Object audit() {
    return service.audit();
  }

  /** 方案搜索筛选分页排序。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/plans")
  public Object list(
      @RequestParam(defaultValue = "") String search,
      @RequestParam(defaultValue = "") String status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "newest") String sort) {
    return service.list(search, status, page, size, sort);
  }

  /** 方案任务及运行历史。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/plans/{id}")
  public Object detail(@PathVariable Long id) {
    return service.detail(id);
  }

  /** 创建本人方案草稿。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/plans")
  public Object create(@RequestBody CutoverService.PlanInput v) {
    return service.save(null, v);
  }

  /** 编辑未冻结方案。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/plans/{id}")
  public Object save(@PathVariable Long id, @RequestBody CutoverService.PlanInput v) {
    return service.save(id, v);
  }

  /** 删除无提交历史草稿。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/plans/{id}")
  public Object delete(@PathVariable Long id, @RequestParam Long version) {
    service.delete(id, null, version);
    return Map.of("ok", true);
  }

  /** 独立放行及退役命令。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/plans/{id}/commands/{action}")
  public Object planCommand(
      @PathVariable Long id, @PathVariable String action, @RequestBody CutoverService.Command v) {
    return service.planCommand(id, action, v);
  }

  /** 新增有依赖的任务。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/plans/{id}/steps")
  public Object createStep(@PathVariable Long id, @RequestBody CutoverService.StepInput v) {
    return service.saveStep(id, null, v);
  }

  /** 维护任务及图结构。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/plans/{id}/steps/{step}")
  public Object updateStep(
      @PathVariable Long id, @PathVariable Long step, @RequestBody CutoverService.StepInput v) {
    return service.saveStep(id, step, v);
  }

  /** 删除未被依赖的任务。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/plans/{id}/steps/{step}")
  public Object deleteStep(
      @PathVariable Long id, @PathVariable Long step, @RequestParam Long version) {
    service.delete(id, step, version);
    return Map.of("ok", true);
  }

  /** 启动演练或正式运行。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/plans/{id}/runs")
  public Object createRun(@PathVariable Long id, @RequestBody CutoverService.RunInput v) {
    return service.createRun(id, v);
  }

  /** 实时任务执行详情。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/runs/{id}")
  public Object run(@PathVariable Long id) {
    return service.runDetail(id);
  }

  /** 运行验收及补偿决策。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/runs/{id}/commands/{action}")
  public Object runCommand(
      @PathVariable Long id, @PathVariable String action, @RequestBody CutoverService.Command v) {
    return service.runCommand(id, action, v);
  }

  /** 指定人员执行和独立核验。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/runs/{id}/tasks/{task}/commands/{action}")
  public Object taskCommand(
      @PathVariable Long id,
      @PathVariable Long task,
      @PathVariable String action,
      @RequestBody CutoverService.Command v) {
    return service.taskCommand(id, task, action, v);
  }

  /** 导出原始业务报告。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/runs/{id}/report.json")
  public ResponseEntity<Object> report(@PathVariable Long id) {
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=run-" + id + ".json")
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .body(service.report(id));
  }

  /** ALL系统管理员目录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/admin/{type}")
  public Object adminList(@PathVariable String type) {
    return admin.list(type);
  }

  /** 创建校验后的管理资源。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/admin/{type}")
  public Object adminCreate(@PathVariable String type, @RequestBody AdminService.Input v) {
    return admin.save(type, null, v);
  }

  /** 编辑管理资源并保护系统管理员。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/admin/{type}/{id}")
  public Object adminSave(
      @PathVariable String type, @PathVariable Long id, @RequestBody AdminService.Input v) {
    return admin.save(type, id, v);
  }

  /** 外键保护已引用管理资源。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/admin/{type}/{id}")
  public Object adminDelete(@PathVariable String type, @PathVariable Long id) {
    admin.delete(type, id);
    return Map.of("ok", true);
  }
}
