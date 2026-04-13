package com.team.rag.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TeamAssistantTools {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String mailFrom;

    @Value("${mail.ticket.to}")
    private String mailTo;

    // @Tool("进行联网搜索。当知识库中没有相关信息，或者用户询问了关于外部世界的通用知识、股票、新闻等信息时，调用此工具获取外部数据。")
    // public String webSearch(@P("需要查询的关键字，请提取用户核心搜索意图") String keyword) {
    // log.info("【Tool 触发】正在执行联网搜索，关键词：{}", keyword);
    // // 这里暂时使用Mock数据代替。你可以在这里接入 Tavily/Bing/Google 或直接用 HttpURLConnection 发起搜索请求
    // return "【系统Mock返回】：关于“" + keyword +
    // "”的搜索结果：暂无互联网最新新闻。注意：这只是一条开发环境下的测试数据，请告知用户该功能正在最终对接中。";
    // }

    @Tool("提交需求工单或记录反馈。当用户询问系统是否包含某功能而你发现没有，或用户明确提出希望加入某个新功能点、报Bug时，调用此工具记录。")
    public String submitRequirementTicket(
            @P("工单或需求的详细描述") String description,
            @P("相关的系统模块或上下文，如果没有则填'通用'") String module) {

        log.info("【Tool 触发】收到来自用户的系统改进需求/建议。");
        log.info(" -> 涉及模块：{}", module);
        log.info(" -> 需求详细描述：{}", description);

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailFrom);
            message.setTo(mailTo);
            message.setSubject("【知识库助手自动工单】新功能建议 - 模块：" + module);
            message.setText("智能助手收集到一条用户的反馈信息：\n\n【功能模块】：\n" + module + "\n\n【详细描述】：\n" + description);

            mailSender.send(message);
            log.info("工单已经通过 JavaMailSender 成功发送！");

            return "需求记录成功！请温柔地告诉用户：您的宝贵建议已被成功打包成工单，并通过邮件发送给开发团队了，非常感谢反馈！";
        } catch (Exception e) {
            log.error("尝试发送工单邮件时失败", e);
            return "抱歉，由于邮件配置异常，工单暂未成功发送。";
        }
    }
}
