package dev.stym.tickradar;

final class StartupRollback {

    private StartupRollback() {
    }

    static void startOrRollBack(Runnable start, Runnable rollBack) {
        try {
            start.run();
        } catch (Throwable failure) {
            rollBackAfter(failure, rollBack);
            throw failure;
        }
    }

    private static void rollBackAfter(Throwable failure, Runnable rollBack) {
        try {
            rollBack.run();
        } catch (Throwable rollBackFailure) {
            failure.addSuppressed(rollBackFailure);
        }
    }
}
