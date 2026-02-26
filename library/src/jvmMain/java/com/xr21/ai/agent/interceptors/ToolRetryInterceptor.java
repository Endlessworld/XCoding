//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package com.xr21.ai.agent.interceptors;

import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallResponse;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

public class ToolRetryInterceptor extends ToolInterceptor {
    private static final Logger log = LoggerFactory.getLogger(ToolRetryInterceptor.class);
    private final int maxRetries;
    private final Set<String> toolNames;
    private final Predicate<Exception> retryOn;
    private final OnFailureBehavior onFailure;
    private final Function<Exception, String> errorFormatter;
    private final double backoffFactor;
    private final long initialDelayMs;
    private final long maxDelayMs;
    private final boolean jitter;

    private ToolRetryInterceptor(Builder builder) {
        this.maxRetries = builder.maxRetries;
        this.toolNames = builder.toolNames != null ? new HashSet(builder.toolNames) : null;
        this.retryOn = builder.retryOn;
        this.onFailure = builder.onFailure;
        this.errorFormatter = builder.errorFormatter;
        this.backoffFactor = builder.backoffFactor;
        this.initialDelayMs = builder.initialDelayMs;
        this.maxDelayMs = builder.maxDelayMs;
        this.jitter = builder.jitter;
    }

    public static Builder builder() {
        return new Builder();
    }

    public ToolCallResponse interceptToolCall(ToolCallRequest request, ToolCallHandler handler) {
        String toolName = request.getToolName();
        if (this.toolNames != null && !this.toolNames.contains(toolName)) {
            return handler.call(request);
        } else {
            Exception lastException = null;
            int attempt = 0;

            while (true) {
                if (attempt <= this.maxRetries) {
                    try {
                        return handler.call(request);
                    } catch (Exception e) {
                        log.warn("Tool call attempt {} failed: {}", attempt, e.getMessage(), e);
                        lastException = e;
                        if (!this.retryOn.test(e)) {
                            log.debug("Exception {} not configured for retry, re-throwing", e.getClass()
                                    .getSimpleName());
                            throw e;
                        }

                        if (attempt != this.maxRetries) {
                            long delay = this.calculateDelay(attempt);
                            log.warn("Tool '{}' failed (attempt {}/{}), retrying in {}ms: {}", toolName, attempt + 1, this.maxRetries + 1, delay, e.getMessage());

                            try {
                                Thread.sleep(delay);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                throw new RuntimeException("Retry interrupted", ie);
                            }

                            ++attempt;
                            continue;
                        }
                    }
                }

                if (this.onFailure == OnFailureBehavior.RAISE) {
                    throw new RuntimeException("Tool call failed after " + (this.maxRetries + 1) + " attempts", lastException);
                }

                String errorMessage = this.errorFormatter != null ? this.errorFormatter.apply(lastException) : "Tool call failed after " + (this.maxRetries + 1) + " attempts: " + lastException.getMessage();
                log.error("Tool '{}' failed after {} attempts: {}", toolName, this.maxRetries + 1, lastException.getMessage());
                return ToolCallResponse.of(request.getToolCallId(), request.getToolName(), errorMessage);
            }
        }
    }

    private long calculateDelay(int retryNumber) {
        long delay = (long) ((double) this.initialDelayMs * Math.pow(this.backoffFactor, retryNumber));
        delay = Math.min(delay, this.maxDelayMs);
        if (this.jitter) {
            double jitterFactor = (double) 0.75F + Math.random() * (double) 0.5F;
            delay = (long) ((double) delay * jitterFactor);
        }

        return delay;
    }

    public String getName() {
        return "ToolRetry";
    }

    public enum OnFailureBehavior {
        RAISE, RETURN_MESSAGE
    }

    public static class Builder {
        private int maxRetries = 2;
        private Set<String> toolNames;
        private Predicate<Exception> retryOn = (e) -> true;
        private OnFailureBehavior onFailure;
        private Function<Exception, String> errorFormatter;
        private double backoffFactor;
        private long initialDelayMs;
        private long maxDelayMs;
        private boolean jitter;

        public Builder() {
            this.onFailure = OnFailureBehavior.RETURN_MESSAGE;
            this.backoffFactor = 2.0F;
            this.initialDelayMs = 1000L;
            this.maxDelayMs = 60000L;
            this.jitter = true;
        }

        public Builder maxRetries(int maxRetries) {
            if (maxRetries < 0) {
                throw new IllegalArgumentException("maxRetries must be >= 0");
            } else {
                this.maxRetries = maxRetries;
                return this;
            }
        }

        public Builder toolNames(Set<String> toolNames) {
            this.toolNames = toolNames;
            return this;
        }

        public Builder toolName(String toolName) {
            if (this.toolNames == null) {
                this.toolNames = new HashSet();
            }

            this.toolNames.add(toolName);
            return this;
        }

        @SafeVarargs
        public final Builder retryOn(Class<? extends Exception>... exceptionTypes) {
            Set<Class<? extends Exception>> types = new HashSet(Arrays.asList(exceptionTypes));
            this.retryOn = (e) -> {
                for (Class<? extends Exception> type : types) {
                    if (type.isInstance(e)) {
                        return true;
                    }
                }

                return false;
            };
            return this;
        }

        public Builder retryOn(Predicate<Exception> predicate) {
            this.retryOn = predicate;
            return this;
        }

        public Builder onFailure(OnFailureBehavior behavior) {
            this.onFailure = behavior;
            return this;
        }

        public Builder errorFormatter(Function<Exception, String> formatter) {
            this.errorFormatter = formatter;
            this.onFailure = OnFailureBehavior.RETURN_MESSAGE;
            return this;
        }

        public Builder backoffFactor(double backoffFactor) {
            this.backoffFactor = backoffFactor;
            return this;
        }

        public Builder initialDelay(long initialDelayMs) {
            this.initialDelayMs = initialDelayMs;
            return this;
        }

        public Builder maxDelay(long maxDelayMs) {
            this.maxDelayMs = maxDelayMs;
            return this;
        }

        public Builder jitter(boolean jitter) {
            this.jitter = jitter;
            return this;
        }

        public ToolRetryInterceptor build() {
            return new ToolRetryInterceptor(this);
        }
    }
}
