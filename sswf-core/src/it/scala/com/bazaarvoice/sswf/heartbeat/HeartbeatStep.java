package com.bazaarvoice.sswf.heartbeat;

import com.bazaarvoice.sswf.ConstantInProgressSleepFunction;
import com.bazaarvoice.sswf.InProgressSleepFunction;
import com.bazaarvoice.sswf.WorkflowStep;

public enum HeartbeatStep implements WorkflowStep {
    HEARTBEAT_STEP;

    @Override public int timeoutSeconds() {
        return 10;
    }

    @Override public InProgressSleepFunction inProgressSleepSecondsFn() {
        return new ConstantInProgressSleepFunction(1);
    }
}
