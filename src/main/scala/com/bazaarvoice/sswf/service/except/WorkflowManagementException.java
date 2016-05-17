package com.bazaarvoice.sswf.service.except;

public class WorkflowManagementException extends RuntimeException {
    public WorkflowManagementException(final String s, final Throwable throwable) {
        super(s, throwable);
    }
}
