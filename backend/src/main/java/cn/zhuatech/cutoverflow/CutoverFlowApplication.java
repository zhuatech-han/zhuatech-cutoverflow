// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.cutoverflow;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/** 切换演练台账服务入口。 官网 https://www.zhuatech.cn/；商业咨询微信 zhuatech / zhuatech2。 */
@SpringBootApplication
public class CutoverFlowApplication {
  /** 启动切换演练服务。 官网 https://www.zhuatech.cn/；商业咨询微信 zhuatech / zhuatech2。 */
  public static void main(String[] args) {
    SpringApplication.run(CutoverFlowApplication.class, args);
  }

  /** 提供可替换的业务时钟。 官网 https://www.zhuatech.cn/；商业咨询微信 zhuatech / zhuatech2。 */
  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
