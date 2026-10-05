// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.cutoverflow;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

/** 依赖图和逆序补偿的纯规则测试。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
class RunbookPolicyTest {
  @Test
  void validForkJoin() {
    assertDoesNotThrow(
        () ->
            RunbookPolicy.validate(
                Map.of(1L, Set.of(), 2L, Set.of(1L), 3L, Set.of(1L), 4L, Set.of(2L, 3L))));
  }

  @Test
  void cycleAndUnknown() {
    assertThrows(
        Problem.class, () -> RunbookPolicy.validate(Map.of(1L, Set.of(2L), 2L, Set.of(1L))));
    assertThrows(Problem.class, () -> RunbookPolicy.validate(Map.of(1L, Set.of(9L))));
  }

  @Test
  void verificationRequired() {
    assertFalse(RunbookPolicy.ready(Set.of(1L, 2L), Map.of(1L, "DONE", 2L, "VERIFYING")));
    assertTrue(RunbookPolicy.ready(Set.of(1L), Map.of(1L, "DONE")));
  }

  @Test
  void reverseDependency() {
    var graph = Map.of(1L, Set.<Long>of(), 2L, Set.of(1L));
    assertFalse(RunbookPolicy.canRollback(1L, graph, Set.of(1L, 2L)));
    assertTrue(RunbookPolicy.canRollback(2L, graph, Set.of(1L, 2L)));
    assertTrue(RunbookPolicy.canRollback(1L, graph, Set.of(1L)));
  }
}
