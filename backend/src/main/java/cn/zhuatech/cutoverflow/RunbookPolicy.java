// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.cutoverflow;

import java.util.*;

/** 切换依赖图与补偿顺序规则，无网络和生产操作。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
public final class RunbookPolicy {
  /** 验证所有前置任务存在且无自环或依赖循环。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static void validate(Map<Long, Set<Long>> graph) {
    var done = new HashSet<Long>();
    var visiting = new HashSet<Long>();
    for (var id : graph.keySet()) visit(id, graph, done, visiting);
  }

  private static void visit(
      Long id, Map<Long, Set<Long>> graph, Set<Long> done, Set<Long> visiting) {
    if (!graph.containsKey(id)) throw new Problem(400, "INVALID_DEPENDENCY");
    if (done.contains(id)) return;
    if (!visiting.add(id)) throw new Problem(400, "CYCLIC_DEPENDENCY");
    for (var dep : graph.get(id)) visit(dep, graph, done, visiting);
    visiting.remove(id);
    done.add(id);
  }

  /** 前置任务全部独立验收后才可开工。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static boolean ready(Set<Long> deps, Map<Long, String> states) {
    return deps.stream().allMatch(id -> "DONE".equals(states.get(id)));
  }

  /** 有尚未回退的已触达后继任务时，禁止回退前置任务。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static boolean canRollback(Long step, Map<Long, Set<Long>> graph, Set<Long> outstanding) {
    return outstanding.stream().noneMatch(id -> graph.getOrDefault(id, Set.of()).contains(step));
  }
}
