/*
 * Copyright © 2026 XR21 Team. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.xr21.ai.agent.channel;

import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.harness.agent.gateway.Gateway;
import io.agentscope.harness.agent.gateway.MsgContext;
import io.agentscope.harness.agent.gateway.channel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;


/**
 * ACP (Agent Client Protocol) channel adapter.
 * <p>
 * Bridges the ACP protocol layer with the Gateway/Agent execution layer via the
 * {@link Channel} interface. Designed for programmatic (in-process) invocation
 * from ACP protocol handlers, unlike external webhook-based channels (Feishu,
 * DingTalk, etc.).
 * </p>
 *
 * <p>ACP layer usage (Kotlin):</p>
 * <pre>{@code
 * val eventFlux = acpChannel.sendStream(
 *     sessionId = "session-xxx",
 *     messages = listOf(msg),
 *     context = mapOf("agentId" to "harness-coding-agent")
 * )
 * }</pre>
 *
 * <h2>Session routing</h2>
 * <p>{@link #sendStream} constructs a {@link MsgContext} keyed by
 * {@code sessionId} so each ACP session maps to a stable Gateway session.
 * Extra context (cwd, mode, etc.) is passed through {@link MsgContext#extra()}.</p>
 *
 * <h2>Outbound (proactive) delivery</h2>
 * <p>ACP sessions register a callback via
 * {@link #registerOutboundSession(String, Consumer)}.
 * When the gateway produces proactive messages (subagent announces),
 * {@link #deliver(OutboundAddress, List)} forwards them through the callback.</p>
 */
@SuppressWarnings("unused")
public class AcpChannel implements Channel {

    private static final Logger log = LoggerFactory.getLogger(AcpChannel.class);

    /** Logical channel identifier used in MsgContext and OutboundAddress. */
    public static final String CHANNEL_ID = "acp";

    private final ChannelConfig config;
    private final ConcurrentHashMap<String, Consumer<List<Msg>>> outboundSessions =
            new ConcurrentHashMap<>();

    private volatile Gateway gateway;

    /** Creates an AcpChannel with default config (uses Gateway's main agent). */
    public AcpChannel() {
        this(ChannelConfig.builder(CHANNEL_ID).build());
    }

    /**
     * Creates an AcpChannel with custom routing config.
     *
     * @param config channel-level routing (defaultAgentId, dmScope, bindings)
     */
    public AcpChannel(ChannelConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    // =================================================================
    //  Channel lifecycle
    // =================================================================

    @Override
    public String channelId() {
        return CHANNEL_ID;
    }

    @Override
    public ChannelConfig config() {
        return config;
    }

    @Override
    public void init(Gateway gateway) {
        if (this.gateway == null) {
            this.gateway = Objects.requireNonNull(gateway, "gateway");
            log.info("AcpChannel initialized with gateway: {}",
                    gateway.getClass().getSimpleName());
        }
    }

    @Override
    public void start() {
        log.info("AcpChannel started");
    }

    @Override
    public void stop() {
        outboundSessions.clear();
        log.info("AcpChannel stopped");
    }

    // =================================================================
    //  ACP-specific streaming API (bypasses chat-oriented routing)
    // =================================================================

    /**
     * Sends a streaming prompt request from the ACP protocol layer.
     * <p>
     * Constructs a {@link MsgContext} keyed by {@code sessionId} so each ACP
     * session maps to a stable Gateway session. The {@code extra} map is passed
     * as {@link MsgContext#extra()} available to the Gateway and agent middleware.
     * </p>
     *
     * @param sessionId ACP session identifier (used as Gateway session routing key)
     * @param messages  the agent's input messages (list of one or more Msg)
     * @param extra     additional context keys (e.g. "agentId", "cwd"); nullable
     * @return streaming Flux of {@link AgentEvent} from agent execution
     */
    public Flux<AgentEvent> sendStream(
            String sessionId,
            List<Msg> messages,
            Map<String, String> extra) {

        Gateway g = gateway;
        if (g == null) {
            return Flux.error(new IllegalStateException(
                    "AcpChannel has no gateway — init(Gateway) must be called first"));
        }
        if (sessionId == null || sessionId.isBlank()) {
            return Flux.error(
                    new IllegalArgumentException("sessionId must not be null or blank"));
        }
        if (messages == null || messages.isEmpty()) {
            return Flux.error(
                    new IllegalArgumentException("messages must not be null or empty"));
        }

        // Build MsgContext with sessionId as the room key for stable session mapping.
        // canonicalKey() → "acp|r:{sessionId}" → deterministic Gateway session.
        Map<String, String> extraMap = extra != null ? extra : Map.of();
        MsgContext ctx = new MsgContext(
                CHANNEL_ID,     // channel = "acp"
                null,            // group
                sessionId,       // room → canonicalKey includes this
                null,            // threadId
                null,            // threadTs
                extraMap,        // extra → "agentId", "cwd", etc.
                null             // userId
        );

        log.debug("AcpChannel.sendStream: sessionId={}, extra={}", sessionId, extraMap);
        return g.runStream(ctx, messages, null)
                .doOnError(err -> log.error(
                        "AcpChannel stream error for session {}: {}",
                        sessionId, err.getMessage()));
    }

    /**
     * Non-streaming variant of {@link #sendStream}.
     * Returns a single {@link Msg} reply aggregated from agent execution.
     */
    public Mono<Msg> send(
            String sessionId,
            List<Msg> messages,
            Map<String, String> extra) {

        Gateway g = gateway;
        if (g == null) {
            return Mono.error(new IllegalStateException(
                    "AcpChannel has no gateway — init(Gateway) must be called first"));
        }

        Map<String, String> extraMap = extra != null ? extra : Map.of();
        MsgContext ctx = new MsgContext(
                CHANNEL_ID, null, sessionId, null, null, extraMap, null);

        return g.run(ctx, messages, null);
    }

    // =================================================================
    //  Outbound session management
    // =================================================================

    /**
     * Registers a callback for outbound (proactive) messages addressed to the
     * given ACP session. When the gateway calls {@link #deliver}, the callback
     * is invoked so the ACP layer can forward messages via
     * {@code ClientSessionOperations.notify()}.
     *
     * @param sessionId ACP session identifier
     * @param callback  receives outbound messages; invoked on Gateway's thread
     */
    public void registerOutboundSession(
            String sessionId, Consumer<List<Msg>> callback) {
        if (sessionId != null && callback != null) {
            outboundSessions.put(sessionId, callback);
            log.debug("Registered outbound handler for ACP session: {}", sessionId);
        }
    }

    /** Removes the outbound callback for the given ACP session. */
    public void unregisterOutboundSession(String sessionId) {
        if (sessionId != null) {
            outboundSessions.remove(sessionId);
            log.debug("Unregistered outbound handler for ACP session: {}", sessionId);
        }
    }

    // =================================================================
    //  Standard Channel interface
    // =================================================================

    @Override
    public Mono<Msg> dispatch(InboundMessage message) {
        Objects.requireNonNull(message, "message");
        Gateway g = gateway;
        if (g == null) {
            return Mono.error(new IllegalStateException(
                    "AcpChannel has no gateway"));
        }
        ChannelRouter router = new ChannelRouter(config.defaultAgentId());
        RouteResult route = router.resolveRoute(config, message);
        log.debug("AcpChannel.dispatch: agentId={}, matchedBy={}",
                route.agentId(), route.matchedBy());
        return g.run(route.context(), message.messages(), route.outboundAddress());
    }

    @Override
    public Flux<AgentEvent> dispatchStream(InboundMessage message) {
        Objects.requireNonNull(message, "message");
        Gateway g = gateway;
        if (g == null) {
            return Flux.error(new IllegalStateException(
                    "AcpChannel has no gateway"));
        }
        ChannelRouter router = new ChannelRouter(config.defaultAgentId());
        RouteResult route = router.resolveRoute(config, message);
        log.debug("AcpChannel.dispatchStream: agentId={}, matchedBy={}",
                route.agentId(), route.matchedBy());
        return g.runStream(route.context(), message.messages(),
                route.outboundAddress());
    }

    @Override
    public void deliver(OutboundAddress address, List<Msg> messages) {
        if (address == null || messages == null || messages.isEmpty()) {
            return;
        }
        // The outbound address "to" uses "acp:{sessionId}" format.
        // Extract the sessionId to find the registered callback.
        String sessionId = extractSessionId(address);
        if (sessionId == null) {
            log.debug("AcpChannel.deliver: cannot extract sessionId "
                    + "from address.to='{}'", address.to());
            return;
        }
        Consumer<List<Msg>> callback = outboundSessions.get(sessionId);
        if (callback != null) {
            try {
                callback.accept(messages);
                log.debug("AcpChannel.deliver: delivered {} message(s) "
                        + "to session {}", messages.size(), sessionId);
            } catch (Exception e) {
                log.warn("AcpChannel.deliver: outbound callback failed "
                        + "for session {}: {}", sessionId, e.getMessage());
            }
        } else {
            log.debug("AcpChannel.deliver: no outbound handler for "
                    + "session {}", sessionId);
        }
    }

    // =================================================================
    //  Internal helpers
    // =================================================================

    /**
     * Extracts the ACP sessionId from an OutboundAddress.
     * Expects format "acp:{sessionId}" in the "to" field.
     */
    private static String extractSessionId(OutboundAddress address) {
        String to = address.to();
        if (to == null || to.isBlank()) {
            return null;
        }
        String prefix = CHANNEL_ID + ":";
        if (to.startsWith(prefix)) {
            return to.substring(prefix.length());
        }
        return to;
    }
}
