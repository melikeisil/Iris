package com.smartwork.workitem.api;

import com.smartwork.workitem.application.JiraStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/jira")
@Tag(name = "jira", description = "Read-only Jira integration status")
public class JiraController {

    private final JiraStatusService jira;

    public JiraController(JiraStatusService jira) {
        this.jira = jira;
    }

    @GetMapping("/status")
    @Operation(summary = "Current Jira integration readiness for this environment")
    public WorkItemDtos.JiraStatusResponse status() {
        var status = jira.status();
        return new WorkItemDtos.JiraStatusResponse(
                status.configured(),
                status.baseUrl(),
                status.projectKey(),
                status.issueType());
    }
}
