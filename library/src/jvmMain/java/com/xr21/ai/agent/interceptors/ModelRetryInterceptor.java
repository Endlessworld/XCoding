/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.xr21.ai.agent.interceptors;

import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

/**
 * Model call retry interceptor that handles both streaming (Flux) and blocking
 * (Message) model responses.
 * <p>
 * The stock {@code com.alibaba.cloud.ai...ModelRetryInterceptor} unconditionally casts
 * {@code ModelResponse#getMessage()} to {@link Message}, which throws a
 * {@link ClassCastException} when the agent runs in streaming mode (where
 * {@code getMessage()} is a {@code Flux<ChatResponse>}). This implementation
 * retries blocking calls synchronously and re-calls the handler to obtain a fresh
 * stream on retryable streaming failures (avoids resubscribing Spring AI's stateful
 * advisor chain, which would throw "No StreamAdvisors available to execute").
 */
public class ModelRetryInterceptor extends ModelInterceptor {

	private static final Logger log = LoggerFactory.getLogger(ModelRetryInterceptor.class);

	private final int maxAttempts;
	private final long initialDelay;
	private final long maxDelay;
	private final double backoffMultiplier;
	private final Predicate<Exception> retryableExceptionPredicate;

	private ModelRetryInterceptor(Builder builder) {
		this.maxAttempts = builder.maxAttempts;
		this.initialDelay = builder.initialDelay;
		this.maxDelay = builder.maxDelay;
		this.backoffMultiplier = builder.backoffMultiplier;
		this.retryableExceptionPredicate = builder.retryableExceptionPredicate;
	}

	public static Builder builder() {
		return new Builder();
	}

	@Override
	public ModelResponse interceptModel(ModelRequest request, ModelCallHandler handler) {
		Exception lastException = null;
		long currentDelay = initialDelay;

		for (int attempt = 1; attempt <= maxAttempts; attempt++) {
			try {
				if (attempt > 1) {
					log.info("Retry model call, on the {}th attempt (out of {} attempts).", attempt, maxAttempts);
				}

				ModelResponse modelResponse = handler.call(request);
				Object responseMessage = modelResponse.getMessage();

				// Streaming mode: the model call happens lazily at subscription time.
				//
				// IMPORTANT: do NOT wrap this Flux with Reactor's retryWhen(). The stream is backed
				// by Spring AI's stateful DefaultAroundAdvisorChain (advisors are popped at
				// subscription time inside Flux.deferContextual). Resubscribing it re-enters the
				// already-drained advisor chain and throws "No StreamAdvisors available to execute".
				// Retry by re-calling handler.call() to obtain a fresh stream (fresh advisor chain).
				if (responseMessage instanceof Flux) {
					return new ModelResponse(
							withStreamingRetry(request, handler, castChatResponseFlux((Flux<?>) responseMessage), attempt, currentDelay));
				}

				// Blocking mode: check if the response carries an exception message.
				Message message = (Message) responseMessage;
				if (message != null && message.getText() != null && message.getText().startsWith("Exception:")) {
					String exceptionText = message.getText();
					log.warn("The model call returned an exception message: {}", exceptionText);
					if (attempt < maxAttempts && isRetryableExceptionMessage(exceptionText)) {
						lastException = new RuntimeException(exceptionText);
						if (currentDelay > 0) {
							sleep(currentDelay);
						}
						currentDelay = Math.min((long) (currentDelay * backoffMultiplier), maxDelay);
						continue;
					} else if (attempt >= maxAttempts) {
						throw new RuntimeException("Model call failed, maximum number of retries reached:" + exceptionText);
					}
					return modelResponse;
				}

				if (attempt > 1) {
					log.info("The model call succeeded after the {}th attempt.", attempt);
				}
				return modelResponse;

			} catch (Exception e) {
				lastException = e;
				log.warn("Model call failed (attempted {}/{}): {}", attempt, maxAttempts, e.getMessage());
				if (attempt >= maxAttempts) {
					throw new RuntimeException("Model call failed, maximum number of retries reached.", lastException);
				}
				if (!retryableExceptionPredicate.test(e)) {
					log.warn("Exceptions cannot be retried and are thrown immediately: {}", e.getMessage());
					throw new RuntimeException("Model call failed (non-retryable exception)", e);
				}
				if (currentDelay > 0) {
					sleep(currentDelay);
				}
				currentDelay = Math.min((long) (currentDelay * backoffMultiplier), maxDelay);
			}
		}

		throw new RuntimeException("Model call failed, maximum number of retries reached. " + maxAttempts, lastException);
	}

	/**
	 * Applies retry to a streaming model call without resubscribing the advisor chain.
	 * <p>
	 * The stream returned by {@code handler.call()} is backed by Spring AI's stateful
	 * {@code DefaultAroundAdvisorChain}. Reactor's {@code retryWhen()} would resubscribe
	 * the chain, re-entering an already-drained advisor deque and failing with
	 * "No StreamAdvisors available to execute". Instead, on a retryable error we call
	 * {@code handler.call()} again to obtain a fresh stream (fresh advisor chain).
	 */
	private Flux<ChatResponse> withStreamingRetry(ModelRequest request, ModelCallHandler handler,
			Flux<ChatResponse> responseFlux, int attempt, long currentDelay) {
		return Flux.defer(() -> {
			AtomicBoolean chunkEmitted = new AtomicBoolean(false);
			return responseFlux.doOnNext(response -> chunkEmitted.set(true)).onErrorResume(error -> {
				// Retrying after partial output would duplicate chunks downstream.
				if (chunkEmitted.get()) {
					return Flux.error(error);
				}
				return retryStreamingModelCall(request, handler, attempt, currentDelay, error);
			});
		});
	}

	private Flux<ChatResponse> retryStreamingModelCall(ModelRequest request, ModelCallHandler handler,
			int failedAttempt, long currentDelay, Throwable error) {
		Exception exception = toException(error);
		log.warn("Streaming model call failed (attempted {}/{}): {}", failedAttempt, maxAttempts,
				exception.getMessage());

		if (failedAttempt >= maxAttempts) {
			log.error("The maximum number of retries has been reached {}, and the streaming model call has failed.",
					maxAttempts);
			return Flux.error(new RuntimeException("Model call failed, maximum number of retries reached.", exception));
		}

		if (!isRetryableStreamingException(exception)) {
			log.warn("Exceptions cannot be retried and are thrown immediately: {}", exception.getMessage());
			return Flux.error(new RuntimeException("Model call failed (non-retryable exception)", exception));
		}

		int nextAttempt = failedAttempt + 1;
		long nextDelay = nextDelay(currentDelay);
		return delay(currentDelay).thenMany(Flux.defer(() -> {
			log.info("Retry model call, on the {}th attempt (out of {} attempts).", nextAttempt, maxAttempts);
			try {
				ModelResponse retryResponse = handler.call(request);
				return toStreamingFlux(request, handler, retryResponse, nextAttempt, nextDelay);
			}
			catch (Exception retryError) {
				return retryStreamingModelCall(request, handler, nextAttempt, nextDelay, retryError);
			}
		}));
	}

	private Flux<ChatResponse> toStreamingFlux(ModelRequest request, ModelCallHandler handler,
			ModelResponse modelResponse, int attempt, long currentDelay) {
		Object messagePayload = modelResponse.getMessage();
		if (messagePayload instanceof Flux<?> responseFlux) {
			return withStreamingRetry(request, handler, castChatResponseFlux(responseFlux), attempt, currentDelay);
		}
		if (messagePayload instanceof Message message) {
			if (message.getText() != null && message.getText().startsWith("Exception:")) {
				return retryStreamingModelCall(request, handler, attempt, currentDelay,
						new RuntimeException(message.getText()));
			}
			if (message instanceof AssistantMessage assistantMessage) {
				return Flux.just(new ChatResponse(List.of(new Generation(assistantMessage))));
			}
		}
		return Flux.error(new IllegalStateException(
				"Streaming model call returned unsupported response type: " + responseTypeName(messagePayload)));
	}

	@SuppressWarnings("unchecked")
	private Flux<ChatResponse> castChatResponseFlux(Flux<?> responseFlux) {
		return (Flux<ChatResponse>) responseFlux;
	}

	private Flux<ChatResponse> delay(long currentDelay) {
		if (currentDelay <= 0) {
			return Flux.empty();
		}
		return Flux.<ChatResponse>empty().delaySubscription(Duration.ofMillis(currentDelay));
	}

	private long nextDelay(long currentDelay) {
		return Math.min((long) (currentDelay * backoffMultiplier), maxDelay);
	}

	private Exception toException(Throwable error) {
		if (error instanceof Exception exception) {
			return exception;
		}
		return new RuntimeException(error);
	}

	private boolean isRetryableStreamingException(Exception exception) {
		String message = exception.getMessage();
		if (message != null && message.startsWith("Exception:")) {
			return isRetryableExceptionMessage(message);
		}
		return retryableExceptionPredicate.test(exception);
	}

	private String responseTypeName(Object messagePayload) {
		return messagePayload != null ? messagePayload.getClass().getName() : "null";
	}

	private void sleep(long delay) {
		try {
			log.info("Retry after {} ms", delay);
			Thread.sleep(delay);
		} catch (InterruptedException ie) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Retry interrupted", ie);
		}
	}

	private boolean isRetryableExceptionMessage(String exceptionText) {
		String lowerText = exceptionText.toLowerCase();
		return lowerText.contains("i/o error") ||
				lowerText.contains("remote host terminated") ||
				lowerText.contains("connection") ||
				lowerText.contains("timeout") ||
				lowerText.contains("network") ||
				lowerText.contains("handshake") ||
				lowerText.contains("socket");
	}

	@Override
	public String getName() {
		return "ModelRetry";
	}

	public static class Builder {
		private int maxAttempts = 3;
		private long initialDelay = 1000;
		private long maxDelay = 30000;
		private double backoffMultiplier = 2.0;
		private Predicate<Exception> retryableExceptionPredicate = Builder::isRetryableException;

		public Builder maxAttempts(int maxAttempts) {
			if (maxAttempts < 1) {
				throw new IllegalArgumentException("maxAttempts must be greater than or equal to 1");
			}
			this.maxAttempts = maxAttempts;
			return this;
		}

		public Builder initialDelay(long initialDelay) {
			if (initialDelay < 0) {
				throw new IllegalArgumentException("initialDelay must be greater than or equal to 0.");
			}
			this.initialDelay = initialDelay;
			return this;
		}

		public Builder maxDelay(long maxDelay) {
			if (maxDelay < 0) {
				throw new IllegalArgumentException("maxDelay must be greater than or equal to 0.");
			}
			this.maxDelay = maxDelay;
			return this;
		}

		public Builder backoffMultiplier(double backoffMultiplier) {
			if (backoffMultiplier < 1.0) {
				throw new IllegalArgumentException("The backoffMultiplier must be >= 1.0");
			}
			this.backoffMultiplier = backoffMultiplier;
			return this;
		}

		public Builder retryableExceptionPredicate(Predicate<Exception> predicate) {
			this.retryableExceptionPredicate = predicate;
			return this;
		}

		public ModelRetryInterceptor build() {
			return new ModelRetryInterceptor(this);
		}

		private static boolean isRetryableException(Exception e) {
			String message = e.getMessage();
			if (message == null) {
				return false;
			}
			String lowerMessage = message.toLowerCase();
			if (lowerMessage.contains("i/o error") ||
					lowerMessage.contains("remote host terminated") ||
					lowerMessage.contains("connection") ||
					lowerMessage.contains("timeout") ||
					lowerMessage.contains("handshake") ||
					lowerMessage.contains("socket")) {
				return true;
			}
			if (e.getClass().getName().contains("ResourceAccessException") ||
					e.getClass().getName().contains("WebClientRequestException")) {
				return true;
			}
			Throwable cause = e.getCause();
			while (cause != null) {
				String causeClassName = cause.getClass().getName();
				if (causeClassName.contains("IOException") ||
						causeClassName.contains("SocketException") ||
						causeClassName.contains("ConnectException") ||
						causeClassName.contains("TimeoutException") ||
						causeClassName.contains("SSLException")) {
					return true;
				}
				cause = cause.getCause();
			}
			return false;
		}
	}
}
