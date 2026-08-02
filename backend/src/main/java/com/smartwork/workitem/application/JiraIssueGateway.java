package com.smartwork.workitem.application;

import com.smartwork.workitem.domain.Priority;

public interface JiraIssueGateway {

    Result createIssue(CreateRequest request);

    record CreateRequest(
            String summary,
            String description,
            String referenceKey,
            Priority priority) {
    }

    record Result(
            Status status,
            String issueId,
            String issueKey,
            String issueUrl,
            String detail) {

        public static Result success(String issueId, String issueKey, String issueUrl) {
            return new Result(Status.SUCCESS, issueId, issueKey, issueUrl, null);
        }

        public static Result notConfigured(String detail) {
            return new Result(Status.NOT_CONFIGURED, null, null, null, detail);
        }

        public static Result authenticationFailed(String detail) {
            return new Result(Status.AUTHENTICATION_FAILED, null, null, null, detail);
        }

        public static Result invalidConfiguration(String detail) {
            return new Result(Status.INVALID_CONFIGURATION, null, null, null, detail);
        }

        public static Result apiFailure(String detail) {
            return new Result(Status.API_FAILURE, null, null, null, detail);
        }
    }

    enum Status {
        SUCCESS,
        NOT_CONFIGURED,
        AUTHENTICATION_FAILED,
        INVALID_CONFIGURATION,
        API_FAILURE
    }
}
