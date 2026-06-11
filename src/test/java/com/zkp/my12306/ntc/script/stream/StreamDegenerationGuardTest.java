package com.zkp.my12306.ntc.script.stream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StreamDegenerationGuardTest {

    private static final String LONG_PREFIX = "林澈来到市档案馆，准备领取母亲的旧资料。".repeat(130);

    @Test
    void detectDegeneration_flagsReplacementChar() {
        assertTrue(StreamDegenerationGuard.detectDegeneration("正常文本\uFFFD继续"));
    }

    @Test
    void detectDegeneration_flagsPhraseRepeat() {
        assertTrue(StreamDegenerationGuard.detectDegeneration(LONG_PREFIX + "问题问题问题"));
    }

    @Test
    void detectDegeneration_flagsConsecutiveChars() {
        assertTrue(StreamDegenerationGuard.detectDegeneration(LONG_PREFIX + "信信信"));
    }

    @Test
    void detectDegeneration_ignoresNormalScript() {
        String normal = """
                林澈：这封邮件怎么回事？
                许知遥：（冷静）你母亲十五年前的事，你想知道吗？
                旁白：登记表最下行写着「林晚舟」，十五年前。
                """;
        assertFalse(StreamDegenerationGuard.detectDegeneration(normal));
    }

    @Test
    void detectDegeneration_ignoresStructuredYaml() {
        String yaml = """
                文档类型: 按章剧本片段
                元信息:
                  标题: 测试
                场景列表:
                  - 场景编号: 场景_001
                    场景标题: 开场
                    剧本正文: |
                      动作：林澈推门而入。
                      旁白：夜色笼罩走廊。
                      动作：他停下脚步。
                说明:
                  改编策略: 保留原文调查线
                  保留重点: 邮件、档案、反转
                  补充内容: 补充环境描写
                  省略内容: 无
                  修改建议: 可加强结尾悬念
                """;
        assertFalse(StreamDegenerationGuard.detectDegeneration(yaml.repeat(8)));
    }
}
