// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.cutoverflow;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 空库仅创建管理目录、岗位及管理员，不生成切换或恢复事实。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Component
public class Bootstrap implements ApplicationRunner {
  final Store db;
  final BCryptPasswordEncoder encoder;
  final String password;

  public Bootstrap(
      Store d, BCryptPasswordEncoder e, @Value("${cutoverflow.admin-password}") String p) {
    db = d;
    encoder = e;
    password = p;
  }

  /** 已有库重启不覆盖账号和业务记录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (!db.all(Account.class).isEmpty()) return;
    AdminService.validatePassword(password);
    var dep = new Department();
    dep.name = "总部";
    db.save(dep);
    var names =
        Map.of(
            "plan.read",
            "查看方案与运行",
            "plan.write",
            "编制方案",
            "task.verify",
            "任务独立核验",
            "plan.direct",
            "方案放行与运行指挥",
            "task.execute",
            "任务执行与回退",
            "dashboard",
            "运行统计",
            "export",
            "导出记录",
            "audit",
            "操作审计",
            "admin",
            "系统管理");
    new TreeMap<>(names)
        .forEach(
            (k, v) -> {
              var p = new Permission();
              p.code = k;
              p.name = v;
              db.save(p);
            });
    role("管理员", "ALL", names.keySet());
    role("方案编制员", "DEPARTMENT", Set.of("plan.read", "plan.write", "dashboard", "export", "audit"));
    role("任务核验员", "SELF", Set.of("plan.read", "task.verify", "dashboard", "export"));
    role("切换指挥员", "DEPARTMENT", Set.of("plan.read", "plan.direct", "dashboard", "export", "audit"));
    role("任务执行员", "SELF", Set.of("plan.read", "task.execute", "dashboard", "export"));
    var a = new Account();
    a.username = "admin";
    a.displayName = "管理员";
    a.departmentId = dep.id;
    a.roleId = db.all(AccessRole.class).getFirst().id;
    a.passwordHash = encoder.encode(password);
    a.enabled = true;
    db.save(a);
    String[][] menu = {
      {"workbench", "切换工作台", "Workbench", "plan.read"},
      {"plans", "切换方案", "Plans", "plan.read"},
      {"dashboard", "运行统计", "Statistics", "dashboard"},
      {"audit", "操作审计", "Audit", "audit"},
      {"users", "账号管理", "Accounts", "admin"},
      {"roles", "角色与权限", "Roles", "admin"},
      {"departments", "部门管理", "Departments", "admin"},
      {"menus", "导航管理", "Navigation", "admin"},
      {"permissions", "权限目录", "Permissions", "admin"},
      {"dictionaries", "切换类别", "Categories", "admin"},
      {"settings", "系统参数", "Settings", "admin"}
    };
    for (int i = 0; i < menu.length; i++) {
      var m = new NavMenu();
      m.code = menu[i][0];
      m.name = menu[i][1];
      m.nameEn = menu[i][2];
      m.permissionCode = menu[i][3];
      m.position = i;
      m.enabled = true;
      db.save(m);
    }
    Map.of("timezone", "Asia/Shanghai", "companyName", "知华系统切换协作", "runWindowHours", "48")
        .forEach(
            (k, v) -> {
              var s = new SystemSetting();
              s.code = k;
              s.value = v;
              db.save(s);
            });
    for (var k :
        new String[][] {
          {"MIGRATION", "应用迁移", "Migration"},
          {"UPGRADE", "平台升级", "Upgrade"},
          {"OTHER", "其他切换", "Other"}
        }) {
      var e = new DictionaryEntry();
      e.type = "cutover";
      e.code = k[0];
      e.name = k[1];
      e.nameEn = k[2];
      db.save(e);
    }
  }

  private void role(String n, String s, Set<String> p) {
    var r = new AccessRole();
    r.name = n;
    r.scope = s;
    r.permissions = new HashSet<>(p);
    db.save(r);
  }
}
